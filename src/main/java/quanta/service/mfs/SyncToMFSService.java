package quanta.service.mfs;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.model.client.NodeProp;
import quanta.model.ipfs.file.IPFSDirStat;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.PublishNodeToIpfsRequest;
import quanta.response.PublishNodeToIpfsResponse;
import quanta.service.AclService;
import quanta.util.ExUtil;
import quanta.util.ThreadLocals;
import quanta.util.XString;

/**
 * Writes every node under the target subnode (recursively) to an IPFS Mutable File System (MFS) and
 * also removes any existing orphans from underneath the MFS path so that MFS is guaranteed to match
 * the nodes tree perfectly after this operation. The 'pth' (path) property on the node is used as
 * the path for MFS.
 * 
 * Security: Note that for now, until encryption is added we only write the 'public' nodes to IPFS
 * because IPFS is a public system.
 * 
 * Spring 'Prototype-scope Bean': We instantiate a new instance of this bean every time it's run.
 */

@Component
@Scope("prototype")
@Slf4j 
public class SyncToMFSService extends ServiceBase {
	MongoSession session;
	HashSet<String> allNodePaths = new HashSet<>();
	HashSet<String> allFilePaths = new HashSet<>();

	int totalNodes = 0;
	int orphansRemoved = 0;

	/*
	 * Creates MFS files (a folder structure/tree) with identical in content to the JSON of each node,
	 * and at the same MFS path as the 'pth' property (Node path)
	 */
	public void writeIpfsFiles(MongoSession ms, PublishNodeToIpfsRequest req, PublishNodeToIpfsResponse res) {
		log.debug("writeIpfsFiles: " + XString.prettyPrint(res));
		ms = ThreadLocals.ensure(ms);
		this.session = ms;
		String nodeId = req.getNodeId();
		SubNode node = read.getNode(ms, nodeId);

		if (!AclService.isPublic(ms, node)) {
			throw new RuntimeException("This experimental IPFS feature only works for public nodes.");
		}

		boolean success = false;
		try {
			auth.ownerAuth(ms, node);

			// Get all public nodes under the subgraph
			Iterable<SubNode> results = read.getSubGraph(ms, node, null, 0, true, true, true);

			/*
			 * process the root node then all subgraph nodes, which will write the JSON of each node to an MFS
			 * file, and add the generated filenames of all files to the 'allNodePaths' set.
			 */
			processNode(node);
			for (SubNode n : results) {
				processNode(n);
			}

			// flush all MFS files to disk
			ipfsFiles.flushFiles(node.getPath());

			// collects all paths into allFilePaths, and deletes any empty dirs as they're encounterd
			ipfsFiles.traverseDir(node.getPath(), allFilePaths, true);

			/*
			 * Now with 'allFilePaths' and 'allNodePaths' we can remove any orphaned MFS files, and this will
			 * result in the MFS files now being perfectly in sync with the Quanta Nodes
			 */
			removeOrphanFiles();

			// Now we can get the IPFS CID of the root and save it on a property on the root of the node we just saved to MFS.
			IPFSDirStat pathStat = ipfsFiles.pathStat(node.getPath());
			if (pathStat != null) {
				node.set(NodeProp.IPFS_CID, pathStat.getHash());
			}

			success = true;
		} catch (Exception ex) {
			throw ExUtil.wrapEx(ex);
		}

		res.setMessage(buildReport());
		res.setSuccess(success);
	}

	private String buildReport() {
		StringBuilder sb = new StringBuilder();
		sb.append("IPFS Sync complete\n\n");
		sb.append("Total Nodes: " + totalNodes + "\n");
		sb.append("Orphans Deleted: " + orphansRemoved + "\n");
		return sb.toString();
	}

	/* Remove orphans from the MFS file system, to sync up with the Quanta DB tree */
	private void removeOrphanFiles() {
		allFilePaths.forEach(path -> {
			/*
			 * if any file path is not a node path, it needes to be deleted.
			 * 
			 * todo-2: this will run more efficiently if we put path values into a list and then sort that list
			 * ascending by the length of the string, so any parent folders are guaranteed to get deleted before
			 * any of their subfolders (as a convenient consequence of children having to have longer paths than
			 * their parents!) are encountered, and we run therefore the minimal number of deletes required to
			 * accomplish this in every case!
			 */
			if (!allNodePaths.contains(path)) {
				try {
					// to delete the files we really just delete it's parent folder instead, because
					// each node has a decicated folder,
					// and this will delete children first.
					path = XString.stripIfEndsWith(path, "/n.json");
					log.debug("DELETE ORPHAN: " + path);
					ipfsFiles.deletePath(path);
					orphansRemoved++;
				} catch (Exception e) {
					/*
					 * I'm expecting this to fail when it attempts to delete any subfolders under folders that were
					 * already deleted because we may have just deleted their parents already in this same loop so...
					 * 
					 * todo-2: when we delete a folder, scan for all other folders that have that matching prefix and
					 * remove them too, because there's no need to call deleteFile on those.
					 */
				}
			}
		});
	}

	private void processNode(SubNode node) {
		// todo-2: This should eventually be unnecessary but for now we need it.
		snUtil.removeDefaultProps(node);

		snUtil.removeUnwantedPropsForIPFS(node);

		/*
		 * todo-2: this and other places needs to generate canonical JSON (basically just sorted properties
		 * ?) using this??
		 */
		// objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
		// objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
		String json = XString.prettyPrint(node);
		String fileName = node.getPath() + "/n.json";
		log.debug("Sync to IPFS: " + fileName);
		allNodePaths.add(fileName);
		totalNodes++;
		addFile(fileName, json);
	}

	/*
	 * todo-2: there *is* a way to eliminate the need for the 'checkExisting' flag and make it always
	 * true but for now the only way to return a CID even if not existing is to attempt to re-add every
	 * time so we do that for now because it's simpler
	 */
	private void addFile(String fileName, String json) {
		if (json.equals(ipfsFiles.readFile(fileName))) {
			log.debug("not writing. Content was up to date.");
			return;
		}
		addFile(fileName, json.getBytes(StandardCharsets.UTF_8));
	}

	private void addFile(String fileName, byte[] bytes) {
		addEntry(fileName, new ByteArrayInputStream(bytes));
	}

	private void addEntry(String fileName, InputStream stream) {
		ipfsFiles.addFileFromStream(session, fileName, stream, null, null);
	}
}
