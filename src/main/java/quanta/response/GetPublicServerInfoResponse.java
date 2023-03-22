package quanta.response;

import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetPublicServerInfoResponse extends ResponseBase {
	private String serverInfo;
}
