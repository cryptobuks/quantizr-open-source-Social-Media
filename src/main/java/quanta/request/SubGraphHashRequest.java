package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SubGraphHashRequest extends RequestBase {
	private boolean recursive;
	private String nodeId;
}
