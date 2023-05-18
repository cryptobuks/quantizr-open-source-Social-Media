package quanta.types;

import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Component;
import quanta.model.client.Constant;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.util.XString;

// IMPORTANT: See TypePluginMgr, and ServiceBase instantiation to initialize tyese Plugin types
@Component
public class RssFeedType extends TypeBase {

    @Override
    public String getName() {
        return NodeType.RSS_FEED.s();
    }

    public void beforeSaveNode(MongoSession ms, SubNode node) {
        String feedSrc = node.getStr(NodeProp.RSS_FEED_SRC);

        // if no content or it's encrypted return
        if (feedSrc == null || feedSrc.startsWith(Constant.ENC_TAG.s()))
            return;

        List<String> lines = XString.tokenizeWithDelims(feedSrc, "\n\r");
        if (lines == null) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        HashSet<String> lineSet = new HashSet<>();

        for (String tok : lines) {
            if (tok.equals("\n") || tok.equals("\r") || tok.startsWith("#") || !tok.startsWith("http")) {
                sb.append(tok);
            } else {
                if (lineSet.add(tok)) {
                    sb.append(tok);
                } else {
                    sb.append("# duplicate -> " + tok);
                }
            }
        }
        node.set(NodeProp.RSS_FEED_SRC, sb.toString());
    }
}
