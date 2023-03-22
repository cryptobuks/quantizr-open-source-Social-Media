package quanta.test;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.actpub.model.APObj;
import quanta.config.ServiceBase;
import quanta.util.XString;

@Component("ActPubTest")
@Slf4j 
public class ActPubTest extends ServiceBase implements TestIntf {
    @Override
    public void test() throws Exception {
        try {
            log.debug("Running ActPubTest: Host " + prop.getHostAndPort());
            if (prop.getHostAndPort().contains("//q2:")) {
                testConnection("q1:8184");
            }
        } finally {
        }
    }

    /**
     * Do basic validation that we can connect to targetHost
     */
    private void testConnection(String targetHostAndPort) throws Exception {
        log.debug("Testing Connection to: " + targetHostAndPort);
        /* ----- GET WEBFINGER ----- */

        String targetUser = "bob";
        String webFingerUrl = targetUser + "@" + targetHostAndPort;

        APObj webFinger = apUtil.getWebFingerSec(null, null, webFingerUrl, false);
        if (webFinger == null) {
            throw new Exception("Unable to get webFinger of " + webFingerUrl);
        }

        log.debug("Got WebFinger ok: " + XString.prettyPrint(webFinger));

        /* ----- GET ACTOR ----- */
        String actorUrl = apUtil.getActorUrlFromWebFingerObj(webFinger);
        APObj actorObj = apUtil.getRemoteAP(null, null, actorUrl);
        if (actorObj == null) {
            throw new Exception("Unable to get actor: " + actorUrl);
        }

        log.debug("Got Actor ok: " + XString.prettyPrint(actorObj));

        /* ===== Follow User ===== */
        apFollowing.setFollowing("adam", targetUser + "@" + targetHostAndPort, true);
    }
}
