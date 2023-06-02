
package quanta.response;

import java.util.List;
import quanta.model.client.NostrUserInfo;
/* Holds a list of data to be pushed down to client for signing */
public class NewNostrUsersPushInfo extends ServerPushInfo {
	private List<NostrUserInfo> users;

	public NewNostrUsersPushInfo(List<NostrUserInfo> users) {
		super("newNostrUsersPush");
		this.users = users;
	}

	
	public List<NostrUserInfo> getUsers() {
		return this.users;
	}

	
	public void setUsers(final List<NostrUserInfo> users) {
		this.users = users;
	}

	public NewNostrUsersPushInfo() {
	}
}
