package quanta.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import quanta.actpub.model.APObj;
import quanta.config.ServiceBase;
import quanta.util.XString;

@Component("ActPubTest")
public class ActPubTest extends ServiceBase implements TestIntf {

    private static Logger log = LoggerFactory.getLogger(ActPubTest.class);

    @Override
    public void test() throws Exception {
        try {
            log.debug("Running ActPubTest: Host " + prop.getHostAndPort());
            if (prop.getHostAndPort().contains("//q2:")) {
                testConnection("q1:8184");
            }
        } finally {}
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
