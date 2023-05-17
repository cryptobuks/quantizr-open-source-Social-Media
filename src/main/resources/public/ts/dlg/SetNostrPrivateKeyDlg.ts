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
import { ConfirmDlg } from "./ConfirmDlg";

export class SetNostrPrivateKeyDlg extends DialogBase {

    keyField: TextField;
    keyState: Validator = new Validator("", [
        { name: ValidatorRuleName.REQUIRED }
    ]);

    constructor() {
        super("Nostr Private Key", "appModalContNarrowWidth");
        this.onMount(() => this.keyField?.focus());
        this.validatedStates = [this.keyState];
    }

    renderDlg(): CompIntf[] {
        return [
            new Diva([
                new TextContent("Enter your new private key..."),
                this.keyField = new TextField({
                    label: "New Private Key",
                    // inputType: "password",
                    val: this.keyState
                }),
                new ButtonBar([
                    new Button("Set Private Key", this.setKey, null, "btn-primary"),
                    new Button("Generate New Private Key", this.genKey, null, "btn-primary"),
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

    genKey = async () => {
        const dlg = new ConfirmDlg("Create New Private Key, and forget old Key?", "Warning",
            "btn-danger", "alert alert-danger");
        await dlg.open();
        if (dlg.yes) {
            await S.nostr.generateNewKey(getAs().userName, true);
            setTimeout(S.nostr.publishUserMetadata, 1500);
            this.close();
        }
    }
}
