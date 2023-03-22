package quanta.response;

import quanta.model.client.UserProfile;
import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetUserProfileResponse extends ResponseBase {
	private UserProfile userProfile;
}
