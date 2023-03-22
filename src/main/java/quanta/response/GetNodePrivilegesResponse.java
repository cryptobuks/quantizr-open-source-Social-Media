package quanta.response;

import java.util.List;

import quanta.model.AccessControlInfo;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetNodePrivilegesResponse extends ResponseBase {
	private List<AccessControlInfo> aclEntries;
}
