package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LikeNodeRequest extends RequestBase {
	private String id;
	private boolean like;
}
