import { getAppState } from "../AppRedux";
import { AppState } from "../AppState";
import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Form } from "../comp/core/Form";
import { TextContent } from "../comp/core/TextContent";
import { TextField } from "../comp/core/TextField";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { ValidatedState } from "../ValidatedState";

export class ShareToPersonDlg extends DialogBase {

    userNameState: ValidatedState<any> = new ValidatedState<any>();

    constructor(private node: J.NodeInfo, private sharedNodeFunc: Function, state: AppState) {
        super("Share Node to Person", "app-modal-content-medium-width", false, state);
    }

    validate = (): boolean => {
        let valid = true;

        if (!this.userNameState.getValue()) {
            this.userNameState.setError("Cannot be empty.");
            valid = false;
        }
        else {
            if (this.userNameState.getValue() === getAppState().userName) {
                this.userNameState.setError("You can't share a node to yourself.");
                valid = false;
            }
            else {
                this.userNameState.setError(null);
            }
        }

        return valid;
    }

    renderDlg(): CompIntf[] {
        return [
            new Form(null, [
                new TextContent("Enter the user name of the person to share this node with:"),
                new TextField({ label: "User to share with", enter: this.shareNodeToPerson, val: this.userNameState }),
                new ButtonBar([
                    new Button("Share", this.shareNodeToPerson, null, "btn-primary"),
                    new Button("Close", this.close, null, "btn-secondary float-end")
                ], "marginTop")
            ])
        ];
    }

    shareNodeToPerson = () => {
        if (!this.validate()) {
            return;
        }

        this.shareImmediate(this.userNameState.getValue());
    }

    shareImmediate = async (userName: string) => {
        let res = await S.util.ajax<J.AddPrivilegeRequest, J.AddPrivilegeResponse>("addPrivilege", {
            nodeId: this.node.id,
            principal: userName,
            privileges: [J.PrivilegeType.READ, J.PrivilegeType.WRITE]
        });
        this.reload(res);
    }

    reload = async (res: J.AddPrivilegeResponse) => {
        if (S.util.checkSuccess("Share Node with Person", res)) {
            this.close();
            if (res.principalPublicKey) {
                await S.edit.addCipherKeyToNode(this.node, res.principalPublicKey, res.principalNodeId);
            }
            this.sharedNodeFunc(res);
        }
    }
}
