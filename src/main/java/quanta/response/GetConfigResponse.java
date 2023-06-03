
package quanta.response;

import java.util.HashMap;
import quanta.response.base.ResponseBase;

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
    // these are the 'system defined' relays so that anonymous users can query for info.
    private String nostrRelays;

    public HashMap<String, Object> getConfig() {
        return this.config;
    }
    
    public Integer getSessionTimeoutMinutes() {
        return this.sessionTimeoutMinutes;
    }
    
    public String getBrandingAppName() {
        return this.brandingAppName;
    }
    
    public boolean isRequireCrypto() {
        return this.requireCrypto;
    }
    
    public String getUrlIdFailMsg() {
        return this.urlIdFailMsg;
    }
    
    public String getUserMsg() {
        return this.userMsg;
    }
    
    public String getDisplayUserProfileId() {
        return this.displayUserProfileId;
    }
    
    public String getInitialNodeId() {
        return this.initialNodeId;
    }
    
    public String getLoadNostrId() {
        return this.loadNostrId;
    }

    public String getLoadNostrIdRelays() {
        return this.loadNostrIdRelays;
    }
    
    public String getNostrRelays() {
        return this.nostrRelays;
    }
    
    public void setConfig(final HashMap<String, Object> config) {
        this.config = config;
    }
    
    public void setSessionTimeoutMinutes(final Integer sessionTimeoutMinutes) {
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
    }
    
    public void setBrandingAppName(final String brandingAppName) {
        this.brandingAppName = brandingAppName;
    }
    
    public void setRequireCrypto(final boolean requireCrypto) {
        this.requireCrypto = requireCrypto;
    }
    
    public void setUrlIdFailMsg(final String urlIdFailMsg) {
        this.urlIdFailMsg = urlIdFailMsg;
    }
    
    public void setUserMsg(final String userMsg) {
        this.userMsg = userMsg;
    }
    
    public void setDisplayUserProfileId(final String displayUserProfileId) {
        this.displayUserProfileId = displayUserProfileId;
    }
    
    public void setInitialNodeId(final String initialNodeId) {
        this.initialNodeId = initialNodeId;
    }
    
    public void setLoadNostrId(final String loadNostrId) {
        this.loadNostrId = loadNostrId;
    }
    
    public void setLoadNostrIdRelays(final String loadNostrIdRelays) {
        this.loadNostrIdRelays = loadNostrIdRelays;
    }
    
    public void setNostrRelays(final String nostrRelays) {
        this.nostrRelays = nostrRelays;
    }
    
    public GetConfigResponse() {
    }
}
