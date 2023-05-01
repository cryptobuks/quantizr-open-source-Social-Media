package quanta.response;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
/* Holds a list of data to be pushed down to client for signing */
public class NewNostrUsersPushInfo extends ServerPushInfo {
	private List<String> users;

	public NewNostrUsersPushInfo(List<String> users) {
		super("newNostrUsersPush");
		this.users = users;
	}
}
