import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Checkbox } from "../comp/core/Checkbox";
import { Diva } from "../comp/core/Diva";
import { Divc } from "../comp/core/Divc";
import { TextField } from "../comp/core/TextField";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { Validator, ValidatorRuleName } from "../Validator";

export class UploadFromUrlDlg extends DialogBase {

    static storeLocally: boolean = false;

    urlState: Validator = new Validator("", [
        { name: ValidatorRuleName.REQUIRED }
    ]);

    constructor(private nodeId: string, private onUploadFunc: Function) {
        super("Upload File");
        this.validatedStates = [this.urlState];
    }

    renderDlg(): CompIntf[] {
        return [
            new Diva([
                new TextField({ label: "Upload from URL", val: this.urlState }),
                new Divc({ className: "marginTop" }, [
                    new Checkbox("Store a copy on this server", null, {
                        setValue: (checked: boolean) => UploadFromUrlDlg.storeLocally = checked,
                        getValue: (): boolean => UploadFromUrlDlg.storeLocally
                    })
                ]),
                new ButtonBar([
                    new Button("Upload", this.upload, null, "btn-primary"),
                    new Button("Close", this.close, null, "btn-secondary float-end")
                ], "marginTop")
            ])
        ];
    }

    upload = async () => {
        if (!this.validate()) {
            return;
        }

        const res = await S.rpcUtil.rpc<J.UploadFromUrlRequest, J.UploadFromUrlResponse>("uploadFromUrl", {
            storeLocally: UploadFromUrlDlg.storeLocally,
            nodeId: this.nodeId,
            sourceUrl: this.urlState.getValue()
        });
        this.uploadFromUrlResponse(res);
    }

    uploadFromUrlResponse = (res: J.UploadFromUrlResponse) => {
        if (S.util.checkSuccess("Upload from URL", res)) {
            this.close();

            if (this.onUploadFunc) {
                this.onUploadFunc();
            }
            else {
                S.quanta.refresh();
            }
        }
    }
}
