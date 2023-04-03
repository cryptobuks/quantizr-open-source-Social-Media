package quanta.service.ipfs;

import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.util.XString;

@Component
@Slf4j 
public class IPFSSwarm extends ServiceBase {
    public static String API_SWARM;

    @PostConstruct
    public void init() {
        API_SWARM = prop.getIPFSApiBase() + "/swarm";
    }

    public Map<String, Object> connect(String peer) {
        if (!prop.ipfsEnabled()) return null;
        Map<String, Object> ret = null;
        try {
            log.debug("Swarm connect: " + peer);
            String url = API_SWARM + "/connect?arg=" + peer;

            HttpHeaders headers = new HttpHeaders();
            MultiValueMap<String, Object> bodyMap = new LinkedMultiValueMap<>();
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(bodyMap, headers);

            ResponseEntity<String> response = ipfs.restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            ret = ipfs.mapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
            log.debug("IPFS swarm connect: " + XString.prettyPrint(ret));
        } catch (Exception e) {
            log.error("Failed in restTemplate.exchange", e);
        }
        return ret;
    }

    // PubSub List peers
    public Map<String, Object> listPeers() {
        checkIpfs();
        Map<String, Object> ret = null;
        try {
            String url = API_SWARM + "/peers";

            HttpHeaders headers = new HttpHeaders();
            MultiValueMap<String, Object> bodyMap = new LinkedMultiValueMap<>();
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(bodyMap, headers);

            ResponseEntity<String> response = ipfs.restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            ret = ipfs.mapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
            log.debug("IPFS swarm peers: " + XString.prettyPrint(ret));
        } catch (Exception e) {
            log.error("Failed in restTemplate.exchange", e);
        }
        return ret;
    }

    public void connect() {
        if (!prop.ipfsEnabled()) return;
        arun.run(as -> {
            List<String> adrsList = getConnectAddresses(as);
            if (adrsList != null) {
                for (String adrs : adrsList) {
                    if (adrs.startsWith("/")) {
                        connect(adrs);
                    }
                }
            }
            return null;
        });
    }

    public List<String> getConnectAddresses(MongoSession ms) {
        checkIpfs();
        List<String> ret = null;
        SubNode node = read.getNode(ms, ":ipfsSwarmAddresses");
        if (node != null) {
            log.debug("swarmAddresses: " + node.getContent());
            ret = XString.tokenize(node.getContent(), "\n", true);
        }
        return ret;
    }
}
