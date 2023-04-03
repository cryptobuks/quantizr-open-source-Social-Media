package quanta.service.ipfs;

import java.io.InputStream;
import java.net.URL;
import javax.annotation.PostConstruct;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.util.Util;

@Component
@Slf4j 
public class IPFSCat extends ServiceBase {
    public static String API_CAT;

    @PostConstruct
    public void init() {
        API_CAT = prop.getIPFSApiBase() + "/cat";
    }

    /**
     * Reads the bytes from 'ipfs hash', expecting them to be UTF-8 and returns the string.
     * 
     * NOTE: The hash is allowed to have a subpath here.
     */
    public String getString(String hash) {
        checkIpfs();
        String ret = null;
        try {
            String url = API_CAT + "?arg=" + hash;
            ResponseEntity<String> response =
                    ipfs.restTemplate.exchange(url, HttpMethod.POST, Util.getBasicRequestEntity(), String.class);
            ret = response.getBody();
            // log.debug("IPFS post cat. Ret " + response.getStatusCode() + "] " + ret);
        } catch (Exception e) {
            log.error("Failed to cat: " + hash, e);
        }
        return ret;
    }

    public InputStream getInputStream(String hash) {
        checkIpfs();
        String url = API_CAT + "?arg=" + hash;
        InputStream is = null;
        try {
            is = new URL(url).openStream();
        } catch (Exception e) {
            log.error("Failed in read: " + url, e);
        }
        return is;
    }
}
