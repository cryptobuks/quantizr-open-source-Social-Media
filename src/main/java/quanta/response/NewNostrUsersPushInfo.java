package quanta.response;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import quanta.model.client.NostrUserInfo;

@Data
@NoArgsConstructor
/* Holds a list of data to be pushed down to client for signing */
public class NewNostrUsersPushInfo extends ServerPushInfo {
	private List<NostrUserInfo> users;

	public NewNostrUsersPushInfo(List<NostrUserInfo> users) {
		super("newNostrUsersPush");
		this.users = users;
	}
}
