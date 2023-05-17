package quanta.types;

import java.util.HashMap;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;

@Component
@Slf4j 
public class TypePluginMgr extends ServiceBase {
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
