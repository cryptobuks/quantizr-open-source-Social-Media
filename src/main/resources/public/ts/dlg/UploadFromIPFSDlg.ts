import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Diva } from "../comp/core/Diva";
import { TextField } from "../comp/core/TextField";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { Validator, ValidatorRuleName } from "../Validator";

export class UploadFromIPFSDlg extends DialogBase {
    static storeLocally: boolean = false;
    uploadButton: Button;

    cidState: Validator = new Validator("", [
        { name: ValidatorRuleName.REQUIRED }
    ]);

    mimeState: Validator = new Validator("", [
        { name: ValidatorRuleName.REQUIRED }
    ]);

    constructor(private nodeId: string, private onUploadFunc: Function) {
        super("Upload File");
        this.validatedStates = [this.cidState, this.mimeState];
    }

    renderDlg(): CompIntf[] {
        return [
            new Diva([
                new TextField({ label: "Upload from IPFS CID", val: this.cidState }),
                new TextField({ label: "Mime Type (or Filename Extension)", val: this.mimeState }),
                new ButtonBar([
                    this.uploadButton = new Button("Save", this.upload, null, "btn-primary"),
                    new Button("Close", this.close, null, "btn-secondary float-end")
                ], "marginTop")

                // todo-2: this would be very nice to have!
                // new Span(null, { className: "marginLeft" }, [
                //     new Checkbox("PIN a copy on this server", null, {
                //         setValue: (checked: boolean) => {
                //             UploadFromUrlDlg.storeLocally = checked;
                //         },
                //         getValue: (): boolean => {
                //             return UploadFromUrlDlg.storeLocally;
                //         }
                //     })
                // ])
            ])
        ];
    }

    upload = async () => {
        if (!this.validate()) {
            return;
        }

        const res = await S.rpcUtil.rpc<J.UploadFromIPFSRequest, J.UploadFromIPFSResponse>("uploadFromIPFS", {
            pinLocally: false, // UploadFromUrlDlg.storeLocally,
            nodeId: this.nodeId,
            cid: this.cidState.getValue(),
            mime: this.mimeState.getValue()
        });
        this.uploadFromIPFSResponse(res);
    }

    uploadFromIPFSResponse = (res: J.UploadFromUrlResponse) => {
        if (S.util.checkSuccess("Upload from IPFS", res)) {
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
