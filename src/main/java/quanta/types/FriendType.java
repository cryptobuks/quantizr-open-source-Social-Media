package quanta.types;

import org.springframework.stereotype.Component;
import quanta.model.NodeInfo;
import quanta.model.PropertyInfo;
import quanta.model.client.Attachment;
import quanta.model.client.Constant;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;

// IMPORTANT: See TypePluginMgr, and ServiceBase instantiation to initialize tyese Plugin types
@Component
public class FriendType extends TypeBase {

    @Override
    public String getName() {
        return NodeType.FRIEND.s();
    }

    @Override
    public void convert(MongoSession ms, NodeInfo nodeInfo, SubNode node, SubNode ownerAccntNode, boolean getFollowers) {
        // yes this is redundant and loads userUrl again below, but I need to test this before removing it.
        String userUrl = node.getStr(NodeProp.ACT_PUB_ACTOR_URL);
        if (userUrl != null) {
            nodeInfo.safeGetClientProps().add(new PropertyInfo(NodeProp.ACT_PUB_ACTOR_URL.s(), userUrl));
        }

        /*
         * Get info from accountId based on the node.owner if this is presenting Followers list, or else
         * from USER_NODE_ID, if we're just displaying a Friend node 'as is' (i.e. based on who it points to
         * as the friend)
         */
        String accountId = getFollowers ? node.getOwner().toHexString() : node.getStr(NodeProp.USER_NODE_ID);
        // log.debug("friendAccountId=" + friendAccountId + " on nodeId=" + node.getIdStr());

        /*
         * NOTE: Right when the Friend node is first created, before a person has been selected, this WILL
         * be null, and is normal
         */
        if (accountId != null) {
            SubNode accountNode = read.getNode(ms, accountId, false, null);

            /*
             * to load up a friend node for the browser to display, we have to populate these "Client Props", on
             * the node object which are not properties of the node itself but values we generate right here on
             * demand. The "Client Props" is a completely different set than the actual node properties
             */
            if (accountNode != null) {
                /* NOTE: This will be the bio for both ActivityPub users and local users */
                String userBio = accountNode.getStr(NodeProp.USER_BIO);
                if (userBio != null) {
                    nodeInfo.safeGetClientProps().add(new PropertyInfo(NodeProp.USER_BIO.s(), userBio));
                }

                userUrl = accountNode.getStr(NodeProp.ACT_PUB_ACTOR_URL);
                if (userUrl != null) {
                    nodeInfo.safeGetClientProps().add(new PropertyInfo(NodeProp.ACT_PUB_ACTOR_URL.s(), userUrl));
                }

                nodeInfo.safeGetClientProps().add(new PropertyInfo("accntId", accountId));
                nodeInfo.safeGetClientProps().add(new PropertyInfo("accntUser", accountNode.getStr(NodeProp.USER)));

                Attachment att = accountNode.getAttachment(Constant.ATTACHMENT_PRIMARY.s(), false, false);
                if (att != null && att.getBin() != null) {
                    nodeInfo.safeGetClientProps().add(new PropertyInfo("avatarVer", att.getBin()));
                }

                String friendDisplayName = user.getFriendlyNameFromNode(accountNode);
                if (friendDisplayName != null) {
                    nodeInfo.safeGetClientProps().add(new PropertyInfo(NodeProp.DISPLAY_NAME.s(), friendDisplayName));
                }

                /*
                 * Note: for ActivityPub foreign users we have this property on their account node that points to
                 * the live URL of their account avatar as it was found in their Actor object
                 */

                String userIconUrl = accountNode.getStr(NodeProp.USER_ICON_URL);
                if (userIconUrl != null) {
                    nodeInfo.safeGetClientProps().add(new PropertyInfo(NodeProp.USER_ICON_URL.s(), userIconUrl));
                }
            }
        }
    }
}
