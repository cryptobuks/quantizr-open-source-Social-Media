import clientInfo from "./ClientInfo";
import { Constants as C } from "./Constants";
import { DialogBase } from "./DialogBase";
import { FullScreenConfig, FullScreenType } from "./Interfaces";
import { TabIntf } from "./intf/TabIntf";
import * as J from "./JavaIntf";
import { NodeHistoryItem } from "./NodeHistoryItem";

export interface ExportSettings {
    exportType: string;
    toIpfs?: boolean;
    includeToc?: boolean;
    includeJypyter?: boolean;
    attOneFolder?: boolean;
    includeJSON?: boolean;
    includeMD?: boolean;
    includeHTML?: boolean;
    includeIDs?: boolean;
    dividerLine?: boolean;
    updateHeadings?: boolean;
}

export class AppState {
    constructor() {
        console.log("Constructing AppState");
    }

    appInitComplete = false;
    tabPanelVisible: boolean = true;
    stateId: number = 0;
    displayFeedSearch: boolean = false;
    docIndent: boolean = true;

    // Initialize to empty object so we don't get NPE. This will hold the YAML configs, which is set on server
    // side in 'getConfigs' endpoint
    config: any = {};

    mobileMode: boolean = clientInfo.isMobileOrTablet;

    // show the ipfsTab to any users, even users without their MFS Files option turned on in their user profile
    // showIpfsTab: boolean;

    dialogStack: DialogBase[] = [];

    /* text at top of page, when user tries to upLevel too far etc */
    pageMessage: string = null;

    /* This allows us to detect when we're rendering the main page so we can set it to hidden visibilty until the scrolling
    has been done, or else we can get a bad flicker effect when page renders at wrong scroll position and then scrolls
    while the user is watching. With this flag it helps us just pop up instantly to the correct content AND scrolled to exactly
    where we want it.
    */
    rendering: boolean = false;

    otherAccountNodesExpanded: boolean = false;
    linksToAttachmentsExpanded: boolean = false;

    inlineEditId: string;
    inlineEditVal: string;

    /* name of currently logged in user */
    userName: string = J.PrincipalName.ANON;
    displayName: string = "";

    node: J.NodeInfo;
    highlightNodeId: string = null;
    endReached: boolean;

    /* Node Ids to move */
    nodesToMove: string[];

    activeTab: string = null;

    /* data for each tab, which we DO want to keep separate from the GUI view components themselves */
    tabData: TabIntf[] = [];

    // null means we haven't read the yet and the server will get them as needed.
    friendHashTags: string[] = null;

    // This determines what thing is being displayed fullscreen, and can only be one thing at a time.
    fullScreenConfig: FullScreenConfig = { type: FullScreenType.NONE };

    graphSearchText: string = null;
    highlightText: string = null;

    // todo-1: put typesafety back on this array
    calendarData: any[];
    calendarShowWeekends: boolean = false;

    fullScreenImageSize: string = C.FULL_SCREEN_MAX_WIDTH;

    /* holds array of all parent items all the way up the tree (as far as user is authorized) */
    breadcrumbs: J.BreadcrumbInfo[];

    speechPaused: boolean = false;
    speechSpeaking: boolean = false;
    speechVoice: number = -1; // index into speech.voices array (-1 means not set)
    speechVoice2: number = -1; // index into speech.voices array (-1 means not set)
    speechRate: string = "normal"; // slow, normal, fast
    ttsRan: boolean = false;
    showTtsInputText: boolean = false;

    anonShowLHSMenu: boolean = false;
    showSchemaOrgProps: boolean = false;
    showRecentProps: boolean = false;

    userPrefs: J.UserPreferences = {
        editMode: false,
        showMetaData: false,
        nsfw: false,
        showProps: false,
        autoRefreshFeed: false, // #add-prop
        showParents: false,
        showReplies: true,
        rssHeadlinesOnly: true,
        maxUploadFileSize: 0,
        enableIPSM: false,
        mainPanelCols: 6
    };

    exportSettings: ExportSettings = {
        exportType: "zip",
        includeToc: true,
        includeJypyter: true,
        attOneFolder: false,
        includeJSON: false,
        includeMD: true,
        includeHTML: true,
        includeIDs: true,
        dividerLine: true,
        updateHeadings: true
    };

    userProfile: J.UserProfile = null;

    myNewMessageCount: number = 0;
    nostrNewMessageCount: number = 0;

    // ids of selected nodes
    selectedNodes: Set<string> = new Set<string>();

    highlightSearchNodeId: string;

    isAdminUser: boolean;
    isAnonUser: boolean = true;
    allowBashScripting: boolean;
    allowedFeatures: string;

    allowFileSystemSearch: boolean;
    anonUserLandingPageNode: any;

    // Rss feeds (cached by a hash of the feedSrc property text as the key)
    rssFeedCache = {};

    // Similar to feedCache but holds the current 'page' the user is on (1, 2, 3,...) based on the user
    // being able to click 'more...' button to page thru a feed.
    rssFeedPage = {};

    // non-null if user is viewing an RSS feed and this will be the actual feed node
    rssNode: J.NodeInfo = null;

    bookmarks: J.Bookmark[];

    editNode: J.NodeInfo;
    protocolFilter: string = J.Constant.NETWORK_ACTPUB;

    editNodeOnTab: string; // holds the tab name of the tab responsible for any current embed edit of 'editNode'
    editNodeReplyToId: string;
    editShowJumpButton: boolean;
    editEncrypt: boolean;
    serverInfoText: string = null;
    showNodeLinksInGraph: boolean = true;
    attractionLinksInGraph: boolean = true;

    // holds node of the current conversation thread leaf node. The one the thread view was 'executed' on.
    threadViewFromNode: J.NodeInfo = null;

    // when we open the Thread View we record here what Tab we were on so that the "Go back", button can switch to it.
    threadViewFromTab: string = null;

    searchViewFromNode: J.NodeInfo = null; // rename to searchViewFromNode

    // when we open the Thread View we record here what Tab we were on so that the "Go back", button can switch to it.
    searchViewFromTab: string = null;

    repliesViewNodeId: string = null;
    repliesViewFromTab: string = null;

    serverInfoCommand: string = null;
    serverInfoTitle: string = null;

    ipsmActive: boolean = false;

    graphData: J.GraphNode;

    morePanelExpanded: boolean;
    propsPanelExpanded: boolean;

    // When this is on null it means user is in the process of linking two nodes, and we're
    // ready for them to select the target node to finish linking the linkSource to the target node
    linkSource: string = null;
    linkTarget: string = null;

    nodeHistory: NodeHistoryItem[] = [];
    nodeHistoryLocked: boolean;

    nostrQueryRunning: boolean = false;
}
