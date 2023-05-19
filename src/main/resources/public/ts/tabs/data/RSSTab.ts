import { getAs } from "../../AppContext";
import { AppState } from "../../AppState";
import { Div } from "../../comp/core/Div";
import { OpenGraphPanel } from "../../comp/OpenGraphPanel";
import { Constants as C } from "../../Constants";
import { TabIntf } from "../../intf/TabIntf";
import * as J from "../../JavaIntf";
import { RSSView } from "../RSSView";

export class RSSTab implements TabIntf<any> {
    name = "RSS Feed";
    tooltip = "Get some news or podcasts";
    id = C.TAB_RSS;
    scrollPos = 0;
    props = {};
    openGraphComps: OpenGraphPanel[] = [];
    topmostVisibleElmId: string = null;
    static inst: RSSTab = null;

    constructor() {
        RSSTab.inst = this;
    }

    isVisible = () => {
        return !!getAs().rssNode;
    }

    constructView = (data: TabIntf) => new RSSView(data);
    getTabSubOptions = (): Div => { return null; };

    findNode = (nodeId: string): J.NodeInfo => {
        return null;
    }

    nodeDeleted = (ust: AppState, nodeId: string): void => {
    }

    replaceNode = (ust: AppState, newNode: J.NodeInfo): void => {
    }

    processNode = (ust: AppState, func: (node: J.NodeInfo) => void): void => {
    }
}
