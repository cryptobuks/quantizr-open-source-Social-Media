import { PubSub } from "../PubSub";
import { DialogBase } from "../DialogBase";
import { MessageDlg } from "./MessageDlg";
import * as J from "../JavaIntf";
import { PasswordTextField } from "../widget/PasswordTextField";
import { TextContent } from "../widget/TextContent";
import { ButtonBar } from "../widget/ButtonBar";
import { Button } from "../widget/Button";
import { Constants as C } from "../Constants";
import { Singletons } from "../Singletons";
import { Form } from "../widget/Form";

let S: Singletons;
PubSub.sub(C.PUBSUB_SingletonsReady, (ctx: Singletons) => {
    S = ctx;
});

export class ChangePasswordDlg extends DialogBase {

    passwordField: PasswordTextField;
    pwd: string;
    private passCode: string;

    constructor(args: Object) {
        super((<any>args).passCode ? "Password Reset" : "Change Password", "app-modal-content-narrow-width");
        this.passCode = (<any>args).passCode;
        
        this.setChildren([
            new Form(null, [
                new TextContent("Enter your new password below..."),
                this.passwordField = new PasswordTextField("New Password"),
                new ButtonBar([
                    new Button("Change Password", () => {
                        this.changePassword();
                        this.close();
                    }, null, "btn-primary"),
                    new Button("Close", () =>{
                        this.close();
                    })
                ])
            ])
        ]);
    }

    /*
     * If the user is doing a "Reset Password" we will have a non-null passCode here, and we simply send this to the server
     * where it will validate the passCode, and if it's valid use it to perform the correct password change on the correct
     * user.
     */
    changePassword = (): void => {
        this.pwd = this.passwordField.getValue();

        if (this.pwd && this.pwd.length >= 4) {
            S.util.ajax<J.ChangePasswordRequest, J.ChangePasswordResponse>("changePassword", {
                "newPassword": this.pwd,
                "passCode": this.passCode
            }, this.changePasswordResponse);
        } else {
            S.util.showMessage("Invalid password(s).");
        }
    }

    changePasswordResponse = (res: J.ChangePasswordResponse) => {
        if (S.util.checkSuccess("Change password", res)) {
            let msg = "Password changed successfully.";

            if (this.passCode) {
                msg += `<p>You can now login as <b>${res.user}</b> with your new password.`;
            }

            let dlg = new MessageDlg(msg, "Password Change",
                () => {
                    if (this.passCode) {
                        window.location.href = window.location.origin;
                    }
                }
            );
            dlg.open();
        }
    }

    init = (): void => {
        this.passwordField.focus();
    }
}
