package quanta.service.ipfs;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import javax.annotation.PostConstruct;
import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.model.client.MFSDirEntry;
import quanta.model.ipfs.dag.DagLink;
import quanta.model.ipfs.dag.DagNode;
import quanta.model.ipfs.dag.MerkleLink;
import quanta.mongo.MongoSession;
import quanta.request.GetIPFSFilesRequest;
import quanta.util.Cast;
import quanta.util.ThreadLocals;
import quanta.util.Util;
import quanta.util.XString;
import quanta.util.val.Val;

@Component
@Slf4j 
public class IPFSDag extends ServiceBase {
    public static String API_DAG;

    @PostConstruct
    public void init() {
        API_DAG = prop.getIPFSApiBase() + "/dag";
    }

    public String getString(String hash) {
        checkIpfs();
        String ret = null;
        try {
            String url = API_DAG + "/get?arg=" + hash; // + "&output-codec=dag-json";
            ResponseEntity<String> response =
                    ipfs.restTemplate.exchange(url, HttpMethod.POST, Util.getBasicRequestEntity(), String.class);
            ret = response.getBody();
            log.debug("IPFS post dagGet Ret " + response.getStatusCode() + "] " + ret);
        } catch (Exception e) {
            log.error("Failed to dagGet: " + hash, e);
        }
        return ret;
    }

    public DagNode getNode(String cid) {
        checkIpfs();
        DagNode ret = null;
        try {
            String url = API_DAG + "/get?arg=" + cid;
            ret = Cast.toDagNode(ipfs.postForJsonReply(url, DagNode.class));
        } catch (Exception e) {
            log.error("Failed in getDagNode", e);
        }
        return ret;
    }

    public List<MFSDirEntry> getIPFSFiles(MongoSession ms, Val<String> folder, Val<String> cid, GetIPFSFilesRequest req) {
        checkIpfs();
        LinkedList<MFSDirEntry> files = new LinkedList<>();

        if (!ThreadLocals.getSC().allowWeb3()) {
            return null;
        }

        // oops, looks like a path
        if (req.getFolder().startsWith("/"))
            return null;

        cid.setVal(req.getFolder());
        folder.setVal(req.getFolder());

        DagNode dagNode = getNode(req.getFolder());
        log.debug("DagNode: " + XString.prettyPrint(dagNode));

        if (dagNode != null && dagNode.getLinks() != null) {
            for (DagLink entry : dagNode.getLinks()) {
                MFSDirEntry me = new MFSDirEntry();
                me.setName(entry.getName());
                me.setHash(entry.getHash().getPath());
                me.setSize(entry.getTsize());
                me.setType(-1); //entry.getType());
                files.add(me);
            }
        }

        return files;
    }

    public MerkleLink putString(MongoSession ms, String val, String mimeType, Val<Integer> streamSize) {
        checkIpfs();
        return ipfs.writeFromStream(ms, API_DAG + "/put", IOUtils.toInputStream(val), null, streamSize);
    }

    public MerkleLink putStream(MongoSession ms, InputStream stream, String mimeType, Val<Integer> streamSize) {
        checkIpfs();
        return ipfs.writeFromStream(ms, API_DAG + "/put", stream, null, streamSize);
    }
}
