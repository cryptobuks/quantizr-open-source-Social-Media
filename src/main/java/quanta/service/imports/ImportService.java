package quanta.service.imports;

import java.io.BufferedInputStream;
import org.apache.commons.io.input.AutoCloseInputStream;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.util.ExUtil;
import quanta.util.StreamUtil;
import quanta.util.ThreadLocals;

@Component
@Slf4j
public class ImportService extends ServiceBase {
	public ResponseEntity<?> streamImport(MongoSession ms, String nodeId, MultipartFile[] uploadFiles) {
		if (nodeId == null) {
			throw ExUtil.wrapEx("target nodeId not provided");
		}
		ms = ThreadLocals.ensure(ms);

		SubNode node = read.getNode(ms, nodeId);
		if (node == null) {
			throw ExUtil.wrapEx("Node not found.");
		}
		auth.ownerAuth(ms, node);

		// This is critical to be correct so we run the actual query based determination of 'hasChildren'
		boolean hasChildren = read.hasChildrenByQuery(ms, node.getPath(), false);
		if (hasChildren) {
			throw ExUtil
					.wrapEx("You can only import into an empty node. There are direct children under path(a): " + node.getPath());
		}

		/*
		 * It's important to be sure there are absolutely no orphans at any level under this branch of the
		 * tree, so even though the check above told us there are no direct children we still need to run
		 * this recursive delete.
		 */
		delete.deleteUnderPath(ms, node.getPath());

		if (uploadFiles.length != 1) {
			throw ExUtil.wrapEx("Multiple file import not allowed");
		}

		MultipartFile uploadFile = uploadFiles[0];

		String fileName = uploadFile.getOriginalFilename();
		if (!StringUtils.isEmpty(fileName)) {
			log.debug("Uploading file: " + fileName);

			BufferedInputStream in = null;
			try {
				// Import ZIP files
				if (fileName.toLowerCase().endsWith(".zip")) {
					log.debug("Import ZIP to Node: " + node.getPath());
					in = new BufferedInputStream(new AutoCloseInputStream(uploadFile.getInputStream()));

					ImportZipService impSvc = (ImportZipService) context.getBean(ImportZipService.class);
					impSvc.importFromStream(ms, in, node, false);
					update.saveSession(ms);
				}
				// Import TAR files (non GZipped)
				else if (fileName.toLowerCase().endsWith(".tar")) {
					log.debug("Import TAR to Node: " + node.getPath());
					in = new BufferedInputStream(new AutoCloseInputStream(uploadFile.getInputStream()));
					ImportTarService impSvc = (ImportTarService) context.getBean(ImportTarService.class);
					impSvc.importFromStream(ms, in, node, false);
					update.saveSession(ms);
				}
				// Import TAR.GZ (GZipped TAR)
				else if (fileName.toLowerCase().endsWith(".tar.gz")) {
					log.debug("Import TAR.GZ to Node: " + node.getPath());
					in = new BufferedInputStream(new AutoCloseInputStream(uploadFile.getInputStream()));
					ImportTarService impSvc = (ImportTarService) context.getBean(ImportTarService.class);
					impSvc.importFromZippedStream(ms, in, node, false);
					update.saveSession(ms);
				} else {
					throw ExUtil.wrapEx("Only ZIP, TAR, TAR.GZ files are supported for importing.");
				}
				node.setHasChildren(true);
			} catch (Exception ex) {
				throw ExUtil.wrapEx(ex);
			} finally {
				StreamUtil.close(in);
			}
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}
}
