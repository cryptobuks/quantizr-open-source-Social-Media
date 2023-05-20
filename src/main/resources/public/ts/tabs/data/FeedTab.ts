import { getAs } from "../../AppContext";
import { AppState } from "../../AppState";
import { AppNavLink } from "../../comp/core/AppNavLink";
import { Div } from "../../comp/core/Div";
import { Divc } from "../../comp/core/Divc";
import { OpenGraphPanel } from "../../comp/OpenGraphPanel";
import { Constants as C } from "../../Constants";
import { TabIntf } from "../../intf/TabIntf";
import * as J from "../../JavaIntf";
import { S } from "../../Singletons";
import { FeedView } from "../FeedView";
import { FeedViewProps } from "../FeedViewProps";

export class FeedTab implements TabIntf<FeedViewProps> {
    name = "Feed";
    tooltip = "Reverse-chronological list of Fediverse posts";
    id = C.TAB_FEED;
    scrollPos = 0;
    props = new FeedViewProps();
    openGraphComps: OpenGraphPanel[] = [];
    topmostVisibleElmId: string = null;

    static inst: FeedTab = null;
    constructor() {
        FeedTab.inst = this;
    }

    isVisible = () => true;
    constructView = (data: TabIntf<FeedViewProps>) => new FeedView(data);

    findNode = (nodeId: string): J.NodeInfo => {
        return S.util.searchNodeArray(this.props.feedResults, nodeId);
    }

    nodeDeleted = (ust: AppState, nodeId: string): void => {
        this.props.feedResults = this.props.feedResults?.filter(n => nodeId !== n.id);
    }

    replaceNode = (ust: AppState, newNode: J.NodeInfo): void => {
        this.props.feedResults = this.props.feedResults?.map(n => {
            return n?.id === newNode?.id ? newNode : n;
        });
    }

    processNode = (ust: AppState, func: (node: J.NodeInfo) => void): void => {
        this.props.feedResults?.forEach(n => func(n));
    }

    getTabSubOptions = (): Div => {
        const ast = getAs();
        if (this.props?.feedFilterRootNode) {
            return !ast.isAnonUser
                ? new Divc({ className: "tabSubOptions" }, [
                    // we close chat by swithing user back to the Fediverse view.
                    new AppNavLink("Close Chat", S.nav.messagesFediverse)
                ]) : null;
        }
        else {
            return new Divc({ className: "tabSubOptions" }, [
                // todo-1: the "My Mentions" for Nostr is showing nothing, so let's disable until it's working/tested
                ast.isAnonUser || ast.protocolFilter === J.Constant.NETWORK_NOSTR ? null : new AppNavLink("My Mentions", S.nav.messagesMyMentions),
                ast.isAnonUser ? null : new AppNavLink("To/From Me", S.nav.messagesToFromMe),
                ast.isAnonUser ? null : new AppNavLink("To Me", S.nav.messagesToMe),
                ast.isAnonUser ? null : new AppNavLink("From Me", S.nav.messagesFromMe),
                ast.isAnonUser ? null : new AppNavLink("From Friends", S.nav.messagesFromFriends),
                // todo-1: eventually we will make available to all users
                ast.isAdminUser ? new AppNavLink("Local Users", S.nav.messagesLocal) : null,
                new AppNavLink("Public Feed", S.nav.messagesFediverse),
                ...S.render.buildCustomLinks(ast.config.rhsFeedLinks)
            ]);
        }
    };
}
