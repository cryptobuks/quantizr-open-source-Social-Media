package quanta.response;

import quanta.model.GraphNode;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GraphResponse extends ResponseBase {
	private GraphNode rootNode;
}

