package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class InsertBookRequest extends RequestBase {
	private String nodeId;
	private String bookName;

	/* set to true to only insert a portion of the entire book */
	private Boolean truncated;
}
