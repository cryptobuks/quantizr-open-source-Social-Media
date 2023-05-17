package quanta.types;

import org.springframework.stereotype.Component;
import quanta.model.client.NodeType;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.CreateSubNodeRequest;
import quanta.util.val.Val;

// IMPORTANT: See TypePluginMgr, and ServiceBase instantiation to initialize tyese Plugin types
@Component
public class BookmarkType extends TypeBase {

    @Override
    public String getName() {
        return NodeType.BOOKMARK.s();
    }

    public void preCreateNode(MongoSession ms, Val<SubNode> node, CreateSubNodeRequest req, boolean linkBookmark) {
        // Note: if 'linkBookmark' is true then 'node' will be null here, and that's ok.
        SubNode nodeToBookmark = null;
        if (node != null) {
            nodeToBookmark = node.getVal();
            node.setVal(
                    read.getUserNodeByType(ms, ms.getUserName(), null, "### Bookmarks", NodeType.BOOKMARK_LIST.s(), null, null));
        }
        if (!linkBookmark && nodeToBookmark != null) {
            req.setContent(render.getFirstLineAbbreviation(nodeToBookmark.getContent(), 100));
        }
    }
}
