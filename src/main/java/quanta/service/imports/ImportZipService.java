package quanta.service.imports;

import java.io.InputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.SessionContext;
import quanta.exception.base.RuntimeEx;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.util.Const;
import quanta.util.ExUtil;
import quanta.util.LimitedInputStreamEx;
import quanta.util.StreamUtil;
import quanta.util.ThreadLocals;

/**
 * Import from ZIP files. Imports zip files that have the same type of directory structure and
 * content as the zip files that are exported from SubNode. The zip file doesn't of course have to
 * have been actually exported from SubNode in order to import it, but merely have the proper
 * layout/content.
 */

@Component
@Scope("prototype")
@Slf4j 
public class ImportZipService extends ImportArchiveBase {
	private ZipArchiveInputStream zis;

	/*
	 * imports the file directly from an internal resource file (classpath resource, built into WAR file
	 * itself)
	 */
	public SubNode inportFromResource(MongoSession ms, String resourceName, SubNode node, String nodeName) {
		Resource resource = context.getResource(resourceName);
		InputStream is = null;
		SubNode rootNode = null;
		try {
			is = resource.getInputStream();
			rootNode = importFromStream(ms, is, node, true);
		} catch (Exception e) {
			throw ExUtil.wrapEx(e);
		} finally {
			StreamUtil.close(is);
		}

		log.debug("Finished Input From Zip file.");
		update.saveSession(ms);
		return rootNode;
	}

	/* Returns the first node created which is always the root of the import 
	 * 
	 * Assumes ms has already been verified as owner of 'node'
	*/
	public SubNode importFromStream(MongoSession ms, InputStream inputStream, SubNode node, boolean isNonRequestThread) {
		SessionContext sc = ThreadLocals.getSC();
		if (used) {
			throw new RuntimeEx("Prototype bean used multiple times is not allowed.");
		}
		used = true;

		SubNode userNode = arun.run(as -> read.getUserNodeByUserName(as, sc.getUserName()));
		if (userNode == null) {
			throw new RuntimeEx("UserNode not found: " + sc.getUserName());
		}

		LimitedInputStreamEx is = null;
		try {
			targetPath = node.getPath();
			this.session = ms;

			// todo-2: replace with the true amount of storage this user has remaining. Admin is unlimited.
			int maxSize = sc.isAdmin() ? Integer.MAX_VALUE : Const.DEFAULT_USER_QUOTA;
			is = new LimitedInputStreamEx(inputStream, maxSize);
			zis = new ZipArchiveInputStream(is);

			ZipArchiveEntry entry;
			while ((entry = zis.getNextZipEntry()) != null) {
				if (!entry.isDirectory()) {
					processFile(entry, zis, userNode.getOwner());
				}
			}

		} catch (Exception ex) {
			throw ExUtil.wrapEx(ex);
		} finally {
			StreamUtil.close(is);
		}
		return importRootNode;
	}
}
