package quanta.service.mfs;

import java.util.HashSet;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.model.client.NodeProp;
import quanta.model.ipfs.file.IPFSDir;
import quanta.model.ipfs.file.IPFSDirEntry;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.mongo.model.SubNodeIdentity;
import quanta.request.LoadNodeFromIpfsRequest;
import quanta.response.LoadNodeFromIpfsResponse;
import quanta.util.ExUtil;
import quanta.util.ThreadLocals;
import quanta.util.XString;

/* Does the reverse of SyncToMFSService */
@Component
@Scope("prototype")
@Slf4j 
public class SyncFromMFSService extends ServiceBase {
	public static final ObjectMapper jsonMapper = new ObjectMapper();
	{
		jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	int failedFiles = 0;
	int matchingFiles = 0;
	int createdFiles = 0;

	MongoSession session;

	HashSet<String> allNodePaths = new HashSet<>();
	HashSet<String> allFilePaths = new HashSet<>();

	int totalNodes = 0;
	int orphansRemoved = 0;

	/**
	 * NOTE: req.path can be a path or CID. Path must of course be a LOCAL path, and is assumed if the
	 * string starts with '/', otherwise is treated as a CID.
	 *
	 * todo-2: currently this is an inefficient AND imcomplete algo, and only a work in progress, and needs these two enhancements:
	 * 
	 * 1) Do a subGraph query at the root first (req.getPath()) and build up a HashSet of all IDs, then
	 * use that to know which nodes already do exist, as a performance aid.
	 * 
	 * 2) Then at the end any of those that are NOT in the HashSet of all the node IDs that came from
	 * IPFS file scanning are known to be orphans to be removed.
	 * 
	 * So, for now, this algo will be slow, and will leave orphans around after pulling in from ipfs.
	 * (orphans meaning those nodes didn't exist in the ipfs files)
	 */
	public void writeNodes(MongoSession ms, LoadNodeFromIpfsRequest req, LoadNodeFromIpfsResponse res) {
		ms = ThreadLocals.ensure(ms);
		this.session = ms;

		try {
			// if the path is a CID we load from CID however this flow path was never perfected/finised and was
			// only ever a partially complete experiment
			if (!req.getPath().startsWith("/")) {
				if (traverseDag(null, req.getPath(), 0, 3)) {
					res.setMessage(buildReport());
					res.setSuccess(true);
				} else {
					res.setMessage("Unable to process: " + req.getPath());
					res.setSuccess(false);
				}
			}
			// Loading from an actual MFS path was completed, but is not very usable because we can only
			// access data from the local MFS
			else {
				if (processPath(req.getPath())) {
					res.setMessage(buildReport());
					res.setSuccess(true);
				} else {
					res.setMessage("Unable to process: " + req.getPath());
					res.setSuccess(false);
				}
			}
		} catch (Exception ex) {
			throw ExUtil.wrapEx(ex);
		}
	}

	public boolean loadNode(MongoSession ms, SubNode node) {
		String cid = node.getStr(NodeProp.IPFS_SCID);
		traverseDag(node, cid, 0, 1);
		return true;
	}

	/*
	 * WORK IN PROGRESS: This code will be the core of how we can have an IPFS explorer capability that
	 * can explore a DAG. Right now we just dump the content we find in MFS and don't try to build the
	 * node subgraph.
	 * 
	 * recursive will be the number of depth levels left allowed
	 */
	public boolean traverseDag(SubNode node, String cid, int level, int recursive) {
		boolean success = false;

		String indent = "";
		for (int i = 0; i < level; i++) {
			indent += "    ";
		}

		log.debug(indent + "DagGet CID: " + cid);

		//todo-2: I think IPFS has changed format and this will fail nowadays.
		// disabling this code until the return value from ipfsDAg is updated
		// MerkleNode dag = ipfsDag.getNode(cid);
		// if (ok(dag)) {
		// 	log.debug(indent + "Dag Dir: " + XString.prettyPrint(dag));

		// 	if (no(dag.getLinks())) {
		// 		return success;
		// 	}

		// 	for (MerkleLink entry : dag.getLinks()) {
		// 		String entryCid = entry.getCid().getPath();

		// 		/*
		// 		 * we rely on the logic of "if not a json file, it's a folder"
		// 		 */
		// 		if (!entry.getName().endsWith(".json")) {
		// 			log.debug(indent + "Processing Folder: " + entry.getName());
		// 			if (recursive > 0) {

		// 				/*
		// 				 * WARNING. This code is Incomplete: Left off working here: Need to create newNode as a child of
		// 				 * 'node', and put the entry.getCid.getPath() onto it's 'ipfs:scid' (make it explorable), and for
		// 				 * now we could either just put it's CID also in as the text for it, or else actually read the
		// 				 * text-content from the JSON (But we'd need to first query all subnodes under 'node' so we can be
		// 				 * sure not to recreate any duplicate nodes in case this scid already exists). Also once we DO load
		// 				 * a level we'd need to set a flag on the node to indicate we DID read it and to avoid attempting to
		// 				 * traverse any node that's already fully loaded.
		// 				 */
		// 				SubNode newNode = null;

		// 				traverseDag(newNode, entry.getCid().getPath(), level + 1, recursive - 1);
		// 			}
		// 		} else {
		// 			// read the node json from ipfs file
		// 			log.debug(indent + "Processing File: " + entry.getName());
		// 			String json = ipfsCat.getString(entryCid);
		// 			if (no(json)) {
		// 				log.debug("fileReadFailed: " + entryCid);
		// 				failedFiles++;
		// 			} else {
		// 				log.debug(indent + "json: " + json);

		// 				try {
		// 					SubNodePojo nodePojo = jsonMapper.readValue(json, SubNodePojo.class);
		// 					log.debug(indent + "nodePojo Parsed: " + XString.prettyPrint(nodePojo));
		// 					// update.save(session, nodePojo);
		// 					log.debug(indent + "Created Node: " + nodePojo.getId());
		// 				} catch (Exception e) {
		// 					// todo
		// 				}
		// 			}
		// 		}
		// 	}
		// 	success = true;
		// }
		return success;
	}

	public boolean processPath(String path) {
		boolean success = false;
		log.debug("processDir: " + path);

		IPFSDir dir = ipfsFiles.getDir(path);
		if (dir != null) {
			log.debug("Dir: " + XString.prettyPrint(dir));

			if (dir.getEntries() == null) {
				return success;
			}

			for (IPFSDirEntry entry : dir.getEntries()) {
				String entryPath = path + "/" + entry.getName();

				if (entry.getSize() == 0) {
					processPath(entryPath);
				}
				// else process a file
				else {
					// process directory
					if (entry.isDir()) {
						processPath(entryPath);
					}
					// process file
					else if (entry.isFile()) {
						log.debug("processFile: " + entryPath);

						// read the node json from ipfs file
						String json = ipfsFiles.readFile(entryPath);
						log.debug("JSON: " + json);
						if (json == null) {
							log.debug("fileReadFailed: " + entryPath);
							failedFiles++;
						} else {
							// we found the ipfs file json, so convert it to SubNode, and save
							SubNodeIdentity node = null;
							try {
								/*
								 * UPDATE: Now that we have SubNodePojo.java for deseralizing we no longer need SubNodeIdentity
								 * and we can refactor it out.
								 * 
								 * todo-2: WARNING! Simply deserializing a SubNode object causes it to become a REAL node and
								 * behave as if it were inserted into the DB, so that after json parses it 'read.getNode()' Mongo
								 * query will immediately find it and 'claim' that it's been inserted into the DB already.
								 * 
								 * Solution: I created SubNodeIdentity to perform a pure (partial) deserialization, but I need to
								 * check the rest of the codebase to be sure there's nowhere that this surprise will break things.
								 * (import/export logic?)
								 */
								node = jsonMapper.readValue(json, SubNodeIdentity.class);

								// we assume the node.id values can be the same across Federated instances.
								SubNode findNode = read.getNode(session, node.getId());
								if (findNode != null) {
									log.debug("Node existed: " + node.getId());
									matchingFiles++;
									// todo-2: check if node is same content here.
								} else {
									SubNode realNode = jsonMapper.readValue(json, SubNode.class);
									update.save(session, realNode);
									log.debug("Created Node: " + node.getId());
									createdFiles++;
								}
							} catch (Exception e) {
								failedFiles++;
								log.error("Failed parsing json: " + json, e);
							}
						}
					} else {
						log.debug("Unknown entry type.");
					}
				}
			}
			success = true;
		}
		return success;
	}

	private String buildReport() {
		StringBuilder sb = new StringBuilder();
		sb.append("Matching Files: " + matchingFiles + "\n");
		sb.append("Created Files: " + createdFiles + "\n");
		sb.append("Failed Files: " + failedFiles + "\n");
		return sb.toString();
	}
}
