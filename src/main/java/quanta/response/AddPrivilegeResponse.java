
package quanta.response;

import quanta.response.base.ResponseBase;

public class AddPrivilegeResponse extends ResponseBase {
    private String principalPublicKey;
    /*
     * we send this back to the client, for use as the more efficient way to identify the user after the
     * browser encrypts a key to send back to the server
     */
    private String principalNodeId;
    
    public String getPrincipalPublicKey() {
        return this.principalPublicKey;
    }
    
    public String getPrincipalNodeId() {
        return this.principalNodeId;
    }
    
    public void setPrincipalPublicKey(final String principalPublicKey) {
        this.principalPublicKey = principalPublicKey;
    }
    
    public void setPrincipalNodeId(final String principalNodeId) {
        this.principalNodeId = principalNodeId;
    }

    public AddPrivilegeResponse() {
    }
}
