
package quanta.response;

import quanta.response.base.ResponseBase;

public class ChangePasswordResponse extends ResponseBase {
	/*
	 * Whenever a password reset is being done, the user will be sent back to the browser in this var
	 */
	private String user;

	
	public String getUser() {
		return this.user;
	}

	
	public void setUser(final String user) {
		this.user = user;
	}

	public ChangePasswordResponse() {
	}
}
