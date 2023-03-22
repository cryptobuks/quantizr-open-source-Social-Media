package quanta.response;

import java.util.List;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetServerInfoResponse extends ResponseBase {
	private List<InfoMessage> messages;
}
