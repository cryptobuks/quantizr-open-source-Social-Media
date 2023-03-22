package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LoadNodeFromIpfsRequest extends RequestBase {
	private String path;
}
