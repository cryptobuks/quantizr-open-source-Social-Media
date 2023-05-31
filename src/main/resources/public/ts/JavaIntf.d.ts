/* tslint:disable */
/* eslint-disable */

export interface APObjIcon {
    type: string;
    mediaType: string;
    url: string;
}

export interface APObjUrl {
    type: string;
    mediaType: string;
    href: string;
}

export interface APTag {
    name: string;
    href: string;
    type: string;
}

export interface Attachment {
    o: number;
    w: number;
    h: number;
    p: string;
    m: string;
    f: string;
    c: string;
    s: number;
    b: string;
    d: string;
    u: string;
    il: string;
    ir: string;
}

export interface Bookmark {
    name: string;
    id: string;
    selfId: string;
}

export interface IPSMData {
    mime: string;
    data: string;
}

export interface IPSMMessage {
    from: string;
    sig: string;
    content: IPSMData[];
    ts: number;
}

export interface MFSDirEntry {
    file: boolean;
    dir: boolean;
    Name: string;
    Type: number;
    Size: number;
    Hash: string;
}

export interface NodeLink {
    o: number;
    i: string;
    n: string;
}

export interface NostrEvent {
    id: string;
    sig: string;
    pubkey: string;
    kind: number;
    content: string;
    tags: string[][];
    createdAt: number;
}

export interface NostrEventWrapper {
    event: NostrEvent;
    nodeId: string;
    npub: string;
    relays: string;
}

export interface NostrMetadata {
    name: string;
    username: string;
    about: string;
    picture: string;
    banner: string;
    website: string;
    nip05: string;
    reactions: boolean;
    display_name: string;
}

export interface NostrQuery {
    authors: string[];
    kinds: number[];
    limit: number;
    since: number;
}

export interface NostrUserInfo {
    pk: string;
    npub: string;
    relays: string;
}

export interface OpenGraph {
    mime: string;
    url: string;
    title: string;
    description: string;
    image: string;
}

export interface RssFeed {
    encoding: string;
    title: string;
    description: string;
    author: string;
    link: string;
    image: string;
    entries: RssFeedEntry[];
}

export interface RssFeedEnclosure {
    type: string;
    url: string;
}

export interface RssFeedEntry {
    parentFeedTitle: string;
    author: string;
    title: string;
    subTitle: string;
    publishDate: string;
    image: string;
    thumbnail: string;
    description: string;
    link: string;
    enclosures: RssFeedEnclosure[];
    mediaContent: RssFeedMediaContent[];
}

export interface RssFeedMediaContent {
    type: string;
    url: string;
    medium: string;
}

export interface SchemaOrgClass {
    id: string;
    comment: string;
    label: string;
    props: SchemaOrgProp[];
}

export interface SchemaOrgProp {
    comment: string;
    label: string;
    ranges: SchemaOrgRange[];
}

export interface SchemaOrgRange {
    id: string;
}

export interface UserProfile {
    displayName: string;
    userName: string;
    homeNodeId: string;
    didIPNS: string;
    mfsEnable: boolean;
    userBio: string;
    userTags: string;
    blockedWords: string;
    recentTypes: string;
    avatarVer: string;
    headerImageVer: string;
    userNodeId: string;
    apIconUrl: string;
    apImageUrl: string;
    actorUrl: string;
    actorId: string;
    followerCount: number;
    followingCount: number;
    following: boolean;
    blocked: boolean;
    relays: string;
    nostrNpub: string;
    nostrTimestamp: number;
}

export interface AddFriendRequest extends RequestBase {
    userName: string;
}

export interface AddPrivilegeRequest extends RequestBase {
    nodeId: string;
    privileges: string[];
    principals: string[];
}

export interface AppDropRequest extends RequestBase {
    data: string;
}

export interface BlockUserRequest extends RequestBase {
    userName: string;
}

export interface BrowseFolderRequest extends RequestBase {
    nodeId: string;
}

export interface ChangePasswordRequest extends RequestBase {
    newPassword: string;
    passCode: string;
}

export interface CheckMessagesRequest extends RequestBase {
}

export interface CloseAccountRequest extends RequestBase {
}

export interface CopySharingRequest extends RequestBase {
    nodeId: string;
}

export interface CreateSubNodeRequest extends RequestBase {
    nodeId: string;
    boostTarget: string;
    pendingEdit: boolean;
    content: string;
    newNodeName: string;
    typeName: string;
    createAtTop: boolean;
    typeLock: boolean;
    properties: PropertyInfo[];
    shareToUserId: string;
    boosterUserId: string;
    fediSend: boolean;
    payloadType?: string;
    reply: boolean;
}

export interface DeleteAttachmentRequest extends RequestBase {
    nodeId: string;
    attName: string;
}

export interface DeleteFriendRequest extends RequestBase {
    userNodeId: string;
}

export interface DeleteMFSFileRequest extends RequestBase {
    item: string;
}

export interface DeleteNodesRequest extends RequestBase {
    nodeIds: string[];
    childrenOnly: boolean;
    bulkDelete: boolean;
}

export interface DeletePropertyRequest extends RequestBase {
    nodeId: string;
    propNames: string[];
}

export interface ExportRequest extends RequestBase {
    nodeId: string;
    exportExt: string;
    fileName: string;
    toIpfs: boolean;
    includeToc: boolean;
    attOneFolder: boolean;
    includeJSON: boolean;
    includeMD: boolean;
    includeHTML: boolean;
    includeJypyter: boolean;
    includeIDs: boolean;
    dividerLine: boolean;
    updateHeadings: boolean;
}

export interface FileSearchRequest extends RequestBase {
    searchText: string;
    reindex: boolean;
    nodeId: string;
}

export interface FileSystemReindexRequest extends RequestBase {
    nodeId: string;
}

export interface GetActPubObjectRequest extends RequestBase {
    url: string;
}

export interface GetBookmarksRequest extends RequestBase {
}

export interface GetConfigRequest extends RequestBase {
    appGuid: string;
}

export interface GetFollowersRequest extends RequestBase {
    page: number;
    targetUserName: string;
}

export interface GetFollowingRequest extends RequestBase {
    page: number;
    targetUserName: string;
}

export interface GetIPFSContentRequest extends RequestBase {
    id: string;
}

export interface GetIPFSFilesRequest extends RequestBase {
    folder: string;
}

export interface GetMultiRssRequest extends RequestBase {
    urls: string;
    page: number;
}

export interface GetNodePrivilegesRequest extends RequestBase {
    nodeId: string;
}

export interface GetNodeStatsRequest extends RequestBase {
    nodeId: string;
    trending: boolean;
    signatureVerify: boolean;
    feed: boolean;
    getWords: boolean;
    getMentions: boolean;
    getTags: boolean;
}

export interface GetOpenGraphRequest extends RequestBase {
    url: string;
}

export interface GetPeopleRequest extends RequestBase {
    nodeId: string;
    type: string;
    subType: string;
}

export interface GetRepliesViewRequest extends RequestBase {
    nodeId: string;
}

export interface GetSchemaOrgTypesRequest extends RequestBase {
}

export interface GetServerInfoRequest extends RequestBase {
    command: string;
    parameter: string;
    nodeId: string;
}

export interface GetSharedNodesRequest extends RequestBase {
    page: number;
    nodeId: string;
    shareTarget: string;
    accessOption: string;
}

export interface GetThreadViewRequest extends RequestBase {
    nodeId: string;
    loadOthers: boolean;
}

export interface GetUserAccountInfoRequest extends RequestBase {
}

export interface GetUserProfileRequest extends RequestBase {
    userId: string;
    nostrPubKey: string;
}

export interface GraphRequest extends RequestBase {
    nodeId: string;
    searchText: string;
}

export interface ImportRequest extends RequestBase {
    nodeId: string;
    sourceFileName: string;
}

export interface InitNodeEditRequest extends RequestBase {
    nodeId: string;
    editMyFriendNode: boolean;
}

export interface InsertBookRequest extends RequestBase {
    nodeId: string;
    bookName: string;
    truncated: boolean;
}

export interface InsertNodeRequest extends RequestBase {
    pendingEdit: boolean;
    parentId: string;
    targetOrdinal: number;
    newNodeName: string;
    typeName: string;
    initialValue: string;
}

export interface JoinNodesRequest extends RequestBase {
    nodeIds: string[];
}

export interface LikeNodeRequest extends RequestBase {
    id: string;
    like: boolean;
}

export interface LinkNodesRequest extends RequestBase {
    sourceNodeId: string;
    targetNodeId: string;
    name: string;
    type: string;
}

export interface LoadNodeFromIpfsRequest extends RequestBase {
    path: string;
}

export interface LoginRequest extends RequestBase {
    userName: string;
    password: string;
    asymEncKey: string;
    sigKey: string;
    nostrNpub: string;
    nostrPubKey: string;
    tzOffset?: number;
    dst?: boolean;
}

export interface LogoutRequest extends RequestBase {
}

export interface LuceneIndexRequest extends RequestBase {
    nodeId: string;
    path: string;
}

export interface LuceneSearchRequest extends RequestBase {
    nodeId: string;
    text: string;
}

export interface MoveNodesRequest extends RequestBase {
    targetNodeId: string;
    nodeIds: string[];
    location: string;
}

export interface NodeFeedRequest extends RequestBase {
    page: number;
    nodeId: string;
    toUser: string;
    toMe: boolean;
    myMentions: boolean;
    fromMe: boolean;
    fromFriends: boolean;
    toPublic: boolean;
    localOnly: boolean;
    nsfw: boolean;
    searchText: string;
    friendsTagSearch: string;
    loadFriendsTags: boolean;
    applyAdminBlocks: boolean;
    name: string;
    protocol: string;
}

export interface NodeSearchRequest extends RequestBase {
    searchRoot: string;
    page: number;
    sortDir: string;
    sortField: string;
    nodeId: string;
    searchText: string;
    searchProp: string;
    fuzzy: boolean;
    caseSensitive: boolean;
    searchDefinition: string;
    searchType: string;
    timeRangeType: string;
    recursive: boolean;
    requirePriority: boolean;
    requireAttachment: boolean;
    deleteMatches: boolean;
}

export interface OpenSystemFileRequest extends RequestBase {
    fileName: string;
}

export interface PingRequest extends RequestBase {
}

export interface PublishNodeToIpfsRequest extends RequestBase {
    nodeId: string;
}

export interface RemovePrivilegeRequest extends RequestBase {
    nodeId: string;
    principalNodeId: string;
    privilege: string;
}

export interface RenderCalendarRequest extends RequestBase {
    nodeId: string;
}

export interface RenderDocumentRequest extends RequestBase {
    rootId: string;
    startNodeId: string;
    includeComments: boolean;
}

export interface RenderNodeRequest extends RequestBase {
    nodeId: string;
    offset: number;
    siblingOffset: number;
    upLevel: boolean;
    renderParentIfLeaf: boolean;
    forceRenderParent: boolean;
    parentCount: number;
    jumpToRss: boolean;
    goToLastPage: boolean;
    singleNode: boolean;
    forceIPFSRefresh: boolean;
}

export interface ResetPasswordRequest extends RequestBase {
    user: string;
    email: string;
}

export interface SaveNodeRequest extends RequestBase {
    node: NodeInfo;
    nostrEvent: NostrEventWrapper;
    saveToActPub: boolean;
}

export interface SaveNostrEventRequest extends RequestBase {
    events: NostrEventWrapper[];
    userInfo: NostrUserInfo[];
}

export interface SaveNostrSettingsRequest extends RequestBase {
    target: string;
    key: string;
    relays: string;
}

export interface SavePublicKeyRequest extends RequestBase {
    asymEncKey: string;
    sigKey: string;
    nostrNpub: string;
    nostrPubKey: string;
}

export interface SaveUserPreferencesRequest extends RequestBase {
    userNodeId: string;
    userPreferences: UserPreferences;
}

export interface SaveUserProfileRequest extends RequestBase {
    userName: string;
    userBio: string;
    userTags: string;
    blockedWords: string;
    recentTypes: string;
    displayName: string;
    publish: boolean;
    mfsEnable: boolean;
}

export interface SearchAndReplaceRequest extends RequestBase {
    recursive: boolean;
    nodeId: string;
    search: string;
    replace: string;
}

export interface SelectAllNodesRequest extends RequestBase {
    parentNodeId: string;
}

export interface SendLogTextRequest extends RequestBase {
    text: string;
}

export interface SendTestEmailRequest extends RequestBase {
}

export interface SetCipherKeyRequest extends RequestBase {
    nodeId: string;
    principalNodeId: string;
    cipherKey: string;
}

export interface SetNodePositionRequest extends RequestBase {
    nodeId: string;
    targetName: string;
}

export interface SetUnpublishedRequest extends RequestBase {
    nodeId: string;
    unpublished: boolean;
}

export interface SignNodesRequest extends RequestBase {
    workloadId: number;
    listToSign: NodeSigData[];
}

export interface SignSubGraphRequest extends RequestBase {
    nodeId: string;
}

export interface SignupRequest extends RequestBase {
    userName: string;
    password: string;
    email: string;
    captcha: string;
}

export interface SplitNodeRequest extends RequestBase {
    splitType: string;
    nodeId: string;
    delimiter: string;
}

export interface SubGraphHashRequest extends RequestBase {
    recursive: boolean;
    nodeId: string;
}

export interface TransferNodeRequest extends RequestBase {
    recursive: boolean;
    nodeId: string;
    fromUser: string;
    toUser: string;
    operation: string;
}

export interface UpdateFriendNodeRequest extends RequestBase {
    nodeId: string;
    tags: string;
}

export interface UpdateHeadingsRequest extends RequestBase {
    nodeId: string;
}

export interface UploadFromIPFSRequest extends RequestBase {
    pinLocally: boolean;
    nodeId: string;
    cid: string;
    mime: string;
}

export interface UploadFromUrlRequest extends RequestBase {
    storeLocally: boolean;
    nodeId: string;
    sourceUrl: string;
}

export interface RequestBase {
}

export interface AddFriendResponse extends ResponseBase {
}

export interface AddPrivilegeResponse extends ResponseBase {
    principalPublicKey: string;
    principalNodeId: string;
}

export interface AppDropResponse extends ResponseBase {
}

export interface BlockUserResponse extends ResponseBase {
}

export interface BrowseFolderResponse {
    listingJson: string;
}

export interface ChangePasswordResponse extends ResponseBase {
    user: string;
}

export interface CheckMessagesResponse extends ResponseBase {
    numNew: number;
}

export interface CloseAccountResponse extends ResponseBase {
}

export interface CopySharingResponse extends ResponseBase {
}

export interface CreateSubNodeResponse extends ResponseBase {
    newNode: NodeInfo;
    encrypt: boolean;
}

export interface DeleteAttachmentResponse extends ResponseBase {
}

export interface DeleteFriendResponse extends ResponseBase {
}

export interface DeleteMFSFileResponse extends ResponseBase {
}

export interface DeleteNodesResponse extends ResponseBase {
}

export interface DeletePropertyResponse extends ResponseBase {
}

export interface ExportResponse extends ResponseBase {
    ipfsCid: string;
    ipfsMime: string;
    fileName: string;
}

export interface FeedPushInfo extends ServerPushInfo {
    nodeInfo: NodeInfo;
}

export interface FileSearchResponse extends ResponseBase {
    searchResultNodeId: string;
}

export interface FileSystemReindexResponse extends ResponseBase {
    report: string;
}

export interface FriendInfo {
    displayName: string;
    userName: string;
    relays: string;
    avatarVer: string;
    userNodeId: string;
    friendNodeId: string;
    foreignAvatarUrl: string;
    tags: string;
    liked: boolean;
}

export interface GetActPubObjectResponse extends ResponseBase {
    node: NodeInfo;
}

export interface GetBookmarksResponse extends ResponseBase {
    bookmarks: Bookmark[];
}

export interface GetConfigResponse extends ResponseBase {
    config: { [index: string]: any };
    sessionTimeoutMinutes: number;
    brandingAppName: string;
    requireCrypto: boolean;
    urlIdFailMsg: string;
    userMsg: string;
    displayUserProfileId: string;
    initialNodeId: string;
    loadNostrId: string;
    loadNostrIdRelays: string;
    nostrRelays: string;
}

export interface GetFollowersResponse extends ResponseBase {
    searchResults: NodeInfo[];
}

export interface GetFollowingResponse extends ResponseBase {
    searchResults: NodeInfo[];
}

export interface GetIPFSContentResponse extends ResponseBase {
    content: string;
}

export interface GetIPFSFilesResponse extends ResponseBase {
    files: MFSDirEntry[];
    folder: string;
    cid: string;
}

export interface GetMultiRssResponse extends ResponseBase {
    feed: RssFeed;
}

export interface GetNodePrivilegesResponse extends ResponseBase {
    aclEntries: AccessControlInfo[];
}

export interface GetNodeStatsResponse extends ResponseBase {
    stats: string;
    topWords: string[];
    topTags: string[];
    topMentions: string[];
    topVotes: string[];
}

export interface GetOpenGraphResponse extends ResponseBase {
    openGraph: OpenGraph;
}

export interface GetPeopleResponse extends ResponseBase {
    nodeOwner: FriendInfo;
    people: FriendInfo[];
    friendHashTags: string[];
}

export interface GetPublicServerInfoResponse extends ResponseBase {
    serverInfo: string;
}

export interface GetRepliesViewResponse extends ResponseBase {
    nodes: NodeInfo[];
}

export interface GetSchemaOrgTypesResponse extends ResponseBase {
    classes: SchemaOrgClass[];
}

export interface GetServerInfoResponse extends ResponseBase {
    messages: InfoMessage[];
}

export interface GetSharedNodesResponse extends ResponseBase {
    searchResults: NodeInfo[];
}

export interface GetThreadViewResponse extends ResponseBase {
    nodes: NodeInfo[];
    topReached: boolean;
    nostrDeadEnd: boolean;
}

export interface GetUserAccountInfoResponse extends ResponseBase {
    binTotal: number;
    binQuota: number;
}

export interface GetUserProfileResponse extends ResponseBase {
    userProfile: UserProfile;
}

export interface GraphResponse extends ResponseBase {
    rootNode: GraphNode;
}

export interface IPSMPushInfo extends ServerPushInfo {
    payload: string;
}

export interface ImportResponse extends ResponseBase {
}

export interface InfoMessage {
    message: string;
    type: string;
}

export interface InitNodeEditResponse extends ResponseBase {
    nodeInfo: NodeInfo;
}

export interface InsertBookResponse extends ResponseBase {
    newNode: NodeInfo;
}

export interface InsertNodeResponse extends ResponseBase {
    newNode: NodeInfo;
}

export interface JoinNodesResponse extends ResponseBase {
}

export interface LikeNodeResponse extends ResponseBase {
}

export interface LinkNodesResponse extends ResponseBase {
}

export interface LoadNodeFromIpfsResponse extends ResponseBase {
}

export interface LoginResponse extends ResponseBase {
    userProfile: UserProfile;
    authToken: string;
    rootNodePath: string;
    allowedFeatures: string;
    anonUserLandingPageNode: string;
    userPreferences: UserPreferences;
    allowFileSystemSearch: boolean;
}

export interface LogoutResponse extends ResponseBase {
}

export interface LuceneIndexResponse extends ResponseBase {
}

export interface LuceneSearchResponse extends ResponseBase {
}

export interface MoveNodesResponse extends ResponseBase {
    signaturesRemoved: boolean;
}

export interface NewNostrUsersPushInfo extends ServerPushInfo {
    users: NostrUserInfo[];
}

export interface NodeEditedPushInfo extends ServerPushInfo {
    nodeInfo: NodeInfo;
}

export interface NodeFeedResponse extends ResponseBase {
    endReached: boolean;
    searchResults: NodeInfo[];
    friendHashTags: string[];
}

export interface NodeSearchResponse extends ResponseBase {
    searchResults: NodeInfo[];
}

export interface NodeSigData {
    nodeId: string;
    data: string;
}

export interface NodeSigPushInfo extends ServerPushInfo {
    workloadId: number;
    listToSign: NodeSigData[];
}

export interface NotificationMessage extends ServerPushInfo {
    nodeId: string;
    fromUser: string;
    message: string;
}

export interface OpenSystemFileResponse extends ResponseBase {
}

export interface PingResponse extends ResponseBase {
    serverInfo: string;
}

export interface PublishNodeToIpfsResponse extends ResponseBase {
}

export interface PushPageMessage extends ServerPushInfo {
    payload: string;
    usePopup: boolean;
}

export interface RemovePrivilegeResponse extends ResponseBase {
}

export interface RenderCalendarResponse extends ResponseBase {
    items: CalendarItem[];
}

export interface RenderDocumentResponse extends ResponseBase {
    searchResults: NodeInfo[];
}

export interface RenderNodeResponse extends ResponseBase {
    node: NodeInfo;
    endReached: boolean;
    noDataResponse: string;
    breadcrumbs: BreadcrumbInfo[];
    rssNode: boolean;
}

export interface ResetPasswordResponse extends ResponseBase {
}

export interface SaveNodeResponse extends ResponseBase {
    node: NodeInfo;
    aclEntries: AccessControlInfo[];
}

export interface SaveNostrEventResponse extends ResponseBase {
    eventNodeIds: string[];
    accntNodeIds: string[];
    saveCount: number;
}

export interface SaveNostrSettingsResponse extends ResponseBase {
}

export interface SavePublicKeyResponse extends ResponseBase {
}

export interface SaveUserPreferencesResponse extends ResponseBase {
}

export interface SaveUserProfileResponse extends ResponseBase {
}

export interface SearchAndReplaceResponse extends ResponseBase {
}

export interface SelectAllNodesResponse extends ResponseBase {
    nodeIds: string[];
}

export interface SendLogTextResponse extends ResponseBase {
}

export interface SendTestEmailResponse extends ResponseBase {
}

export interface ServerPushInfo {
}

export interface SetCipherKeyResponse extends ResponseBase {
}

export interface SetNodePositionResponse extends ResponseBase {
}

export interface SetUnpublishedResponse extends ResponseBase {
}

export interface SignNodesResponse extends ResponseBase {
}

export interface SignSubGraphResponse extends ResponseBase {
}

export interface SignupResponse extends ResponseBase {
    userError: string;
    passwordError: string;
    emailError: string;
    captchaError: string;
}

export interface SplitNodeResponse extends ResponseBase {
}

export interface SubGraphHashResponse extends ResponseBase {
}

export interface TransferNodeResponse extends ResponseBase {
}

export interface UpdateFriendNodeResponse extends ResponseBase {
}

export interface UpdateHeadingsResponse extends ResponseBase {
}

export interface UploadFromIPFSResponse extends ResponseBase {
}

export interface UploadFromUrlResponse extends ResponseBase {
}

export interface UploadResponse extends ResponseBase {
    payloads: string[];
}

export interface ResponseBase {
    success: boolean;
    message: string;
    stackTrace: string;
    errorType: string;
}

export interface PropertyInfo {
    name: string;
    value: any;
}

export interface NodeInfo {
    id: string;
    path: string;
    name: string;
    content: string;
    renderContent: string;
    tags: string;
    lastModified: number;
    timeAgo: string;
    logicalOrdinal: number;
    ordinal: number;
    type: string;
    properties: PropertyInfo[];
    attachments: { [index: string]: Attachment };
    links: { [index: string]: NodeLink };
    clientProps: PropertyInfo[];
    ac: AccessControlInfo[];
    hasChildren: boolean;
    cipherKey: string;
    lastChild: boolean;
    parent: NodeInfo;
    children: NodeInfo[];
    parents: NodeInfo[];
    linkedNodes: NodeInfo[];
    likes: string[];
    imgId: string;
    displayName: string;
    owner: string;
    ownerId: string;
    nostrPubKey: string;
    transferFromId: string;
    avatarVer: string;
    apAvatar: string;
    apImage: string;
    boostedNode: NodeInfo;
}

export interface UserPreferences {
    editMode: boolean;
    showMetaData: boolean;
    nsfw: boolean;
    showProps: boolean;
    autoRefreshFeed: boolean;
    showParents: boolean;
    showReplies: boolean;
    rssHeadlinesOnly: boolean;
    mainPanelCols: number;
    enableIPSM: boolean;
    maxUploadFileSize: number;
}

export interface AccessControlInfo {
    displayName: string;
    principalName: string;
    principalNodeId: string;
    nostrNpub: string;
    nostrRelays: string;
    avatarVer: string;
    foreignAvatarUrl: string;
    privileges: PrivilegeInfo[];
    publicKey: string;
}

export interface GraphNode {
    id: string;
    level: number;
    highlight: boolean;
    name: string;
    children: GraphNode[];
    links: { [index: string]: NodeLink };
}

export interface CalendarItem {
    id: string;
    title: string;
    start: number;
    end: number;
}

export interface BreadcrumbInfo {
    id: string;
    name: string;
    type: string;
}

export interface PrivilegeInfo {
    privilegeName: string;
}

export const enum Constant {
    NETWORK_NOSTR = "nostr",
    NETWORK_ACTPUB = "ap",
    SEARCH_TYPE_USER_LOCAL = "userLocal",
    SEARCH_TYPE_USER_ALL = "userAll",
    SEARCH_TYPE_USER_FOREIGN = "userForeign",
    SEARCH_TYPE_USER_NOSTR = "userNostr",
    ENC_TAG = "<[ENC]>",
    FEED_NEW = "myNewMessages",
    FEED_PUB = "publicFediverse",
    FEED_TOFROMME = "toFromMe",
    FEED_TOME = "toMe",
    FEED_MY_MENTIONS = "myMentions",
    FEED_FROMMETOUSER = "fromMeToUser",
    FEED_FROMME = "fromMe",
    FEED_FROMFRIENDS = "fromFriends",
    FEED_LOCAL = "local",
    FEED_NODEFEED = "nodeFeed",
    ATTACHMENT_PRIMARY = "p",
    ATTACHMENT_HEADER = "h",
}

export const enum ConstantInt {
    ROWS_PER_PAGE = 25,
}

export const enum ErrorType {
    OUT_OF_SPACE = "oos",
    TIMEOUT = "timeout",
    AUTH = "auth",
}

export const enum NodeProp {
    NOSTR_RELAYS = "sn:relays",
    NOSTR_USER_NPUB = "sn:npub",
    NOSTR_USER_PUBKEY = "sn:nopk",
    NOSTR_TAGS = "sn:ntags",
    NOSTR_NAME = "sn:nosName",
    NOSTR_USER_NAME = "sn:nosUserName",
    NOSTR_NIP05 = "sn:nosNip05",
    NOSTR_USER_WEBSITE = "sn:nosWebsite",
    NOSTR_USER_TIMESTAMP = "sn:nosTimestamp",
    OBJECT_ID = "apid",
    ACT_PUB_OBJ_TYPE = "ap:objType",
    ACT_PUB_OBJ_CONTENT = "ap:objContent",
    INREPLYTO = "ap:objInReplyTo",
    ACT_PUB_OBJ_URL = "ap:objUrl",
    ACT_PUB_OBJ_URLS = "ap:objUrls",
    ACT_PUB_OBJ_ICONS = "ap:objIcons",
    ACT_PUB_OBJ_NAME = "ap:objName",
    ACT_PUB_OBJ_ATTRIBUTED_TO = "ap:objAttributedTo",
    USER_ICON_URL = "ap:userIcon",
    ACT_PUB_SHARED_INBOX = "ap:sharedInbox",
    USER_BANNER_URL = "ap:userImage",
    ACT_PUB_ACTOR_ID = "ap:actorId",
    ACT_PUB_ACTOR_URL = "ap:actorUrl",
    ACT_PUB_KEYPEM = "ap:keyPem",
    ACT_PUB_ACTOR_INBOX = "ap:actorInbox",
    ACT_PUB_SENSITIVE = "ap:nsfw",
    ACT_PUB_TAG = "ap:tag",
    ACT_PUB_REPLIES = "ap:replies",
    ENC_KEY = "sn:encKey",
    CRYPTO_SIG = "sn:sig",
    SUBGRAPH_HASH = "sn:rSHA256",
    RSS_FEED_SRC = "sn:rssFeedSrc",
    AUDIO_URL = "sn:audioUrl",
    USER_PREF_PUBLIC_KEY = "sn:publicKey",
    USER_PREF_PUBLIC_SIG_KEY = "sn:publicSigKey",
    USER_PREF_EDIT_MODE = "sn:editMode",
    USER_PREF_SHOW_METADATA = "sn:showMetaData",
    USER_PREF_NSFW = "sn:nsfw",
    USER_PREF_SHOW_PROPS = "sn:showProps",
    USER_PREF_AUTO_REFRESH_FEED = "sn:autoRefreshFeed",
    USER_PREF_SHOW_PARENTS = "sn:showParents",
    USER_PREF_SHOW_REPLIES = "sn:showReplies",
    USER_PREF_PASSWORD_RESET_AUTHCODE = "sn:pwdResetAuth",
    USER_PREF_RSS_HEADINGS_ONLY = "sn:rssHeadingsOnly",
    USER_PREF_MAIN_PANEL_COLS = "sn:mainPanelCols",
    SIGNUP_PENDING = "sn:signupPending",
    EMAIL_CONTENT = "sn:content",
    EMAIL_RECIP = "sn:recip",
    EMAIL_SUBJECT = "sn:subject",
    TARGET_ID = "sn:target_id",
    USER = "sn:user",
    DISPLAY_NAME = "sn:displayName",
    MFS_ENABLE = "sn:mfsEnable",
    USER_BIO = "sn:userBio",
    USER_DID_IPNS = "sn:didIPNS",
    USER_IPFS_KEY = "sn:ipfsKey",
    USER_TAGS = "sn:tags",
    USER_BLOCK_WORDS = "sn:blockWords",
    USER_RECENT_TYPES = "sn:recentTypes",
    PWD_HASH = "sn:pwdHash",
    VOTE = "vote",
    FILE_SYNC_LINK = "fs:link",
    USER_NODE_ID = "sn:userNodeId",
    NAME = "sn:name",
    IPFS_CID = "ipfs:cid",
    IPNS_CID = "ipns:cid",
    IPFS_SCID = "ipfs:scid",
    JSON_HASH = "ipfs:json",
    SAVE_TO_IPFS = "sn:saveToIpfs",
    IPFS_LINK_NAME = "ipfs:linkName",
    IPFS_SOURCE = "ipfs:source",
    FS_LINK = "fs:link",
    IPFS_OK = "ipfs:ok",
    MIME_EXT = "sn:ext",
    EMAIL = "sn:email",
    CODE = "sn:code",
    JSON_FILE_SEARCH_RESULT = "sn:json",
    NOWRAP = "sn:nowrap",
    BIN = "bin",
    BIN_TOTAL = "sn:binTot",
    BIN_QUOTA = "sn:binQuota",
    ALLOWED_FEATURES = "sn:features",
    LAST_LOGIN_TIME = "sn:lastLogin",
    LAST_ACTIVE_TIME = "sn:lastActive",
    CRYPTO_KEY_PUBLIC = "sn:cryptoKeyPublic",
    CRYPTO_KEY_PRIVATE = "sn:cryptoKeyPrivate",
    INLINE_CHILDREN = "inlineChildren",
    PRIORITY = "priority",
    PRIORITY_FULL = "p.priority",
    LAYOUT = "layout",
    ORDER_BY = "orderBy",
    NO_EXPORT = "noexport",
    TYPE_LOCK = "sn:typLoc",
    DATE = "date",
    DATE_FULL = "p.date",
    UNPUBLISHED = "unpub",
    BOOST = "boost",
    DURATION = "duration",
    IN_PENDING_PATH = "pendingPath",
    TRUNCATED = "trunc",
}

export const enum NodeType {
    ACCOUNT = "sn:account",
    REPO_ROOT = "sn:repoRoot",
    INBOX = "sn:inbox",
    INBOX_ENTRY = "sn:inboxEntry",
    ROOM = "sn:room",
    NOTES = "sn:notes",
    BOOKMARK = "sn:bookmark",
    BOOKMARK_LIST = "sn:bookmarkList",
    EXPORTS = "sn:exports",
    CALCULATOR = "sn:calculator",
    CALENDAR = "sn:calendar",
    COMMENT = "sn:comment",
    RSS_FEED = "sn:rssfeed",
    RSS_FEEDS = "sn:rssfeeds",
    FRIEND_LIST = "sn:friendList",
    BLOCKED_USERS = "sn:blockedUsers",
    FRIEND = "sn:friend",
    POSTS = "sn:posts",
    ACT_PUB_POSTS = "ap:posts",
    NONE = "u",
    NOSTR_ENC_DM = "sn:ned",
    PLAIN_TEXT = "sn:txt",
    FS_FILE = "fs:file",
    FS_FOLDER = "fs:folder",
    FS_LUCENE = "fs:lucene",
    IPFS_NODE = "sn:ipfsNode",
}

export const enum PrincipalName {
    FOLLOW_BOT = "FollowBot",
    ANON = "anonymous",
    ADMIN = "admin",
    PUBLIC = "public",
}

export const enum PrivilegeType {
    READ = "rd",
    WRITE = "wr",
}
