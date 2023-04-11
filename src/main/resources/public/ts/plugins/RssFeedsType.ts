import { Comp } from "../comp/base/Comp";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Divc } from "../comp/core/Divc";
import { Heading } from "../comp/core/Heading";
import { TabIntf } from "../intf/TabIntf";
import { NodeActionType } from "../intf/TypeIntf";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { TypeBase } from "./base/TypeBase";

export class RssFeedsType extends TypeBase {
    static helpExpanded: boolean;

    constructor() {
        super(J.NodeType.RSS_FEEDS, "RSS Feeds", "fa-rss", false);
    }

    override isSpecialAccountNode(): boolean {
        return true;
    }

    override allowAction(action: NodeActionType, node: J.NodeInfo): boolean {
        switch (action) {
            case NodeActionType.delete:
                return true;
            default:
                return false;
        }
    }

    override render = (node: J.NodeInfo, tabData: TabIntf<any>, rowStyling: boolean, isTreeView: boolean, isLinkedNode: boolean): Comp => {
        return new Divc({ className: "systemNodeContent" }, [
            new ButtonBar([
                new Button("Add RSS Feed", () => S.edit.createNode(node, J.NodeType.RSS_FEED, true, false, null, null), {
                    title: "Add a new RSS Feed Subscription"
                })
            ], null, "float-end"),
            new Heading(4, "RSS Feed Subscriptions")
        ]);
    }
}
