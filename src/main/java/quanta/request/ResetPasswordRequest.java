
package quanta.request;

import quanta.request.base.RequestBase;

public class ResetPasswordRequest extends RequestBase {
	private String user;
	private String email;

	
	public String getUser() {
		return this.user;
	}

	
	public String getEmail() {
		return this.email;
	}

	
	public void setUser(final String user) {
		this.user = user;
	}

	
	public void setEmail(final String email) {
		this.email = email;
	}

	public ResetPasswordRequest() {
	}
}
