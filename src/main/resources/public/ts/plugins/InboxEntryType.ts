import { NodeActionType } from "../intf/TypeIntf";
import * as J from "../JavaIntf";
import { TypeBase } from "./base/TypeBase";

export class InboxEntryType extends TypeBase {
    constructor() {
        super(J.NodeType.INBOX_ENTRY, "Notification", "fa-envelope", false);
    }

    override allowAction(action: NodeActionType, node: J.NodeInfo): boolean {
        switch (action) {
            case NodeActionType.delete:
                return true;
            default:
                return false;
        }
    }

    override getAllowPropertyAdd(): boolean {
        return false;
    }

    override getAllowContentEdit(): boolean {
        return false;
    }

    override allowPropertyEdit(propName: string): boolean {
        return false;
    }
}
