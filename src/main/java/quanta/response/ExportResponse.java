package quanta.response;

import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ExportResponse extends ResponseBase {
	private String ipfsCid;
	private String ipfsMime;
	private String fileName;
}
