package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UploadFromIPFSRequest extends RequestBase {
	/* if this is false we store only a link to the file, rather than copying it into our db */
	private boolean pinLocally;
	private String nodeId;
	private String cid;
	private String mime;
}
