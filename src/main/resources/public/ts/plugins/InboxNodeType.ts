import { Comp } from "../comp/base/Comp";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Clearfix } from "../comp/core/Clearfix";
import { Heading } from "../comp/core/Heading";
import { HorizontalLayout } from "../comp/core/HorizontalLayout";
import { TabIntf } from "../intf/TabIntf";
import { NodeActionType } from "../intf/TypeIntf";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { TypeBase } from "./base/TypeBase";

export class InboxNodeType extends TypeBase {
    constructor() {
        super(J.NodeType.INBOX, "Inbox", "fa-inbox", false);
    }

    override allowAction(action: NodeActionType, node: J.NodeInfo): boolean {
        return false;
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

    override render = (node: J.NodeInfo, tabData: TabIntf<any>, rowStyling: boolean, isTreeView: boolean, isLinkedNode: boolean): Comp => {
        return new HorizontalLayout([
            new Heading(4, "Inbox"),
            new ButtonBar([
                new Button("Clear Inbox", () => S.edit.clearInbox())
            ], null, "float-end marginBottom"),
            new Clearfix()
        ], "displayTable systemNodeContent");
    }

    override isSpecialAccountNode(): boolean {
        return true;
    }

    override subOrdinal(): number {
        return 3;
    }
}
