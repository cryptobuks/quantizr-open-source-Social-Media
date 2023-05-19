import { AppState } from "../../AppState";
import { AppNavLink } from "../../comp/core/AppNavLink";
import { Div } from "../../comp/core/Div";
import { Divc } from "../../comp/core/Divc";
import { OpenGraphPanel } from "../../comp/OpenGraphPanel";
import { Constants as C } from "../../Constants";
import { TabIntf } from "../../intf/TabIntf";
import * as J from "../../JavaIntf";
import { S } from "../../Singletons";
import { TrendingRSInfo } from "../../TrendingRSInfo";
import { TrendingView } from "../TrendingView";

export class TrendingTab implements TabIntf<TrendingRSInfo> {
    name = "Trending";
    tooltip = "What's popular right now on the Fediverse";
    id = C.TAB_TRENDING;
    props = new TrendingRSInfo();
    scrollPos = 0;
    openGraphComps: OpenGraphPanel[] = [];
    topmostVisibleElmId: string = null;

    static inst: TrendingTab = null;
    constructor() {
        TrendingTab.inst = this;
    }

    isVisible = () => true;
    constructView = (data: TabIntf) => new TrendingView(data);
    getTabSubOptions = (): Div => {
        return new Divc({ className: "tabSubOptions" }, [
            new AppNavLink("Hashtags", S.nav.showTrendingHashtags),
            new AppNavLink("Mentions", S.nav.showTrendingMentions),
            new AppNavLink("Words", S.nav.showTrendingWords)
        ]);
    };

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
