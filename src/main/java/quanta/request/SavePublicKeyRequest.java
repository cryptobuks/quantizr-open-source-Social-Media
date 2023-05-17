package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SavePublicKeyRequest extends RequestBase {
	private String asymEncKey;
	private String sigKey;

	private String nostrNpub;
    private String nostrPubKey;
}
