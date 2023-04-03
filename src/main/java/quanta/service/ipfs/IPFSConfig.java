package quanta.service.ipfs;

import java.util.LinkedHashMap;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.util.Cast;
import quanta.util.XString;

@Component
@Slf4j 
public class IPFSConfig extends ServiceBase {
    public String API_CONFIG;

    @PostConstruct
    public void init() {
        API_CONFIG = prop.getIPFSApiBase() + "/config";
    }

    // todo-1:
    // add call to support CORS by calling this endpoint:
    //  https://docs.ipfs.tech/reference/kubo/rpc/#api-v0-config
    //  example: 
    //   curl -X POST "http://127.0.0.1:5001/api/v0/config?arg=<key>&arg=<value>&bool=<value>&json=<value>"
    // We'll put these values in:
    // docker-compose -f ${dc_yaml} exec $1 ipfs config --json API.HTTPHeaders.Access-Control-Allow-Origin '["*"]'
    // docker-compose -f ${dc_yaml} exec $1 ipfs config --json API.HTTPHeaders.Access-Control-Allow-Methods '["PUT", "GET", "POST"]'
    // docker-compose -f ${dc_yaml} exec $1 ipfs config --json Gateway.HTTPHeaders.Access-Control-Allow-Origin '["*"]'
    // docker-compose -f ${dc_yaml} exec $1 ipfs config --json Gateway.HTTPHeaders.Access-Control-Allow-Methods '["PUT", "GET", "POST"]'
    //
    // WARNING: It WILL require a restart of IPFS to make changes go into effect. Note you can probably check that these have been 
    // applied right after running the command however, by looking inside /ipfs/config (in the IPFS data directory)

    public String getStat() {
        StringBuilder sb = new StringBuilder();
        LinkedHashMap<String, Object> res = null;

        if (prop.ipfsEnabled()) {
            res = Cast.toLinkedHashMap(ipfs.postForJsonReply(ipfsRepo.API_REPO + "/stat?human=true", LinkedHashMap.class));
            sb.append("\nIPFS Repository Status:\n" + XString.prettyPrint(res) + "\n");

            res = Cast.toLinkedHashMap(ipfs.postForJsonReply(API_CONFIG + "/show", LinkedHashMap.class));
            sb.append("\nIPFS Config:\n" + XString.prettyPrint(res) + "\n");

            res = Cast.toLinkedHashMap(ipfs.postForJsonReply(ipfs.API_ID, LinkedHashMap.class));
            sb.append("\nIPFS Instance ID:\n" + XString.prettyPrint(res) + "\n");
        } else {
            sb.append("\nIPFS Repository Status: ipfsEnabled=false\n\n");
        }

        // res = Cast.toLinkedHashMap(postForJsonReply(API_PUBSUB + "/peers?arg=" + topic,
        // LinkedHashMap.class));
        // sb.append("\nIPFS Peers for topic:\n" + XString.prettyPrint(res) + "\n");

        // res = Cast.toLinkedHashMap(postForJsonReply(API_PUBSUB + "/ls", LinkedHashMap.class));
        // sb.append("\nIPFS Topics List:\n" + XString.prettyPrint(res) + "\n");

        return sb.toString();
    }
}
