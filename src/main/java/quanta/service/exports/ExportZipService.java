package quanta.service.exports;

import java.io.FileOutputStream;
import java.io.InputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.util.ExUtil;

@Component
@Scope("prototype")
@Slf4j 
public class ExportZipService extends ExportArchiveBase {
    private ZipArchiveOutputStream out = null;

    @Override
    public void openOutputStream(String fileName) {
        log.debug("Opening Export File: " + fileName);
        try {
            out = new ZipArchiveOutputStream(new FileOutputStream(fileName));
        } catch (Exception ex) {
            throw ExUtil.wrapEx(ex);
        }
    }

    @Override
    public void closeOutputStream() {
        try {
            out.finish();
            out.close();
        } catch (Exception ex) {
            throw ExUtil.wrapEx(ex);
        }
    }

    @Override
    public void addEntry(String fileName, byte[] bytes) {
        while (fileName.startsWith("/")) {
            fileName = fileName.substring(1);
        }
        // log.debug("Add Entry3: " + fileName + " bytes.length=" + bytes.length);
        try {
            ZipArchiveEntry entry = new ZipArchiveEntry(fileName);

            // I saw this in an example but haven't tried using it.
            // CRC32 crc32 = new CRC32();
            // crc32.update(tail);
            // zipEntry.setCrc(crc32.getValue());

            entry.setSize(bytes.length);
            out.putArchiveEntry(entry);
            out.write(bytes);
            out.closeArchiveEntry();
        } catch (Exception ex) {
            throw ExUtil.wrapEx(ex);
        }
    }

    @Override
    public void addEntry(String fileName, InputStream stream, long length) {
        while (fileName.startsWith("/")) {
            fileName = fileName.substring(1);
        }
        // log.debug("Add Entry4: " + fileName);
        try {
            ZipArchiveEntry entry = new ZipArchiveEntry(fileName);

            entry.setSize(length);
            out.putArchiveEntry(entry);
            IOUtils.copyLarge(stream, out, 0, length);
            out.closeArchiveEntry();
        } catch (Exception ex) {
            throw ExUtil.wrapEx(ex);
        }
    }

    @Override
    public String getFileExtension() {
        return "zip";
    }
}
