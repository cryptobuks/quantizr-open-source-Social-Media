import { AppState } from "../../AppState";
import { Div } from "../../comp/core/Div";
import { OpenGraphPanel } from "../../comp/OpenGraphPanel";
import { Constants as C } from "../../Constants";
import { FollowersRSInfo } from "../../FollowersRSInfo";
import { TabIntf } from "../../intf/TabIntf";
import * as J from "../../JavaIntf";
import { S } from "../../Singletons";
import { FollowersResultSetView } from "../FollowersResultSetView";

export class FollowersTab implements TabIntf<FollowersRSInfo> {
    name = "Followers";
    tooltip = "Followers of your account";
    id = C.TAB_FOLLOWERS;
    props = new FollowersRSInfo();
    scrollPos = 0;
    openGraphComps: OpenGraphPanel[] = [];
    topmostVisibleElmId: string = null;

    static inst: FollowersTab = null;
    constructor() {
        FollowersTab.inst = this;
    }

    isVisible = () => S.tabUtil.resultSetHasData(C.TAB_FOLLOWERS);
    constructView = (data: TabIntf) => new FollowersResultSetView<FollowersRSInfo>(data);
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
