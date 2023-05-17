package quanta.types;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.model.NodeInfo;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.CreateSubNodeRequest;
import quanta.util.val.Val;

// IMPORTANT: See TypePluginMgr, and ServiceBase instantiation to initialize tyese Plugin types
@Component
@Slf4j 
public abstract class TypeBase extends ServiceBase {
    public void postContruct() {
        TypePluginMgr.addType(this);
    }

    /* Must match the actual type name of the nodes */
    public abstract String getName();

    public void convert(MongoSession ms, NodeInfo nodeInfo, SubNode node, SubNode ownerAccntNode, boolean getFollowers) {}

    public void preCreateNode(MongoSession ms, Val<SubNode> node, CreateSubNodeRequest req, boolean linkBookmark) {}

    public void beforeSaveNode(MongoSession ms, SubNode node) {}
}
