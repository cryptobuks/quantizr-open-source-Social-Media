import { Comp } from "../comp/base/Comp";
import { Divc } from "../comp/core/Divc";
import { Heading } from "../comp/core/Heading";
import { UserProfileDlg } from "../dlg/UserProfileDlg";
import { TabIntf } from "../intf/TabIntf";
import { NodeActionType } from "../intf/TypeIntf";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { TypeBase } from "./base/TypeBase";

export class AccountType extends TypeBase {
    constructor() {
        super(J.NodeType.ACCOUNT, "Account Root", "fa-database", false);
    }

    getAllowRowHeader(): boolean {
        return false;
    }

    allowAction(action: NodeActionType, node: J.NodeInfo): boolean {
        switch (action) {
            case NodeActionType.editNode:
                return false;
            default:
                return true;
        }
    }

    allowPropertyEdit(propName: string): boolean {
        return true;
    }

    render = (node: J.NodeInfo, tabData: TabIntf<any>, rowStyling: boolean, isTreeView: boolean, isLinkedNode: boolean): Comp => {
        return new Divc({
            className: "systemNodeContent"
        }, [
            new Heading(4, "User: " + node.owner, {
                className: "clickable noMargin",
                onClick: () => {
                    // If we're clicking on our own Account Node, then don't open the UserProfileDlg. For a person editing
                    // their own account this is not a way to do it.
                    if (!S.props.isMine(node)) {
                        new UserProfileDlg(node.ownerId).open();
                    }
                }
            })
        ]);
    }
}
