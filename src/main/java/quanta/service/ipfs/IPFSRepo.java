package quanta.service.ipfs;

import java.util.LinkedHashMap;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import quanta.config.ServiceBase;
import quanta.util.Cast;
import quanta.util.XString;

@Component
public class IPFSRepo extends ServiceBase {

    private static Logger log = LoggerFactory.getLogger(IPFSRepo.class);
    public static String API_REPO;

    @PostConstruct
    public void init() {
        API_REPO = prop.getIPFSApiBase() + "/repo";
    }

    /*
     * this appears to be broken due to a bug in IPFS? Haven't reported an error to them yet. Returns
     * HTTP success (200), but no data. It should be returnin JSON but doesn't, so I have hacked the
     * postForJsonReply to always return 'success' in this scenario (200 with no body)
     */
    public String verify() {
        String url = API_REPO + "/verify";
        LinkedHashMap<String, Object> res = Cast.toLinkedHashMap(ipfs.postForJsonReply(url, LinkedHashMap.class));
        return "\nIPFS Repository Verify:\n" + XString.prettyPrint(res) + "\n";
    }

    public String gc() {
        if (!prop.ipfsEnabled()) return "IPFS Disabled.";
        String url = API_REPO + "/gc";
        // LinkedHashMap<String, Object> res = Cast.toLinkedHashMap(postForJsonReply(url,
        // LinkedHashMap.class));
        // return "\nIPFS Repository Garbage Collect:\n" + XString.prettyPrint(res) + "\n";
        String res = (String) ipfs.postForJsonReply(url, String.class);
        return "\nIPFS Repository Garbage Collect:\n" + res + "\n";
    }
}
