import { AppState } from "../../AppState";
import { AppNavLink } from "../../comp/core/AppNavLink";
import { Div } from "../../comp/core/Div";
import { OpenGraphPanel } from "../../comp/OpenGraphPanel";
import { Constants as C } from "../../Constants";
import { TabIntf } from "../../intf/TabIntf";
import { S } from "../../Singletons";
import { ValidatedState } from "../../ValidatedState";
import { FeedView } from "../FeedView";
import { FeedViewProps } from "../FeedViewProps";
import * as J from "../../JavaIntf";

export class FeedViewData implements TabIntf<FeedViewProps> {
    name = "Feed";
    tooltip = "Reverse-chronological list of Fediverse posts";
    id = C.TAB_FEED;
    scrollPos = 0;

    // todo-0: need to makeout interface a 'class' and just construct it here.
    props: FeedViewProps = {
        page: 0,
        refreshCounter: 0,
        autoRefresh: true,
        searchTextState: new ValidatedState<any>(),
        feedFilterFriends: false,
        feedFilterToMe: false,
        feedFilterFromMe: false,
        feedFilterToUser: null, 
        feedFilterToPublic: true,
        feedFilterLocalServer: false,
        applyAdminBlocks: true,

        /* If we're presenting a specific node as the root of our "Feed" view this holds it's id, otherwise
         for any non-node specific feed query this stays null. */
        feedFilterRootNode: null, 
        feedDirty: false,
        feedLoading: false,
        feedResults: null,
        feedEndReached: false
    };

    openGraphComps: OpenGraphPanel[] = [];

    static inst: FeedViewData = null;
    constructor() {
        FeedViewData.inst = this;
    }

    isVisible = (state: AppState) => true;
    constructView = (data: TabIntf<FeedViewProps>) => new FeedView(data);

    findNode = (nodeId: string): J.NodeInfo => {
        return this.props.feedResults.find(n => n.id === nodeId);
    }

    nodeDeleted = (nodeId: string): void => {
        this.props.feedResults = this.props.feedResults.filter(n => nodeId !== n.id);
    }

    getTabSubOptions = (state: AppState): Div => {
        if (this.props?.feedFilterRootNode) {
            return !state.isAnonUser
                ? new Div(null, { className: "tabSubOptions" }, [
                    // we close chat by swithing user back to the Fediverse view.
                    new AppNavLink("Close Chat", S.nav.messagesFediverse)
                ]) : null;
        }
        else {
            return new Div(null, { className: "tabSubOptions" }, [
                state.isAnonUser ? null : new AppNavLink("To/From Me", S.nav.messagesToFromMe),
                state.isAnonUser ? null : new AppNavLink("To Me", S.nav.messagesToMe),
                state.isAnonUser ? null : new AppNavLink("From Me", S.nav.messagesFromMe),
                state.isAnonUser ? null : new AppNavLink("From Friends", S.nav.messagesFromFriends),
                // We need to make this a configurable option.
                // new MenuItem("From Local Users", S.nav.messagesLocal),
                new AppNavLink("Federated", S.nav.messagesFediverse)
            ]);
        }
    };
}
