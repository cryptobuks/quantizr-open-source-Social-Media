package quanta.response;

import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AddPrivilegeResponse extends ResponseBase {
    private String principalPublicKey;

    /*
     * we send this back to the client, for use as the more efficient way to identify the user after the
     * browser encrypts a key to send back to the server
     */
    private String principalNodeId;
}
