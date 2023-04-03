package quanta.service.ipfs;

import java.util.HashMap;
import java.util.LinkedHashMap;
import javax.annotation.PostConstruct;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.model.client.Attachment;
import quanta.model.client.Constant;
import quanta.model.ipfs.file.IPFSObjectStat;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.util.Cast;
import quanta.util.Util;
import quanta.util.XString;

@Component
@Slf4j 
public class IPFSPin extends ServiceBase {
    public static String API_PIN;

    @PostConstruct
    public void init() {
        API_PIN = prop.getIPFSApiBase() + "/pin";
    }

    public String verify() {
        if (!prop.ipfsEnabled()) return "\nIPFS not enabled.";
        String url = API_PIN + "/verify";
        // LinkedHashMap<String, Object> res =
        // Cast.toLinkedHashMap(postForJsonReply(url, LinkedHashMap.class));
        // casting to a string now, because a bug in IPFS is making it not return data,
        // so we get back string "success"
        String res = (String) ipfs.postForJsonReply(url, String.class);
        return "\nIPFS Pin Verify:\n" + XString.prettyPrint(res) + "\n";
    }

    public boolean remove(String cid) {
        checkIpfs();
        // log.debug("Remove Pin: " + cid);
        String url = API_PIN + "/rm?arg=" + cid;
        return ipfs.postForJsonReply(url, Object.class) != null;
    }

    public boolean add(String cid) {
        if (cid == null) return false;
        checkIpfs();
        // log.debug("Add Pin: " + cid);
        String url = API_PIN + "/add?arg=" + cid;
        return ipfs.postForJsonReply(url, Object.class) != null;
    }

    public LinkedHashMap<String, Object> getPins() {
        if (!prop.ipfsEnabled()) return null;
        LinkedHashMap<String, Object> pins = null;
        HashMap<String, Object> res = null;
        try {
            String url = API_PIN + "/ls?type=recursive";
            res = Cast.toLinkedHashMap(ipfs.postForJsonReply(url, LinkedHashMap.class));
            // log.debug("RAW PINS LIST RESULT: " + XString.prettyPrint(res));

            if (res != null) {
                pins = Cast.toLinkedHashMap(res.get("Keys"));
            }
        } catch (Exception e) {
            log.error("Failed to get pins", e);
        }
        return pins;
    }

    public void ipfsAsyncPinNode(MongoSession ms, ObjectId nodeId) {
        if (!prop.ipfsEnabled()) return;
        exec.run(() -> {
            // wait for node to be saved. Waits up to 30 seconds, because of the 10 retries.
            /*
             * todo-2: What we could do here instead of of this polling is hook into the MongoEventListener
             * class and have a pub/sub model in effect so we can detect immediately when the node is saved.
             */
            Util.sleep(3000);
            SubNode node = read.getNode(ms, nodeId, false, 10);

            if (node == null)
                return;

            // todo-2: make this handle multiple attachments, and all calls to it
            Attachment att = node.getAttachment(Constant.ATTACHMENT_PRIMARY.s(), true, false);
            String ipfsLink = att.getIpfsLink();
            add(ipfsLink);

            // always get bytes here from IPFS, and update the node prop with that too.
            IPFSObjectStat stat = ipfsObj.objectStat(ipfsLink, false);

            // note: the enclosing scope this we're running in will take care of comitting the node change to
            // the db.
            att.setSize((long)stat.getCumulativeSize());

            /* And finally update this user's quota for the added storage */
            SubNode accountNode = read.getUserNodeByUserName(ms, null);
            if (accountNode != null) {
                user.addBytesToUserNodeBytes(ms, stat.getCumulativeSize(), accountNode);
            }
        });
    }
}
