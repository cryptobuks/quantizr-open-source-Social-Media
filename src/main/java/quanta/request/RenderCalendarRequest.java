package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RenderCalendarRequest extends RequestBase {
	/* can be node id or path. server interprets correctly no matter which */
	private String nodeId;
}
