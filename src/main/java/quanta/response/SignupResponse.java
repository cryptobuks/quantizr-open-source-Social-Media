
package quanta.response;

import quanta.response.base.ResponseBase;

public class SignupResponse extends ResponseBase {
    private String userError;
    private String passwordError;
    private String emailError;
    private String captchaError;

    
    public String getUserError() {
        return this.userError;
    }

    
    public String getPasswordError() {
        return this.passwordError;
    }

    
    public String getEmailError() {
        return this.emailError;
    }

    
    public String getCaptchaError() {
        return this.captchaError;
    }

    
    public void setUserError(final String userError) {
        this.userError = userError;
    }

    
    public void setPasswordError(final String passwordError) {
        this.passwordError = passwordError;
    }

    
    public void setEmailError(final String emailError) {
        this.emailError = emailError;
    }

    
    public void setCaptchaError(final String captchaError) {
        this.captchaError = captchaError;
    }
    
    public SignupResponse() {
    }
}
