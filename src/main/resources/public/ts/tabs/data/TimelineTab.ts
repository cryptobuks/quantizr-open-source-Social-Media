import { AppState } from "../../AppState";
import { Div } from "../../comp/core/Div";
import { OpenGraphPanel } from "../../comp/OpenGraphPanel";
import { Constants as C } from "../../Constants";
import { TabIntf } from "../../intf/TabIntf";
import * as J from "../../JavaIntf";
import { S } from "../../Singletons";
import { TimelineRSInfo } from "../../TimelineRSInfo";
import { TimelineResultSetView } from "../TimelineResultSetView";

export class TimelineTab implements TabIntf<TimelineRSInfo> {
    name = "Timeline";
    tooltip = "Reverse-chronological list of the entire subgraph of all nodes under one node"
    id = C.TAB_TIMELINE;
    props = new TimelineRSInfo();
    scrollPos = 0;
    openGraphComps: OpenGraphPanel[] = [];
    topmostVisibleElmId: string = null;

    static inst: TimelineTab = null;
    constructor() {
        TimelineTab.inst = this;
    }

    isVisible = () => S.tabUtil.resultSetHasData(C.TAB_TIMELINE);
    constructView = (data: TabIntf) => new TimelineResultSetView<TimelineRSInfo>(data);
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
