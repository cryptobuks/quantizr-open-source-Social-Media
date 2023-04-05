package quanta.service.exports;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Scope;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.model.client.Attachment;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.util.ExUtil;
import quanta.util.FileUtils;
import quanta.util.StreamUtil;
import quanta.util.val.Val;

/**
 * Import/Export of Raw JSON and Binaries to and from filesystem/classpath)
 */
@Component
@Scope("prototype")
@Slf4j 
public class ExportJsonService extends ServiceBase {
	/* This object is Threadsafe so this is the correct usage 'static final' */
	private static final ObjectMapper objectMapper = new ObjectMapper();
	static {
		objectMapper.setSerializationInclusion(Include.NON_NULL);
	}
	private static final ObjectWriter jsonWriter = objectMapper.writerWithDefaultPrettyPrinter();

	/*
	 * todo-2: need capability to handle binary files also, but before implementing that look for any
	 * export options on the grid api itself.
	 * 
	 * Dumps all nodes that have property "pth" starting with 'pathPrefix', or all nodes if pathPrefix
	 * is null.
	 */
	public String dumpAllNodes(MongoSession ms, String pathPrefix, String fileName) {
		try {
			if (!FileUtils.dirExists(prop.getAdminDataFolder())) {
				throw ExUtil.wrapEx("adminDataFolder does not exist");
			}

			String targetFolder = prop.getAdminDataFolder() + File.separator + fileName;
			FileUtils.createDirectory(targetFolder);

			/* This is not a typo, this path will be like ".../fileName/fileName.json" */
			String fullFileName = targetFolder + File.separator + fileName + ".json";

			Val<Integer> numDocs = new Val<>(0);
			Val<Integer> numBins = new Val<>(0);

			byte[] newLine = "\n,\n".getBytes(StandardCharsets.UTF_8);

			Query q = new Query();
			Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexRecursiveChildrenOfPath(pathPrefix));
			q.addCriteria(crit);

			Iterable<SubNode> iter = mongoUtil.find(q);
			BufferedOutputStream os = null;
			try {
				os = new BufferedOutputStream(new FileOutputStream(fullFileName));
				BufferedOutputStream _os = os;
				iter.forEach((node) -> {
					// todo-2: this is not yet handling multiple images, but this method isn't currently used.
					Attachment att = node.getFirstAttachment();
					String binFileName = att != null ? att.getFileName() : null;
					if (binFileName != null) {
						if (saveBinaryToFileSystem(binFileName, targetFolder, node)) {
							numBins.setVal(numBins.getVal() + 1);
						}
					} else {
						String path = node.getPath();
						log.debug("Node has no binary: " + path);
					}

					try {
						String json = jsonWriter.writeValueAsString(node);
						_os.write(json.getBytes(StandardCharsets.UTF_8));
						_os.write(newLine);
						numDocs.setVal(numDocs.getVal() + 1);
					} catch (Exception e) {
						// todo-2
						e.printStackTrace();
					}
				});
				os.flush();
			} catch (Exception ex) {
				throw ExUtil.wrapEx(ex);
			} finally {
				StreamUtil.close(os);
			}

			return "NodeCount: " + numDocs.getVal() + " exported to " + fileName + ". BinaryCount=" + numBins.getVal() + "<p>";
		} catch (Exception e) {
			return "Failed exporting " + fileName;
		}
	}

	// Not used, but let's keep this code for now.
	// private boolean readBinaryFromResource(MongoSession ms, SubNode node, String binFileName, String subFolder) {
	// 	boolean ret = false;

	// 	Attachment att = node.getAttachment(null, true, true);
	// 	String binMime = ok(att) ? att.getMime() : null;
	// 	ObjectId oid = node.getId();
	// 	if (ok(oid)) {

	// 		InputStream is = null;
	// 		LimitedInputStreamEx lis = null;
	// 		try {
	// 			String resourceName = "classpath:/nodes/" + subFolder + "/" + oid.toHexString() + "-" + binFileName;
	// 			Resource resource = context.getResource(resourceName);
	// 			is = resource.getInputStream();
	// 			lis = new LimitedInputStreamEx(is, user.getMaxUploadSize(ms));
	// 			attach.writeStream(ms, "", node, lis, binFileName, binMime, null);
	// 			update.save(ms, node);

	// 		} catch (Exception e) {
	// 			e.printStackTrace();
	// 		} finally {
	// 			StreamUtil.close(lis);
	// 		}
	// 	} else {
	// 		log.debug("unable to get oid");
	// 	}
	// 	return ret;
	// }

	private boolean saveBinaryToFileSystem(String binFileName, String targetFolder, SubNode node) {
		boolean ret = false;
		if (binFileName != null) {
			log.debug("FileName: " + binFileName);
		}

		InputStream is = attach.getStreamByNode(node, "");
		if (is != null) {
			try {
				String targetFileName = targetFolder + File.separator + node.getIdStr() + "-" + binFileName;
				File targetFile = new File(targetFileName);
				/* warning: we have our own FileUtils (conflict of name) */
				org.apache.commons.io.FileUtils.copyInputStreamToFile(is, targetFile);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				StreamUtil.close(is);
			}

			ret = true;
		} else {
			log.debug("Unable to get inputstream or oid.");
		}

		return ret;
	}

	/*
	 * Imports the data from /src/main/resources/nodes/[subFolder] into the db, which will update the
	 * targetPath node path (like "/r/public"), content on the tree.
	 * 
	 * NOTE: This code no longer being used, but I want to keep for future reference if we ever need to
	 * import JSON into the DB again in some automated way.
	 */
	// public String resetNode(MongoSession ms, String subFolder) {
	// 	try {
	// 		ThreadLocals.setParentCheckEnabled(false);

	// 		String resourceName = "classpath:/nodes/" + subFolder + "/" + subFolder + ".json";
	// 		Resource resource = context.getResource(resourceName);
	// 		InputStream is = resource.getInputStream();
	// 		BufferedReader in = new BufferedReader(new InputStreamReader(is));

	// 		try {
	// 			String line;
	// 			StringBuilder buf = new StringBuilder();

	// 			while (ok(line = in.readLine())) {
	// 				if (!line.equals(",")) {
	// 					buf.append(line);
	// 					buf.append("\n"); // not needed right?
	// 					continue;
	// 				}
	// 				String json = buf.toString();
	// 				buf.setLength(0);

	// 				// log.debug("JSON: " + json);

	// 				// jsonToNodeService.importJsonContent(json, node);
	// 				SubNode node = objectMapper.readValue(json, SubNode.class);
	// 				update.save(ms, node);

	// 				Attachment att = node.getAttachment();
	// 				String binFileName = ok(att) ? att.getFileName() : null;
	// 				if (ok(binFileName)) {
	// 					attach.deleteBinary(ms, "", node, null);
	// 					readBinaryFromResource(ms, node, binFileName, subFolder);
	// 				}
	// 			}
	// 		} finally {
	// 			ThreadLocals.setParentCheckEnabled(true);
	// 			StreamUtil.close(in);
	// 		}
	// 		log.debug("import successful.");
	// 	} catch (Exception ex) {
	// 		ex.printStackTrace();
	// 	}
	// 	return "success!";
	// }
}
