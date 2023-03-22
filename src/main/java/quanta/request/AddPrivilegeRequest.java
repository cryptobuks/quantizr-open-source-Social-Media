package quanta.request;

import java.util.List;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AddPrivilegeRequest extends RequestBase {

	private String nodeId;

	/* for now only 'public' is the only option we support */
	private List<String> privileges;

	private String[] principals;
}
