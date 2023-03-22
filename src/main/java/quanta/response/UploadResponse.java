package quanta.response;

import java.util.List;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UploadResponse extends ResponseBase {
	private List<String> payloads;
}
