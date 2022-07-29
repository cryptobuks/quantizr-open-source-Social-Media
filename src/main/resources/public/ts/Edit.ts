import { EventInput } from "@fullcalendar/react";
import { dispatch, getAppState } from "./AppRedux";
import { AppState } from "./AppState";
import { Constants as C } from "./Constants";
import { ChangePasswordDlg } from "./dlg/ChangePasswordDlg";
import { ConfirmDlg } from "./dlg/ConfirmDlg";
import { EditNodeDlg } from "./dlg/EditNodeDlg";
import { ExportDlg } from "./dlg/ExportDlg";
import { ManageAccountDlg } from "./dlg/ManageAccountDlg";
import { ManageStorageDlg } from "./dlg/ManageStorageDlg";
import { PrefsDlg } from "./dlg/PrefsDlg";
import { SharingDlg } from "./dlg/SharingDlg";
import { UploadFromFileDropzoneDlg } from "./dlg/UploadFromFileDropzoneDlg";
import { TabIntf } from "./intf/TabIntf";
import * as J from "./JavaIntf";
import { NodeHistoryItem } from "./NodeHistoryItem";
import { S } from "./Singletons";
import { FeedView } from "./tabs/FeedView";

export class Edit {

    showReadOnlyProperties: boolean = false;
    pendingContent: string = null;
    pendingContentId: string = null;

    openChangePasswordDlg = (state: AppState) => {
        new ChangePasswordDlg(null).open();
    }

    openManageAccountDlg = (state: AppState) => {
        new ManageAccountDlg().open();
    }

    openManageStorageDlg = (state: AppState) => {
        new ManageStorageDlg().open();
    }

    editPreferences = (state: AppState) => {
        new PrefsDlg().open();
    }

    openImportDlg = (state: AppState) => {
        const node: J.NodeInfo = S.nodeUtil.getHighlightedNode(state);
        if (!node) {
            S.util.showMessage("No node is selected.", "Warning");
            return;
        }

        const dlg = new UploadFromFileDropzoneDlg(node.id, "", false, null, true, true, () => {
            S.view.jumpToId(node.id);
        });
        dlg.open();
    }

    openExportDlg = (state: AppState) => {
        let node = S.nodeUtil.getHighlightedNode(state);
        if (node) {
            new ExportDlg(node).open();
        }
    }

    private insertBookResponse = (res: J.InsertBookResponse, state: AppState) => {
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
            state
        });
        S.view.scrollToNode(state);
    }

    private joinNodesResponse = (res: J.JoinNodesResponse, state: AppState) => {
        state = getAppState(state);
        if (S.util.checkSuccess("Join node", res)) {
            S.nodeUtil.clearSelNodes(state);
            S.view.refreshTree({
                nodeId: state.node.id,
                zeroOffset: false,
                renderParentIfLeaf: false,
                highlightId: null,
                forceIPFSRefresh: false,
                scrollToTop: false,
                allowScroll: true,
                setTab: true,
                forceRenderParent: false,
                state
            });
        }
    }

    public initNodeEditResponse = (res: J.InitNodeEditResponse, forceUsePopup: boolean, encrypt: boolean, showJumpButton: boolean, replyToId: string, afterEditAction, state: AppState) => {
        if (S.util.checkSuccess("Editing node", res)) {
            /* NOTE: Removing 'editMode' check here is new 4/14/21, and without was stopping editing from calendar view which we
            do need even when edit mode is technically off */
            const editingAllowed = /* state.userPrefs.editMode && */ this.isEditAllowed(res.nodeInfo, state);
            if (editingAllowed) {
                // these conditions determine if we want to run editing in popup, instead of inline in the page.
                let editInPopup = forceUsePopup || state.mobileMode ||
                    // node not found on tree.
                    (!S.nodeUtil.getDisplayingNode(state, res.nodeInfo.id) &&
                        !S.nodeUtil.getDisplayingNode(state, S.quanta.newNodeTargetId)) ||
                    // not currently viewing tree
                    S.quanta.activeTab !== C.TAB_MAIN ||
                    S.util.fullscreenViewerActive(state);

                /* If we're editing on the feed tab, we set the 'state.editNode' which makes the gui know to render
                the editor at that place rather than opening a popup now */
                if (!forceUsePopup && S.quanta.activeTab === C.TAB_FEED) {
                    dispatch("StartEditingInFeed", s => {
                        s.editNodeReplyToId = replyToId;
                        s.editNodeOnTab = S.quanta.activeTab;
                        s.editNode = res.nodeInfo;
                        s.editShowJumpButton = showJumpButton;
                        s.editEncrypt = encrypt;
                        return s;
                    });
                }
                /* Either run the node editor as a popup or embedded, depending on whether we have a fullscreen
                calendar up and wether we're on the main tab, etc */
                else if (editInPopup) {
                    const dlg = new EditNodeDlg(res.nodeInfo, encrypt, showJumpButton, null, afterEditAction);
                    dlg.open();
                } else {
                    dispatch("startEditing", s => {
                        s.editNode = res.nodeInfo;
                        s.editNodeOnTab = S.quanta.activeTab;
                        s.editShowJumpButton = showJumpButton;
                        s.editEncrypt = encrypt;
                        return s;
                    });
                }
            } else {
                S.util.showMessage("Editing not allowed on node.", "Warning");
            }
        }
    }

    /* nodeId is optional and represents what to highlight after the paste if anything */
    private moveNodesResponse = (res: J.MoveNodesResponse, nodeId: string, pasting: boolean, state: AppState) => {
        if (S.util.checkSuccess("Move nodes", res)) {
            dispatch("SetNodesToMove", s => {
                s.nodesToMove = null;
                return s;
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
                    state
                });
            }
            else {
                S.view.jumpToId(nodeId);
            }
        }
    }

    private setNodePositionResponse = (res: J.SetNodePositionResponse, id: string, state: AppState) => {
        if (S.util.checkSuccess("Change node position", res)) {
            S.view.jumpToId(id, true);
        }
    }

    /* returns true if we are admin or else the owner of the node */
    isEditAllowed = (node: any, state: AppState): boolean => {
        if (!node) return false;
        let owner: string = node.owner;

        // if we don't know who owns this node assume the admin owns it.
        if (!owner) {
            owner = "admin";
        }

        return state.isAdminUser || state.userName === owner;
    }

    isInsertAllowed = (node: J.NodeInfo, state: AppState): boolean => {
        if (!node) return false;
        if (state.homeNodeId === node.id) {
            return true;
        }

        if (S.props.isPublicWritable(node)) {
            return true;
        }

        let owner: string = node.owner;

        // if we don't know who owns this node assume the admin owns it.
        if (!owner) {
            owner = "admin";
        }

        // if this node is admin owned, and we aren't the admin, then just disable editing. Admin himself is not even allowed to
        // make nodes editable by any other user.
        if (owner === "admin" && !state.isAdminUser) return false;

        // right now, for logged in users, we enable the 'new' button because the CPU load for determining it's enablement is too much, so
        // we throw an exception if they cannot. todo-2: need to make this work better.
        // however we CAN check if this node is an "admin" node and at least disallow any inserts under admin-owned nodess
        if (state.isAdminUser) return true;
        if (state.isAnonUser) return false;

        /* if we own the node we can edit it! */
        if (state.userName === node.owner) {
            // console.log("node owned by me: " + node.owner);
            return true;
        }

        if (S.props.isPublicReadOnly(node)) {
            return false;
        }

        // console.log("isInsertAllowed: node.owner="+node.owner+" nodeI="+node.id);
        return node.owner !== "admin";
    }

    /*
    * nodeInsertTarget holds the node that was clicked on at the time the insert was requested, and
    * is sent to server for ordinal position assignment of new node. Also if this var is null, it indicates we are
    * creating in a 'create under parent' mode, versus non-null meaning 'insert inline' type of insert.
    */
    startEditingNewNode = async (typeName: string, createAtTop: boolean, parentNode: J.NodeInfo, nodeInsertTarget: J.NodeInfo, ordinalOffset: number, state: AppState) => {
        if (!this.isInsertAllowed(parentNode, state)) {
            // console.log("Rejecting request to edit. Not authorized");
            return;
        }

        if (S.util.ctrlKeyCheck()) {
            let blob = null;
            let clipboardText = await (navigator as any).clipboard.readText();
            if (!clipboardText) {
                blob = await S.util.readClipboardFile();
                if (blob) {
                    EditNodeDlg.pendingUploadFile = blob;
                }
            }

            if (nodeInsertTarget) {
                let res = await S.util.ajax<J.InsertNodeRequest, J.InsertNodeResponse>("insertNode", {
                    pendingEdit: false,
                    parentId: parentNode.id,
                    targetOrdinal: nodeInsertTarget.ordinal + ordinalOffset,
                    newNodeName: "",
                    typeName: typeName || "u",
                    initialValue: clipboardText
                });
                if (blob) {
                    this.insertNodeResponse(res, state);
                }
            } else {
                let res = await S.util.ajax<J.CreateSubNodeRequest, J.CreateSubNodeResponse>("createSubNode", {
                    pendingEdit: false,
                    nodeId: parentNode.id,
                    newNodeName: "",
                    typeName: typeName || "u",
                    createAtTop,
                    content: clipboardText,
                    typeLock: false,
                    properties: null,
                    shareToUserId: null,
                    boostTarget: null,
                    reply: false,
                    fediSend: false
                });
                if (blob) {
                    this.createSubNodeResponse(res, false, null, null, state);
                }
            }

            if (!blob) {
                S.quanta.refresh(state);
            }
        }
        else {
            if (nodeInsertTarget) {
                let res = await S.util.ajax<J.InsertNodeRequest, J.InsertNodeResponse>("insertNode", {
                    pendingEdit: true,
                    parentId: parentNode.id,
                    targetOrdinal: nodeInsertTarget.ordinal + ordinalOffset,
                    newNodeName: "",
                    typeName: typeName || "u",
                    initialValue: ""
                });
                this.insertNodeResponse(res, state);
            } else {
                let res = await S.util.ajax<J.CreateSubNodeRequest, J.CreateSubNodeResponse>("createSubNode", {
                    pendingEdit: true,
                    nodeId: parentNode.id,
                    newNodeName: "",
                    typeName: typeName || "u",
                    createAtTop,
                    content: null,
                    typeLock: false,
                    properties: null,
                    shareToUserId: null,
                    boostTarget: null,
                    reply: false,
                    fediSend: false
                });
                this.createSubNodeResponse(res, false, null, null, state);
            }
        }
    }

    insertNodeResponse = (res: J.InsertNodeResponse, state: AppState) => {
        if (S.util.checkSuccess("Insert node", res)) {
            S.nodeUtil.updateNodeMap(res.newNode, state);
            S.nodeUtil.highlightNode(res.newNode, false, state);
            this.runEditNode(null, res.newNode.id, false, false, false, null, null, state);
        }
    }

    createSubNodeResponse = (res: J.CreateSubNodeResponse, forceUsePopup: boolean, replyToId: string, afterEditAction: Function, state: AppState) => {
        if (S.util.checkSuccess("Create subnode", res)) {
            if (!res.newNode) {
                S.quanta.refresh(state);
            }
            else {
                S.nodeUtil.updateNodeMap(res.newNode, state);
                this.runEditNode(null, res.newNode.id, forceUsePopup, res.encrypt, false, replyToId, afterEditAction, state);
            }
        }
    }

    saveNodeResponse = async (node: J.NodeInfo, res: J.SaveNodeResponse, allowScroll: boolean, state: AppState) => {
        if (S.util.checkSuccess("Save node", res)) {
            await this.distributeKeys(node, res.aclEntries);

            // if on feed tab, and it became dirty while we were editing then refresh it.
            if (state.activeTab === C.TAB_FEED) {
                let feedData: TabIntf = S.tabUtil.getTabDataById(state, C.TAB_FEED);
                if (feedData?.props?.feedDirtyList) {
                    FeedView.updateFromFeedDirtyList(feedData, state);
                }
            }

            // If we're on some tab other than MAIN (tree) we don't need to update anything.
            if (state.activeTab !== C.TAB_MAIN) {
                return;
            }

            // find and update the history item if it exists.
            let histItem: NodeHistoryItem = S.quanta.nodeHistory.find(function (h: NodeHistoryItem) {
                return h.id === node.id;
            });
            if (histItem) {
                histItem.content = S.nodeUtil.getShortContent(node);
            }

            // It's possible to end up editing a node that's not even on the page, or a child of a node on the page,
            // and so before refreshing the screen we check for that edge case.
            // console.log("saveNodeResponse: " + S.util.prettyPrint(node));

            let parentPath = S.props.getParentPath(node);
            if (!parentPath) return;

            // I had expected the save to have already move into the non-pending folder by now,
            // but i haven't investigated yet, this must be right.
            if (parentPath.startsWith("/r/p")) {
                parentPath = S.util.replaceAll(parentPath, "/r/p", "/r");
            }

            // if 'node.id' is not being displayed on the page we need to jump to it from scratch
            if (!S.nodeUtil.getDisplayingNode(state, node.id)) {
                S.view.jumpToId(node.id);
            }
            // otherwise we just pull down the new node data and replace it into our 'state.node.children' (or page root) and we're done.
            else {
                this.refreshNodeFromServer(node.id);
            }

            if (state.fullScreenCalendarId) {
                S.render.showCalendar(state.fullScreenCalendarId, state);
            }
        }
    }

    refreshNodeFromServer = async (nodeId: string) => {
        // console.log("refreshNodeFromServer: " + nodeId);
        let state = getAppState();

        let res = await S.util.ajax<J.RenderNodeRequest, J.RenderNodeResponse>("renderNode", {
            nodeId,
            upLevel: false,
            siblingOffset: 0,
            renderParentIfLeaf: false,
            forceRenderParent: false,
            offset: 0,
            goToLastPage: false,
            forceIPFSRefresh: false,
            singleNode: true,
            parentCount: state.userPrefs.showParents ? 1 : 0
        });

        if (!res?.node) {
            return;
        }

        dispatch("RefreshNodeFromServer", s => {
            // if the node is our page parent (page root)
            if (res.node.id === s.node.id) {
                // preserve the children, when updating the root node, because they will not have been obtained
                // due to the 'singleNode=true' in the request
                res.node.children = s.node.children;
                s.node = res.node;
            }
            // otherwise a child
            else if (s.node?.children) {
                // replace the old node with the new node.
                s.node.children = s.node.children.map(node => {
                    return node.id === res.node.id ? res.node : node;
                });
            }
            S.nodeUtil.updateNodeMap(res.node, s);
            return s;
        });
    }

    distributeKeys = async (node: J.NodeInfo, aclEntries: J.AccessControlInfo[]) => {
        if (!aclEntries || !S.props.isEncrypted(node)) {
            return;
        }

        for (let ac of aclEntries) {
            // console.log("Distribute Key to Principal: " + S.util.prettyPrint(ac));
            await S.edit.addCipherKeyToNode(node, ac.publicKey, ac.principalNodeId);
        }
        // console.log("Key distribution complete.");
    }

    setRssHeadlinesOnly = async (state: AppState, val: boolean) => {
        state.userPrefs.rssHeadlinesOnly = val;
        S.util.saveUserPreferences(state);
    }

    toggleEditMode = async (state: AppState) => {
        state.userPrefs.editMode = !state.userPrefs.editMode;
        S.util.saveUserPreferences(state);

        /* scrolling is required because nodes will have scrolled out of view by the page just now updating */
        S.view.scrollToNode(state);
    }

    setMainPanelCols = (val: number) => {
        setTimeout(() => {
            let state = getAppState();
            if (val < 4) val = 4;
            if (val > 8) val = 8;
            state.userPrefs.mainPanelCols = val;
            S.util.saveUserPreferences(state);
        }, 100);
    };

    setMetadataOption = (val: boolean) => {
        setTimeout(() => {
            let state = getAppState();
            state.userPrefs.showMetaData = val;
            S.util.saveUserPreferences(state);
        }, 100);
    };

    toggleShowMetaData = (state: AppState) => {
        state.userPrefs.showMetaData = !state.userPrefs.showMetaData;
        S.util.saveUserPreferences(state);

        /* scrolling is required because nodes will have scrolled out of view by the page just now updating */
        S.view.scrollToNode(state);
    }

    toggleNsfw = async (state: AppState) => {
        state.userPrefs.nsfw = !state.userPrefs.nsfw;
        return S.util.saveUserPreferences(state, true);
    }

    toggleShowParents = (state: AppState) => {
        state.userPrefs.showParents = !state.userPrefs.showParents;
        S.util.saveUserPreferences(state, false);
        S.quanta.refresh(state);
    }

    // without the await on saveUserPreferences the refresh will be done with WRONG userProfile SO...look for
    // other places we need to have this await and perhaps don't.
    toggleShowReplies = async (state: AppState) => {
        state.userPrefs.showReplies = !state.userPrefs.showReplies;
        await S.util.saveUserPreferences(state, false);
        S.quanta.refresh(state);
    }

    moveNodeUp = async (evt: Event, id: string, state?: AppState) => {
        id = S.util.allowIdFromEvent(evt, id);
        state = getAppState(state);
        if (!id) {
            const selNode: J.NodeInfo = S.nodeUtil.getHighlightedNode(state);
            id = selNode.id;
        }

        const node: J.NodeInfo = state.idToNodeMap.get(id);
        if (node) {
            let res = await S.util.ajax<J.SetNodePositionRequest, J.SetNodePositionResponse>("setNodePosition", {
                nodeId: node.id,
                targetName: "up"
            });
            this.setNodePositionResponse(res, id, state);
        }
    }

    moveNodeDown = async (evt: Event, id: string, state: AppState) => {
        id = S.util.allowIdFromEvent(evt, id);
        state = getAppState(state);
        if (!id) {
            const selNode: J.NodeInfo = S.nodeUtil.getHighlightedNode(state);
            id = selNode.id;
        }

        const node: J.NodeInfo = state.idToNodeMap.get(id);
        if (node) {
            let res = await S.util.ajax<J.SetNodePositionRequest, J.SetNodePositionResponse>("setNodePosition", {
                nodeId: node.id,
                targetName: "down"
            });
            this.setNodePositionResponse(res, id, state);
        }
    }

    moveNodeToTop = async (id: string = null, state: AppState = null) => {
        state = getAppState(state);
        if (!id) {
            const selNode: J.NodeInfo = S.nodeUtil.getHighlightedNode(state);
            id = selNode.id;
        }
        const node: J.NodeInfo = state.idToNodeMap.get(id);
        if (node) {
            let res = await S.util.ajax<J.SetNodePositionRequest, J.SetNodePositionResponse>("setNodePosition", {
                nodeId: node.id,
                targetName: "top"
            });
            this.setNodePositionResponse(res, id, state);
        }
    }

    moveNodeToBottom = async (id: string = null, state: AppState = null) => {
        state = getAppState(state);
        if (!id) {
            const selNode: J.NodeInfo = S.nodeUtil.getHighlightedNode(state);
            id = selNode.id;
        }
        const node: J.NodeInfo = state.idToNodeMap.get(id);
        if (node) {
            let res = await S.util.ajax<J.SetNodePositionRequest, J.SetNodePositionResponse>("setNodePosition", {
                nodeId: node.id,
                targetName: "bottom"
            });
            this.setNodePositionResponse(res, id, state);
        }
    }

    getFirstChildNode = (state: AppState): any => {
        if (!state.node || !state.node.children || state.node.children.length === 0) return null;
        return state.node.children[0];
    }

    getLastChildNode = (state: AppState): J.NodeInfo => {
        if (!state.node || !state.node.children || state.node.children.length === 0) return null;
        return state.node.children[state.node.children.length - 1];
    }

    checkEditPending = (): boolean => {
        let state: AppState = getAppState(null);

        // state.editNode holds non-null always whenever there is editing underway.
        if (state.editNode) {
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
        this.runEditNode(null, id, false, false, false, null, null);

        // it's safest and best to just disable scrolling for a couple of seconds during which editing is being initiated.
        setTimeout(() => {
            S.quanta.noScrollToId = null;
        }, 2000);
    }

    /* This can run as an actuall click event function in which only 'evt' is non-null here */
    runEditNode = async (evt: Event, id: string, forceUsePopup: boolean, encrypt: boolean, showJumpButton: boolean, replyToId: string, afterEditAction: Function, state?: AppState) => {
        id = S.util.allowIdFromEvent(evt, id);
        state = getAppState(state);
        if (!id) {
            let node = S.nodeUtil.getHighlightedNode(state);
            if (node) {
                id = node.id;
            }
        }

        if (!id) {
            S.util.showMessage("Unknown nodeId in editNodeClick: ", "Warning");
            return;
        }

        let res = await S.util.ajax<J.InitNodeEditRequest, J.InitNodeEditResponse>("initNodeEdit", {
            nodeId: id
        });
        this.initNodeEditResponse(res, forceUsePopup, encrypt, showJumpButton, replyToId, afterEditAction, state);
    }

    insertNode = (id: string, typeName: string, ordinalOffset: number, state?: AppState) => {
        if (this.checkEditPending()) return;

        state = getAppState(state);
        if (!state.node || !state.node.children) return;

        /*
         * We get the node selected for the insert position by using the uid if one was passed in or using the
         * currently highlighted node if no uid was passed.
         */
        let node: J.NodeInfo = null;
        if (!id) {
            node = S.nodeUtil.getHighlightedNode(state);
        } else {
            node = state.idToNodeMap.get(id);
        }

        if (node) {
            S.quanta.newNodeTargetId = id;
            S.quanta.newNodeTargetOffset = ordinalOffset;
            this.startEditingNewNode(typeName, false, state.node, node, ordinalOffset, state);
        }
    }

    newSubNode = async (evt: Event, id: string) => {
        if (this.checkEditPending()) return;

        id = S.util.allowIdFromEvent(evt, id);
        let state = getAppState();
        if (S.util.ctrlKeyCheck()) {
            this.saveClipboardToChildNode(id);
        }
        else {
            this.createSubNode(id, null, true, state.node, null);
        }
    }

    createSubNode = (id: any, typeName: string, createAtTop: boolean, parentNode: J.NodeInfo, state: AppState) => {
        state = getAppState(state);
        /*
         * If no uid provided we deafult to creating a node under the currently viewed node (parent of current page), or any selected
         * node if there is a selected node.
         */
        if (!id) {
            const node: J.NodeInfo = S.nodeUtil.getHighlightedNode(state);
            if (node) {
                parentNode = node;
            }
            else {
                if (!state.node || !state.node.children) return null;
                parentNode = state.node;
            }
        } else {
            parentNode = state.idToNodeMap.get(id);
            if (!parentNode) {
                // console.log("Unknown nodeId in createSubNode: " + id);
                return;
            }
        }

        this.startEditingNewNode(typeName, createAtTop, parentNode, null, 0, state);
    }

    selectAllNodes = async (state: AppState) => {
        const highlightNode = S.nodeUtil.getHighlightedNode(state);
        let res = await S.util.ajax<J.SelectAllNodesRequest, J.SelectAllNodesResponse>("selectAllNodes", {
            parentNodeId: highlightNode.id
        });
        S.nodeUtil.selectAllNodes(res.nodeIds);
    }

    clearInbox = async (state: AppState) => {
        S.nodeUtil.clearSelNodes(state);

        let dlg = new ConfirmDlg("Permanently delete the nodes in your Inbox", "Clear Inbox",
            "btn-danger", "alert alert-danger");
        await dlg.open();
        if (dlg.yes) {
            await S.util.ajax<J.DeleteNodesRequest, J.DeleteNodesResponse>("deleteNodes", {
                nodeIds: ["~" + J.NodeType.INBOX],
                childrenOnly: true,
                bulkDelete: false
            });
            S.nav.openContentNode(state.homeNodePath, state);
        }
    }

    joinNodes = async (state?: AppState) => {
        state = getAppState(state);

        const selNodesArray = S.nodeUtil.getSelNodeIdsArray(state);
        if (!selNodesArray || selNodesArray.length === 0) {
            S.util.showMessage("Select some nodes to join.", "Warning");
            return;
        }

        let confirmMsg = "Join " + selNodesArray.length + " node(s) ?";
        let dlg = new ConfirmDlg(confirmMsg, "Confirm Join " + selNodesArray.length,
            "btn-danger", "alert alert-info");
        await dlg.open();
        if (dlg.yes) {
            let res = await S.util.ajax<J.JoinNodesRequest, J.JoinNodesResponse>("joinNodes", {
                nodeIds: selNodesArray
            });
            this.joinNodesResponse(res, state);
        }
    }

    /*
    * Deletes all nodes owned by you but NOT rooted in your own account root.
    */
    bulkDelete = async () => {
        let state = getAppState();

        let confirmMsg = "Bulk Delete all your nodes *not* rooted in your account?";
        let dlg = new ConfirmDlg(confirmMsg, "Confirm Delete",
            "btn-danger", "alert alert-danger");
        await dlg.open();
        if (dlg.yes) {
            let res = await S.util.ajax<J.DeleteNodesRequest, J.DeleteNodesResponse>("deleteNodes", {
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
        let state = getAppState();
        id = S.util.allowIdFromEvent(evt, id);

        // if a nodeId was specified we use it as the selected nodes to delete
        if (id) {
            // note we ARE updating 'state' here but it doesn't matter we can discard state, becasue
            // all we needed is selNodesArray which we get and as long as selNodesArray is preserved
            // we can let that change to 'state' get discarded in the next dispatch
            S.nav.setNodeSel(true, id, state);
        }

        // note: the setNodeSel above isn't causing this to get anything here
        const selNodesArray = S.nodeUtil.getSelNodeIdsArray(state);

        if (!selNodesArray || selNodesArray.length === 0) {
            S.util.showMessage("Select some nodes to delete.", "Warning");
            return;
        }

        if (selNodesArray.find(id => id === state.homeNodeId)) {
            S.util.showMessage("You can't delete your account root node!", "Warning");
            return;
        }

        if (selNodesArray.find(id => id === state.node.id)) {
            S.util.showMessage("You can't delete your page node! Go up a level to do that.", "Warning");
            return;
        }

        let confirmMsg = "Delete " + selNodesArray.length + " node(s) ?";
        let dlg = new ConfirmDlg(confirmMsg, "Confirm Delete " + selNodesArray.length,
            "btn-danger", "alert alert-danger");
        await dlg.open();
        if (dlg.yes) {
            let res = await S.util.ajax<J.DeleteNodesRequest, J.DeleteNodesResponse>("deleteNodes", {
                nodeIds: selNodesArray,
                childrenOnly: false,
                bulkDelete: false
            });
            this.removeNodesFromHistory(selNodesArray, state);
            this.removeNodesFromCalendarData(selNodesArray, state);

            if (S.util.checkSuccess("Delete node", res)) {
                if (state.node.children) {
                    state.node.children = state.node.children.filter(child => !selNodesArray.find(id => id === child.id));
                }

                if (state.node.children.length === 0) {
                    dispatch("NodeDeleteComplete", s => {
                        // remove this node from all data from all the tabs, so they all refresh without
                        // the deleted node without being queries from the server again.
                        selNodesArray.forEach(id => {
                            S.srch.removeNodeById(id, s);
                        });
                        s.selectedNodes.clear();
                        return s;
                    });
                    S.view.jumpToId(state.node.id);
                }
                else {
                    dispatch("UpdateChildren", s => {
                        selNodesArray.forEach(id => {
                            S.srch.removeNodeById(id, s);
                        });
                        s.selectedNodes.clear();
                        s.node.children = state.node.children;
                        return s;
                    });
                }
            }

            /* We waste a tiny bit of CPU/bandwidth here by just always updating the bookmarks in case
             we just deleted some. This could be slightly improved to KNOW if we deleted any bookmarks, but
            the added complexity to achieve that for recursive tree deletes doesn't pay off */
            setTimeout(() => {
                S.util.loadBookmarks();
            }, 1000);
        }
    }

    /* Updates 'nodeHistory' when nodes are deleted */
    removeNodesFromHistory = (selNodesArray: string[], appState: AppState) => {
        if (!selNodesArray) return;
        selNodesArray.forEach((id: string) => {
            // remove any top level history item that matches 'id'
            S.quanta.nodeHistory = S.quanta.nodeHistory.filter(function (h: NodeHistoryItem) {
                return h.id !== id;
            });

            // scan all top level history items, and remove 'id' from any subItems
            S.quanta.nodeHistory.forEach(function (h: NodeHistoryItem) {
                if (h.subItems) {
                    h.subItems = h.subItems.filter(function (hi: NodeHistoryItem) {
                        return hi.id !== id;
                    });
                }
            });
        });
    }

    removeNodesFromCalendarData = (selNodesArray: string[], appState: AppState) => {
        if (!appState.calendarData) return;

        selNodesArray.forEach((id: string) => {
            appState.calendarData = appState.calendarData.filter((item: EventInput) => item.id !== id);
        });

        // I'll leave this here commented until I actually TEST deleting calendar items again.
        // dispatch("UpdateCalendarData", s => {
        //     return appState;
        // });
    }

    undoCutSelNodes = () => {
        dispatch("SetNodesToMove", s => {
            s.nodesToMove = null;
            return s;
        });
    }

    cutSelNodes = (evt: Event, id: string) => {
        id = S.util.allowIdFromEvent(evt, null);

        dispatch("SetNodesToMove", s => {
            S.nav.setNodeSel(true, id, s);
            let selNodesArray = S.nodeUtil.getSelNodeIdsArray(s);
            s.nodesToMove = selNodesArray;
            s.selectedNodes.clear();
            return s;
        });
    }

    pasteSelNodesInside = (evt: Event, id: string) => {
        id = S.util.allowIdFromEvent(evt, id);
        const state = getAppState();
        this.pasteSelNodes(id, "inside", state);
    }

    // location=inside | inline | inline-above (todo-2: put in java-aware enum)
    pasteSelNodes = async (nodeId: string, location: string, state?: AppState) => {
        state = getAppState(state);
        /*
         * For now, we will just cram the nodes onto the end of the children of the currently selected
         * page (for the 'inside' option). Later on we can get more specific about allowing precise destination location for moved
         * nodes.
         */
        let res = await S.util.ajax<J.MoveNodesRequest, J.MoveNodesResponse>("moveNodes", {
            targetNodeId: nodeId,
            nodeIds: state.nodesToMove,
            location
        });
        this.moveNodesResponse(res, nodeId, true, state);
    }

    pasteSelNodes_InlineAbove = (evt: Event, id: string) => {
        id = S.util.allowIdFromEvent(evt, id);
        this.pasteSelNodes(id, "inline-above");
    }

    pasteSelNodes_Inline = (evt: Event, id: string) => {
        id = S.util.allowIdFromEvent(evt, id);
        this.pasteSelNodes(id, "inline");
    }

    insertBookWarAndPeace = async (state: AppState) => {
        let dlg = new ConfirmDlg("Warning: You should have an EMPTY node selected now, to serve as the root node of the book!",
            "Confirm", null, null);
        await dlg.open();
        if (dlg.yes) {
            /* inserting under whatever node user has focused */
            const node = S.nodeUtil.getHighlightedNode(state);

            if (!node) {
                S.util.showMessage("No node is selected.", "Warning");
            } else {
                let res = await S.util.ajax<J.InsertBookRequest, J.InsertBookResponse>("insertBook", {
                    nodeId: node.id,
                    bookName: "War and Peace",
                    truncated: S.user.isTestUserAccount(state)
                });
                this.insertBookResponse(res, state);
            }
        }
    }

    // showIpfsTab = () => {
    //     dispatch("showIpfsTab", s => {
    //         s.showIpfsTab = true;
    //         setTimeout(() => {
    //             S.tabUtil.selectTab(C.TAB_IPFSVIEW);
    //         }, 250);
    //         return s;
    //     });
    // }

    saveClipboardToChildNode = async (parentId: string) => {
        let clipText: string = await (navigator as any).clipboard.readText();

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

        // DO NOT DELETE (yet)
        // const items = await (navigator as any).clipboard.read();
        // for (let item of items) {
        //     const blob = await item.getType("text/html");
        //     if (blob) {
        //         let html = await blob.text();
        //         clipText = "```html\n" + html + "\n```\n";
        //         break;
        //     }
        // }

        if (!clipText && !blob) {
            S.util.flashMessage("Nothing saved clipboard is empty!", "Warning", true);
            return;
        }

        let res = await S.util.ajax<J.CreateSubNodeRequest, J.CreateSubNodeResponse>("createSubNode", {
            pendingEdit: false,
            nodeId: parentId,
            newNodeName: "",
            typeName: "u",
            createAtTop: true,
            content: clipText,
            typeLock: false,
            properties: null,
            shareToUserId: null,
            boostTarget: null,
            reply: false,
            fediSend: false
        });

        if (blob) {
            let state = getAppState(null);
            this.createSubNodeResponse(res, false, null, null, state);
        }
        else {
            setTimeout(() => {
                let state = getAppState();
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
                    state
                });
            }, 500);
        }
    }

    splitNode = async (node: J.NodeInfo, splitType: string, delimiter: string, state: AppState) => {
        if (!node) {
            node = S.nodeUtil.getHighlightedNode(state);
        }

        if (!node) {
            S.util.showMessage("You didn't select a node to split.", "Warning");
            return;
        }

        let res = await S.util.ajax<J.SplitNodeRequest, J.SplitNodeResponse>("splitNode", {
            splitType: splitType,
            nodeId: node.id,
            delimiter
        });
        this.splitNodeResponse(res, state);
    }

    splitNodeResponse = (res: J.SplitNodeResponse, state: AppState) => {
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
                state
            });
            S.view.scrollToNode(state);
        }
    }

    addBookmark = (node: J.NodeInfo, state: AppState) => {
        this.createNode(node, J.NodeType.BOOKMARK, true, true, null, null, state);
    }

    addLinkBookmark = async (content: any, audioUrl: string, state: AppState) => {
        state = getAppState(state);

        let res = await S.util.ajax<J.CreateSubNodeRequest, J.CreateSubNodeResponse>("createSubNode", {
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
            reply: false,
            fediSend: false
        });
        this.createSubNodeResponse(res, true, null, null, state);
    }

    // like==false means 'unlike'
    likeNode = async (node: J.NodeInfo, like: boolean, state: AppState) => {
        let res = await S.util.ajax<J.LikeNodeRequest, J.LikeNodeResponse>("likeNode", {
            id: node.id,
            like
        }, true);

        dispatch("likeNode", s => {
            if (!node.likes) {
                node.likes = [];
            }

            if (like && !node.likes.find(u => u === state.userName)) {
                // add userName to likes
                node.likes.push(state.userName);
            }
            else {
                // remove userName from likes
                node.likes = node.likes.filter(u => u !== state.userName);
            }
            return s;
        });
    }

    /* If this is the user creating a 'boost' then boostTarget is the NodeId of the node being boosted */
    addNode = async (nodeId: string, reply: boolean, content: string, shareToUserId: string, replyToId: string, afterEditAction: Function, boostTarget: string, fediSend: boolean, state: AppState) => {
        state = getAppState(state);

        // auto-enable edit mode
        if (!state.userPrefs.editMode) {
            await S.edit.toggleEditMode(state);
        }

        // pending edit will only be true if not a boost, becasue ActPub doesn't support posting content into a boost
        // so we save the node without any content in this case.
        let res = await S.util.ajax<J.CreateSubNodeRequest, J.CreateSubNodeResponse>("createSubNode", {
            pendingEdit: !boostTarget,
            nodeId,
            newNodeName: "",
            typeName: J.NodeType.NONE,
            createAtTop: true,
            content,
            typeLock: false,
            properties: null,
            shareToUserId,
            boostTarget,
            reply,
            fediSend
        });

        if (!boostTarget) {
            this.createSubNodeResponse(res, false, replyToId, afterEditAction, state);
        }
        else {
            S.util.flashMessage("Your boost was posted.", "Boost");
        }
    }

    createNode = async (node: J.NodeInfo, typeName: string, forceUsePopup: boolean, pendingEdit: boolean, payloadType: string, content: string, state: AppState) => {
        state = getAppState(state);

        let res = await S.util.ajax<J.CreateSubNodeRequest, J.CreateSubNodeResponse>("createSubNode", {
            pendingEdit,
            nodeId: node ? node.id : null,
            newNodeName: "",
            typeName,
            createAtTop: true,
            content,
            typeLock: true,
            properties: null,
            payloadType,
            shareToUserId: null,
            boostTarget: null,
            reply: false,
            fediSend: false
        });

        // auto-enable edit mode
        if (!state.userPrefs.editMode) {
            await S.edit.toggleEditMode(state);
        }
        this.createSubNodeResponse(res, forceUsePopup, null, null, state);
    }

    addCalendarEntry = async (initDate: number, state: AppState) => {
        state = getAppState(state);

        let res = await S.util.ajax<J.CreateSubNodeRequest, J.CreateSubNodeResponse>("createSubNode", {
            pendingEdit: false,
            nodeId: state.fullScreenCalendarId,
            newNodeName: "",
            typeName: J.NodeType.NONE,
            createAtTop: true,
            content: null,
            typeLock: true,
            properties: [{ name: J.NodeProp.DATE, value: "" + initDate }],
            shareToUserId: null,
            boostTarget: null,
            reply: false,
            fediSend: false
        });
        this.createSubNodeResponse(res, false, null, null, state);
    }

    moveNodeByDrop = async (targetNodeId: string, sourceNodeId: string, location: string, refreshCurrentNode: boolean) => {
        // console.log("targetNodeId=" + targetNodeId);

        /* if node being dropped on itself, then ignore */
        if (targetNodeId === sourceNodeId) {
            return;
        }

        // console.log("Moving node[" + targetNodeId + "] into position of node[" + sourceNodeId + "]");
        const state = getAppState(null);

        let res = await S.util.ajax<J.MoveNodesRequest, J.MoveNodesResponse>("moveNodes", {
            targetNodeId,
            nodeIds: [sourceNodeId],
            location
        });
        S.render.fadeInId = sourceNodeId;

        if (refreshCurrentNode) {
            if (S.util.checkSuccess("Move nodes", res)) {
                dispatch("SetNodesToMove", s => {
                    S.util.removeHistorySubItem(sourceNodeId);
                    s.nodesToMove = null;
                    return s;
                });
                let state = getAppState();
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
                    state
                });
            }
        }
        else {
            this.moveNodesResponse(res, sourceNodeId, false, state);
        }
    }

    updateHeadings = async (state: AppState) => {
        state = getAppState(state);
        const node: J.NodeInfo = S.nodeUtil.getHighlightedNode(state);
        if (node) {
            await S.util.ajax<J.UpdateHeadingsRequest, J.UpdateHeadingsResponse>("updateHeadings", {
                nodeId: node.id
            });
            S.quanta.refresh(state);
        }
    }

    /*
     * Handles 'Sharing' button on a specific node, from button bar above node display in edit mode
     */
    editNodeSharing = async (state: AppState, node: J.NodeInfo) => {
        if (!node) {
            node = S.nodeUtil.getHighlightedNode(state);
        }

        if (!node) {
            S.util.showMessage("No node is selected.", "Warning");
            return;
        }
        let dlg: SharingDlg = new SharingDlg(node);
        await dlg.open();
    }

    /* Whenever we share an encrypted node to a another user, this is the final operation we run which
   generates a key to the data which is encrypted with the public key of the person (identified by principalNodeId)
   the node is shared to. Then publishes that key info into the DB, so that only the other person who this node is shared to
   can use their private key to decrypt the key to the data, to view the node.
   */
    addCipherKeyToNode = async (node: J.NodeInfo, principalPublicKeyStr: string, principalNodeId: string) => {
        if (principalNodeId === "public") {
            console.warn("public node has encryption turned on. This is a bug.");
            return;
        }
        // console.log("PrincipalPublicKeyStr:" + principalPublicKeyStr + " principalNodeId:" + principalNodeId);

        // get the asym-encrypted sym Key to this node (decryptable by owner of node only, which is us)
        const cipherKey = S.props.getPropStr(J.NodeProp.ENC_KEY, node);
        // console.log("cipherKey on ENC_KEY: "+cipherKey);

        // get this broswer's private key from browser storage
        const privateKey: CryptoKey = await S.encryption.getPrivateKey();

        // so this is the decrypted symmetric key to the data (the unencrypted copy of the actual AES key to the data)
        const clearKey = await S.encryption.asymDecryptString(privateKey, cipherKey);
        if (!clearKey) {
            throw new Error("Unable to access encryption key.");
        }

        // console.log("clear text key to re-encrypt: " + clearKey + "\nEncrpyting key using this pub key of user: " +
        //     principalPublicKeyStr);

        // first parse the key and build a usable key from principalPublicKey.
        const principalSymKeyJsonObj: JsonWebKey = JSON.parse(principalPublicKeyStr);
        const principalPublicKey = await S.encryption.importKey(principalSymKeyJsonObj, S.encryption.ASYM_IMPORT_ALGO, true, S.encryption.OP_ENC);

        // now re-encrypt this clearTextKey using the public key (of the user being shared to).
        const userCipherKey = await S.encryption.asymEncryptString(principalPublicKey, clearKey);
        // console.log("userCipherKey=" + userCipherKey);

        /* Now post this encrypted key (decryptable only by principalNodeId's private key) up to the server which will
        then store this key alongside the ACL (access control list) for the sharing entry for this user */
        await S.util.ajax<J.SetCipherKeyRequest, J.SetCipherKeyResponse>("setCipherKey", {
            nodeId: node.id,
            principalNodeId,
            cipherKey: userCipherKey
        });
    }
}
