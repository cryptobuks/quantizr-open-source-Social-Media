package quanta.service.ipfs;

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

@Component
@Slf4j 
public class IPFSName extends ServiceBase {
    public static String API_NAME;

    @PostConstruct
    public void init() {
        API_NAME = prop.getIPFSApiBase() + "/name";
    }

    // todo-2: convert to actual type, not map.
    public Map<String, Object> publish(MongoSession ms, String key, String cid) {
        checkIpfs();
        Map<String, Object> ret = null;
        try {
            if (key == null)
                throw new RuntimeException("Key is required for publishing.");
            String url = API_NAME + "/publish?arg=" + cid + "&key=" + key;

            HttpHeaders headers = new HttpHeaders();
            MultiValueMap<String, Object> bodyMap = new LinkedMultiValueMap<>();
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(bodyMap, headers);

            // Use a rest call with no timeout because publish can take a LONG time.
            log.debug("Publishing IPNS: " + url);
            ResponseEntity<String> response =
                    ipfs.restTemplateNoTimeout.exchange(url, HttpMethod.POST, requestEntity, String.class);
            ret = ipfs.mapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});

            // ret output:
            // {
            // "Name" : "QmYHQEW7NTczSxcaorguczFRNwAY1r7UkF8uU4FMTGMRJm",
            // "Value" : "/ipfs/bafyreibr77jhjmkltu7zcnyqwtx46fgacbjc7ayejcfp7yazxc6xt476xe"
            // }
        } catch (Exception e) {
            log.error("Failed in restTemplate.exchange", e);
        }
        return ret;
    }

    // todo-2: convert return val to a type (not map)
    public Map<String, Object> resolve(MongoSession ms, String name) {
        checkIpfs();
        Map<String, Object> ret = null;
        try {
            String url = API_NAME + "/resolve?arg=" + name;

            HttpHeaders headers = new HttpHeaders();
            MultiValueMap<String, Object> bodyMap = new LinkedMultiValueMap<>();
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(bodyMap, headers);

            ResponseEntity<String> response = ipfs.restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            ret = ipfs.mapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});

        } catch (Exception e) {
            log.error("Failed in restTemplate.exchange", e);
        }
        return ret;
    }
}
