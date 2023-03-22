package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SetCipherKeyRequest extends RequestBase {
	private String nodeId;
	private String principalNodeId;
	private String cipherKey;
}
