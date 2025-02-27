package quanta.service.ipfs;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import quanta.config.ServiceBase;
import quanta.mongo.MongoSession;

@Component
public class IPFSKey extends ServiceBase {

    private static Logger log = LoggerFactory.getLogger(IPFSKey.class);
    public static String API_NAME;

    @PostConstruct
    public void init() {
        API_NAME = prop.getIPFSApiBase() + "/key";
    }

    // todo-2: convert to actual type, not map.
    public Map<String, Object> gen(MongoSession ms, String keyName) {
        checkIpfs();
        Map<String, Object> ret = null;
        try {
            String url = API_NAME + "/gen?arg=" + keyName;
            HttpHeaders headers = new HttpHeaders();
            MultiValueMap<String, Object> bodyMap = new LinkedMultiValueMap<>();
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(bodyMap, headers);
            // Use a rest call with no timeout because publish can take a LONG time.
            log.debug("Generate IPFS Key: " + url);
            ResponseEntity<String> response = ipfs.restTemplateNoTimeout.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                String.class
            );
            ret = ipfs.mapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
        } catch (
            // ret output:
            // {
            // "Id" : ...
            // "Name" : ...
            // }
            Exception e
        ) {
            log.error("Failed in restTemplate.exchange", e);
        }
        return ret;
    }
}
