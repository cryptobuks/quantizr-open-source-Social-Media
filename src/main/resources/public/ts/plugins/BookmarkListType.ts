import { Comp } from "../comp/base/Comp";
import { Div } from "../comp/core/Div";
import { Divc } from "../comp/core/Divc";
import { Heading } from "../comp/core/Heading";
import { TabIntf } from "../intf/TabIntf";
import { NodeActionType } from "../intf/TypeIntf";
import * as J from "../JavaIntf";
import { TypeBase } from "./base/TypeBase";

export class BookmarkListType extends TypeBase {
    constructor() {
        super(J.NodeType.BOOKMARK_LIST, "Bookmarks", "fa-bookmark", false);
    }

    getAllowRowHeader(): boolean {
        return false;
    }

    allowAction(action: NodeActionType, node: J.NodeInfo): boolean {
        return false;
    }

    render = (node: J.NodeInfo, tabData: TabIntf<any>, rowStyling: boolean, isTreeView: boolean, isLinkedNode: boolean): Comp => {
        return new Divc({ className: "systemNodeContent" }, [
            new Heading(4, this.displayName),
            new Div("Delete, edit, or order your bookmarks here.", { className: "marginLeft" })
        ]);
    }

    isSpecialAccountNode(): boolean {
        return true;
    }

    subOrdinal(): number {
        return 1;
    }
}
