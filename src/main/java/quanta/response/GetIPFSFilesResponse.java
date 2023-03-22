package quanta.response;

import java.util.List;
import quanta.model.client.MFSDirEntry;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetIPFSFilesResponse extends ResponseBase {
	public List<MFSDirEntry> files;

	// returns whatever folder ended up gettin gloaded
	public String folder;
	public String cid;
}
