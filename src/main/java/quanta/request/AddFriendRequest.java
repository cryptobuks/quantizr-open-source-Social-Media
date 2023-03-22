package quanta.request;

import quanta.request.base.RequestBase;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AddFriendRequest extends RequestBase {
	private String userName;
}
