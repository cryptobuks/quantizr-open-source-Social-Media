package quanta.test;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.model.ipfs.dag.MerkleLink;
import quanta.model.ipfs.dag.MerkleNode;
import quanta.util.XString;

@Component("IPFSTest")
@Slf4j 
public class IPFSTest extends ServiceBase implements TestIntf {
    @Override
    public void test() throws Exception {
        log.debug("IPFSTest.test() running.");
        // testUploadDirectory();
        // ipfs.getRepoStat();
        ipnsTest();
        // mfsTest();
    }

    private void mfsTest() {
        arun.run(as -> {
            String fileName = "/a/b/file.txt";

            // Write a text file to MFS.
            ipfsFiles.addFile(as, fileName, MediaType.TEXT_PLAIN_VALUE, "This is a test");
            log.debug("MFS File Added: " + fileName);

            // Now read back the file
            String content = ipfsFiles.readFile(fileName);
            if (content != null) {
                log.debug("Read Confirmed: " + content);
            }

            return null;
        });
    }

    private void testUploadDirectory() {
        arun.run(as -> {
            // create the root directory
            MerkleNode rootDir = ipfsObj.newObject();
            log.debug("rootDir: " + XString.prettyPrint(rootDir));

            // create a file to put in the directory.
            MerkleLink file1 = ipfs.addFileFromString(as, "Test file one (new)", "fileone.txt", "text/plain", false);
            log.debug("file1: " + XString.prettyPrint(file1));

            MerkleLink file2 = ipfs.addFileFromString(as, "Test file two", "filetwo.txt", "text/plain", false);
            log.debug("file2: " + XString.prettyPrint(file2));

            MerkleNode newRootDir = ipfsObj.addFileToDagRoot(rootDir.getHash(), "subfolder/fileone.txt", file1.getHash());
            log.debug("newRoot (first file added): " + XString.prettyPrint(newRootDir));
            return null;
        });
    }

    public void ipnsTest() throws Exception {
        // ipfs.getPins();
        arun.run(as -> {
            log.debug("Running IPNS Test.");

            // Save some JSON to a CID
            MerkleLink ml = ipfsDag.putString(as, "{\"data\": \"MY FIRST DAG PUT\"}", null, null);
            log.debug("Cid=" + ml.getCid().getPath());

            // Read back the data to be sure we can get it
            String verify = ipfsDag.getString(ml.getCid().getPath());
            log.debug("verify: " + verify);

            // Publish the CID under a Key
            Map<String, Object> ret = ipfsName.publish(as, "ClaysKey", ml.getCid().getPath());
            log.debug("ipnsPublishRet: " + XString.prettyPrint(ret));
            // The line above will print something like this:
            // {
            //     "Name" : "k51qzi5uqu5dkvzwaur63dwcvl49faobfu5wehnhgprr40sd91ut1quiwhg7fa",
            //     "Value" : "/ipfs/bafyreibr77jhjmkltu7zcnyqwtx46fgacbjc7ayejcfp7yazxc6xt476xe"
            // }

            String ipnsName = (String) ret.get("Name");
            ret = ipfsName.resolve(as, ipnsName);
            log.debug("ipnsResolveRet: " + XString.prettyPrint(ret));
            // Resolve will print something like this:
            // {
            //     "Path" : "/ipfs/bafyreibr77jhjmkltu7zcnyqwtx46fgacbjc7ayejcfp7yazxc6xt476xe"
            // }

            // verify = ipfs.dagGet(ipnsName);
            // log.debug("verifyIPNS!: " + verify);

            // --------------

            ml = ipfsDag.putString(as, "{\"data\": \"MY SECOND DAG PUT\"}", null, null);
            log.debug("Cid (Second Version)=" + ml.getCid().getPath());

            verify = ipfsDag.getString(ml.getCid().getPath());
            log.debug("verify (second): " + verify);

            ret = ipfsName.publish(as, "ClaysKey", ml.getCid().getPath());
            log.debug("ipnsPublishRet (second): " + XString.prettyPrint(ret));

            ipnsName = (String) ret.get("Name");
            ret = ipfsName.resolve(as, ipnsName);
            log.debug("ipnsResolveRet (second): " + XString.prettyPrint(ret));
            return null;
        });
    }

    public void oldTest() throws Exception {
        try {
            String hash = "QmaaqrHyAQm7gALkRW8DcfGX3u8q9rWKnxEMmf7m9z515w";
            log.debug("Querying Hash: " + hash);
            String json = ipfsObj.getAsString(hash, "json");
            log.debug("JSON [" + hash + "]=" + json);

            log.debug("Querying for MerkleNode...");
            MerkleNode mnode = ipfsObj.getMerkleNode(hash, "json");
            log.debug("MerkleNode: " + XString.prettyPrint(mnode));

            // String merkContent = ipfs.objectCat(hash);
        } finally {
        }
    }
}
