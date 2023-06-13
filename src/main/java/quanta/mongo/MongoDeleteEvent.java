package quanta.mongo;

import org.springframework.context.ApplicationEvent;

public class MongoDeleteEvent extends ApplicationEvent {

    public MongoDeleteEvent(Object source) {
        super(source);
    }
}
