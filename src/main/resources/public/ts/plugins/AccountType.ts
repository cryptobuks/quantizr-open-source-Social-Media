import { nip19 } from "nostr-tools";
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

    override getAllowRowHeader(): boolean {
        return false;
    }

    override allowAction(action: NodeActionType, node: J.NodeInfo): boolean {
        switch (action) {
            case NodeActionType.editNode:
                return false;
            default:
                return true;
        }
    }

    override allowPropertyEdit(propName: string): boolean {
        return true;
    }

    override render = (node: J.NodeInfo, tabData: TabIntf<any>, rowStyling: boolean, isTreeView: boolean, isLinkedNode: boolean): Comp => {
        // Note: Nostr names start with '.'
        const isNostr = S.nostr.isNostrUserName(node.owner);
        let name = null;
        if (isNostr) {
            name = S.props.getPropStr(J.NodeProp.DISPLAY_NAME, node) ||
                S.props.getPropStr(J.NodeProp.NOSTR_NAME, node) ||
                S.props.getPropStr(J.NodeProp.NOSTR_USER_NAME, node);

            if (!name) {
                name = nip19.npubEncode(node.owner.substring(1));
                if (name) {
                    name = name.substring(0, 15) + "...";
                }
            }
        }
        else {
            name = node.owner;
        }
        return new Divc({
            className: "systemNodeContent"
        }, [
            new Heading(4, "User: " + name, {
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
