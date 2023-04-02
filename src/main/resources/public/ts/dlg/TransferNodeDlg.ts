import { getAs } from "../AppContext";
import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Checkbox } from "../comp/core/Checkbox";
import { Diva } from "../comp/core/Diva";
import { HorizontalLayout } from "../comp/core/HorizontalLayout";
import { TextField } from "../comp/core/TextField";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { Validator, ValidatorRuleName } from "../Validator";

interface LS { // Local State
    recursive?: boolean;
}

export class TransferNodeDlg extends DialogBase {
    toUserState: Validator = new Validator("", [
        { name: ValidatorRuleName.REQUIRED }
    ]);

    fromUserState: Validator = new Validator();

    constructor(private operation: string) {
        super(TransferNodeDlg.operationName(operation) + " Nodes", "appModalContNarrowWidth");
        this.mergeState<LS>({ recursive: false });

        if (operation === "transfer") {
            this.validatedStates = [this.toUserState];
        }
    }

    renderDlg(): CompIntf[] {
        return [
            new Diva([
                this.operation === "transfer" ? new HorizontalLayout([
                    // Only the admin user can transfer from anyone to anyone. Other users can only transfer nodes they own
                    getAs().isAdminUser ? new TextField({ label: "From User", val: this.fromUserState }) : null,
                    new TextField({ label: "To User", val: this.toUserState })
                ]) : null,
                new HorizontalLayout([
                    new Checkbox("Include Sub-Nodes", null, {
                        setValue: (checked: boolean) => this.mergeState<LS>({ recursive: checked }),
                        getValue: (): boolean => this.getState<LS>().recursive
                    })
                ]),
                new ButtonBar([
                    new Button(TransferNodeDlg.operationName(this.operation), this.transfer, null, "btn-primary"),
                    new Button("Close", this.close, null, "btn-secondary float-end")
                ], "marginTop")
            ])
        ];
    }

    static operationName(op: string) {
        switch (op) {
            case "transfer":
                return "Transfer";
            case "reject":
                return "Reject";
            case "accept":
                return "Accept";
            case "reclaim":
                return "Reclaim";
            default:
                return "???";
        }
    }

    transfer = async () => {
        if (!this.validate()) {
            return;
        }

        const node = S.nodeUtil.getHighlightedNode();
        if (!node) {
            S.util.showMessage("No node was selected.", "Warning");
            return;
        }

        const res = await S.rpcUtil.rpc<J.TransferNodeRequest, J.TransferNodeResponse>("transferNode", {
            recursive: this.getState<LS>().recursive,
            nodeId: node.id,
            fromUser: this.fromUserState.getValue(),
            toUser: this.toUserState.getValue(),
            operation: this.operation
        });

        S.view.refreshTree({
            nodeId: null,
            zeroOffset: false,
            renderParentIfLeaf: false,
            highlightId: null,
            forceIPFSRefresh: false,
            scrollToTop: true,
            allowScroll: true,
            setTab: true,
            forceRenderParent: false,
            jumpToRss: false
        });
        S.util.showMessage(res.message, "Success");
        this.close();
    }
}
