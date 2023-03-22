package quanta.request;

import java.util.List;
import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DeletePropertyRequest extends RequestBase {
	private String nodeId;
	private List<String> propNames;
}
