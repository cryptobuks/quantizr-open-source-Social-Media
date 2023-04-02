import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Diva } from "../comp/core/Diva";
import { TextContent } from "../comp/core/TextContent";
import { TextField } from "../comp/core/TextField";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { Validator, ValidatorRuleName } from "../Validator";

interface LS { // Local State
    user: string;
}

export class ResetPasswordDlg extends DialogBase {

    userState: Validator = new Validator("", [{ name: ValidatorRuleName.REQUIRED }]);
    emailState: Validator = new Validator("", [{ name: ValidatorRuleName.REQUIRED }]);

    constructor(user: string) {
        super("Reset Password", "appModalContNarrowWidth");
        this.mergeState<LS>({ user });
        this.validatedStates = [this.userState, this.emailState];
    }

    renderDlg(): CompIntf[] {
        return [
            new Diva([
                new TextContent("Enter your user name and email address to receive a reset link."),
                new TextField({ label: "User Name", val: this.userState }),
                new TextField({ label: "Email Address", val: this.emailState }),
                new ButtonBar([
                    new Button("Reset Password", this.resetPassword, null, "btn-primary"),
                    new Button("Close", this.close, null, "btn-secondary float-end")
                ], "marginTop")
            ])
        ];
    }

    resetPassword = async () => {
        if (!this.validate()) {
            return;
        }

        const res = await S.rpcUtil.rpc<J.ResetPasswordRequest, J.ResetPasswordResponse>("resetPassword", {
            user: this.userState.getValue(),
            email: this.emailState.getValue()
        });
        this.resetPasswordResponse(res);
    }

    resetPasswordResponse = (res: J.ResetPasswordResponse) => {
        if (S.util.checkSuccess("Reset password", res)) {
            this.close();
            S.util.showMessage("Password reset email was sent. Check your email.", "Note");
        }
    }
}
