package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SetUnpublishedRequest extends RequestBase {
	private String nodeId;
	private boolean unpublished;
}
