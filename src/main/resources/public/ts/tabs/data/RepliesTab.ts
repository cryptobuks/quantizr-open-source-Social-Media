import { getAs } from "../../AppContext";
import { AppState } from "../../AppState";
import { Div } from "../../comp/core/Div";
import { OpenGraphPanel } from "../../comp/OpenGraphPanel";
import { Constants as C } from "../../Constants";
import { TabIntf } from "../../intf/TabIntf";
import * as J from "../../JavaIntf";
import { S } from "../../Singletons";
import { RepliesRSInfo } from "../../RepliesRSInfo";
import { RepliesView } from "../RepliesView";

export class RepliesTab implements TabIntf<RepliesRSInfo> {
    name = "Replies";
    tooltip = "All known replies to the post"
    id = C.TAB_REPLIES;
    props = new RepliesRSInfo();
    scrollPos = 0;
    openGraphComps: OpenGraphPanel[] = [];
    topmostVisibleElmId: string = null;

    static inst: RepliesTab = null;
    constructor() {
        RepliesTab.inst = this;
    }

    isVisible = () => { return !!getAs().repliesViewNodeId; };

    constructView = (data: TabIntf) => new RepliesView(data);
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
