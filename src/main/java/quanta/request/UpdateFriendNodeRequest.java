package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor

public class UpdateFriendNodeRequest extends RequestBase {
	private String nodeId;
	private String tags;
}
