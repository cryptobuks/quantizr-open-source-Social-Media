package quanta.types;

import org.springframework.stereotype.Component;
import quanta.model.NodeInfo;
import quanta.model.PropertyInfo;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;

// IMPORTANT: See TypePluginMgr, and ServiceBase instantiation to initialize tyese Plugin types
@Component
public class NostrEncryptedDMType extends TypeBase {

    @Override
    public String getName() {
        return NodeType.NOSTR_ENC_DM.s();
    }

    @Override
    public void convert(MongoSession ms, NodeInfo nodeInfo, SubNode node, SubNode ownerAcctNode, boolean getFollowers) {
        if (ownerAcctNode == null)
            return;

        String pubKey = null;
        String userName = ownerAcctNode.getStr(NodeProp.USER);
        if (userName != null && userName.startsWith(".")) {
            pubKey = userName.substring(1);
        } else {
            // yes this is redundant and loads userUrl again below, but I need to test this before removing it.
            pubKey = ownerAcctNode.getStr(NodeProp.NOSTR_USER_PUBKEY);
        }

        if (pubKey != null) {
            nodeInfo.safeGetClientProps().add(new PropertyInfo(NodeProp.NOSTR_USER_PUBKEY.s(), pubKey));
        }
    }
}
