package quanta.service.ipfs;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.model.client.MFSDirEntry;
import quanta.model.ipfs.dag.MerkleLink;
import quanta.model.ipfs.file.IPFSDir;
import quanta.model.ipfs.file.IPFSDirEntry;
import quanta.model.ipfs.file.IPFSDirStat;
import quanta.mongo.MongoSession;
import quanta.request.DeleteMFSFileRequest;
import quanta.request.GetIPFSContentRequest;
import quanta.request.GetIPFSFilesRequest;
import quanta.util.ThreadLocals;
import quanta.util.XString;
import quanta.util.val.Val;

@Component
@Slf4j 
public class IPFSFiles extends ServiceBase {
    public static String API_FILES;

    @PostConstruct
    public void init() {
        API_FILES = prop.getIPFSApiBase() + "/files";
    }

    public IPFSDir getDir(String path) {
        checkIpfs();
        String url = API_FILES + "/ls?arg=" + path + "&long=true";
        return (IPFSDir) ipfs.postForJsonReply(url, IPFSDir.class);
    }

    /* Deletes the file or if a folder deletes it recursively */
    public boolean deletePath(String path) {
        checkIpfs();
        String url = API_FILES + "/rm?arg=" + path + "&force=true";
        return ipfs.postForJsonReply(url, Object.class) != null;
    }

    public boolean flushFiles(String path) {
        checkIpfs();
        String url = API_FILES + "/flush?arg=" + path;
        return ipfs.postForJsonReply(url, Object.class) != null;
    }

    public void addFile(MongoSession ms, String fileName, String mimeType, String content) {
        checkIpfs();
        addFile(ms, fileName, mimeType, content.getBytes(StandardCharsets.UTF_8));
    }

    public void addFile(MongoSession ms, String fileName, String mimeType, byte[] bytes) {
        checkIpfs();
        addEntry(ms, fileName, mimeType, new ByteArrayInputStream(bytes));
    }

    public void addEntry(MongoSession ms, String fileName, String mimeType, InputStream stream) {
        checkIpfs();
        ipfsFiles.addFileFromStream(ms, fileName, stream, mimeType, null);
    }

    public MerkleLink addFileFromStream(MongoSession ms, String fileName, InputStream stream, String mimeType,
            Val<Integer> streamSize) {
                checkIpfs();
        // NOTE: the 'write' endpoint doesn't send back any data (no way to get the CID back)
        return ipfs.writeFromStream(ms, API_FILES + "/write?arg=" + fileName + "&create=true&parents=true&truncate=true", stream,
                null, streamSize);
    }

    public IPFSDirStat pathStat(String path) {
        checkIpfs();
        String url = API_FILES + "/stat?arg=" + path;
        return (IPFSDirStat) ipfs.postForJsonReply(url, IPFSDirStat.class);
    }

    public String readFile(String path) {
        checkIpfs();
        String url = API_FILES + "/read?arg=" + path;
        return (String) ipfs.postForJsonReply(url, String.class);
    }

    /* This has a side effect of deleting empty directories */
    public void traverseDir(String path, HashSet<String> allFilePaths, boolean deleteEmptyDirs) {
        checkIpfs();
        log.debug("dumpDir: " + path);
        IPFSDir dir = getDir(path);
        if (dir != null) {
            log.debug("Dir: " + XString.prettyPrint(dir));

            if (deleteEmptyDirs && dir.getEntries() == null) {
                log.debug("DEL EMPTY FOLDER: " + path);
                deletePath(path);
                return;
            }

            for (IPFSDirEntry entry : dir.getEntries()) {
                String entryPath = path + "/" + entry.getName();

                // entries with 0 size are folders
                if (entry.getSize() == 0) {
                    traverseDir(entryPath, allFilePaths, deleteEmptyDirs);
                } else {
                    /*
                     * as a workaround to the IPFS bug, we rely on the logic of "if not a json file, it's a folder
                     */
                    if (!entry.getName().endsWith(".json")) {
                        traverseDir(entryPath, allFilePaths, deleteEmptyDirs);
                    } else {
                        log.debug("dump: " + entryPath);
                        // String readTest = readFile(entryPath);
                        // log.debug("readTest: " + readTest);
                        if (allFilePaths != null) {
                            allFilePaths.add(entryPath);
                        }
                    }
                }
            }
        }
    }

    public void deleteMFSFile(MongoSession ms, DeleteMFSFileRequest req) {
        checkIpfs();
        if (!ThreadLocals.getSC().allowWeb3()) {
            return;
        }

        // Note: we need to access the current thread, because the rest of the logic runs in a damon thread.
        String userNodeId = ThreadLocals.getSC().getUserNodeId().toHexString();

        // make sure the user is deleting something ONLY in their own folder.
        if (!req.getItem().startsWith("/" + userNodeId)) {
            throw new RuntimeException("You do not own the path: " + req.getItem());
        }

        deletePath(req.getItem());
    }

    public String getIPFSContent(MongoSession ms, GetIPFSContentRequest req) {
        checkIpfs();
        if (!ThreadLocals.getSC().allowWeb3()) {
            return null;
        }

        return readFile(req.getId());
    }

    public List<MFSDirEntry> getIPFSFiles(MongoSession ms, Val<String> folder, Val<String> cid, GetIPFSFilesRequest req) {
        checkIpfs();
        LinkedList<MFSDirEntry> files = new LinkedList<>();

        if (!ThreadLocals.getSC().allowWeb3()) {
            return null;
        }

        // Note: we need to access the current thread, because the rest of the logic runs in a damon thread.
        String userNodeId = ThreadLocals.getSC().getUserNodeId().toHexString();

        String mfsPath = req.getFolder() == null ? ("/" + userNodeId) : req.getFolder();
        folder.setVal(mfsPath);

        // opps, not a path
        if (!mfsPath.startsWith("/"))
            return null;

        IPFSDirStat pathStat = ipfsFiles.pathStat(mfsPath);
        if (pathStat != null) {
            cid.setVal(pathStat.getHash());
        }

        IPFSDir dir = getDir(mfsPath);
        if (dir != null) {
            log.debug("Dir: " + XString.prettyPrint(dir));
            if (dir.getEntries() != null) {
                for (IPFSDirEntry entry : dir.getEntries()) {
                    MFSDirEntry me = new MFSDirEntry();
                    me.setName(entry.getName());
                    me.setHash(entry.getHash());
                    me.setSize(entry.getSize());
                    me.setType(entry.getType());
                    files.add(me);
                }
            }
        }

        return files;
    }
}
