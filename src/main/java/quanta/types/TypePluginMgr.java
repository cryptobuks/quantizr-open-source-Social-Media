package quanta.types;

import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import quanta.config.ServiceBase;

@Component
public class TypePluginMgr extends ServiceBase {

    private static Logger log = LoggerFactory.getLogger(TypePluginMgr.class);
    private static HashMap<String, TypeBase> types = new HashMap<>();

    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        ServiceBase.init(event.getApplicationContext());
        log.debug("ContextRefreshedEvent");
        nostrEncryptedDMType.postContruct();
        bookmarkType.postContruct();
        friendType.postContruct();
        roomType.postContruct();
        rssType.postContruct();
    }

    public static void addType(TypeBase type) {
        log.debug("Registering Plugin: " + type.getClass().getName());
        types.put(type.getName().toLowerCase(), type);
    }

    public HashMap<String, TypeBase> getTypes() {
        return types;
    }

    public TypeBase getPluginByType(String type) {
        return types.get(type.toLowerCase());
    }
}
