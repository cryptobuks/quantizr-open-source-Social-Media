package quanta.response;

import quanta.model.NodeInfo;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class InsertBookResponse extends ResponseBase {
	private NodeInfo newNode;
}
