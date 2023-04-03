package quanta.service.exports;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.model.ipfs.dag.MerkleLink;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.ExportRequest;
import quanta.response.ExportResponse;
import quanta.util.ExUtil;
import quanta.util.FileUtils;
import quanta.util.StreamUtil;
import quanta.util.ThreadLocals;

@Component
@Scope("prototype")
@Slf4j 
public class ExportTextService extends ServiceBase {
	private MongoSession session;

	private BufferedOutputStream output = null;
	private String shortFileName;
	private String fullFileName;
	private static final byte[] NL = "\n".getBytes(StandardCharsets.UTF_8);
	private ExportRequest req;
	private ExportResponse res;

	/*
	 * Exports the node specified in the req. If the node specified is "/", or the repository root, then
	 * we don't expect a filename, because we will generate a timestamped one.
	 */
	public void export(MongoSession ms, ExportRequest req, ExportResponse res) {
		ms = ThreadLocals.ensure(ms);
		this.session = ms;
		this.req = req;
		this.res = res;
		String nodeId = req.getNodeId();

		if (!FileUtils.dirExists(prop.getAdminDataFolder())) {
			throw ExUtil.wrapEx("adminDataFolder does not exist");
		}

		if (nodeId.equals("/")) {
			throw ExUtil.wrapEx("Exporting entire repository is not supported.");
		} else {
			log.info("Exporting to Text File");
			exportNodeToFile(ms, nodeId);
			res.setFileName(shortFileName);
		}

		res.setSuccess(true);
	}

	private void exportNodeToFile(MongoSession ms, String nodeId) {
		if (!FileUtils.dirExists(prop.getAdminDataFolder())) {
			throw ExUtil.wrapEx("adminDataFolder does not exist.");
		}

		SubNode exportNode = read.getNode(ms, nodeId, true, null);
		String fileName = snUtil.getExportFileName(req.getFileName(), exportNode);
		shortFileName = fileName + ".md";
		fullFileName = prop.getAdminDataFolder() + File.separator + shortFileName;

		try {
			log.debug("Export Node: " + exportNode.getPath() + " to file " + fullFileName);
			output = new BufferedOutputStream(new FileOutputStream(fullFileName));
			recurseNode(exportNode, 0);
			output.flush();
			StreamUtil.close(output);

			if (req.isToIpfs()) {
				// now write the file we just generated out to IPFS.
				FileInputStream is = null;
				try {
					is = new FileInputStream(fullFileName);
					String mime = "text/markdown";
					MerkleLink ret = ipfs.addFromStream(ms, is, shortFileName, mime, null, false);
					ipfs.writeIpfsExportNode(ms, ret.getHash(), mime, shortFileName, null);
					res.setIpfsCid(ret.getHash());
					res.setIpfsMime(mime);
				} finally {
					StreamUtil.close(is);
				}
			}

		} catch (Exception ex) {
			throw ExUtil.wrapEx(ex);
		} finally {
			StreamUtil.close(output);
			(new File(fullFileName)).deleteOnExit();
		}
	}

	private void recurseNode(SubNode node, int level) {
		if (node == null)
			return;

		/* process the current node */
		processNode(node);

		Sort sort = Sort.by(Sort.Direction.ASC, SubNode.ORDINAL);

		for (SubNode n : read.getChildren(session, node, sort, null, 0)) {
			recurseNode(n, level + 1);
		}
	}

	private void processNode(SubNode node) {
		print(node.getContent());
		print("\n");
	}

	private void print(String val) {
		try {
			output.write(val.getBytes(StandardCharsets.UTF_8));
			if (!val.endsWith("\n")) {
				output.write(NL);
			}
		} catch (Exception ex) {
			throw ExUtil.wrapEx(ex);
		}
	}
}
