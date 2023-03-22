package quanta.request;

import java.util.List;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class JoinNodesRequest extends RequestBase {
	private List<String> nodeIds;
}
