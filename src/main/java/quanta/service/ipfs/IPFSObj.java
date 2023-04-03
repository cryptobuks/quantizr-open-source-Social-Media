package quanta.service.ipfs;

import java.net.URI;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.model.ipfs.dag.MerkleNode;
import quanta.model.ipfs.file.IPFSObjectStat;
import quanta.util.XString;

@Component
@Slf4j 
public class IPFSObj extends ServiceBase {
    public static String API_OBJECT;

    @PostConstruct
    public void init() {
        API_OBJECT = prop.getIPFSApiBase() + "/object";
    }

    /**
     * returns MerkleNode of the hash, as requested using the 'encoding=' url argument specified.
     */
    public MerkleNode getMerkleNode(String hash, String encoding) {
        checkIpfs();
        MerkleNode ret = null;
        try {
            String url = API_OBJECT + "/get?arg=" + hash + "&" + ipfs.ENCODING_PARAM_NAME + "=" + encoding;
            log.debug("REQ: " + url);

            ResponseEntity<String> result = ipfs.restTemplate.getForEntity(new URI(url), String.class);
            MediaType contentType = result.getHeaders().getContentType();

            // log.debug("RAW RESULT: " + result.getBody());

            if (MediaType.APPLICATION_JSON.equals(contentType)) {
                ret = XString.jsonMapper.readValue(result.getBody(), MerkleNode.class);
                ret.setHash(hash);
                ret.setContentType(contentType.getType());
                // String formatted = XString.prettyPrint(ret);
                // log.debug(formatted);
            }

        } catch (Exception e) {
            log.error("Failed in restTemplate.getForEntity", e);
        }
        return ret;
    }

    public MerkleNode objectOperation(String endpoint) {
        checkIpfs();
        MerkleNode ret = null;
        try {
            HttpHeaders headers = new HttpHeaders();
            MultiValueMap<String, Object> bodyMap = new LinkedMultiValueMap<>();
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(bodyMap, headers);

            ResponseEntity<String> response = ipfs.restTemplate.exchange(endpoint, HttpMethod.POST, requestEntity, String.class);
            ret = ipfs.mapper.readValue(response.getBody(), new TypeReference<MerkleNode>() {});
            // log.debug("new Object: " + XString.prettyPrint(ret));

        } catch (Exception e) {
            log.error("Failed in restTemplate.exchange", e);
        }
        return ret;
    }

    /**
     * Returns string of the hash get, as requested using the 'encoding=' url argument specified.
     */
    public String getAsString(String hash, String encoding) {
        checkIpfs();
        String ret = null;
        try {
            String url = API_OBJECT + "/get?arg=" + hash + "&" + ipfs.ENCODING_PARAM_NAME + "=" + encoding;

            ResponseEntity<String> result = ipfs.restTemplate.getForEntity(new URI(url), String.class);
            MediaType contentType = result.getHeaders().getContentType();

            if (MediaType.APPLICATION_JSON.equals(contentType)) {
                ret = result.getBody();
            } else {
                log.debug("RAW BODY: " + result.getBody());
            }
        } catch (Exception e) {
            log.error("Failed in restTemplate.getForEntity", e);
        }
        return ret;
    }

    public MerkleNode newObject() {
        checkIpfs();
        return objectOperation(API_OBJECT + "/new");
    }

    /*
     * Adds an existing CID into the directory strcture at rootCid, and returns the new rootCid
     */
    public MerkleNode addFileToDagRoot(String rootCid, String filePath, String fileCid) {
        checkIpfs();
        if (StringUtils.isEmpty(filePath)) {
            filePath = fileCid;
        }
        return objectOperation(
                API_OBJECT + "/patch/add-link?arg=" + rootCid + "&arg=" + filePath + "&arg=" + fileCid + "&create=true");
    }

    public IPFSObjectStat objectStat(String cid, boolean humanReadable) {
        checkIpfs();
        String url = API_OBJECT + "/stat?arg=" + cid;
        if (humanReadable) {
            url += "&human=true";
        }
        return (IPFSObjectStat) ipfs.postForJsonReply(url, IPFSObjectStat.class);
    }
}
