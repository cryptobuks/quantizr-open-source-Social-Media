package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ChangePasswordRequest extends RequestBase {
	private String newPassword;

	/* passCode is only used during a Password Reset (not used during normal Change Password) */
	private String passCode;
}
