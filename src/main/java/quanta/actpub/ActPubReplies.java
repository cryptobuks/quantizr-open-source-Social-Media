package quanta.actpub;

import java.util.LinkedList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.actpub.model.APOOrderedCollection;
import quanta.actpub.model.APObj;
import quanta.config.NodePath;
import quanta.config.ServiceBase;
import quanta.instrument.PerfMon;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.mongo.model.SubNode;
import quanta.service.AclService;

/**
 * Methods related to generating AP Replies endpoing
 */
@Component
@Slf4j
public class ActPubReplies extends ServiceBase {
    @Autowired
    private ActPubLog apLog;

    /**
     * Generates outbound replies collection data
     */
    @PerfMon(category = "apReplies")
    public APOOrderedCollection generateReplies(String nodeId) {
        String url = prop.getProtocolHostAndPort() + APConst.PATH_REPLIES + "/" + nodeId;

        return arun.<APOOrderedCollection>run(as -> {
            SubNode node = read.getNode(as, nodeId, false, null);
            LinkedList<SubNode> nodes = getRepliesToNode(nodeId);
            List<APObj> items = apFactory.makeAPONotes(as, nodes, node);
            APOOrderedCollection ret = new APOOrderedCollection(url, items);
            return ret;
        });
    }

    /*
     * Note: All replies to native-Quanta nodes (i.e. as opposed to cached foreign nodes) are stored as
     * children under that node, so we don't need to consider any "inReplyTo" stuff when generating all
     * the replies to any Quanta Node. They're just the children.
     */
    public LinkedList<SubNode> getRepliesToNode(String nodeId) {
        return arun.<LinkedList<SubNode>>run(as -> {
            LinkedList<SubNode> nodes = new LinkedList<>();

            SubNode node = read.getNode(as, nodeId);
            if (node != null && AclService.isPublic(as, node)) {
                // We only get COMMENT nodes, because those are considered 'replies' and other things are considered
                // core content,
                // at least as far as ActPub is concerned.
                Sort sort = Sort.by(Sort.Direction.ASC, SubNode.CREATE_TIME);
                Iterable<SubNode> children = read.findSubNodesByType(as, node, NodeType.COMMENT.s(), false, sort, null);
                for (SubNode child : children) {
                    if (AclService.isPublic(as, child)) {
                        nodes.add(child);
                    }
                }

                Iterable<SubNode> iter = read.findNodesByProp(as, NodePath.USERS_PATH + //
                        "/(" + NodePath.LOCAL + "|" + NodePath.REMOTE + ")", NodeProp.INREPLYTO.s(), nodeId);
                for (SubNode child : iter) {
                    if (AclService.isPublic(as, child)) {
                        nodes.add(child);
                    }
                }
            }
            return nodes;
        });
    }
}
