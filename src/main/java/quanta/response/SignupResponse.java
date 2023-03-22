package quanta.response;

import quanta.response.base.ResponseBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SignupResponse extends ResponseBase {
    private String userError;
    private String passwordError;
    private String emailError;
    private String captchaError;
}
