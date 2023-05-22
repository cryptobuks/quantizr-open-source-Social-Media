import { Validator } from "../Validator";
import * as J from "../JavaIntf";

export class FeedViewProps {
    page = 0;
    refreshCounter = 0;
    searchTextState = new Validator();
    feedFilterFriends = false;
    feedFilterToMe = false;
    feedFilterMyMentions = false;
    feedFilterFromMe = false;
    feedFilterToUser: string = null;
    feedFilterToDisplayName: string = null;
    feedFilterToPublic = true;
    feedFilterLocalServer = false;
    applyAdminBlocks: true;
    name: string = J.Constant.FEED_PUB;

    friendsTagSearch: string = null;

    /* If we're presenting a specific node as the root of our "Feed" view this holds it's id, otherwise
     for any non-node specific feed query this stays null. */
    feedFilterRootNode: J.NodeInfo = null;
    feedDirty = false;
    feedLoading = false;
    feedResults: J.NodeInfo[] = null;
    feedEndReached = false;

    feedDirtyList: J.NodeInfo[] = null;
    filterExpanded = false;
}
