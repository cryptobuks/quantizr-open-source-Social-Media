package quanta.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import quanta.request.base.RequestBase;

@Data
@NoArgsConstructor
public class GetThreadViewRequest extends RequestBase {
	private String nodeId;
	private boolean loadOthers;
}
