package quanta.actpub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * This class exists only to create a single point of control over logging configuration to control
 * logging levels for ActivityPub processing
 */
@Component
public class ActPubLog {

    private static Logger log = LoggerFactory.getLogger(ActPubLog.class);

    public void trace(String message) {
        log.trace(message);
    }
}
