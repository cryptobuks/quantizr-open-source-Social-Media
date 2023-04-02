import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Diva } from "../comp/core/Diva";
import { HorizontalLayout } from "../comp/core/HorizontalLayout";
import { Img } from "../comp/core/Img";
import { TextField } from "../comp/core/TextField";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { Validator, ValidatorRuleName } from "../Validator";

export class SignupDlg extends DialogBase {

    userNameState: Validator = new Validator("", [
        { name: ValidatorRuleName.REQUIRED },
        { name: ValidatorRuleName.MINLEN, payload: 3 },
        { name: ValidatorRuleName.MAXLEN, payload: 100 },
        { name: ValidatorRuleName.USERNAME }
    ]);

    passwordState: Validator = new Validator("", [
        { name: ValidatorRuleName.MINLEN, payload: 5 },
        { name: ValidatorRuleName.MAXLEN, payload: 40 },
        { name: ValidatorRuleName.REQUIRED }
    ]);

    emailState: Validator = new Validator("", [
        { name: ValidatorRuleName.REQUIRED },
        { name: ValidatorRuleName.MINLEN, payload: 5 },
        { name: ValidatorRuleName.MAXLEN, payload: 100 }
    ]);

    captchaState: Validator = new Validator("", [{ name: ValidatorRuleName.REQUIRED }]);

    constructor(private adminCreatingUser: boolean = false) {
        super("Create Account", "appModalContMediumWidth");
        this.validatedStates = [this.userNameState, this.passwordState, this.emailState, this.captchaState];
    }

    renderDlg(): CompIntf[] {
        return [
            new Diva([
                new TextField({ label: "User Name", val: this.userNameState }),
                new TextField({ label: "Password", inputType: "password", val: this.passwordState }),
                new TextField({ label: "Email", val: this.emailState }),

                this.adminCreatingUser ? null : new HorizontalLayout([
                    new Img({
                        src: window.location.origin + "/mobile/api/captcha?cacheBuster=" + this.getId(),
                        className: "captchaImage"
                    }),
                    new Diva([
                        new TextField({ label: "Enter Numbers Displayed", val: this.captchaState })
                    ])
                ]),
                new ButtonBar([
                    new Button("Create Account", this.signup, null, "btn-primary"),
                    new Button("Cancel", this.close, { className: "float-end" })
                ], "marginTop")
            ])
        ];
    }

    signup = () => {
        if (this.adminCreatingUser) {
            // this string is just to make the validator succeed, and used as an extra confirmation on the server
            // that the intent of the admin is the creation of a new user. This is not a security risk, but is safe
            // in terms of security, because the server side also confirms independently that this is the admin doing this.
            this.captchaState.setValue("adminCreatingUser");
        }

        if (!this.validate()) {
            return;
        }
        this.signupNow();
    }

    signupNow = async () => {
        const res = await S.rpcUtil.rpc<J.SignupRequest, J.SignupResponse>("signup", {
            userName: this.userNameState.getValue(),
            password: this.passwordState.getValue(),
            email: this.emailState.getValue(),
            captcha: this.captchaState.getValue()
        });
        this.signupResponse(res);
    }

    signupResponse = async (res: J.SignupResponse): Promise<void> => {
        if (res.success) {
            this.close();

            if (this.adminCreatingUser) {
                await S.util.showMessage("User '" + this.userNameState.getValue() + "' created successfully.", "New User");
            }
            else {
                await S.util.showMessage(
                    "Check your email for verification link.", "Welcome to " + S.quanta.configRes.brandingAppName + "!"
                );

                window.location.href = window.location.origin;
            }
        }
        else {
            this.userNameState.setError(res.userError);
            this.passwordState.setError(res.passwordError);
            this.emailState.setError(res.emailError);
            this.captchaState.setError(res.captchaError);
        }
        return null;
    }
}
