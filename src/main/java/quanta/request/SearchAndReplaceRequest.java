package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SearchAndReplaceRequest extends RequestBase {
	private boolean recursive;
	private String nodeId;
	private String search;
	private String replace;
}
