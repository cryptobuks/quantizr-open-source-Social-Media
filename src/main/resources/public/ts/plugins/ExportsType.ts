import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { Comp } from "../comp/base/Comp";
import { Divc } from "../comp/core/Divc";
import { Heading } from "../comp/core/Heading";
import { TabIntf } from "../intf/TabIntf";
import { TypeBase } from "./base/TypeBase";

export class ExportsType extends TypeBase {
    constructor() {
        super(J.NodeType.EXPORTS, "Exports", "fa-briefcase", false);
    }

    override getAllowRowHeader(): boolean {
        return false;
    }

    override getEditorHelp(): string {
        return S.quanta.cfg.help?.editor?.dialog;
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
