package quanta.types;

import org.springframework.stereotype.Component;
import quanta.model.client.NodeType;

// IMPORTANT: See TypePluginMgr, and ServiceBase instantiation to initialize tyese Plugin types
@Component
public class RoomType extends TypeBase {

    @Override
    public String getName() {
        return NodeType.ROOM.s();
    }
}
