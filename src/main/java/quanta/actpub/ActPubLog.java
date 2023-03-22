package quanta.actpub;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * This class exists only to create a single point of control over logging configuration to control
 * logging levels for ActivityPub processing
 */
@Component
@Slf4j 
public class ActPubLog {
    public void trace(String message) {
        log.trace(message);
    }
}
