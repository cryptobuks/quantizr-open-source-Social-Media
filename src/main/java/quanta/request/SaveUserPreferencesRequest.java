package quanta.request;

import quanta.model.UserPreferences;
import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SaveUserPreferencesRequest extends RequestBase {
	private String userNodeId;
	private UserPreferences userPreferences;
}
