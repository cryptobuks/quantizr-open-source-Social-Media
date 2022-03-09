import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { TypeBase } from "./base/TypeBase";

export class ExportsTypeHandler extends TypeBase {
    constructor() {
        super(J.NodeType.EXPORTS, "Exports", "fa-briefcase", true);
    }

    getAllowRowHeader(): boolean {
        return false;
    }

    getIconClass(): string {
        return super.getIconClass();
    }

    getEditorHelp(): string {
        return S.quanta?.config?.help?.editor?.dialog;
    }

    isSpecialAccountNode(): boolean {
        return true;
    }
}
