import { Comp } from "../comp/base/Comp";
import { Heading } from "../comp/core/Heading";
import { HorizontalLayout } from "../comp/core/HorizontalLayout";
import { TabIntf } from "../intf/TabIntf";
import * as J from "../JavaIntf";
import { TypeBase } from "./base/TypeBase";

export class RepoRootType extends TypeBase {
    constructor() {
        super(J.NodeType.REPO_ROOT, "Root", "fa-home", false);
    }

    override allowPropertyEdit(propName: string): boolean {
        return true;
    }

    override render = (node: J.NodeInfo, tabData: TabIntf<any>, rowStyling: boolean, isTreeView: boolean, isLinkedNode: boolean): Comp => {
        return new HorizontalLayout([
            new Heading(4, "Root", { className: "noMargin" })
        ], "displayTable systemNodeContent");
    }
}
