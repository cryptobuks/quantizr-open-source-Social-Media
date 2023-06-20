import { asyncDispatch, dispatch, getAs, promiseDispatch } from "./AppContext";
import { CompIntf } from "./comp/base/CompIntf";
import { Div } from "./comp/core/Div";
import { Menu } from "./comp/Menu";
import { MenuItem } from "./comp/MenuItem";
import { MenuItemSeparator } from "./comp/MenuItemSeparator";
import { Constants as C } from "./Constants";
import { AskNodeLinkNameDlg } from "./dlg/AskNodeLinkNameDlg";
import { BlockedUsersDlg } from "./dlg/BlockedUsersDlg";
import { FriendsDlg } from "./dlg/FriendsDlg";
import { MultiFollowDlg } from "./dlg/MultiFollowDlg";
import { PickNodeTypeDlg } from "./dlg/PickNodeTypeDlg";
import { SearchAndReplaceDlg } from "./dlg/SearchAndReplaceDlg";
import { SearchByFediUrlDlg } from "./dlg/SearchByFediUrlDlg";
import { SearchByIDDlg } from "./dlg/SearchByIDDlg";
import { SearchByNameDlg } from "./dlg/SearchByNameDlg";
import { SearchByNostrDlg } from "./dlg/SearchByNostr";
import { SearchContentDlg } from "./dlg/SearchContentDlg";
import { SearchUsersDlg } from "./dlg/SearchUsersDlg";
import { SplitNodeDlg } from "./dlg/SplitNodeDlg";
import { TransferNodeDlg } from "./dlg/TransferNodeDlg";
import { UserProfileDlg } from "./dlg/UserProfileDlg";
import { TypeIntf } from "./intf/TypeIntf";
import * as J from "./JavaIntf";
import { PubSub } from "./PubSub";
import { S } from "./Singletons";
import { SettingsTab } from "./tabs/data/SettingsTab";
import { TTSTab } from "./tabs/data/TTSTab";

PubSub.sub(C.PUBSUB_tabChanging, (tabId: string) => {
    // These menu options are too important to have the user just "maybe" happen to find them
    // when needed so we actively set the expansion state based on context of what user is doing.
    if (tabId === C.TAB_FEED || tabId == C.TAB_TRENDING) {
        asyncDispatch("menuExpandChanged", s => {
            S.nav.changeMenuExpansion(s, "expand", C.PROTOCOL_MENU_TEXT);
            S.nav.changeMenuExpansion(s, "collapse", C.OPTIONS_MENU_TEXT);
        });
    }
    else if (tabId === C.TAB_MAIN || tabId === C.TAB_DOCUMENT) {
        asyncDispatch("menuExpandChanged", s => {
            S.nav.changeMenuExpansion(s, "expand", C.OPTIONS_MENU_TEXT);
            S.nav.changeMenuExpansion(s, "collapse", C.PROTOCOL_MENU_TEXT);
        });
    }
});

export class MenuPanel extends Div {
    static initialized: boolean = false;

    constructor() {
        super(null, {
            id: C.ID_MENU,
            role: "tablist",
            className: (getAs().mobileMode ? "menuPanelMobile" : "menuPanel")
        });
        if (!MenuPanel.initialized) {
            // if anon user keep the page very clean and don't show this.
            asyncDispatch("autoExpandOptionsMenu", s => {
                s.expandedMenus.add(C.OPTIONS_MENU_TEXT);
            });
            MenuPanel.initialized = true;
        }
    }

    // leaving for reference how to open this.
    static openNotesNode = () => S.nav.openContentNode("~" + J.NodeType.NOTES, false);

    static editFriends = () => {
        // DO NOT DELETE (This is good know as the way to access raw friends nodes)
        // S.nav.openContentNode("~" + J.NodeType.FRIEND_LIST);
        const friendsDlg = new FriendsDlg("Friends", null, true);
        friendsDlg.open();
    };

    static openBookmarksNode = () => {
        S.util.setUserPreferences(true);
        S.nav.openContentNode("~" + J.NodeType.BOOKMARK_LIST, false);
    };

    static continueEditing = () => {
        const ast = getAs();
        if (ast.editNode) {
            S.view.jumpToId(ast.editNode.id);
        }
    };

    static setLinkSource = () => {
        const node = S.nodeUtil.getHighlightedNode();
        dispatch("setLinkSourceNodeId", s => {
            if (node) {
                s.linkSource = node.id;
            }
        });
    };

    static setLinkTarget = () => {
        const node = S.nodeUtil.getHighlightedNode();
        dispatch("setLinkTargetNodeId", s => {
            if (node) {
                s.linkTarget = node.id;
            }
        });
    };

    static linkNodes = () => {
        dispatch("setLinkSourceNodeId", s => {
            const node = S.nodeUtil.getHighlightedNode();
            if (node) {
                const sourceId = s.linkSource;
                const targetId = s.linkTarget;

                const run = async () => {
                    const dlg = new AskNodeLinkNameDlg();
                    await dlg.open();
                    if (dlg.nameEntered) {
                        S.edit.linkNodes(sourceId, targetId, dlg.nameEntered, "forward-link");
                    }
                };
                run();
            }
            s.linkSource = null;
            s.linkTarget = null;
        });
    };

    // We pre-create all these functions so that the re-rendering of this component doesn't also create functions
    // which can be slow in JS.

    static showBlockedUsers = () => {
        // S.nav.openContentNode("~" + J.NodeType.BLOCKED_USERS);
        const dlg = new BlockedUsersDlg("Blocked Users");
        dlg.open();
    }

    static toggleEditMode = () => {
        S.edit.setEditMode(!getAs().userPrefs.editMode);

    }
    static toggleInfoMode = () => {
        S.edit.setShowMetaData(!getAs().userPrefs.showMetaData);
    }

    static userSettings = () => {
        SettingsTab.tabSelected = true;
        S.tabUtil.selectTab(C.TAB_SETTINGS);
    }

    static userProfile = () => { new UserProfileDlg(null).open(); }

    static openRSSFeedsNode = () => S.nav.openContentNode("~" + J.NodeType.RSS_FEEDS, false);
    static openPostsNode = () => S.nav.openContentNode("~" + J.NodeType.POSTS, false);
    static openHomeNode = () => S.nav.openContentNode(":" + getAs().userName + ":home", false);
    static openExportsNode = () => S.nav.openContentNode("~" + J.NodeType.EXPORTS, false);
    static openUsersNode = () => S.nav.openContentNode("/r/usr", false);

    static transferNode = () => { new TransferNodeDlg("transfer").open(); };
    static acceptTransfer = () => { new TransferNodeDlg("accept").open(); };
    static rejectTransfer = () => { new TransferNodeDlg("reject").open(); };
    static reclaimTransfer = () => { new TransferNodeDlg("reclaim").open(); };
    static subgraphHash = () => { S.edit.subGraphHash(); };
    static searchAndReplace = () => { new SearchAndReplaceDlg().open(); };
    static splitNode = () => { new SplitNodeDlg(null).open(); }
    static joinNodes = () => { S.edit.joinNodes(); }
    static showPublicWritableShares = () => { S.srch.findShares(J.PrincipalName.PUBLIC, J.PrivilegeType.WRITE); }
    static showPublicReadonlyShares = () => { S.srch.findShares(J.PrincipalName.PUBLIC, J.PrivilegeType.READ); }
    static showAllShares = () => { S.srch.findShares(null, null); }
    static searchByContent = () => { new SearchContentDlg().open(); };
    static searchByName = () => { new SearchByNameDlg().open(); }
    static searchById = () => { new SearchByIDDlg().open(); };
    static searchByNostr = () => { new SearchByNostrDlg().open(); };
    static searchByFediUrl = () => { new SearchByFediUrlDlg().open(); };
    static findUsers = () => { new SearchUsersDlg().open(); };
    static multiFollow = () => { new MultiFollowDlg().open(); };
    static showFollowers = () => { S.srch.showFollowers(0, null); };
    static timelineByCreated = () => S.srch.timeline(null, "ctm", null, "Rev-chron by Create Time", 0, true);
    static timelineByModified = () => S.srch.timeline(null, "mtm", null, "Rev-chron by Modify Time", 0, true);
    static timelineByCreatedNonRecursive = () => S.srch.timeline(null, "ctm", null, "Rev-chron by Create Time (top level)", 0, false);
    static timelineByModifiedNonRecursive = () => S.srch.timeline(null, "mtm", null, "Rev-chron by Modify Time (top level)", 0, false);
    static showCalendar = () => S.render.showCalendar(null);
    static calendarFutureDates = () => S.srch.timeline(null, J.NodeProp.DATE_FULL, "futureOnly", "Future calendar dates (Soonest at the top)", 0, true);
    static calendarPastDates = () => S.srch.timeline(null, J.NodeProp.DATE_FULL, "pastOnly", "Past calendar dates (Newest at the top)", 0, true);
    static calendarAllDates = () => S.srch.timeline(null, J.NodeProp.DATE_FULL, "all", "All calendar dates", 0, true);
    // static toolsShowClipboard = () => S.edit.saveClipboardToChildNode("~" + J.NodeType.NOTES);
    // static toolsShowIpfsTab = () => S.edit.showIpfsTab();
    static import = () => S.edit.openImportDlg();
    static listSubgraphByPriority = () => S.srch.listSubgraphByPriority();
    static export = () => S.edit.openExportDlg();

    static openTtsTab = () => {
        // this ttsTabSelected var is a quick hack to make tab show up, but we really need common
        // forceSelectTab for thsi purpose (or maybe selectTab SHOULD naturally force things? probably so)
        TTSTab.ttsTabSelected = true;
        S.tabUtil.selectTab(C.TAB_TTS);
    };

    static showUrls = () => S.render.showNodeUrl(null);
    static showRawData = () => S.view.runServerCommand("getJson", null, "Node Data", "");
    static queryNostrRelays = () => {
        S.nostr.queryAndDisplayNodeInfo(S.nodeUtil.getHighlightedNode());
    };

    static showActPubJson = () => S.view.runServerCommand("getActPubJson", null, "ActivityPub JSON", "");
    static nodeStats = () => S.view.getNodeStats();
    static nodeSignatureVerify = () => S.view.getNodeSignatureVerify();
    static signSubGraph = () => S.view.signSubGraph();

    override preRender(): boolean {
        const ast = getAs();

        const hltNode = S.nodeUtil.getHighlightedNode();
        const selNodeIsMine = !!hltNode && (hltNode.owner === ast.userName || ast.userName === J.PrincipalName.ADMIN);
        const onMainTab: boolean = ast.activeTab == C.TAB_MAIN;
        const nostrNodeHighlighted = !!hltNode && S.nostr.isNostrNode(hltNode);
        const transferFromMe = !!hltNode && hltNode.transferFromId === ast.userProfile?.userNodeId;
        const transferring = !!hltNode && !!hltNode.transferFromId;

        const importFeatureEnabled = selNodeIsMine || (!!hltNode && ast.userProfile?.userNodeId === hltNode.id);
        const exportFeatureEnabled = selNodeIsMine || (!!hltNode && ast.userProfile?.userNodeId === hltNode.id);

        const orderByProp = S.props.getPropStr(J.NodeProp.ORDER_BY, hltNode);
        const allowNodeMove: boolean = !orderByProp && S.props.isWritableByMe(ast.node);
        const isPageRootNode = ast.node && hltNode && ast.node.id === hltNode.id;
        const canMoveUp = !isPageRootNode && !ast.isAnonUser && (allowNodeMove && hltNode && hltNode.logicalOrdinal > 0);
        const canMoveDown = !isPageRootNode && !ast.isAnonUser && (allowNodeMove && hltNode && !hltNode.lastChild);

        const children = [];

        const allowEditMode = !ast.isAnonUser;
        const fullScreenViewer = S.util.fullscreenViewerActive();

        children.push(new Menu(C.OPTIONS_MENU_TEXT, [
            ast.isAnonUser ? null : new MenuItem("Edit Mode", MenuPanel.toggleEditMode, allowEditMode && !fullScreenViewer, () => getAs().userPrefs.editMode),
            new MenuItem("Node Info", MenuPanel.toggleInfoMode, !fullScreenViewer, () => getAs().userPrefs.showMetaData),
        ]));

        children.push(new Menu(C.PROTOCOL_MENU_TEXT, [
            new MenuItem("Nostr", () => S.util.setProtocol(J.Constant.NETWORK_NOSTR), true, () => getAs().protocolFilter == J.Constant.NETWORK_NOSTR),
            new MenuItem("ActivityPub", () => S.util.setProtocol(J.Constant.NETWORK_ACTPUB), true, () => getAs().protocolFilter == J.Constant.NETWORK_ACTPUB),
        ]));

        const bookmarkItems = [];
        if (!ast.isAnonUser) {
            if (ast.bookmarks) {
                ast.bookmarks.forEach(bookmark => {
                    const nodeId = bookmark.id || bookmark.selfId;
                    const mi = new MenuItem(bookmark.name, () => S.view.jumpToId(nodeId), true, null);
                    S.domUtil.makeDropTarget(mi.attribs, nodeId);
                    bookmarkItems.push(mi);
                });
            }

            const hasBookmarks = bookmarkItems.length > 0;
            if (bookmarkItems.length > 0) {
                bookmarkItems.push(new MenuItemSeparator());
            }
            bookmarkItems.push(new MenuItem("Manage...", MenuPanel.openBookmarksNode, !ast.isAnonUser));

            if (hasBookmarks) {
                children.push(new Menu(C.BOOKMARKS_MENU_TEXT, bookmarkItems, null));
            }
        }

        if (!ast.isAnonUser) {
            const systemFolderLinks = this.getSystemFolderLinks();

            children.push(new Menu("Folders", [
                new MenuItem("My Account", S.nav.navToMyAccntRoot),
                new MenuItem("My Home", MenuPanel.openHomeNode),
                new MenuItem("My Posts", MenuPanel.openPostsNode),
                ast.isAdminUser ? new MenuItem("All Users", MenuPanel.openUsersNode) : null,
                new MenuItemSeparator(),
                new MenuItem("Text-to-Speech", MenuPanel.openTtsTab),
                new MenuItem("RSS Feeds", MenuPanel.openRSSFeedsNode),
                new MenuItem("Notes", MenuPanel.openNotesNode),
                new MenuItem("Exports", MenuPanel.openExportsNode),
                systemFolderLinks.length > 0 ? new MenuItemSeparator() : null,
                ...systemFolderLinks
            ], null));
        }

        if (!ast.isAnonUser) {
            children.push(new Menu("People", [
                new MenuItem("Friends", MenuPanel.editFriends),
                new MenuItem("Followers", MenuPanel.showFollowers),
                new MenuItem("Blocked", MenuPanel.showBlockedUsers),
                new MenuItemSeparator(),
                new MenuItem("Find People", MenuPanel.findUsers), //

                /* It would be possible to allow this multiFollow capability for all users, but I don't want to make it that easy
                 to create a heavy server load for now. Users can add one at a time for now, and only the FollowBot user has
                 this superpower. */
                ast.userName === J.PrincipalName.FOLLOW_BOT ? new MenuItem("Multi-Follow", MenuPanel.multiFollow) : null //
            ], null));
        }

        if (!ast.isAnonUser) {
            children.push(new Menu("Edit", [
                ast.editNode ? new MenuItem("Resume Editing...", MenuPanel.continueEditing) : null, //
                ast.editNode ? new MenuItemSeparator() : null, //

                new MenuItem("Clear Selections", S.nodeUtil.clearSelNodes, onMainTab && ast.selectedNodes.size > 0, null, true), //

                // new MenuItem("Select All", S.edit.selectAllNodes, () => { return  !state.isAnonUser }), //

                new MenuItem("Set Headings", S.edit.setHeadings, onMainTab && selNodeIsMine, null, true), //
                new MenuItem("Search and Replace", MenuPanel.searchAndReplace, onMainTab && selNodeIsMine, null, true), //

                new MenuItemSeparator(), //

                new MenuItem("Split Node", MenuPanel.splitNode, onMainTab && selNodeIsMine, null, true), //
                new MenuItem("Join Nodes", MenuPanel.joinNodes, onMainTab && selNodeIsMine, null, true), //

                new MenuItemSeparator(), //

                new MenuItem("Move to Top", S.edit.moveNodeToTop, onMainTab && canMoveUp, null, true), //
                new MenuItem("Move to Bottom", S.edit.moveNodeToBottom, onMainTab && canMoveDown, null, true), //
                new MenuItemSeparator(), //

                new MenuItem("Cut", S.edit.cutSelNodes, onMainTab && selNodeIsMine, null, true), //
                new MenuItem("Undo Cut", S.edit.undoCutSelNodes, onMainTab && !!ast.nodesToMove, null, true), //
                new MenuItemSeparator(), //

                new MenuItem("Delete", S.edit.deleteSelNodes, onMainTab && selNodeIsMine, null, true) //

                // todo-2: disabled during mongo conversion
                // new MenuItem("Set Node A", view.setCompareNodeA, () => { return state.isAdminUser && highlightNode != null }, () => { return state.isAdminUser }), //
                // new MenuItem("Compare as B (to A)", view.compareAsBtoA, //
                //    () => { return state.isAdminUser && highlightNode != null }, //
                //    () => { return state.isAdminUser }, //
                //    true
                // ), //
            ], null));
        }

        const createMenuItems: CompIntf[] = [];
        const types = S.plugin.getAllTypes();
        const recentTypes = ast.userProfile && ast.userProfile.recentTypes ? ast.userProfile.recentTypes.split(",") : null;
        let typesAdded = false;

        if (!ast.isAnonUser) {
            types.forEach((type: TypeIntf, k: string) => {
                if (type.schemaOrg && !(recentTypes?.includes(k))) {
                    return;
                }
                typesAdded = true;
                if (ast.isAdminUser || type.getAllowUserSelect()) {
                    createMenuItems.push(new MenuItem(type.getName(), () => S.edit.createNode(hltNode, type.getTypeName(), true, true, null, null), //
                        onMainTab && !ast.isAnonUser && !!hltNode));
                }
            });
        }

        if (!ast.isAnonUser) {
            if (typesAdded) createMenuItems.push(new MenuItemSeparator());
            createMenuItems.push(new MenuItem("Choose Type...", async () => {
                await promiseDispatch("chooseType", s => { s.showSchemaOrgProps = true; });
                const dlg = new PickNodeTypeDlg(null);
                await dlg.open();
                if (dlg.chosenType) {
                    S.edit.createNode(hltNode, dlg.chosenType, true, true, null, null);
                }
            }, //
                onMainTab && !ast.isAnonUser && !!hltNode, null, true));

            children.push(new Menu("Create", createMenuItems, null));
        }

        if (!ast.isAnonUser) {
            children.push(new Menu("Search", [
                new MenuItem("By Content", MenuPanel.searchByContent, onMainTab && !!hltNode, null, true), //
                new MenuItem("By Node Name", MenuPanel.searchByName), //
                new MenuItem("By Node ID", MenuPanel.searchById), //

                new MenuItemSeparator(), //
                new MenuItem("By Fediverse URL", MenuPanel.searchByFediUrl), //
                new MenuItem("By Nostr ID", MenuPanel.searchByNostr), //

                // moved into editor dialog
                // new MenuItem("Edit Node Sharing", () => S.edit.editNodeSharing(state), //
                //     !state.isAnonUser && !!highlightNode && selNodeIsMine), //

                new MenuItemSeparator(), //

                new MenuItem("Shared Nodes", MenuPanel.showAllShares, //
                    !ast.isAnonUser && !!hltNode),

                new MenuItem("Public Read-only", MenuPanel.showPublicReadonlyShares, //
                    !ast.isAnonUser && !!hltNode),

                new MenuItem("Public Appendable", MenuPanel.showPublicWritableShares, //
                    !ast.isAnonUser && !!hltNode),

                new MenuItemSeparator(), //

                new MenuItem("Priority Listing", MenuPanel.listSubgraphByPriority, //
                    !ast.isAnonUser && !!hltNode)

                // new MenuItem("Files", nav.searchFiles, () => { return  !state.isAnonUser && S.quanta.allowFileSystemSearch },
                //    () => { return  !state.isAnonUser && S.quanta.allowFileSystemSearch })
            ], null));
        }

        if (!ast.isAnonUser) {
            children.push(new Menu("Timeline", [
                // Backing out the Chat Room feature for now.
                // new MenuItem("Live Rev-Chron (Chat Room)", S.nav.messagesNodeFeed, hltNode?.id != null),
                // new MenuItemSeparator(), //
                new MenuItem("Created", MenuPanel.timelineByCreated, onMainTab && !!hltNode, null, true),
                new MenuItem("Modified", MenuPanel.timelineByModified, onMainTab && !!hltNode, null, true),
                new MenuItemSeparator(), //
                new MenuItem("Created (non-Recursive)", MenuPanel.timelineByCreatedNonRecursive, onMainTab && !!hltNode, null, true), //
                new MenuItem("Modified (non-Recursive)", MenuPanel.timelineByModifiedNonRecursive, onMainTab && !!hltNode, null, true) //
            ], null));
        }

        if (!ast.isAnonUser) {
            children.push(new Menu("Calendar", [
                new MenuItem("Display", MenuPanel.showCalendar, onMainTab && !!hltNode, null, true),
                new MenuItemSeparator(), //
                new MenuItem("Future", MenuPanel.calendarFutureDates, onMainTab && !!hltNode, null, true),
                new MenuItem("Past", MenuPanel.calendarPastDates, onMainTab && !!hltNode, null, true),
                new MenuItem("All", MenuPanel.calendarAllDates, onMainTab && !!hltNode, null, true)
            ]));
        }

        if (!ast.isAnonUser) {
            children.push(new Menu("Tools", [
                // new MenuItem("IPFS Explorer", MenuPanel.toolsShowIpfsTab), //

                new MenuItem("Import", MenuPanel.import, onMainTab && importFeatureEnabled, null, true),
                new MenuItem("Export", MenuPanel.export, onMainTab && exportFeatureEnabled, null, true),
                new MenuItemSeparator(), //

                S.crypto.avail ? new MenuItem("Sign", MenuPanel.signSubGraph, selNodeIsMine, null, true) : null, //
                new MenuItem("Verify Signatures", MenuPanel.nodeSignatureVerify, onMainTab && selNodeIsMine, null, true), //
                new MenuItem("Generate SHA256", MenuPanel.subgraphHash, onMainTab && selNodeIsMine, null, true) //

                // Removing for now. Our PostIt node icon makes this easy enough.
                // new MenuItem("Save Clipboard", MenuPanel.toolsShowClipboard, !state.isAnonUser), //

                // DO NOT DELETE
                // new MenuItem("Open IPSM Console", MenuPanel.setIpsmActive, !state.isAnonUser) //
            ], null));
        }

        if (!ast.isAnonUser) {
            children.push(new Menu("Info", [
                // I decided with this on the toolbar we don't need it repliated here.
                // !state.isAnonUser ? new MenuItem("Save clipboard (under Notes node)", () => S.edit.saveClipboardToChildNode("~" + J.NodeType.NOTES)) : null, //

                new MenuItem("Show URLs", MenuPanel.showUrls, onMainTab && !!hltNode, null, true), //
                new MenuItem("Show Raw Data", MenuPanel.showRawData, onMainTab && selNodeIsMine, null, true), //
                new MenuItem("Query Nostr Relays", MenuPanel.queryNostrRelays, !ast.isAnonUser && nostrNodeHighlighted), //
                ast.isAdminUser ? new MenuItem("Show ActivityPub JSON", MenuPanel.showActPubJson, onMainTab, null, true) : null, //
                new MenuItemSeparator(), //
                new MenuItem("Node Stats", onMainTab && MenuPanel.nodeStats) //
            ], null));

            children.push(new Menu("Shortcuts", [
                new MenuItem("Set Link Source", MenuPanel.setLinkSource, onMainTab && ast.userPrefs.editMode && selNodeIsMine, null, true), //
                new MenuItem("Set Link Target", MenuPanel.setLinkTarget, onMainTab && ast.userPrefs.editMode, null, true), //
                new MenuItem("Link Nodes", MenuPanel.linkNodes, onMainTab && ast.userPrefs.editMode && !!ast.linkSource && !!ast.linkTarget, null, true)
            ]));
        }

        if (!ast.isAnonUser) {
            children.push(new Menu("Transfer", [
                new MenuItem("Transfer", MenuPanel.transferNode, onMainTab && selNodeIsMine && !transferring, null, true), //
                new MenuItem("Accept", MenuPanel.acceptTransfer, onMainTab && selNodeIsMine && transferring, null, true), //
                new MenuItem("Reject", MenuPanel.rejectTransfer, onMainTab && selNodeIsMine && transferring, null, true), //
                new MenuItem("Reclaim", MenuPanel.reclaimTransfer, onMainTab && transferFromMe, null, true) //

                // todo-1: need "Show Incomming" transfers menu option
            ], null));

            children.push(new Menu("Account", [
                new MenuItem("Profile", MenuPanel.userProfile),
                new MenuItem("Settings", MenuPanel.userSettings)
            ]));
        }

        // //need to make export safe for end users to use (regarding file sizes)
        // if (state.isAdminUser) {
        //     children.push(new Menu(localState, "Admin Tools", [
        //         //todo-2: disabled during mongo conversion
        //         //new MenuItem("Set Node A", view.setCompareNodeA, () => { return state.isAdminUser && highlightNode != null }, () => { return state.isAdminUser }), //
        //         //new MenuItem("Compare as B (to A)", view.compareAsBtoA, //
        //         //    () => { return state.isAdminUser && highlightNode != null }, //
        //         //    () => { return state.isAdminUser }, //
        //         //    true
        //         //), //
        //     ]));
        // }

        // WORK IN PROGRESS (do not delete)
        // let fileSystemMenuItems = //
        //     menuItem("Reindex", "fileSysReindexButton", "systemfolder.reindex();") + //
        //     menuItem("Search", "fileSysSearchButton", "systemfolder.search();"); //
        //     //menuItem("Browse", "fileSysBrowseButton", "systemfolder.browse();");
        // let fileSystemMenu = makeTopLevelMenu("FileSys", fileSystemMenuItems);

        /* This was experimental, and does work perfectly well (based on a small aount of testing done).
          These menu items can save a node subgraph to IPFS files (MFS) and then restore those nodes back
          from that tree branch. But the feature is not currently needed or enabled.
          */
        if (ast.isAdminUser) {
            // DO NOT DELETE: Work in Progress....
            // children.push(new Menu(localState, "IPFS", [
            //     new MenuItem("Sync: To IPFS", () => S.nodeUtil.publishNodeToIpfs(hltNode), //
            //         state.isAdminUser || (S.user.isTestUserAccount(state) && selNodeIsMine)), //
            //     new MenuItem("Sync: From IPFS", () => S.nodeUtil.loadNodeFromIpfs(hltNode), //
            //         state.isAdminUser || (S.user.isTestUserAccount(state) && selNodeIsMine)) //
            // ]));
        }

        this.setChildren(children);
        return true;
    }

    // These are defined externally in config-text.yaml
    // helpMenuItems = (): Div[] => {
    //     const ast = getAs();
    //     const items: Div[] = [];
    //     if (ast.config.menu?.help) {
    //         for (const menuItem of ast.config.menu.help) {
    //             this.appendMenuItemFromConfig(menuItem, items);
    //         }
    //     }
    // }
    //     return items;
    // }

    getSystemFolderLinks = (): MenuItem[] => {
        const ret: MenuItem[] = [];
        if (!S.quanta.cfg.systemFolderLinks) return ret;
        for (const menuItem of S.quanta.cfg.systemFolderLinks) {
            this.appendMenuItemFromConfig(menuItem, ret);
        }
        return ret;
    }

    appendMenuItemFromConfig = (cfgItem: any, items: CompIntf[]): void => {
        if (cfgItem.name === "separator") {
            items.push(new MenuItemSeparator());
        }
        else {
            const link: string = cfgItem.link;
            let func: Function = null;

            if (link) {
                // allows ability to select a tab
                if (link.startsWith("tab:")) {
                    const tab = link.substring(4);

                    /* special case for feed tab */
                    if (tab === C.TAB_FEED) {
                        func = S.nav.messagesFediverse;
                    }
                    else {
                        func = () => S.tabUtil.selectTab(tab);
                    }
                }
                // covers http and https
                else if (link.startsWith("http")) {
                    func = () => window.open(link);
                }
                // named nodes like ":myName"
                else {
                    func = () => S.nav.openContentNode(link, true);
                }
            }

            items.push(new MenuItem(cfgItem.name, func));
        }
    }
}
