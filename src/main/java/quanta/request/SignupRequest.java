
package quanta.request;

import quanta.request.base.RequestBase;

public class SignupRequest extends RequestBase {
	private String userName;
	private String password;
	private String email;
	private String captcha;
	
	public String getUserName() {
		return this.userName;
	}
	
	public String getPassword() {
		return this.password;
	}
	
	public String getEmail() {
		return this.email;
	}
	
	public String getCaptcha() {
		return this.captcha;
	}
	
	public void setUserName(final String userName) {
		this.userName = userName;
	}
	
	public void setPassword(final String password) {
		this.password = password;
	}
	
	public void setEmail(final String email) {
		this.email = email;
	}
	
	public void setCaptcha(final String captcha) {
		this.captcha = captcha;
	}

	public SignupRequest() {
	}
}
