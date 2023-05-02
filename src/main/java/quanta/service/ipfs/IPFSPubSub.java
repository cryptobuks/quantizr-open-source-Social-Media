package quanta.service.ipfs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import quanta.AppServer;
import quanta.config.ServiceBase;
import quanta.config.SessionContext;
import quanta.model.client.IPSMData;
import quanta.model.client.IPSMMessage;
import quanta.mongo.MongoRepository;
import quanta.response.IPSMPushInfo;
import quanta.response.ServerPushInfo;
import quanta.util.Cast;
import quanta.util.DateUtil;
import quanta.util.Util;
import quanta.util.XString;

// IPFS Reference: https://docs.ipfs.io/reference/http/api

@Component
@Slf4j 
public class IPFSPubSub extends ServiceBase {
    private static final boolean IPSM_ENABLE = false;
    private static final String IPSM_TOPIC_HEARTBEAT = "ipsm-heartbeat";
    private static final String IPSM_TOPIC_TEST = "/ipsm/test";

    private static final RestTemplate restTemplate = new RestTemplate(Util.getClientHttpRequestFactory(10000));
    private static final ObjectMapper mapper = new ObjectMapper();

    public static String API_PUBSUB;

    // private static int heartbeatCounter = 0;

    private static final HashMap<String, Integer> fromCounter = new HashMap<>();

    @PostConstruct
    public void init() {
        API_PUBSUB = prop.getIPFSApiBase() + "/pubsub";
    }

    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        ServiceBase.init(event.getApplicationContext());
        log.debug("ContextRefreshedEvent");
        // log.debug("Checking swarmPeers");
        // swarmPeers();

        if (prop.ipfsEnabled() && IPSM_ENABLE) {
            exec.run(() -> {
                setOptions();
                ipfsSwarm.connect();
                Util.sleep(3000);
                openChannel(IPSM_TOPIC_HEARTBEAT);
                openChannel(IPSM_TOPIC_TEST);
            });
        }
    }

    public void setOptions() {
        if (!prop.ipfsEnabled()) return;
        // Only used this for some testing (shouldn't be required?)
        // if these are the defaults ?
        LinkedHashMap<String, Object> res = null;

        // Pubsub.Router="floodsub" | "gossipsub"
        // todo-2: we can add this to the startup bash scripts along with the CORS configs?
        res = Cast.toLinkedHashMap(
                ipfs.postForJsonReply(ipfsConfig.API_CONFIG + "?arg=Pubsub.Router&arg=gossipsub", LinkedHashMap.class));
        log.debug("\nIPFS Pubsub.Router set:\n" + XString.prettyPrint(res) + "\n");

        res = Cast.toLinkedHashMap(ipfs.postForJsonReply(
                ipfsConfig.API_CONFIG + "?arg=Pubsub.DisableSigning&arg=false&bool=true", LinkedHashMap.class));
        log.debug("\nIPFS Pubsub.DisableSigning set:\n" + XString.prettyPrint(res) + "\n");
    }

    // DO NOT DELETE (IPSM)
    // send out a heartbeat from this server every few seconds for testing purposes
    // @Scheduled(fixedDelay = 10 * DateUtil.SECOND_MILLIS)
    // public void ipsmHeartbeat() {
    // if (IPSM_ENABLE) {
    // // ensure instanceId loaded
    // ipfs.getInstanceId();
    // pub(IPSM_TOPIC_HEARTBEAT, (String) ipfs.instanceId.get("ID") + "-ipsm-" +
    // String.valueOf(heartbeatCounter++) + "\n");
    // }}

    public void openChannel(String topic) {
        checkIpfs();
        if (IPSM_ENABLE) {
            exec.run(() -> {
                log.debug("openChannel: " + topic);
                // we do some reads every few seconds so we should pick up several heartbeats
                // if there are any being sent from other servers
                while (!AppServer.isShuttingDown()) {
                    sub(topic);
                    Util.sleep(1000);
                }
                log.debug("channel sub thread terminating: " + topic);
            });
        }
    }

    // PubSub publish
    public Map<String, Object> pub(String topic, String message) {
        checkIpfs();
        Map<String, Object> ret = null;
        try {
            String url = API_PUBSUB + "/pub?arg=" + topic + "&arg=" + message;

            HttpHeaders headers = new HttpHeaders();
            MultiValueMap<String, Object> bodyMap = new LinkedMultiValueMap<>();
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(bodyMap, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            // ret = mapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
            log.debug("IPFS pub to [resp code=" + response.getStatusCode() + "] " + topic);
        } catch (Exception e) {
            log.error("Failed in restTemplate.exchange", e);
        }
        return ret;
    }

    public void sub(String topic) {
        checkIpfs();
        String url = API_PUBSUB + "/sub?arg=" + topic;
        try {
            HttpURLConnection conn = configureConnection(new URL(url), "POST");
            InputStream is = conn.getInputStream();
            getObjectStream(is);
        } catch (Exception e) {
            log.error("Failed to read: " + topic); // , e);
        }
    }

    HttpURLConnection configureConnection(URL target, String method) throws IOException {
        checkIpfs();
        HttpURLConnection conn = (HttpURLConnection) target.openConnection();
        conn.setRequestMethod(method);
        // conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        return conn;
    }

    private void getObjectStream(InputStream in) throws IOException {
        checkIpfs();
        byte LINE_FEED = (byte) 10;
        ByteArrayOutputStream resp = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;

        while ((r = in.read(buf)) >= 0) {
            resp.write(buf, 0, r);
            if (buf[r - 1] == LINE_FEED) {
                log.debug("LINE: " + new String(resp.toByteArray()));
                Map<String, Object> event = mapper.readValue(resp.toByteArray(), new TypeReference<Map<String, Object>>() {});
                processInboundEvent(event);
                resp = new ByteArrayOutputStream();
            }
        }
    }

    // clear throttle counters every minute.
    @Scheduled(fixedDelay = DateUtil.MINUTE_MILLIS)
    public void clearThrottles() {
        if (!MongoRepository.fullInit)
            return;
        synchronized (fromCounter) {
            fromCounter.clear();
        }
    }

    public void processInboundEvent(Map<String, Object> msg) {
        checkIpfs();
        String from = (String) msg.get("from");
        if (from == null)
            return;
        if (throttle(from))
            return;

        String data = (String) msg.get("data");
        // String seqno = (String) msg.get("seqno");
        String payload = (new String(Base64.getDecoder().decode(data)));
        log.debug("PAYLOAD: " + payload);
        processInboundPayload(payload);
    }

    /* Returns true if we're throttling the 'from' */
    boolean throttle(String from) {
        synchronized (fromCounter) {
            Integer hitCount = fromCounter.get(from);

            if (hitCount == null) {
                fromCounter.put(from, 1);
                return false;
            } else {
                if (hitCount.intValue() > 10) {
                    return true;
                }
                hitCount = hitCount.intValue() + 1;
                fromCounter.put(from, hitCount);
                return false;
            }
        }
    }

    private void processInboundPayload(String payload) {
        if (payload == null)
            return;

        ServerPushInfo pushInfo = null;
        payload = payload.trim();
        if (payload.startsWith("{") && payload.endsWith("}")) {
            IPSMMessage msg = parseIpsmPayload(payload);
            if (msg == null)
                return;

            String message = getMessageText(msg);
            pushInfo = new IPSMPushInfo(message);
        } else {
            pushInfo = new IPSMPushInfo(payload);
        }

        for (SessionContext sc : SessionContext.getAllSessions(true, false)) {

            // only consider sessions that have viewed their IPSM tab
            if (!sc.isEnableIPSM() || sc.isAnonUser() || !sc.isLive()) {
                continue;
            }

            // log.debug("Pushing to session: sc.user: " + sc.getUserName() + " " + payload);
            push.sendServerPushInfo(sc, pushInfo);
        }
    }

    private String getMessageText(IPSMMessage msg) {
        if (msg == null || msg.getContent() == null)
            return null;
        StringBuilder sb = new StringBuilder();
        for (IPSMData data : msg.getContent()) {
            String text = ipfsCat.getString(data.getData());
            sb.append(text);
        }
        return sb.toString();
    }

    private IPSMMessage parseIpsmPayload(String payload) {
        try {
            IPSMMessage msg = mapper.readValue(payload, IPSMMessage.class);
            if (verifySignature(msg)) {
                log.debug("Signature Failed.");
                return null;
            }
            if (msg != null) {
                log.debug("JSON: " + XString.prettyPrint(msg));
            }
            return msg;
        } catch (Exception e) {
            log.error("JSON Parse failed: " + payload);
            return null;
        }
    }

    // https://www.npmjs.com/package/node-rsa
    // Default signature scheme: 'pkcs1-sha256'
    public boolean verifySignature(IPSMMessage msg) {
        String strDat = String.valueOf(msg.getTs()) + XString.compactPrint(msg.getContent());
        // log.debug("strDat=" + strDat);
        return true;
    }
}
