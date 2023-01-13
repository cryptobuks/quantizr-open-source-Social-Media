import { getAs } from "../AppContext";
import { Comp } from "../comp/base/Comp";
import { Div } from "../comp/core/Div";
import { Heading } from "../comp/core/Heading";
import { TabIntf } from "../intf/TabIntf";
import * as J from "../JavaIntf";
import { TypeBase } from "./base/TypeBase";

export class PostsType extends TypeBase {
    constructor() {
        super(J.NodeType.POSTS, "Posts", "fa-comments-o", false);
    }

    isSpecialAccountNode(): boolean {
        return true;
    }

    render = (node: J.NodeInfo, tabData: TabIntf<any>, rowStyling: boolean, isTreeView: boolean, isLinkedNode: boolean): Comp => {
        return new Div(null, { className: "systemNodeContent" }, [
            new Heading(4, "Posts", { className: "noMargin" })
        ]);
    }

    getEditorHelp(): string {
        const ast = getAs();
        return ast.config.help?.editor?.dialog;
    }
}
