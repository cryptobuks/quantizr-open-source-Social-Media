package quanta.response;

import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor

public class ChangePasswordResponse extends ResponseBase {
	/*
	 * Whenever a password reset is being done, the user will be sent back to the browser in this var
	 */
	private String user;
}
