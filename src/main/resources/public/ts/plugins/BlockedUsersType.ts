import { Comp } from "../comp/base/Comp";
import { Div } from "../comp/core/Div";
import { Divc } from "../comp/core/Divc";
import { Heading } from "../comp/core/Heading";
import { TabIntf } from "../intf/TabIntf";
import { NodeActionType } from "../intf/TypeIntf";
import * as J from "../JavaIntf";
import { TypeBase } from "./base/TypeBase";

export class BlockedUsersType extends TypeBase {
    static helpExpanded: boolean;

    constructor() {
        super(J.NodeType.BLOCKED_USERS, "Blocked Users", "fa-ban", false);
    }

    override getAllowRowHeader(): boolean {
        return false;
    }

    override allowAction(action: NodeActionType, node: J.NodeInfo): boolean {
        return false;
    }

    override render = (node: J.NodeInfo, tabData: TabIntf<any>, rowStyling: boolean, isTreeView: boolean, isLinkedNode: boolean): Comp => {
        return new Divc({ className: "systemNodeContent" }, [
            new Heading(4, this.displayName),
            new Div("These are the people you've blocked. Delete from this list to unblock.", { className: "marginLeft" })
        ]);
    }

    override isSpecialAccountNode(): boolean {
        return true;
    }

    override subOrdinal(): number {
        return 4;
    }
}
