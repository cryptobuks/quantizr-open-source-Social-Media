import { dispatch, getAs, promiseDispatch, StateModFunc } from "./AppContext";
import { AppState } from "./AppState";
import { Comp } from "./comp/base/Comp";
import { TabPanel } from "./comp/TabPanel";
import { Constants as C } from "./Constants";
import { ConfirmDlg } from "./dlg/ConfirmDlg";
import { EditBlockedWordsDlg } from "./dlg/EditBlockedWordsDlg";
import { EditNodeDlg } from "./dlg/EditNodeDlg";
import { EditTagsDlg } from "./dlg/EditTagsDlg";
import { ExportDlg } from "./dlg/ExportDlg";
import { SharingDlg } from "./dlg/SharingDlg";
import { UploadFromFileDropzoneDlg } from "./dlg/UploadFromFileDropzoneDlg";
import { FullScreenType } from "./Interfaces";
import { TabIntf } from "./intf/TabIntf";
import * as J from "./JavaIntf";
import { S } from "./Singletons";
import { FeedTab } from "./tabs/data/FeedTab";
import { MainTab } from "./tabs/data/MainTab";
import { DocumentResultSetView } from "./tabs/DocumentResultSetView";

export class Edit {
    showReadOnlyProperties: boolean = false;

    editHashtags = async () => {
        const dlg = new EditTagsDlg();
        await dlg.open();
    }

    editBlockedWords = async () => {
        const dlg = new EditBlockedWordsDlg();
        await dlg.open();
    }

    openImportDlg = (): any => {
        const node = S.nodeUtil.getHighlightedNode();
        if (!node) {
            S.util.showMessage("No node is selected.", "Warning");
            return;
        }

        const dlg = new UploadFromFileDropzoneDlg(node.id, "", false, null, true, true, () => {
            S.view.jumpToId(node.id);
        });
        dlg.open();
    }

    openExportDlg = (): any => {
        const node = S.nodeUtil.getHighlightedNode();
        if (node) {
            new ExportDlg(node).open();
        }
    }

    private insertBookResponse = (res: J.InsertBookResponse): any => {
        S.util.checkSuccess("Insert Book", res);

        S.view.refreshTree({
            nodeId: null,
            zeroOffset: true,
            renderParentIfLeaf: false,
            highlightId: null,
            forceIPFSRefresh: false,
            scrollToTop: true,
            allowScroll: true,
            setTab: true,
            forceRenderParent: false,
            jumpToRss: false
        });
        S.view.scrollToNode();
    }

    private joinNodesResponse = (res: J.JoinNodesResponse): any => {
        const ast = getAs();
        if (S.util.checkSuccess("Join node", res)) {
            S.nodeUtil.clearSelNodes();
            S.view.refreshTree({
                nodeId: ast.node.id,
                zeroOffset: false,
                renderParentIfLeaf: false,
                highlightId: null,
                forceIPFSRefresh: false,
                scrollToTop: false,
                allowScroll: true,
                setTab: true,
                forceRenderParent: false,
                jumpToRss: false
            });
        }
    }

    public initNodeEditResponse = async (res: J.InitNodeEditResponse, forceUsePopup: boolean, encrypt: boolean,
        showJumpButton: boolean, replyToId: string) => {
        const ast = getAs();
        if (S.util.checkSuccess("Editing node", res)) {
            if (ast.mobileMode) forceUsePopup = true;

            const editingAllowed = this.isEditAllowed(res.nodeInfo);
            if (editingAllowed) {

                // these conditions determine if we want to run editing in popup, instead of inline in the page.
                let editInPopup = forceUsePopup || ast.mobileMode ||
                    // node not found on tree.
                    (!S.nodeUtil.displayingOnTree(res.nodeInfo.id) &&
                        !S.nodeUtil.displayingOnTree(S.quanta.newNodeTargetId)) ||
                    // not currently viewing tree
                    ast.activeTab !== C.TAB_MAIN ||
                    S.util.fullscreenViewerActive();

                if (ast.activeTab === C.TAB_DOCUMENT || //
                    ast.activeTab === C.TAB_SEARCH || //
                    ast.activeTab === C.TAB_TIMELINE) {
                    editInPopup = true;
                }

                /* If we're editing on the feed tab, we set the 'state.editNode' which makes the gui know to render
                the editor at that place rather than opening a popup now */
                if (!editInPopup && ast.activeTab === C.TAB_FEED) {
                    dispatch("StartEditingInFeed", s => {
                        s.editNodeReplyToId = replyToId;
                        s.editNodeOnTab = s.mobileMode ? null : ast.activeTab;
                        s.editNode = res.nodeInfo;
                        s.editShowJumpButton = showJumpButton;
                        s.editEncrypt = encrypt;
                    });
                }
                /* Either run the node editor as a popup or embedded, depending on whether we have a fullscreen
                calendar up and wether we're on the main tab, etc */
                else if (editInPopup) {
                    await promiseDispatch("startEditing", s => {
                        s.editNode = res.nodeInfo;
                    });
                    const dlg = new EditNodeDlg(encrypt, showJumpButton, null);
                    dlg.open();
                } else {
                    dispatch("startEditing", s => {
                        s.editNode = res.nodeInfo;
                        s.editNodeOnTab = s.mobileMode ? null : ast.activeTab;
                        s.editShowJumpButton = showJumpButton;
                        s.editEncrypt = encrypt;
                    });
                }
            } else {
                S.util.showMessage("Editing not allowed on node.", "Warning");
            }
        }
    }

    /* nodeId is optional and represents what to highlight after the paste if anything */
    private moveNodesResponse = (res: J.MoveNodesResponse, nodeId: string, pasting: boolean) => {
        if (S.util.checkSuccess("Move nodes", res)) {

            // todo-1: We DO need to do something to indicate to user that signatures got removed, but this way
            // ended up being an annoyance when I'm doing a lot of editing back to back pasting every minute or two.
            // if (res.signaturesRemoved) {
            //     setTimeout(() => {
            //         S.util.showMessage("Signatures on these nodes were removed, because signature is dependent upon path location.", "Signatures");
            //     }, 1000);
            // }

            dispatch("SetNodesToMove", s => {
                s.nodesToMove = null;
            });

            // if pasting do a kind of refresh which will maintain us at the same page parent.
            if (pasting) {
                S.view.refreshTree({
                    nodeId: null,
                    zeroOffset: false,
                    renderParentIfLeaf: false,
                    highlightId: nodeId,
                    forceIPFSRefresh: false,
                    scrollToTop: false,
                    allowScroll: true,
                    setTab: true,
                    forceRenderParent: false,
                    jumpToRss: false
                });
            }
            else {
                S.view.jumpToId(nodeId);
            }
        }
    }

    private setNodePositionResponse = (res: J.SetNodePositionResponse, id: string) => {
        if (S.util.checkSuccess("Change node position", res)) {
            S.view.jumpToId(id, true);

            S.util.notifyNodeMoved();
        }
    }

    /* returns true if we are admin or else the owner of the node */
    isEditAllowed = (node: any): boolean => {
        const ast = getAs();
        if (!node) return false;
        if (ast.isAdminUser) return true;

        // if no owner treat as if admin owns
        return ast.userName === (node.owner || J.PrincipalName.ADMIN);
    }

    /*
    * nodeInsertTarget holds the node that was clicked on at the time the insert was requested, and
    * is sent to server for ordinal position assignment of new node. Also if this var is null, it indicates we are
    * creating in a 'create under parent' mode, versus non-null meaning 'insert inline' type of insert.
    */
    startEditingNewNode = async (typeName: string, createAtTop: boolean, parentNode: J.NodeInfo,
        nodeInsertTarget: J.NodeInfo, ordinalOffset: number) => {
        if (!S.props.isWritableByMe(parentNode)) {
            console.log("Rejecting request to edit. Not authorized");
            return;
        }

        if (S.util.ctrlKeyCheck()) {
            let blob = null;
            const clipboardText = await (navigator as any)?.clipboard?.readText();
            if (!clipboardText) {
                blob = await S.util.readClipboardFile();
                if (blob) {
                    EditNodeDlg.pendingUploadFile = blob;
                }
            }

            if (nodeInsertTarget) {
                const res = await S.rpcUtil.rpc<J.InsertNodeRequest, J.InsertNodeResponse>("insertNode", {
                    pendingEdit: false,
                    parentId: parentNode.id,
                    targetOrdinal: nodeInsertTarget.ordinal + ordinalOffset,
                    newNodeName: "",
                    typeName: typeName || J.NodeType.NONE,
                    initialValue: clipboardText
                });
                if (blob) {
                    this.insertNodeResponse(res);
                }
            } else {
                const res = await S.rpcUtil.rpc<J.CreateSubNodeRequest, J.CreateSubNodeResponse>("createSubNode", {
                    pendingEdit: false,
                    nodeId: parentNode.id,
                    newNodeName: "",
                    typeName: typeName || J.NodeType.NONE,
                    createAtTop,
                    content: clipboardText,
                    typeLock: false,
                    properties: null,
                    shareToUserId: null,
                    boostTarget: null,
                    fediSend: false,
                    boosterUserId: null,
                    reply: false
                });
                if (blob) {
                    this.createSubNodeResponse(res, false, null);
                }
            }

            if (!blob) {
                S.quanta.refresh();
            }
        }
        else {
            if (nodeInsertTarget) {
                const res = await S.rpcUtil.rpc<J.InsertNodeRequest, J.InsertNodeResponse>("insertNode", {
                    pendingEdit: true,
                    parentId: parentNode.id,
                    targetOrdinal: nodeInsertTarget.ordinal + ordinalOffset,
                    newNodeName: "",
                    typeName: typeName || J.NodeType.NONE,
                    initialValue: ""
                });
                this.insertNodeResponse(res);
            } else {
                const res = await S.rpcUtil.rpc<J.CreateSubNodeRequest, J.CreateSubNodeResponse>("createSubNode", {
                    pendingEdit: true,
                    nodeId: parentNode.id,
                    newNodeName: "",
                    typeName: typeName || J.NodeType.NONE,
                    createAtTop,
                    content: null,
                    typeLock: false,
                    properties: null,
                    shareToUserId: null,
                    boostTarget: null,
                    fediSend: false,
                    boosterUserId: null,
                    reply: false
                });
                this.createSubNodeResponse(res, false, null);
            }
        }
    }

    insertNodeResponse = (res: J.InsertNodeResponse) => {
        if (S.util.checkSuccess("Insert node", res)) {
            S.nodeUtil.highlightNode(res.newNode, false, getAs());
            this.runEditNode(null, res.newNode.id, false, false, false, null, false);
        }
    }

    createSubNodeResponse = (res: J.CreateSubNodeResponse, forceUsePopup: boolean, replyToId: string) => {
        // console.log("createSubNode Res: " + S.util.prettyPrint(res));
        if (S.util.checkSuccess("Create subnode", res)) {
            if (!res.newNode) {
                S.quanta.refresh();
            }
            else {
                this.runEditNode(null, res.newNode.id, forceUsePopup, res.encrypt, false, replyToId, //
                    false);
            }
        }
    }

    saveNodeResponse = async (node: J.NodeInfo, res: J.SaveNodeResponse,
        newNodeTargetId: string, newNodeTargetOffset: number) => {
        const ast = getAs();
        if (S.util.checkSuccess("Save node", res)) {
            await this.distributeKeys(node, res.aclEntries);

            // if on feed tab, and it became dirty while we were editing then refresh it.
            // todo-2: shouldn't we do this regardless of which tab is active?
            if (ast.activeTab === C.TAB_FEED) {
                if (FeedTab.inst?.props?.feedDirtyList) {
                    for (const node of FeedTab.inst.props.feedDirtyList) {
                        // console.log("Force Feed: " + node.content);
                        S.push.forceFeedItem(node);
                    }
                    FeedTab.inst.props.feedDirtyList = null;

                    // all the data in feedData will have been updated by forceFeedItem so force react to render now.
                    dispatch("ForceFeedResults", s => { });
                }
            }

            // It's possible to end up editing a node that's not even on the page, or a child of a node on the page,
            // and so before refreshing the screen we check for that edge case.
            const parentPath = S.props.getParentPath(node);
            if (!parentPath) return;

            const newNode = res.node;
            if (!newNodeTargetId) {
                promiseDispatch("nodeUpdated", s => {
                    // if the node is our page parent (page root)
                    if (newNode.id === s.node?.id) {
                        // preserve the children, when updating the root node, because they will not have been obtained
                        // due to the 'singleNode=true' in the request
                        newNode.children = s.node.children;
                        s.node = newNode;
                    }

                    ast.tabData.forEach(td => td.replaceNode(s, newNode));
                });
            }

            S.util.updateNodeHistory(newNode, false);

            if (ast.activeTab === C.TAB_MAIN) {
                // Inject the new node right into the page children
                if (newNodeTargetId) {
                    await this.injectNewNodeIntoChildren(newNode, newNodeTargetId, newNodeTargetOffset);
                }
                // any kind of insert that's not a new node injected into the page ends up here.
                else {
                    // Note the special case here for bookmark. We never want to jump to a bookmark just
                    // because it got updated. That would take us away from whatever we're working on and
                    // is never right.
                    if (node.type !== J.NodeType.BOOKMARK && !S.nodeUtil.displayingOnTree(node.id)) {
                        S.view.jumpToId(node.id);
                    }
                }
            }

            if (ast.fullScreenConfig.type === FullScreenType.CALENDAR) {
                S.render.showCalendar(ast.fullScreenConfig.nodeId);
            }
        }
    }

    injectNewNodeIntoChildren = (newNode: J.NodeInfo, newNodeTargetId: string, newNodeTargetOffset: number): Promise<void> => {
        // we return the promise from the dispatch and to not wait for it here.
        return promiseDispatch("InjectNewNodeIntoChildren", s => {
            if (s.node.children) {
                const newChildren: J.NodeInfo[] = [];

                // we'll be renumbering ordinals so use this to keep track, by starting at whatever
                // the first child was at before the insert
                let ord = s.node.children[0].logicalOrdinal;

                // build newChildren by inserting the 'newNode' into it's proper place into the children array.
                s.node.children.forEach(child => {
                    // offset==0 means insert above.
                    if (newNodeTargetId === child.id && newNodeTargetOffset === 0) {
                        newNode.logicalOrdinal = ord++;
                        newChildren.push(newNode);
                    }
                    child.logicalOrdinal = ord++;
                    newChildren.push(child);

                    // offset==0 means insert below.
                    if (newNodeTargetId === child.id && newNodeTargetOffset === 1) {

                        // if node was the lastChild, we have a new last child.
                        if (child.lastChild) {
                            child.lastChild = false;
                            newNode.lastChild = true;
                        }

                        newNode.logicalOrdinal = ord++;
                        newChildren.push(newNode);
                    }
                });
                s.node.children = newChildren;
            }
            else {
                s.node.children = [newNode];
            }
        });
    }

    // NOT CURRENTLY USED (but let's keep for future possible needs)
    //
    // refreshNodeFromServer = async (nodeId: string, newNodeTargetId: string): Promise<J.NodeInfo> => {
    //     return new Promise<J.NodeInfo>(async (resolve, reject) => {
    //         const ast = getAst();

    //         const res = await S.rpcUtil.rpc<J.RenderNodeRequest, J.RenderNodeResponse>("renderNode", {
    //             nodeId,
    //             upLevel: false,
    //             siblingOffset: 0,
    //             renderParentIfLeaf: false,
    //             forceRenderParent: false,
    //             offset: 0,
    //             goToLastPage: false,
    //             forceIPFSRefresh: false,
    //             singleNode: true,
    //             parentCount: ast.userPrefs.showParents ? 1 : 0,
    //             jumpToRss: false
    //         });

    //         if (!res || !res.node) {
    //             resolve(null);
    //             return;
    //         }

    //         resolve(res.node);

    //         // we only need to update/dispatch here if we're NOT doing an 'insert into page' type insert
    //         if (!newNodeTargetId) {
    //             await promiseDispatch("RefreshNodeFromServer", s => {
    //                 // if the node is our page parent (page root)
    //                 if (res.node.id === s.node?.id) {
    //                     // preserve the children, when updating the root node, because they will not have been obtained
    //                     // due to the 'singleNode=true' in the request
    //                     res.node.children = s.node.children;
    //                     s.node = res.node;
    //                 }

    //                 // make all tabs update their copy of the node of they have it
    //                 ast.tabData.forEach(td => td.replaceNode(s, res.node));
    //             });
    //         }
    //     });
    // }

    distributeKeys = async (node: J.NodeInfo, aclEntries: J.AccessControlInfo[]) => {
        if (!aclEntries || !S.props.isEncrypted(node)) {
            return;
        }

        for (const ac of aclEntries) {
            await this.addCipherKeyToNode(node, ac.publicKey, ac.principalNodeId);
        }
    }

    setRssHeadlinesOnly = async (s: AppState, val: boolean) => {
        S.util.saveUserPrefs(s => s.userPrefs.rssHeadlinesOnly = val);
    }

    setMainPanelCols = (val: number) => {
        setTimeout(() => {
            if (val < 4) val = 4;
            if (val > 8) val = 8;
            S.util.saveUserPrefs(s => s.userPrefs.mainPanelCols = val);
        }, 100);
    };

    // saveTabsTopmostVisibie and scrollTabsTopmostVisible should always be called as a pair
    saveTabsTopmostVisible = async (): Promise<boolean> => {
        let doScrolling = false;
        const ast = getAs();

        // in this loop record the currently topmost visible element in each tab, so we can scroll
        // those back it view after doing some change to the DOM that will potentially cause the page
        // to jump to a different effective scroll position.
        for (const data of ast.tabData) {
            // Warning: Uninitialized tabs will have 'scrollPos==undefined' here, so we check for that case, because
            // otherwise it will get interpreted as a number
            // We do nothing if user hasn't scrolled down enough to loose their place when the screen rerenders.
            if (data.scrollPos == null || data.scrollPos === undefined || data.scrollPos < window.innerHeight / 2) {
                // do nothing, if window isn't scrolled hafway at least
            }
            else {
                doScrolling = true;
                // NOTE: This is tricky here, but correct. The first 'id' is an ID, and the second one is a "class" (passed as arguments
                // into findFirstVisibleElm), and this is not a bug. It's just a coincidence that 'data.id' is the correct thing
                // to use for both parameters per what's in the DOM.
                const elm = S.util.findFirstVisibleElm(data.id, data.id);
                data.topmostVisibleElmId = elm?.id;
            }
        }

        if (doScrolling) {
            await TabPanel.inst.setVisibility(false);
        }
        return doScrolling;
    }

    // saveTabsTopmostVisibie and scrollTabsTopmostVisible should always be called as a pair
    scrollTabsTopmostVisible = () => {
        // this timer is because all scrolling in browser needs to be delayed or we can have failures.
        setTimeout(async () => {
            // scroll into view whatever was the topmost item
            for (const data of getAs().tabData) {
                if (data.topmostVisibleElmId) {
                    // we have to lookup the element again, because our DOM will have rendered and we will likely
                    // have a new actual element.
                    const elm = document.getElementById(data.topmostVisibleElmId);
                    if (elm) {
                        // scrollIntoView works, but is off a bit because we have a 'sticky' header covering up
                        // part of the window making scrollIntoView appaer not to work.
                        // elm.scrollIntoView(true);
                        data.inst.scrollToElm(elm);
                    }
                }
            }
            setTimeout(async () => {
                await TabPanel.inst.setVisibility(true);
            }, 250);
        }, 250);
    }

    // WARNING: This func is expected to NOT alter that the active tab is!
    runScrollAffectingOp = async (func: Function) => {
        const doScrolling = await this.saveTabsTopmostVisible();
        if (doScrolling) {
            // turn off Comp stuff so it doesn't interfere with what we're about to do with scrolling.
            Comp.allowScrollSets = false;
        }
        await func();

        if (doScrolling) {
            this.scrollTabsTopmostVisible();
            Comp.allowScrollSets = true;
        }
    }

    // We allow a function (func) to run here in such a way that the scroll positions of every tab panel are
    // maintained so the user doesn't loose their place after the screen completely updates.
    setUserPreferenceVal = (mod: StateModFunc) => {
        this.runScrollAffectingOp(async () => {
            S.util.saveUserPrefs(mod);
        });
    }

    setEditMode = async (val: boolean) => {
        this.setUserPreferenceVal(s => s.userPrefs.editMode = val);
    }

    toggleEditMode = async () => {
        this.setUserPreferenceVal(s => s.userPrefs.editMode = !s.userPrefs.editMode);
    }

    setShowMetaData = (val: boolean) => {
        this.setUserPreferenceVal(s => s.userPrefs.showMetaData = val);
    }

    toggleShowMetaData = () => {
        this.setUserPreferenceVal(s => s.userPrefs.showMetaData = !s.userPrefs.showMetaData);
    }

    toggleNsfw = async () => {
        return S.util.saveUserPrefs(s => s.userPrefs.nsfw = !s.userPrefs.nsfw);
    }

    // #add-prop
    setAutoRefreshFeed = async (autoRefreshFeed: boolean) => {
        return S.util.saveUserPrefs(s => s.userPrefs.autoRefreshFeed = autoRefreshFeed);
    }

    toggleShowParents = async () => {
        await S.util.saveUserPrefs(s => s.userPrefs.showParents = !s.userPrefs.showParents);
        S.quanta.refresh();
    }

    setShowComments = async (showReplies: boolean): Promise<void> => {
        return S.util.saveUserPrefs(s => s.userPrefs.showReplies = showReplies);
    }

    setShowReplies = async (showReplies: boolean) => {
        await S.util.saveUserPrefs(s => s.userPrefs.showReplies = showReplies);

        const ast = getAs();
        // todo-1: we need a PubSub broadcast event for "SHOW_REPLIES_CHANGED" that we can send out to all tabs.
        if (ast.activeTab === C.TAB_MAIN) {
            S.quanta.refresh();
        }
        else if (ast.activeTab === C.TAB_DOCUMENT) {
            const data: TabIntf = S.tabUtil.getAppTabData(C.TAB_DOCUMENT);
            if (data) {
                S.srch.showDocument(data.props.node, false);
            }
        }
        else {
            // update render state (using local state), this way if we're not refreshing the tree.
            dispatch("setShowReplies", (s) => {
                s.userPrefs.showReplies = ast.userPrefs.showReplies;
            });
        }
    }

    moveNodeUp = async (evt: Event) => {
        let id = S.util.allowIdFromEvent(evt, null);
        if (!id) {
            const selNode = S.nodeUtil.getHighlightedNode();
            id = selNode?.id;
        }

        if (id) {
            const res = await S.rpcUtil.rpc<J.SetNodePositionRequest, J.SetNodePositionResponse>("setNodePosition", {
                nodeId: id,
                targetName: "up"
            });
            this.setNodePositionResponse(res, id);
        }
    }

    moveNodeDown = async (evt: Event) => {
        let id = S.util.allowIdFromEvent(evt, null);
        if (!id) {
            const selNode = S.nodeUtil.getHighlightedNode();
            id = selNode?.id;
        }

        if (id) {
            const res = await S.rpcUtil.rpc<J.SetNodePositionRequest, J.SetNodePositionResponse>("setNodePosition", {
                nodeId: id,
                targetName: "down"
            });
            this.setNodePositionResponse(res, id);
        }
    }

    moveNodeToTop = async () => {
        const selNode = S.nodeUtil.getHighlightedNode();
        const id = selNode?.id;

        if (id) {
            const res = await S.rpcUtil.rpc<J.SetNodePositionRequest, J.SetNodePositionResponse>("setNodePosition", {
                nodeId: id,
                targetName: "top"
            });
            this.setNodePositionResponse(res, id);
        }
    }

    moveNodeToBottom = async () => {
        const selNode = S.nodeUtil.getHighlightedNode();
        const id = selNode?.id;

        if (id) {
            const res = await S.rpcUtil.rpc<J.SetNodePositionRequest, J.SetNodePositionResponse>("setNodePosition", {
                nodeId: id,
                targetName: "bottom"
            });
            this.setNodePositionResponse(res, id);
        }
    }

    getFirstChildNode = (): any => {
        const ast = getAs();
        if (!ast.node || !ast.node.children || ast.node.children.length === 0) return null;
        return ast.node.children[0];
    }

    getLastChildNode = (): J.NodeInfo => {
        const ast = getAs();
        if (!ast.node || !ast.node.children || ast.node.children.length === 0) return null;
        return ast.node.children[ast.node.children.length - 1];
    }

    checkEditPending = (): boolean => {
        const ast = getAs();

        // state.editNode holds non-null always whenever there is editing underway.
        if (ast.editNode) {
            S.util.showMessage("You're already editing a node. Finish that edit first. Tip: Use `Menu -> Edit -> Continue Editing` if you forgot which node you're editing.", "Warning");
            return true;
        }
        return false;
    }

    runEditNodeByClick = (evt: Event, id: string) => {
        if (this.checkEditPending()) return;

        id = S.util.allowIdFromEvent(evt, id);

        // we set noScrollToId just to block the future attempt (one time) to
        // scroll to this, because this is a hint telling us we are ALREADY
        // scrolled to this ID so any scrolling will be unnecessary
        S.quanta.noScrollToId = id;
        this.runEditNode(null, id, false, false, false, null, false);

        // it's safest and best to just disable scrolling for a couple of seconds during which editing is being initiated.
        setTimeout(() => {
            S.quanta.noScrollToId = null;
        }, 2000);
    }

    /* This can run as an actuall click event function in which only 'evt' is non-null here */
    runEditNode = async (overrideContent: string, id: string, forceUsePopup: boolean, encrypt: boolean,
        showJumpButton: boolean, replyToId: string, editMyFriendNode: boolean) => {
        if (S.quanta.configRes.requireCrypto && !S.crypto.avail) {
            S.util.showMessage("Crypto support not available", "Warning");
            return;
        }

        if (!id) {
            const node = S.nodeUtil.getHighlightedNode();
            if (node) {
                id = node.id;
            }
        }

        if (!id) {
            S.util.showMessage("Unknown nodeId in editNodeClick: ", "Warning");
            return;
        }

        const res = await S.rpcUtil.rpc<J.InitNodeEditRequest, J.InitNodeEditResponse>("initNodeEdit", {
            nodeId: id,
            editMyFriendNode
        });

        if (res.nodeInfo && overrideContent) {
            res.nodeInfo.content = overrideContent;
        }

        this.initNodeEditResponse(res, forceUsePopup, encrypt, showJumpButton, replyToId);
    }

    insertNode = (id: string, typeName: string, ordinalOffset: number, ast?: AppState) => {
        if (this.checkEditPending()) return;

        ast = ast || getAs();
        if (!ast.node || !ast.node.children) return;

        /*
         * We get the node selected for the insert position by using the uid if one was passed in or using the
         * currently highlighted node if no uid was passed.
         */
        let node: J.NodeInfo = null;
        if (!id) {
            node = S.nodeUtil.getHighlightedNode();
        } else {
            node = MainTab.inst?.findNode(id, ast);
        }

        if (node) {
            S.quanta.newNodeTargetId = id;
            S.quanta.newNodeTargetOffset = ordinalOffset;
            this.startEditingNewNode(typeName, false, ast.node, node, ordinalOffset);
        }
    }

    newSubNode = async (evt: Event, id: string) => {
        if (this.checkEditPending()) return;

        id = S.util.allowIdFromEvent(evt, id);
        const ast = getAs();

        if (S.util.ctrlKeyCheck()) {
            this.saveClipboardToChildNode(id);
        }
        else {
            this.createSubNode(id, null, true, ast.node);
        }
    }

    createSubNode = (id: any, typeName: string, createAtTop: boolean, parentNode: J.NodeInfo): any => {
        const ast = getAs();
        /*
         * If no uid provided we deafult to creating a node under the currently viewed node (parent of current page), or any selected
         * node if there is a selected node.
         */
        if (!id) {
            const node = S.nodeUtil.getHighlightedNode();
            if (node) {
                parentNode = node;
            }
            else {
                if (!ast.node || !ast.node.children) return null;
                parentNode = ast.node;
            }
        } else {
            parentNode = MainTab.inst?.findNode(id, ast);
            if (!parentNode) {
                return;
            }
        }

        this.startEditingNewNode(typeName, createAtTop, parentNode, null, 0);
    }

    // todo-1: method is not used?
    selectAllNodes = async () => {
        const highlightNode = S.nodeUtil.getHighlightedNode();
        const res = await S.rpcUtil.rpc<J.SelectAllNodesRequest, J.SelectAllNodesResponse>("selectAllNodes", {
            parentNodeId: highlightNode.id
        });
        S.nodeUtil.selectAllNodes(res.nodeIds);
    }

    clearInbox = async () => {
        const ast = getAs();
        S.nodeUtil.clearSelNodes();

        const dlg = new ConfirmDlg("Permanently delete the nodes in your Inbox", "Clear Inbox",
            "btn-danger", "alert alert-danger");
        await dlg.open();
        if (dlg.yes) {
            await S.rpcUtil.rpc<J.DeleteNodesRequest, J.DeleteNodesResponse>("deleteNodes", {
                nodeIds: ["~" + J.NodeType.INBOX],
                childrenOnly: true,
                bulkDelete: false
            });
            S.nav.openContentNode(ast.userProfile.userNodeId, false);
        }
    }

    subGraphHash = async () => {
        const node = S.nodeUtil.getHighlightedNode();

        if (!node) {
            S.util.showMessage("No node is selected.", "Warning");
            return;
        }

        S.rpcUtil.rpc<J.SubGraphHashRequest, J.SubGraphHashResponse>("subGraphHash", {
            nodeId: node.id,
            recursive: true
        }).then((res: J.SubGraphHashResponse) => {
            if (res.success) {
                S.util.showMessage(res.message);
            }
            else {
                S.util.showMessage("operation failed.");
            }
        });

        S.util.showMessage("Request sumitted. Check the node for property " + J.NodeProp.SUBGRAPH_HASH);
    }

    joinNodes = async () => {
        const selNodesArray = S.nodeUtil.getSelNodeIdsArray();
        if (!selNodesArray || selNodesArray.length === 0) {
            S.util.showMessage("Select some nodes to join.", "Warning");
            return;
        }

        const confirmMsg = "Join " + selNodesArray.length + " node(s) ?";
        const dlg = new ConfirmDlg(confirmMsg, "Confirm Join");
        await dlg.open();
        if (dlg.yes) {
            const res = await S.rpcUtil.rpc<J.JoinNodesRequest, J.JoinNodesResponse>("joinNodes", {
                nodeIds: selNodesArray
            });
            this.joinNodesResponse(res);
        }
    }

    /*
    * Deletes all nodes owned by you but NOT rooted in your own account root.
    */
    bulkDelete = async () => {
        const confirmMsg = "Bulk Delete all your nodes *not* rooted in your account?";
        const dlg = new ConfirmDlg(confirmMsg, "Confirm Delete",
            "btn-danger", "alert alert-danger");
        await dlg.open();
        if (dlg.yes) {
            const res = await S.rpcUtil.rpc<J.DeleteNodesRequest, J.DeleteNodesResponse>("deleteNodes", {
                nodeIds: null,
                childrenOnly: false,
                bulkDelete: true
            });
            S.util.showMessage(res.message, "Message");
        }
    }

    /*
     * Deletes the selNodesArray items, and if none are passed then we fall back to using whatever the user
     * has currenly selected (via checkboxes)
     */
    deleteSelNodes = async (evt: Event = null, id: string = null) => {
        id = S.util.allowIdFromEvent(evt, id);

        // if a nodeId was specified we use it as the selected node to delete
        if (id) {
            await promiseDispatch("SelectNode", s => {
                S.nav.setNodeSel(true, id, s);
            });
        }

        const ast = getAs();
        // note: the setNodeSel above isn't causing this to get anything here
        const selNodesArray: string[] = S.nodeUtil.getSelNodeIdsArray();

        if (!selNodesArray || selNodesArray.length === 0) {
            S.util.showMessage("Select some nodes to delete.", "Warning");
            return;
        }

        if (selNodesArray.find(id => id === ast.userProfile?.userNodeId)) {
            S.util.showMessage("You can't delete your account root node!", "Warning");
            return;
        }

        let deletedPageNode: boolean = false;
        if (selNodesArray.find(id => id === ast.node?.id)) {
            deletedPageNode = true;
        }

        const confirmMsg = "Delete " + selNodesArray.length + " node(s) ?";
        const dlg = new ConfirmDlg(confirmMsg, "Confirm Delete " + selNodesArray.length,
            "btn-danger", "alert alert-danger");
        await dlg.open();
        if (dlg.yes) {
            const res = await S.rpcUtil.rpc<J.DeleteNodesRequest, J.DeleteNodesResponse>("deleteNodes", {
                nodeIds: selNodesArray,
                childrenOnly: false,
                bulkDelete: false
            });

            this.removeNodesFromHistory(selNodesArray);
            this.removeNodesFromCalendarData(selNodesArray);

            /* Node: state.node can be null if we've never been to the tree view yet */
            if (ast.node && S.util.checkSuccess("Delete node", res)) {
                S.util.notifyNodeDeleted();

                if (ast.node.children) {
                    ast.node.children = ast.node.children.filter(child => !selNodesArray.find(id => id === child?.id));
                }

                this.afterDeleteCleanup(selNodesArray);
                if (ast.activeTab === C.TAB_MAIN && deletedPageNode) {
                    // todo-1: Improvement here would be to try to go to the parent of the node, so we could pass
                    // the deletedPageNode indicator to the deleteNodes endpoint and let that signal to it
                    // to pass back to us the ID of the parent node or null if we don't have access to it, but for
                    // now if user deletes their page root node we take them to their account node.
                    S.nav.navToMyAccntRoot();
                }
                else if (ast.activeTab === C.TAB_MAIN && ast.node.children.length === 0) {
                    S.view.jumpToId(ast.node.id);
                }
                else {
                    getAs().node.children = ast.node.children;
                }
            }
        }
    }

    afterDeleteCleanup = (selNodesArray: string[]) => {
        dispatch("AfterDeleteCleanup", s => {
            // remove this node from all data from all the tabs, so they all refresh without
            // the deleted node without being queries from the server again.
            selNodesArray.forEach(id => {
                S.srch.removeNodeById(id, s);
            });
            s.selectedNodes.clear();
        });
    }

    /* Updates 'nodeHistory' when nodes are deleted */
    removeNodesFromHistory = (selNodesArray: string[]) => {
        if (!selNodesArray) return;
        dispatch("removeNodesFromHistory", s => {
            selNodesArray.forEach(id => {
                // remove any top level history item that matches 'id'
                s.nodeHistory = s.nodeHistory.filter(h => h.id !== id);
            });
        });
    }

    removeNodesFromCalendarData = (selNodesArray: string[]) => {
        dispatch("UpdateCalendarData", s => {
            selNodesArray.forEach(id => {
                if (!s.calendarData) return;
                // todo-1: can we put typesafety back on this 'item' here?
                s.calendarData = s.calendarData.filter((item: any) => item.id !== id);
            });
        });
    }

    undoCutSelNodes = () => {
        dispatch("SetNodesToMove", s => {
            s.nodesToMove = null;
        });
    }

    cutSelNodes = (evt: Event) => {
        const id = S.util.allowIdFromEvent(evt, null);

        dispatch("SetNodesToMove", s => {
            S.nav.setNodeSel(true, id, s);
            s.nodesToMove = S.nodeUtil.getSelNodeIdsArray();
            s.selectedNodes.clear();
        });
    }

    pasteSelNodesInside = (evt: Event, id: string) => {
        id = S.util.allowIdFromEvent(evt, id);
        const ast = getAs();
        this.pasteSelNodes(id, "inside", ast);
    }

    // location=inside | inline | inline-above (todo-2: put in java-aware enum)
    pasteSelNodes = async (nodeId: string, location: string, ast?: AppState) => {
        ast = ast || getAs();
        /*
         * For now, we will just cram the nodes onto the end of the children of the currently selected
         * page (for the 'inside' option). Later on we can get more specific about allowing precise destination location for moved
         * nodes.
         */
        const res = await S.rpcUtil.rpc<J.MoveNodesRequest, J.MoveNodesResponse>("moveNodes", {
            targetNodeId: nodeId,
            nodeIds: ast.nodesToMove,
            location
        });
        this.moveNodesResponse(res, nodeId, true);
    }

    pasteSelNodes_InlineAbove = (evt: Event, id: string) => {
        id = S.util.allowIdFromEvent(evt, id);
        this.pasteSelNodes(id, "inline-above");
    }

    pasteSelNodes_Inline = (evt: Event, id: string) => {
        id = S.util.allowIdFromEvent(evt, id);
        this.pasteSelNodes(id, "inline");
    }

    insertBookWarAndPeace = async () => {
        const dlg = new ConfirmDlg("Warning: You should have an EMPTY node selected now, to serve as the root node of the book!",
            "Confirm");
        await dlg.open();
        if (dlg.yes) {
            /* inserting under whatever node user has focused */
            const node = S.nodeUtil.getHighlightedNode();

            if (!node) {
                S.util.showMessage("No node is selected.", "Warning");
            } else {
                const res = await S.rpcUtil.rpc<J.InsertBookRequest, J.InsertBookResponse>("insertBook", {
                    nodeId: node.id,
                    bookName: "War and Peace",
                    truncated: S.user.isTestUserAccount()
                });
                this.insertBookResponse(res);
            }
        }
    }

    saveClipboardToChildNode = async (parentId: string) => {
        let clipText: string = await (navigator as any)?.clipboard?.readText();
        if (clipText) {
            clipText = clipText.trim();
        }

        let blob = null;
        if (!clipText) {
            blob = await S.util.readClipboardFile();
            if (blob) {
                EditNodeDlg.pendingUploadFile = blob;
            }
        }

        if (!clipText && !blob) {
            S.util.flashMessage("Nothing saved clipboard is empty!", "Warning", true);
            return;
        }

        const res = await S.rpcUtil.rpc<J.CreateSubNodeRequest, J.CreateSubNodeResponse>("createSubNode", {
            pendingEdit: false,
            nodeId: parentId,
            newNodeName: "",
            typeName: J.NodeType.NONE,
            createAtTop: true,
            content: clipText,
            typeLock: false,
            properties: null,
            shareToUserId: null,
            boostTarget: null,
            fediSend: false,
            boosterUserId: null,
            reply: false
        });

        if (blob) {
            this.createSubNodeResponse(res, false, null);
        }
        else {
            setTimeout(() => {
                S.view.refreshTree({
                    nodeId: null,
                    zeroOffset: true,
                    renderParentIfLeaf: false,
                    highlightId: null,
                    forceIPFSRefresh: false,
                    scrollToTop: false,
                    allowScroll: true,
                    setTab: true,
                    forceRenderParent: false,
                    jumpToRss: false
                });
            }, 500);
        }
    }

    splitNode = async (node: J.NodeInfo, splitType: string, delimiter: string) => {
        node = node || S.nodeUtil.getHighlightedNode();

        if (!node) {
            S.util.showMessage("You didn't select a node to split.", "Warning");
            return;
        }

        const res = await S.rpcUtil.rpc<J.SplitNodeRequest, J.SplitNodeResponse>("splitNode", {
            splitType,
            nodeId: node.id,
            delimiter
        });
        this.splitNodeResponse(res);
    }

    splitNodeResponse = (res: J.SplitNodeResponse) => {
        if (S.util.checkSuccess("Split content", res)) {
            S.view.refreshTree({
                nodeId: null,
                zeroOffset: false,
                renderParentIfLeaf: false,
                highlightId: null,
                forceIPFSRefresh: false,
                scrollToTop: false,
                allowScroll: true,
                setTab: true,
                forceRenderParent: false,
                jumpToRss: false
            });
            S.view.scrollToNode();
        }
    }

    addBookmark = (node: J.NodeInfo) => {
        this.createNode(node, J.NodeType.BOOKMARK, true, true, null, null);
    }

    addLinkBookmark = async (content: any, audioUrl: string) => {
        const res = await S.rpcUtil.rpc<J.CreateSubNodeRequest, J.CreateSubNodeResponse>("createSubNode", {
            pendingEdit: true,
            nodeId: null,
            newNodeName: "",
            typeName: J.NodeType.BOOKMARK,
            createAtTop: true,
            content,
            typeLock: true,
            payloadType: "linkBookmark",
            properties: audioUrl ? [{ name: J.NodeProp.AUDIO_URL, value: audioUrl }] : null,
            shareToUserId: null,
            boostTarget: null,
            fediSend: false,
            boosterUserId: null,
            reply: false
        });
        this.createSubNodeResponse(res, true, null);
    }

    // like==false means 'unlike'
    likeNode = async (node: J.NodeInfo, like: boolean) => {
        await S.rpcUtil.rpc<J.LikeNodeRequest, J.LikeNodeResponse>("likeNode", {
            id: node.id,
            like
        }, true);

        dispatch("likeNode", s => {
            node.likes = node.likes || [];

            if (like && !node.likes.find(u => u === s.userName)) {
                // add userName to likes
                node.likes.push(s.userName);
            }
            else {
                // remove userName from likes
                node.likes = node.likes.filter(u => u !== s.userName);
            }
        });
    }

    /* If this is the user creating a 'boost' then boostTarget is the NodeId of the node being boosted */
    addNode = async (boosterUserId: string, nodeId: string, typeName: string, reply: boolean, content: string, shareToUserId: string, replyToId: string,
        boostTarget: string, fediSend: boolean) => {

        // auto-enable edit mode
        if (!boostTarget && !getAs().userPrefs.editMode) {
            await this.setEditMode(true);
        }

        // pending edit will only be true if not a boost, because ActPub doesn't support posting content into a boost
        // so we save the node without any content in this case.
        const res = await S.rpcUtil.rpc<J.CreateSubNodeRequest, J.CreateSubNodeResponse>("createSubNode", {
            pendingEdit: !boostTarget,
            nodeId,
            newNodeName: "",
            typeName: typeName || J.NodeType.NONE,
            createAtTop: true,
            content,
            typeLock: false,
            properties: null,
            shareToUserId,
            boostTarget,
            fediSend,
            boosterUserId,
            reply
        });

        if (!boostTarget) {
            this.createSubNodeResponse(res, false, replyToId);
        }
        else {
            S.util.flashMessageQuick("Post was boosted!", "Boost");
        }
    }

    createNode = async (node: J.NodeInfo, typeName: string, forceUsePopup: boolean,
        pendingEdit: boolean, payloadType: string, content: string) => {
        let properties: J.PropertyInfo[] = null;
        if (payloadType === "addDateProp") {
            properties = [{
                name: J.NodeProp.DATE,
                value: new Date().getTime()
            }, {
                name: J.NodeProp.DURATION,
                value: "01:00"
            }];
        }
        const res = await S.rpcUtil.rpc<J.CreateSubNodeRequest, J.CreateSubNodeResponse>("createSubNode", {
            pendingEdit,
            nodeId: node ? node.id : null,
            newNodeName: "",
            typeName,
            createAtTop: true,
            content,
            typeLock: true,
            properties,
            payloadType,
            shareToUserId: null,
            boostTarget: null,
            fediSend: false,
            boosterUserId: null,
            reply: false
        });

        // auto-enable edit mode
        if (!getAs().userPrefs.editMode) {
            await this.setEditMode(true);
        }
        this.createSubNodeResponse(res, forceUsePopup, null);

        // this is low priority so do it asynchronously
        setTimeout(() => {
            S.props.addRecentType(typeName);
        }, 1000);
    }

    addCalendarEntry = async (initDate: number) => {
        const res = await S.rpcUtil.rpc<J.CreateSubNodeRequest, J.CreateSubNodeResponse>("createSubNode", {
            pendingEdit: false,
            nodeId: getAs().fullScreenConfig.nodeId,
            newNodeName: "",
            typeName: J.NodeType.NONE,
            createAtTop: true,
            content: null,
            typeLock: true,
            properties: [{ name: J.NodeProp.DATE, value: "" + initDate }],
            shareToUserId: null,
            boostTarget: null,
            fediSend: false,
            boosterUserId: null,
            reply: false
        });
        this.createSubNodeResponse(res, false, null);
    }

    linkNodes = async (sourceNodeId: string, targetNodeId: string, name: string, type: string) => {
        if (targetNodeId === sourceNodeId) {
            return;
        }

        const res = await S.rpcUtil.rpc<J.LinkNodesRequest, J.LinkNodesResponse>("linkNodes", {
            sourceNodeId,
            targetNodeId,
            name,
            type
        });

        if (S.util.checkSuccess("LinkNodes Response", res)) {
            S.view.refreshTree({
                nodeId: null,
                zeroOffset: false,
                renderParentIfLeaf: false,
                highlightId: null,
                forceIPFSRefresh: false,
                scrollToTop: false,
                allowScroll: false,
                setTab: false,
                forceRenderParent: false,
                jumpToRss: false
            });
        }
    }

    moveNodeByDrop = async (targetNodeId: string, sourceNodeId: string, location: string) => {
        /* if node being dropped on itself, then ignore */
        if (!sourceNodeId || targetNodeId === sourceNodeId) {
            return;
        }

        const res = await S.rpcUtil.rpc<J.MoveNodesRequest, J.MoveNodesResponse>("moveNodes", {
            targetNodeId,
            nodeIds: [sourceNodeId],
            location
        });
        S.render.fadeInId = sourceNodeId;

        if (S.util.checkSuccess("Move nodes", res)) {
            dispatch("SetNodesToMove", s => {
                s.nodesToMove = null;
            });

            const ast = getAs();
            if (ast.activeTab === C.TAB_MAIN) {
                S.view.refreshTree({
                    nodeId: null,
                    zeroOffset: false,
                    renderParentIfLeaf: false,
                    highlightId: null,
                    forceIPFSRefresh: false,
                    scrollToTop: false,
                    allowScroll: false,
                    setTab: false,
                    forceRenderParent: false,
                    jumpToRss: false
                });
            }
            else if (ast.activeTab === C.TAB_DOCUMENT) {
                const data: TabIntf<any, DocumentResultSetView<any>> = S.tabUtil.getAppTabData(C.TAB_DOCUMENT);
                if (data) {
                    data.inst.pageChange(null);
                }
            }
        }
    }

    setHeadings = async () => {
        const node = S.nodeUtil.getHighlightedNode();
        if (node) {
            await S.rpcUtil.rpc<J.UpdateHeadingsRequest, J.UpdateHeadingsResponse>("updateHeadings", {
                nodeId: node.id
            });
            S.quanta.refresh();
        }
    }

    /*
     * Handles 'Sharing' button on a specific node, from button bar above node display in edit mode
     */
    editNodeSharing = async (dlg: EditNodeDlg, node: J.NodeInfo) => {
        node = node || S.nodeUtil.getHighlightedNode();
        if (!node) {
            S.util.showMessage("No node is selected.", "Warning");
            return;
        }

        const sharingDlg = new SharingDlg();
        await sharingDlg.open();

        // if not all the shares are mentioned in the text ask the user about putting them the content automatically
        if (!dlg.areAllSharesInContent()) {
            const confDlg = new ConfirmDlg("Insert Mentions into content text?", "Add Mentions ?");
            await confDlg.open();
            if (confDlg.yes) {
                await dlg.addSharingToContentText();
            }
        }
    }

    /* Whenever we share an encrypted node to a another user, this is the final operation we run which
   generates a key to the data which is encrypted with the public key of the person (identified by principalNodeId)
   the node is shared to. Then publishes that key info into the DB, so that only the other person who this node is shared to
   can use their private key to decrypt the key to the data, to view the node.
   */
    addCipherKeyToNode = async (node: J.NodeInfo, principalPublicKeyStr: string, principalNodeId: string) => {
        if (principalNodeId === J.PrincipalName.PUBLIC || !S.crypto.avail) {
            console.warn("public node has encryption turned on. This is a bug.");
            return;
        }

        // get the asym-encrypted sym Key to this node (decryptable by owner of node only, which is us)
        const cipherKey = S.props.getPropStr(J.NodeProp.ENC_KEY, node);

        // get this broswer's private key from browser storage
        const privateKey: CryptoKey = await S.crypto.getPrivateEncKey();

        // so this is the decrypted symmetric key to the data (the unencrypted copy of the actual AES key to the data)
        const clearKey = await S.crypto.asymDecryptString(privateKey, cipherKey);
        if (!clearKey) {
            throw new Error("Unable to access encryption key.");
        }

        // first parse the key and build a usable key from principalPublicKey.
        const principalSymKeyJsonObj: JsonWebKey = JSON.parse(principalPublicKeyStr);
        const principalPublicKey = await S.crypto.importKey(principalSymKeyJsonObj, S.crypto.ASYM_IMPORT_ALGO, true, S.crypto.OP_ENC);

        // now re-encrypt this clearTextKey using the public key (of the user being shared to).
        const userCipherKey = await S.crypto.asymEncryptString(principalPublicKey, clearKey);

        /* Now post this encrypted key (decryptable only by principalNodeId's private key) up to the server which will
        then store this key alongside the ACL (access control list) for the sharing entry for this user */
        await S.rpcUtil.rpc<J.SetCipherKeyRequest, J.SetCipherKeyResponse>("setCipherKey", {
            nodeId: node.id,
            principalNodeId,
            cipherKey: userCipherKey
        });
    }

    updateNode = (node: J.NodeInfo) => {
        dispatch("UpdateNode", s => s.editNode = node);
    }
}
