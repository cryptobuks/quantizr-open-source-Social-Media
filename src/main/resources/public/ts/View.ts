import { dispatch, getAs } from "./AppContext";
import { Comp } from "./comp/base/Comp";
import { Constants as C } from "./Constants";
import { NodeStatsDlg } from "./dlg/NodeStatsDlg";
import { TabIntf } from "./intf/TabIntf";
import * as J from "./JavaIntf";
import { PubSub } from "./PubSub";
import { S } from "./Singletons";

// These are magically defined in webpack.common.js;
declare const BUILDTIME: string;
declare const PROFILE: string;

export class View {
    docElm: any = (document.documentElement || document.body.parentNode || document.body);

    jumpToId = async (id: string, forceRenderParent: boolean = false) => {
        if (C.DEBUG_SCROLLING) {
            console.log("view.jumpToId");
        }
        await this.refreshTree({
            nodeId: id,
            zeroOffset: true,
            renderParentIfLeaf: true,
            highlightId: id,
            forceIPFSRefresh: false,
            scrollToTop: false,
            allowScroll: true,
            setTab: true,
            forceRenderParent,
            jumpToRss: false
        });
    }

    /*
     * newId is optional and if specified makes the page scroll to and highlight that node upon re-rendering.
     */
    refreshTree = async (a: RefreshTreeArgs) => {
        const ast = getAs();
        if (!a.nodeId && ast.node) {
            a.nodeId = ast.node.id;
        }

        if (!a.highlightId) {
            const currentSelNode = S.nodeUtil.getHighlightedNode();
            a.highlightId = currentSelNode ? currentSelNode.id : a.nodeId;
        }

        let offset = 0;
        if (!a.zeroOffset) {
            const firstChild = S.edit.getFirstChildNode();
            offset = firstChild ? firstChild.logicalOrdinal : 0;
        }

        /* named nodes aren't persisting in url without this and i may decide to just get rid
         of 'renderParentIfLeaf' altogether (todo-2) but for now i'm just fixing the case when we are
         rendering a named node. */
        if (a.nodeId.indexOf(":") !== -1) {
            a.renderParentIfLeaf = false;
        }

        try {
            const res = await S.rpcUtil.rpc<J.RenderNodeRequest, J.RenderNodeResponse>("renderNode", {
                nodeId: a.nodeId,
                upLevel: false,
                siblingOffset: 0,
                renderParentIfLeaf: a.renderParentIfLeaf,
                forceRenderParent: a.forceRenderParent,
                offset,
                goToLastPage: false,
                forceIPFSRefresh: a.forceIPFSRefresh,
                singleNode: false,
                parentCount: ast.userPrefs.showParents ? 1 : 0,
                jumpToRss: a.jumpToRss
            });

            // if jumpToRss that means we don't want to display the node, but jump straight to the RSS Tab and display
            // the actual RSS feed that this node defines.
            if (a.jumpToRss && res?.rssNode) {
                dispatch("LoadingFeed", s => {
                    s.rssNode = res.node;
                    s.activeTab = C.TAB_RSS;
                    S.domUtil.focusId(C.TAB_RSS);
                    S.tabUtil.tabScroll(C.TAB_RSS, 0);
                });
                return;
            }

            if (!res || !res.success) {
                console.log("Unable to access node: " + a.nodeId);
                return;
            }

            if (C.DEBUG_SCROLLING) {
                console.log("refreshTree -> renderPage (scrollTop=" + a.scrollToTop + ")");
            }
            await S.render.renderPage(res, a.scrollToTop, a.highlightId, a.setTab, a.allowScroll);
        }
        catch (e) {
            S.util.logErr(e);
            S.nodeUtil.clearLastNodeIds();
        }
    }

    firstPage = () => {
        this.loadPage(false, 0, false);
    }

    prevPage = () => {
        const firstChild = S.edit.getFirstChildNode();
        if (firstChild?.logicalOrdinal > 0) {
            let targetOffset = firstChild.logicalOrdinal - J.ConstantInt.ROWS_PER_PAGE;
            if (targetOffset < 0) {
                targetOffset = 0;
            }

            this.loadPage(false, targetOffset, false);
        }
    }

    nextPage = () => {
        const lastChild = S.edit.getLastChildNode();
        if (lastChild) {
            const targetOffset = lastChild.logicalOrdinal + 1;
            this.loadPage(false, targetOffset, false);
        }
    }

    lastPage = () => {
        // nav.mainOffset += J.ConstantInt.ROWS_PER_PAGE;
        // this.loadPage(true, targetOffset, state);
    }

    /* As part of 'infinite scrolling', this gets called when the user scrolls to the end of a page and we
    need to load more records automatically, and add to existing page records */
    growPage = () => {
        const lastChild = S.edit.getLastChildNode();
        if (lastChild) {
            const targetOffset = lastChild.logicalOrdinal + 1;
            this.loadPage(false, targetOffset, true);
        }
    }

    /* Note: if growingPage==true we preserve the existing row data, and append more rows onto the current view */
    private loadPage = async (goToLastPage: boolean, offset: number, growingPage: boolean) => {
        const ast = getAs();
        try {
            const res = await S.rpcUtil.rpc<J.RenderNodeRequest, J.RenderNodeResponse>("renderNode", {
                nodeId: ast.node.id,
                upLevel: false,
                siblingOffset: 0,
                renderParentIfLeaf: true,
                forceRenderParent: false,
                offset,
                goToLastPage,
                forceIPFSRefresh: false,
                singleNode: false,
                parentCount: ast.userPrefs.showParents ? 1 : 0,
                jumpToRss: false
            });

            if (!res.node) return;

            // if this is an "infinite scroll" call to load in additional nodes
            if (growingPage) {
                /* if the response has some children, and we already have local children we can add to, and we haven't reached
                max dynamic rows yet, then make our children equal the concatenation of existing rows plus new rows */
                if (res?.node?.children && ast?.node?.children) {
                    // create a set for duplicate detection
                    const idSet: Set<string> = new Set<string>();

                    // load set for known children.
                    ast.node.children.forEach(child => idSet.add(child.id));

                    // assign 'res.node.chidren' as the new list appending in the new ones with dupliates removed.
                    res.node.children = ast.node.children.concat(res.node.children.filter(child => !idSet.has(child.id)));
                }
                S.render.renderPage(res, false, null, false, false);
            }
            // else, loading in a page which overrides and discards all existing nodes in browser view
            else {
                if (C.DEBUG_SCROLLING) {
                    console.log("loadPage -> renderPage (scrollTop=true)");
                }
                S.render.renderPage(res, true, null, true, true);
            }
        }
        catch (e) {
            S.util.logErr(e);
            S.nodeUtil.clearLastNodeIds();
        }
    }

    // NOTE: Method not still being used. Let's keep it for future reference
    // scrollRelativeToNode = (dir: string, ast : AppState) => {
    //     const currentSelNode: J.NodeInfo = S.nodeUtil.getHighlightedNode();
    //     if (!currentSelNode) return;

    //     let newNode: J.NodeInfo = null;

    //     // First detect if page root node is selected, before doing a child search
    //     if (currentSelNode.id === state.node.id) {
    //         // if going down that means first child node.
    //         if (dir === "down" && state.node.children?.length > 0) {
    //             newNode = state.node.children[0];
    //         }
    //         else if (dir === "up") {
    //             S.nav.navUpLevel(false);
    //         }
    //     }
    //     else if (state.node.children && state.node.children.length > 0) {
    //         let prevChild = null;
    //         let nodeFound = false;

    //         state.node.children.some((child: J.NodeInfo) => {
    //             let ret = false;

    //             if (nodeFound && dir === "down") {
    //                 ret = true;
    //                 newNode = child;
    //                 S.nodeUtil.highlightNode(child, true, state);
    //             }

    //             if (child.id === currentSelNode.id) {
    //                 if (dir === "up") {
    //                     if (prevChild) {
    //                         ret = true;
    //                         newNode = prevChild;
    //                     }
    //                     else {
    //                         newNode = state.node;
    //                     }
    //                 }
    //                 nodeFound = true;
    //             }
    //             prevChild = child;
    //             return ret; // NOTE: returning true stops the iteration.
    //         });
    //     }
    //     if (newNode) {
    //         dispatch("HighlightAndScrollToNode", s => {
    //             S.nodeUtil.highlightNode(newNode, true, s);
    //         });
    //     }
    // }

    scrollActiveToTop = () => {
        if (C.DEBUG_SCROLLING) {
            console.log("scrollAllTop");
        }
        const activeTabComp = S.tabUtil.getActiveTabComp();
        if (activeTabComp?.getRef()) {
            activeTabComp.setScrollTop(0);
        }
    }

    scrollToNode = (node: J.NodeInfo = null, delay: number = 100) => {
        if (!Comp.allowScrollSets || !node) return;

        const func = () => {
            setTimeout(() => {
                /* Check to see if we are rendering the top node (page root), and if so
                it is better looking to just scroll to zero index, because that will always
                be what user wants to see */
                node = node || S.nodeUtil.getHighlightedNode();

                /* the scrolling got slightly convoluted, so I invented 'editNodeId' just to be able to detect
                 a case where the user is editing a node and we KNOW we don't need to scroll after editing,
                 so this is where we detect and reset that scenario. */
                if (!node || node.id === S.quanta.noScrollToId) {
                    return;
                }

                if (getAs().node.id === node.id) {
                    this.scrollActiveToTop();
                    return;
                }

                const elm = S.domUtil.domElm(S.nav._UID_ROWID_PREFIX + node.id);
                if (elm) {
                    // ---------------------------
                    // scrollIntoView works, but is off a bit because we have a 'sticky' header covering up
                    // part of the window making scrollIntoView appear not to work.
                    // elm.scrollIntoView(true);
                    // ---------------------------
                    const data: TabIntf = S.tabUtil.getAppTabData(C.TAB_MAIN);
                    if (data) {
                        data.inst.scrollToElm(elm);
                    }
                }
            }, delay);
        };

        PubSub.subSingleOnce(C.PUBSUB_mainWindowScroll, () => {
            func();
        });
    }

    scrollToTop = async () => {
        PubSub.subSingleOnce(C.PUBSUB_mainWindowScroll, () => {
            this.scrollActiveToTop();
        });
    }

    getNodeStats = async (trending: boolean, feed: boolean): Promise<any> => {
        const ast = getAs();
        const node = S.nodeUtil.getHighlightedNode();
        const isMine = !!node && (node.owner === ast.userName || ast.userName === J.PrincipalName.ADMIN);

        const res = await S.rpcUtil.rpc<J.GetNodeStatsRequest, J.GetNodeStatsResponse>("getNodeStats", {
            nodeId: node ? node.id : null,
            trending,
            feed,
            getWords: isMine,
            getTags: isMine,
            getMentions: isMine,
            signatureVerify: false
        });
        new NodeStatsDlg(res, trending, feed).open();
    }

    signSubGraph = async (): Promise<any> => {
        if (!S.crypto.sigKeyOk()) {
            return null;
        }
        const node = S.nodeUtil.getHighlightedNode();
        await S.rpcUtil.rpc<J.SignSubGraphRequest, J.SignSubGraphResponse>("signSubGraph", {
            nodeId: node ? node.id : null
        });
        S.util.showMessage("Signature generation initiated. Leave this browser window open until notified signatures are complete.", "Signatures");
    }

    getNodeSignatureVerify = async (): Promise<any> => {
        const node = S.nodeUtil.getHighlightedNode();
        const res = await S.rpcUtil.rpc<J.GetNodeStatsRequest, J.GetNodeStatsResponse>("getNodeStats", {
            nodeId: node ? node.id : null,
            trending: false,
            feed: false,
            getWords: false,
            getTags: false,
            getMentions: false,
            signatureVerify: true
        });
        new NodeStatsDlg(res, false, false).open();
    }

    runServerCommand = async (command: string, parameter: string, dlgTitle: string, dlgDescription: string) => {
        const node = S.nodeUtil.getHighlightedNode();

        const res = await S.rpcUtil.rpc<J.GetServerInfoRequest, J.GetServerInfoResponse>("getServerInfo", {
            command,
            parameter,
            nodeId: node ? node.id : null
        });

        if (res.messages) {
            res.messages.forEach(m => {
                /* a bit confusing here but this command is the same as the name of the AJAX call above (getServerInfo), but
              there are other commands that exist also */
                if (command === "getServerInfo") {
                    m.message += "\nBrowser Memory: " + S.util.getBrowserMemoryInfo() + "\n";
                    m.message += "Build Time: " + BUILDTIME + "\n";
                    m.message += "Profile: " + PROFILE + "\n";
                }

                /* For now just prefix description onto the text. This will be made 'prettier' later todo-2 */
                if (dlgDescription) {
                    m.message = dlgDescription + "\n\n" + m.message;
                }

                dispatch("showServerInfo", s => {
                    S.tabUtil.tabChanging(s.activeTab, C.TAB_SERVERINFO);
                    s.activeTab = C.TAB_SERVERINFO;
                    s.serverInfoText = m.message;
                    s.serverInfoCommand = command;
                    s.serverInfoTitle = dlgTitle;
                });
            });
        }
    }
}

interface RefreshTreeArgs {
    nodeId: string;
    zeroOffset: boolean;
    renderParentIfLeaf: boolean;
    highlightId: string;
    forceIPFSRefresh: boolean;
    scrollToTop: boolean;
    allowScroll: boolean;
    setTab: boolean;
    forceRenderParent: boolean;
    jumpToRss: boolean;
}
