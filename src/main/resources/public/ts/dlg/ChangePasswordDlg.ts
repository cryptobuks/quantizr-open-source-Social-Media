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
import { MessageDlg } from "./MessageDlg";

export class ChangePasswordDlg extends DialogBase {

    passwordField: TextField;
    pwdState: Validator = new Validator("", [
        { name: ValidatorRuleName.REQUIRED },
        { name: ValidatorRuleName.MINLEN, payload: 4 }
    ]);

    constructor(private passCode: string) {
        super(passCode ? "Password Reset" : "Change Password", "appModalContNarrowWidth");
        this.onMount(() => this.passwordField?.focus());
        this.validatedStates = [this.pwdState];
    }

    renderDlg(): CompIntf[] {
        return [
            new Diva([
                new TextContent("Enter new password below..."),
                this.passwordField = new TextField({
                    label: "New Password",
                    inputType: "password",
                    val: this.pwdState
                }),
                new ButtonBar([
                    new Button("Change Password", this.changePassword, null, "btn-primary"),
                    new Button("Close", this.close, null, "btn-secondary float-end")
                ], "marginTop")
            ])
        ];
    }

    /*
     * If the user is doing a "Reset Password" we will have a non-null passCode here, and we simply send this to the server
     * where it will validate the passCode, and if it's valid use it to perform the correct password change on the correct
     * user.
     */
    changePassword = async () => {
        if (!this.validate()) {
            return;
        }
        const pwd = this.pwdState.getValue();
        const res = await S.rpcUtil.rpc<J.ChangePasswordRequest, J.ChangePasswordResponse>("changePassword", {
            newPassword: pwd,
            passCode: this.passCode
        });
        this.changePasswordResponse(res);
    }

    changePasswordResponse = (res: J.ChangePasswordResponse) => {
        if (S.util.checkSuccess("Change password", res)) {
            this.close();
            let msg = "Password changed successfully.";

            if (this.passCode) {
                msg += `<p>You can now login as <b>${res.user}</b> with your new password.`;
            }

            new MessageDlg(msg, "Password Change",
                () => {
                    if (this.passCode) {
                        window.location.href = window.location.origin;
                    }
                }, null, false, 0, null
            ).open();
        }
    }
}
