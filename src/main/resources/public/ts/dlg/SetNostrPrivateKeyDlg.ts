import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Diva } from "../comp/core/Diva";
import { TextContent } from "../comp/core/TextContent";
import { TextField } from "../comp/core/TextField";
import { DialogBase } from "../DialogBase";
import { Validator, ValidatorRuleName } from "../Validator";
import { S } from "../Singletons";
import { getAs } from "../AppContext";

export class SetNostrPrivateKeyDlg extends DialogBase {

    keyField: TextField;
    keyState: Validator = new Validator("", [
        { name: ValidatorRuleName.REQUIRED }
    ]);

    constructor() {
        super("Nostr Public Key", "appModalContNarrowWidth");
        this.onMount(() => this.keyField?.focus());
        this.validatedStates = [this.keyState];
    }

    renderDlg(): CompIntf[] {
        return [
            new Diva([
                new TextContent("Enter your new public key..."),
                this.keyField = new TextField({
                    label: "New Public Key",
                    // inputType: "password",
                    val: this.keyState
                }),
                new ButtonBar([
                    new Button("Set Public Key", this.setKey, null, "btn-primary"),
                    new Button("Cancel", this.close, null, "btn-secondary float-end")
                ], "marginTop")
            ])
        ];
    }

    /*
     * If the user is doing a "Reset Password" we will have a non-null passCode here, and we simply send this to the server
     * where it will validate the passCode, and if it's valid use it to perform the correct password change on the correct
     * user.
     */
    setKey = async () => {
        if (!this.validate()) {
            return;
        }
        await S.nostr.setPrivateKey(this.keyState.getValue(), getAs().userName);
        this.close();
    }
}
