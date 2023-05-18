console.log("entering Constants.ts");
export class Constants {

    // NOTE: Browsers can and DO fail sometimes if we don't have 'data-' as the prefix on any 'custom data attributes'
    // that aren't an official part of HTML Spec.
    static NODE_ID_ATTR = "data-nid";
    static WORD_ATTR = "data-word";
    static ARROW_OPTION_ATTR = "data-arrow-option";

    static DND_TYPE_NODEID = "nodeid"; // keep lowercase
    static TTS_BREAK = "([[TTS-BREAK]])";

    // max rows to allow to scroll in via infinite scroll capability before we reset back to 25 rows.
    static MAX_DYNAMIC_ROWS = 500;

    static TREE_INFINITE_SCROLL = true;
    static FEED_INFINITE_SCROLL = true;

    static ID_LHS = "leftNavPanelId";
    static ID_TAB = "tabPanelId";
    static ID_RHS = "rightNavPanelId";
    static ID_MENU = "appMainMenuPanelId";

    static ADMIN_COMMAND_FEDIVERSE = "{{FederatedFeedButton}}";
    static ADMIN_COMMAND_TRENDING = "{{TrendingHashtagsButton}}";

    // WARNING: These must be able to be prefixes on class names.
    static TAB_MAIN: string = "mainRS";
    static TAB_SEARCH: string = "srchRS";
    static TAB_SHARES: string = "sharRS";
    static TAB_TIMELINE: string = "timeRS";
    static TAB_DOCUMENT: string = "docRS";
    static TAB_FOLLOWERS: string = "flwrsRS";
    static TAB_FOLLOWING: string = "flwngRS";

    // note: This tab name does exist in java, so we probably should put it in a constants that comes from JAVA.
    static TAB_FEED: string = "feedTab";

    // static TAB_IPSM: string = "ipsmTab";
    static TAB_TRENDING: string = "trendingTab";
    static TAB_THREAD: string = "threadTab";
    static TAB_REPLIES: string = "repliesTab";
    static TAB_SERVERINFO: string = "serverInfoTab";
    static TAB_IPFSVIEW: string = "ipfsFilesTab";
    static TAB_LOG: string = "logTab";
    static TAB_TTS: string = "ttsTab";
    static TAB_RSS: string = "rssTab";
    static TAB_SETTINGS: string = "settingsTab";
    static TAB_ADMIN: string = "adminTab";

    static BOOKMARKS_MENU_TEXT = "Bookmarks";

    static readonly ONE_MB = 1048576; // 1024 * 1024;
    static readonly MAX_UPLOAD_MB = 20;
    static readonly DEBUG_SCROLLING = false;
    static readonly ALLOW_ADMIN_NODE_HEADERS = true;

    // This works well at 100% but requires user to scroll down when the content was taller than
    // what can fit on the screen, and for making the Quanta Screencasts a good experience, for now
    // I'm making this 75% instead so they fit better, but this isn't the final solution here.
    static readonly FULL_SCREEN_MAX_WIDTH = "75%";

    static readonly NEW_ON_ROOT: boolean = false;
    static readonly NEW_ON_TOOLBAR: boolean = true;
    static readonly MOVE_UPDOWN_ON_TOOLBAR: boolean = true;
    static readonly SHOW_CLEAR_BUTTON_IN_EDITOR: boolean = false;

    static readonly PUBSUB_mainWindowScroll = "PUBSUB_mainWindowScroll";
    static readonly PUBSUB_postMainWindowScroll = "PUBSUB_postMainWindowScroll";
    static readonly PUBSUB_tabChanging = "PUBSUB_tabChanging";
    static readonly PUBSUB_closeNavPanel = "PUBSUB_closeNavPanel";
    static readonly PUBSUB_menuExpandChanged = "PUBSUB_menuExpandChanged";
    static readonly PUBSUB_dispatcherReady = "PUBSUB_dispatcherReady";

    static readonly LOCALDB_LOGIN_USR: string = "loginUsr";
    static readonly LOCALDB_LOGIN_PWD: string = "loginPwd";

    /*
     * loginState="0" if user logged out intentionally. loginState="1" if last known state of user was 'logged in'
     */
    static readonly LOCALDB_LOGIN_STATE: string = "loginState";
    static readonly LOCALDB_MOBILE_MODE: string = "mobileMode";

    /* These two persist what the user is viewing so when the come back later we can go to same node
     location they were last viewing */
    static readonly LOCALDB_LAST_PARENT_NODEID: string = "lastParentId";
    static readonly LOCALDB_LAST_CHILD_NODEID: string = "lastChildId";
    static readonly LOCALDB_MOUSE_EFFECT: string = "mouseEffect";

    static readonly LOCALDB_AUDIO_SOURCE: string = "audioSource";
    static readonly LOCALDB_VIDEO_SOURCE: string = "videoSource";
    static readonly LOCALDB_NOSTR_PRIVATE_KEY: string = "nostrKey";

    static readonly STORE_EDITOR_DATA: string = "editObj"; // holds {nodeId: "xxxxx", content: "stuff that never got saved"}

    static readonly ATT_MAX_WIDTH: string = "att-max-width";

    static readonly ID_PREFIX_EDIT: string = "edit_";

    static readonly LOCALDB_VOICE_INDEX: string = "voiceIndex";
    static readonly LOCALDB_VOICE2_INDEX: string = "voice2Index";
    static readonly LOCALDB_VOICE_RATE: string = "voiceRate";

    static readonly LOCALDB_NETWORK_SELECTION: string = "networkProtocol";
}
