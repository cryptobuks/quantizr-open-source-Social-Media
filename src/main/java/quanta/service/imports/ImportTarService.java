package quanta.service.imports;

import java.io.InputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.exception.base.RuntimeEx;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.util.ExUtil;
import quanta.util.StreamUtil;
import quanta.util.ThreadLocals;

@Component
@Scope("prototype")
@Slf4j 
public class ImportTarService extends ImportArchiveBase {
	private TarArchiveInputStream zis;

	public SubNode importFromZippedStream(MongoSession ms, InputStream is, SubNode node, boolean isNonRequestThread) {
		InputStream gis = null;
		try {
			gis = new GzipCompressorInputStream(is);
			return importFromStream(ms, gis, node, isNonRequestThread);
		} catch (Exception e) {
			throw ExUtil.wrapEx(e);
		} finally {
			StreamUtil.close(gis);
		}
	}

	/* Returns the first node created which is always the root of the import */
	public SubNode importFromStream(MongoSession ms, InputStream is, SubNode node, boolean isNonRequestThread) {
		if (used) {
			throw new RuntimeEx("Prototype bean used multiple times is not allowed.");
		}
		used = true;

		SubNode userNode = arun.run(as -> read.getUserNodeByUserName(as, ThreadLocals.getSC().getUserName()));
		if (userNode == null) {
			throw new RuntimeEx("UserNode not found: " + ThreadLocals.getSC().getUserName());
		}

		try {
			targetPath = node.getPath();
			this.session = ms;

			zis = new TarArchiveInputStream(is);
			TarArchiveEntry entry;
			while ((entry = zis.getNextTarEntry()) != null) {
				if (!entry.isDirectory()) {
					processFile(entry, zis, userNode.getOwner());
				}
			}
		} catch (final Exception ex) {
			throw ExUtil.wrapEx(ex);
		} finally {
			StreamUtil.close(zis);
		}
		return importRootNode;
	}
}
