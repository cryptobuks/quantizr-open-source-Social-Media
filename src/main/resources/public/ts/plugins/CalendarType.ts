import { Comp } from "../comp/base/Comp";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Diva } from "../comp/core/Diva";
import { TabIntf } from "../intf/TabIntf";
import { NodeActionType } from "../intf/TypeIntf";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { TypeBase } from "./base/TypeBase";

export class CalendarType extends TypeBase {
    constructor() {
        super(J.NodeType.CALENDAR, "Calendar", "fa-calendar", true);
    }

    override allowAction(action: NodeActionType, node: J.NodeInfo): boolean {
        return true;
    }

    super_render = this.render;
    override render = (node: J.NodeInfo, tabData: TabIntf<any>, rowStyling: boolean, isTreeView: boolean, isLinkedNode: boolean): Comp => {
        const baseComp = this.super_render(node, tabData, rowStyling, isTreeView, isLinkedNode);
        return new Diva([
            baseComp,
            new ButtonBar([
                new Button("Past", () => {
                    S.srch.timeline(node, J.NodeProp.DATE_FULL, "pastOnly", "Past calendar dates (Newest at the top)", 0, true);
                }, null),
                new Button("Future", () => {
                    S.srch.timeline(node, J.NodeProp.DATE_FULL, "futureOnly", "Future calendar dates (Soonest at the top)", 0, true);
                }, null),
                new Button("Calendar", () => {
                    S.render.showCalendar(node.id);
                }, null),
                new Button("Create", () => S.edit.createNode(node, J.NodeType.NONE, true, false, "addDateProp", null), {
                    title: "Add new Calendar Item"
                })
            ], "marginLeft marginBottom")
        ]);
    }
}
