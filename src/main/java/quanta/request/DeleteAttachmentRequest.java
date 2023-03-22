package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DeleteAttachmentRequest extends RequestBase {
	private String nodeId;

	// comma delimited list of names of attachments to delete (the map keys)
	private String attName;
}
