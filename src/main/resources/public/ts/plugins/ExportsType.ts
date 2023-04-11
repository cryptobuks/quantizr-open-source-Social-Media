import { getAs } from "../AppContext";
import { Comp } from "../comp/base/Comp";
import { Divc } from "../comp/core/Divc";
import { Heading } from "../comp/core/Heading";
import { TabIntf } from "../intf/TabIntf";
import * as J from "../JavaIntf";
import { TypeBase } from "./base/TypeBase";

export class ExportsType extends TypeBase {
    constructor() {
        super(J.NodeType.EXPORTS, "Exports", "fa-briefcase", false);
    }

    override getAllowRowHeader(): boolean {
        return false;
    }

    override getEditorHelp(): string {
        const ast = getAs();
        return ast.config.help?.editor?.dialog;
    }

    override isSpecialAccountNode(): boolean {
        return true;
    }

    override render = (node: J.NodeInfo, tabData: TabIntf<any>, rowStyling: boolean, isTreeView: boolean, isLinkedNode: boolean): Comp => {
        return new Divc({ className: "systemNodeContent" }, [
            new Heading(4, "Exports", { className: "noMargin" })
        ]);
    }
}
