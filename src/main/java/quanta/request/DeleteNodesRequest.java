package quanta.request;

import java.util.List;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DeleteNodesRequest extends RequestBase {
	private List<String> nodeIds;
	private boolean childrenOnly;
	private boolean bulkDelete;
}
