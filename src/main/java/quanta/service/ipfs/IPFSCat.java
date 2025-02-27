package quanta.service.ipfs;

import java.io.InputStream;
import java.net.URL;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import quanta.config.ServiceBase;
import quanta.util.Util;

@Component
public class IPFSCat extends ServiceBase {

    private static Logger log = LoggerFactory.getLogger(IPFSCat.class);
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
            ResponseEntity<String> response = ipfs.restTemplate.exchange(
                url,
                HttpMethod.POST,
                Util.getBasicRequestEntity(),
                String.class
            );
            ret = response.getBody();
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
