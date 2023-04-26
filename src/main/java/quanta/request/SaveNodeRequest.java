package quanta.request;

import quanta.model.NodeInfo;
import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SaveNodeRequest extends RequestBase {
	private NodeInfo node;
	boolean saveToActPub;
}
