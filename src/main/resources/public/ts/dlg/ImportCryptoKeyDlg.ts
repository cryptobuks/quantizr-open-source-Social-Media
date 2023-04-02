import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Diva } from "../comp/core/Diva";
import { TextArea } from "../comp/core/TextArea";
import { DialogBase } from "../DialogBase";
import { S } from "../Singletons";
import { Validator } from "../Validator";
import { MessageDlg } from "./MessageDlg";

export class ImportCryptoKeyDlg extends DialogBase {
    keyState: Validator = new Validator();

    constructor(private keyType: string, keyDescription: string) {
        super("Import " + keyDescription, "appModalContMediumWidth");
    }

    renderDlg(): CompIntf[] {
        return [
            new Diva([
                new TextArea("Key JWK", { rows: 15 }, this.keyState, null, false),
                new ButtonBar([
                    new Button("Import", this.import, null, "btn-primary"),
                    new Button("Close", this.close, null, "btn-secondary float-end")
                ], "marginTop")
            ])
        ];
    }

    import = async () => {
        const keyText = this.keyState.getValue();
        if (!keyText) {
            S.util.showMessage("Enter key text.", "Warning");
            return;
        }
        try {
            let success = false;
            switch (this.keyType) {
                case "sig":
                    success = await S.crypto.importSigKeyPair(keyText);
                    break;
                case "asym":
                    success = await S.crypto.importAsymKeyPair(keyText);
                    break;
                case "sym":
                    // not currently used
                    // alert("Feature not available.");
                    break;
                default: break;
            }

            if (!success) {
                S.util.showMessage("Invalid key text", "Warning");
                return;
            }
            else {
                this.close();
                await S.crypto.initKeys(S.quanta.userName, false, true, false, this.keyType);

                const dlg = new MessageDlg("Keys imported successfully", "Keys", () => {
                    window.location.reload();
                }, null, false, 0, null);
                dlg.open();
            }
        }
        catch (e) {
            alert("Unable to import key: " + e.message);
        }
    }
}
