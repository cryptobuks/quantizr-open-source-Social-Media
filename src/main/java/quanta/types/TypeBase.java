package quanta.types;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import quanta.config.ServiceBase;
import quanta.model.NodeInfo;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.CreateSubNodeRequest;
import quanta.util.val.Val;

// IMPORTANT: See TypePluginMgr, and ServiceBase instantiation to initialize tyese Plugin types
@Component
public abstract class TypeBase extends ServiceBase {

    private static Logger log = LoggerFactory.getLogger(TypeBase.class);

    public void postContruct() {
        TypePluginMgr.addType(this);
    }

    /* Must match the actual type name of the nodes */
    public abstract String getName();

    public void convert(MongoSession ms, NodeInfo nodeInfo, SubNode node, SubNode ownerAccntNode, boolean getFollowers) {}

    public void preCreateNode(MongoSession ms, Val<SubNode> node, CreateSubNodeRequest req, boolean linkBookmark) {}

    public void beforeSaveNode(MongoSession ms, SubNode node) {}
}
