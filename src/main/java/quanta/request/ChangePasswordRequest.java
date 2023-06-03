
package quanta.request;

import quanta.request.base.RequestBase;

public class ChangePasswordRequest extends RequestBase {
	private String newPassword;
	/* passCode is only used during a Password Reset (not used during normal Change Password) */
	private String passCode;

	public String getNewPassword() {
		return this.newPassword;
	}

	public String getPassCode() {
		return this.passCode;
	}
	
	public void setNewPassword(final String newPassword) {
		this.newPassword = newPassword;
	}
	
	public void setPassCode(final String passCode) {
		this.passCode = passCode;
	}

	public ChangePasswordRequest() {
	}
}
