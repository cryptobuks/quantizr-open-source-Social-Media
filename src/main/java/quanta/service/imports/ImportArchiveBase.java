package quanta.service.imports;

import java.io.InputStream;
import java.util.HashMap;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.io.IOUtils;
import org.bson.types.ObjectId;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.model.client.Attachment;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.util.ExUtil;
import quanta.util.LimitedInputStreamEx;
import quanta.util.ThreadLocals;
import quanta.util.XString;
import quanta.util.val.Val;

@Slf4j 
public abstract class ImportArchiveBase extends ServiceBase {
	public static final ObjectMapper jsonMapper = new ObjectMapper();

	/*
	 * This is used to detect if this 'prototype scope' object might have been autowired, and is getting
	 * called for a second time which is NOT supported. Each use of this object requires a new instance
	 * of it.
	 */
	public boolean used;

	public String targetPath;
	public MongoSession session;
	public SubNode importRootNode;

	// JAR path to new nodeId map.
	public HashMap<String, String> pathToIdMap = new HashMap<>();

	public void processFile(ArchiveEntry entry, InputStream zis, ObjectId ownerId) {
		String name = entry.getName();
		int lastSlashIdx = name.lastIndexOf("/");
		String fileName = lastSlashIdx == -1 ? name : name.substring(lastSlashIdx + 1);
		String path = lastSlashIdx == -1 ? name : name.substring(0, lastSlashIdx);

		log.trace("Import FILE Entry: " + entry.getName());
		try {
			ThreadLocals.setParentCheckEnabled(false);
			Val<Boolean> done = new Val<>(false);

			// First try to attach the file as a binary which will fail gracefully and leave done=false if this
			// file's filename is not one of the attName(s) of it's attachment list.
			if (lastSlashIdx != -1) {
				// log.debug(" isBIN: " + entry.getName());
				String nodeId = pathToIdMap.get(path);
				if (nodeId != null) {
					arun.run(as -> {
						SubNode node = read.getNode(as, nodeId);
						if (node != null) {
							if (importBinary(entry, node, zis, fileName)) {
								done.setVal(true);
							}
						}
						return null;
					});
				}
			}

			// if we processed the above as an attachment we're done bail out.
			if (done.getVal())
				return;

			// HTML FILE
			if (mimeUtil.isHtmlTypeFileName(fileName)) {
				// log.debug(" isHTML: " + fileName);
				// we ignore the html files during import. Data will be in JSON files
			}
			// JSON FILE
			else if (mimeUtil.isJsonFileType(fileName)) {
				log.debug("  isJSON: " + fileName);
				String json = IOUtils.toString(zis, "UTF-8");
				// log.debug(" JSON STRING: " + json);

				// run unmarshalling as admin (otherwise setPath can bark about user being not same as owner)
				SubNode node = (SubNode) arun.run(as -> {
					try {
						SubNode n = jsonMapper.readValue(json, SubNode.class);
						// log.debug("Raw Marshal ID: " + n.getIdStr() + " content=" + n.getContent());

						// this may not be necessary but we definitely don't want this node cached now
						// with it's currently undetermined id.
						ThreadLocals.clean(n);

						// set nodeId to null right away so that as we start setting property values, it
						// won't cause the 'dirty' cache to start thinking this node needs to be cached
						// for the wrong id. We can't do any caching until we save to DB and it gets
						// it's new imported id value.
						n.setId(null);

						String newPath = mongoUtil.findAvailablePath(targetPath + n.getPath());
						n.setPath(newPath);

						// verifyParentPath=false signals to MongoListener to not waste cycles checking the path on this
						// to verify the parent exists upon saving, because we know the path is fine correct.
						n.verifyParentPath = false;

						// nullify name because we don't want to blow up indexes
						n.setName(null);
						n.setOwner(ownerId);
						log.debug("IMPORT NODE: " + XString.prettyPrint(n));
						return n;
					} catch (Exception e) {
						log.error("Failed unmarshalling node: " + json);
						return null;
					}
				});

				if (node == null) {
					throw new RuntimeException("import unmarshalling failed.");
				}

				/*
				 * when importing we want to keep all the attachment info EXCEPT the binary IDs because those will
				 * be changing and obsolete for the imported data, will be reassigned. Nullifying those makes sure
				 * the obsolete values cannot be reused.
				 */
				if (node.getAttachments() != null) {
					node.getAttachments().forEach((String key, Attachment att) -> {
						att.setBin(null);
					});
				}

				/*
				 * NOTE: It's important to save this node and NOT let the 'node' before this save, ever get set into
				 * the dirty cache either, so we can't call any setters on it UNTIL it's saved here and we get the
				 * DB to give us the new ID for it.
				 */
				update.save(session, node);
				pathToIdMap.put(path, node.getIdStr());
			}
		} catch (Exception ex) {
			throw ExUtil.wrapEx(ex);
		}
		finally {
			ThreadLocals.setParentCheckEnabled(true);
		}
	}

	/*
	 * This method assumes node has already been loaded which means as we process the zip stream we're
	 * expecting the JSON for the node to be encountered before any of the attachments.
	 * 
	 * Returns true only if we imported a file.
	 */
	public boolean importBinary(ArchiveEntry entry, SubNode node, InputStream zis, String fileName) {
		String attName = fileUtil.stripExtension(fileName);
		HashMap<String, Attachment> atts = node.getAttachments();
		if (atts == null)
			return false;

		/*
		 * note the filename in the imported JAR is the 'attName', but when we import we name the
		 * Attachment.name back to what it originally was before the export which is in the JSON, but also
		 * on the node we have now.
		 */
		Attachment att = atts.get(attName);
		if (att == null)
			return false;

		Long length = att.getSize();
		String mimeType = att.getMime();
		LimitedInputStreamEx lzis = new LimitedInputStreamEx(zis, Integer.MAX_VALUE);

		// log.debug("Attaching binary to nodeId: " + node.getIdStr());
		attach.attachBinaryFromStream(session, true, attName, node, null, fileName, length, lzis, mimeType, -1, -1, false,
				false, true, false, true, null, false);
		return true;
	}
}
