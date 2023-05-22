import { getAs } from "../../AppContext";
import { AppState } from "../../AppState";
import { Div } from "../../comp/core/Div";
import { OpenGraphPanel } from "../../comp/OpenGraphPanel";
import { Constants as C } from "../../Constants";
import { TabIntf } from "../../intf/TabIntf";
import * as J from "../../JavaIntf";
import { S } from "../../Singletons";
import { ThreadRSInfo } from "../../ThreadRSInfo";
import { ThreadView } from "../ThreadView";

export class ThreadTab implements TabIntf<ThreadRSInfo> {
    name = "Thread";
    tooltip = "View of Posts in top-down chronological order showing the full reply chain"
    id = C.TAB_THREAD;
    props = new ThreadRSInfo();
    scrollPos = 0;
    openGraphComps: OpenGraphPanel[] = [];
    topmostVisibleElmId: string = null;

    static inst: ThreadTab = null;
    constructor() {
        ThreadTab.inst = this;
    }

    isVisible = () => { return !!getAs().threadViewFromNode; };

    constructView = (data: TabIntf) => new ThreadView(data);
    getTabSubOptions = (): Div => { return null; };

    findNode = (nodeId: string): J.NodeInfo => {
        return S.util.searchNodeArray(this.props.results, nodeId);
    }

    nodeDeleted = (ust: AppState, nodeId: string): void => {
        this.props.results = this.props.results?.filter(n => nodeId !== n.id);
    }

    replaceNode = (ust: AppState, newNode: J.NodeInfo): void => {
        this.props.results = this.props.results?.map(n => {
            return n?.id === newNode?.id ? newNode : n;
        });
    }

    processNode = (ust: AppState, func: (node: J.NodeInfo) => void): void => {
        this.props.results?.forEach(n => func(n));
    }
}
