package quanta.response;

import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FileSearchResponse extends ResponseBase {
	private String searchResultNodeId;
}
