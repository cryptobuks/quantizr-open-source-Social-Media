package quanta.response;

import java.util.HashMap;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetConfigResponse extends ResponseBase {
    private HashMap<String, Object> config;
    private Integer sessionTimeoutMinutes;
    private String brandingAppName;
    private boolean requireCrypto;
    private String urlIdFailMsg;
    private String userMsg;
    private String displayUserProfileId;
    private String initialNodeId;
    private String loadNostrId;
    private String loadNostrIdRelays;
}
