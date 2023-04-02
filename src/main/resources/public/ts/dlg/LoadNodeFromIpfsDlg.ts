import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Diva } from "../comp/core/Diva";
import { HorizontalLayout } from "../comp/core/HorizontalLayout";
import { TextField } from "../comp/core/TextField";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { Validator, ValidatorRuleName } from "../Validator";

export class LoadNodeFromIpfsDlg extends DialogBase {

    ipfsPathState: Validator = new Validator("", [
        { name: ValidatorRuleName.REQUIRED }
    ]);

    constructor() {
        super("Load from IPFS", "appModalContNarrowWidth");
        this.validatedStates = [this.ipfsPathState];
    }

    renderDlg(): CompIntf[] {
        return [
            new Diva([
                new HorizontalLayout([
                    new TextField({ label: "IPFS Path", enter: this.load, val: this.ipfsPathState })
                ]),
                new ButtonBar([
                    new Button("Load", this.load, null, "btn-primary"),
                    new Button("Close", this.close, null, "btn-secondary float-end")
                ], "marginTop")
            ])
        ];
    }

    load = async () => {
        if (!this.validate()) {
            return;
        }

        const res = await S.rpcUtil.rpc<J.LoadNodeFromIpfsRequest, J.LoadNodeFromIpfsResponse>("loadNodeFromIpfs", {
            path: this.ipfsPathState.getValue()
        });

        S.util.showMessage(res.message, "Server Reply", true);
    }
}
