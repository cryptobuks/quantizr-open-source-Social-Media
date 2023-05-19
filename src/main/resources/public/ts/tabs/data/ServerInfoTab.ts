import { getAs } from "../../AppContext";
import { AppState } from "../../AppState";
import { Div } from "../../comp/core/Div";
import { OpenGraphPanel } from "../../comp/OpenGraphPanel";
import { Constants as C } from "../../Constants";
import { TabIntf } from "../../intf/TabIntf";
import * as J from "../../JavaIntf";
import { ServerInfoView } from "../ServerInfoView";

export class ServerInfoTab implements TabIntf<any> {
    name = "Info View";
    tooltip = "Displaying the information view for the most recently requested info";
    id = C.TAB_SERVERINFO;
    scrollPos = 0;
    props = {};
    openGraphComps: OpenGraphPanel[] = [];
    topmostVisibleElmId: string = null;

    static inst: ServerInfoTab = null;
    constructor() {
        ServerInfoTab.inst = this;
    }

    isVisible = () => {
        return !!getAs().serverInfoText;
    };

    constructView = (data: TabIntf) => new ServerInfoView(data);
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
