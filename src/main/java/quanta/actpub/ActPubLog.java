package quanta.actpub;

import org.springframework.stereotype.Component;

/**
 * This class exists only to create a single point of control over logging configuration to control
 * logging levels for ActivityPub processing
 */
@Component
public class ActPubLog {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ActPubLog.class);

    public void trace(String message) {
        log.trace(message);
    }
}
