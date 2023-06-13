package quanta.mongo;

import org.springframework.context.ApplicationEvent;

public class AppStartupEvent extends ApplicationEvent {

    public AppStartupEvent() {
        super("AppStartupEvent Payload");
    }
}
