import { getAs } from "../../AppContext";
import { AppState } from "../../AppState";
import { Div } from "../../comp/core/Div";
import { OpenGraphPanel } from "../../comp/OpenGraphPanel";
import { Constants as C } from "../../Constants";
import { TabIntf } from "../../intf/TabIntf";
import * as J from "../../JavaIntf";
import { AdminView } from "../AdminView";

export class AdminTab implements TabIntf<any> {
    name = "Admin";
    tooltip = "Manage the Server Instance";
    id = C.TAB_ADMIN;
    scrollPos = 0;
    props = {};
    openGraphComps: OpenGraphPanel[] = [];
    topmostVisibleElmId: string = null;

    static inst: AdminTab = null;
    static tabSelected: boolean = false;

    constructor() {
        AdminTab.inst = this;
    }

    isVisible = () => {
        return getAs().isAdminUser;
    };

    constructView = (data: TabIntf) => new AdminView(data);
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
