import { dispatch, getAs, promiseDispatch } from "./AppContext";
import { AppState } from "./AppState";
import { Comp } from "./comp/base/Comp";
import { Clearfix } from "./comp/core/Clearfix";
import { Div } from "./comp/core/Div";
import { Divc } from "./comp/core/Divc";
import { NodeCompContent } from "./comp/node/NodeCompContent";
import { NodeCompRowFooter } from "./comp/node/NodeCompRowFooter";
import { NodeCompRowHeader } from "./comp/node/NodeCompRowHeader";
import { Constants as C } from "./Constants";
import { MessageDlg } from "./dlg/MessageDlg";
import { DocumentRSInfo } from "./DocumentRSInfo";
import { FollowersRSInfo } from "./FollowersRSInfo";
import { FollowingRSInfo } from "./FollowingRSInfo";
import { TabIntf } from "./intf/TabIntf";
import * as J from "./JavaIntf";
import { SharesRSInfo } from "./SharesRSInfo";
import { S } from "./Singletons";
import { DocumentTab } from "./tabs/data/DocumentTab";
import { FeedTab } from "./tabs/data/FeedTab";
import { FollowersTab } from "./tabs/data/FollowersTab";
import { FollowingTab } from "./tabs/data/FollowingTab";
import { RepliesTab } from "./tabs/data/RepliesTab";
import { SearchTab } from "./tabs/data/SearchTab";
import { SharesTab } from "./tabs/data/SharesTab";
import { ThreadTab } from "./tabs/data/ThreadTab";
import { TimelineTab } from "./tabs/data/TimelineTab";
import { TimelineRSInfo } from "./TimelineRSInfo";

export class Search {
    findSharedNodes = async (node: J.NodeInfo, page: number, type: string, shareTarget: string, accessOption: string) => {
        const res = await S.rpcUtil.rpc<J.GetSharedNodesRequest, J.GetSharedNodesResponse>("getSharedNodes", {
            page,
            nodeId: node.id,
            shareTarget,
            accessOption
        });

        if (res.searchResults?.length > 0) {
            dispatch("RenderSearchResults", s => {
                S.domUtil.focusId(C.TAB_SHARES);
                S.tabUtil.tabScroll(C.TAB_SHARES, 0);
                if (!SharesTab.inst) return;
                const info = SharesTab.inst.props as SharesRSInfo;

                info.results = res.searchResults;
                info.page = page;
                info.description = "Showing " + type + " shared nodes";
                info.node = node;
                info.shareTarget = shareTarget;
                info.accessOption = accessOption;
                info.endReached = !res.searchResults || res.searchResults.length < J.ConstantInt.ROWS_PER_PAGE;

                S.tabUtil.selectTabStateOnly(SharesTab.inst.id);
            });
        }
        else {
            S.util.showMessage("No search results found for " + type + " shared nodes", "Search");
        }
    }

    showThread = async (node: J.NodeInfo) => {
        // First call the server in case it has enough data already to render the Thread, in which case
        // we don't need to load any events from relays via client
        let res = await S.rpcUtil.rpc<J.GetThreadViewRequest, J.GetThreadViewResponse>("getNodeThreadView", {
            nodeId: node.id,
            loadOthers: true
        });

        // console.log("res=" + S.util.prettyPrint(res));

        // if we dead-ended on a nostr item we didn't have on server...load the data, and then attempt 'getNodeThreadView' again.
        if (res.nostrDeadEnd) {
            // get the node we dead ended at to resume from, or else if nothing at all was gotten from server
            // we resume from the actual 'node' we're trying to get Thread of.
            const resumeFromNode = res.nodes?.length > 0 ? res.nodes[0] : node;
            await S.nostr.loadReplyChain(resumeFromNode, 2);

            console.log("Dead End Repaired: Calling Server again: getNodeThreadView");
            res = await S.rpcUtil.rpc<J.GetThreadViewRequest, J.GetThreadViewResponse>("getNodeThreadView", {
                nodeId: node.id,
                loadOthers: true
            });
        }

        if (res.nodes?.length > 0) {
            dispatch("RenderThreadResults", s => {
                s.highlightSearchNodeId = node.id;

                S.domUtil.focusId(C.TAB_THREAD);
                S.tabUtil.tabScroll(C.TAB_THREAD, -1); // -1 scrolls to bottom

                const data = ThreadTab.inst;
                if (!data) return;

                // update threadViewFromTab only if NOT currently comming from thread tab itself
                if (s.activeTab !== C.TAB_THREAD) {
                    s.threadViewFromTab = s.activeTab;
                }
                s.threadViewFromNode = node;
                data.openGraphComps = [];

                data.props.results = res.nodes;

                // if apReplies then we set endReached to 'true' always.
                data.props.endReached = res.topReached && !res.nostrDeadEnd;
                S.tabUtil.selectTabStateOnly(data.id);
            });
        }
        else {
            // The most common known reason we can get here due to lack of feature support is when something like
            // a "object.type=Video", or "Question" type, (a type not yet supported) is encountered as we attempted to read the thread.
            let msg = "Thread not available, or contains unsupported post types.";

            // make 'msg' a little more specific if we know there's a 'remote link' showing.
            const objUrl = S.props.getPropStr(J.NodeProp.ACT_PUB_OBJ_URL, node);
            if (objUrl) {
                if (objUrl.indexOf(location.protocol + "//" + location.hostname) === -1) {
                    msg = "Top-level post. No conversation to display.";
                }
            }

            S.util.showMessage(msg, "Thread");
        }
    }

    showReplies = async (node: J.NodeInfo) => {
        const res = await S.rpcUtil.rpc<J.GetRepliesViewRequest, J.GetRepliesViewResponse>("getNodeRepliesView", {
            nodeId: node.id
        });

        if (res.nodes && res.nodes.length > 0) {
            dispatch("RenderRepliesResults", s => {
                s.highlightSearchNodeId = node.id;

                S.domUtil.focusId(C.TAB_REPLIES);
                S.tabUtil.tabScroll(C.TAB_REPLIES, -1); // -1 scrolls to bottom

                const data = RepliesTab.inst;
                if (!data) return;

                s.repliesViewFromTab = s.activeTab;
                s.repliesViewNodeId = node.id;
                data.openGraphComps = [];

                data.props.results = res.nodes;
                data.props.endReached = true;
                S.tabUtil.selectTabStateOnly(data.id);
            });
        }
        else {
            // The most common known reason we can get here due to lack of feature support is when something like
            // a "object.type=Video", or "Question" type, (a type not yet supported) is encountered as we attempted to read the thread.
            const msg = "Replies not available, or contains unsupported post types.";

            // make 'msg' a little more specific if we know there's a 'remote link' showing.
            // const objUrl = S.props.getPropStr(J.NodeProp.ACT_PUB_OBJ_URL, node);
            // if (objUrl) {
            //     if (objUrl.indexOf(location.protocol + "//" + location.hostname) === -1) {
            //         msg = "Top-level post. No conversation to display.";
            //     }
            // }

            S.util.showMessage(msg, "Replies");
        }
    }

    listSubgraphByPriority = async () => {
        const node = S.nodeUtil.getHighlightedNode();
        if (!node) {
            S.util.showMessage("No node is selected to search under.", "Warning");
            return;
        }
        this.search(node, null, null, null, "Priority Listing", null, false, false, 0, true,
            J.NodeProp.PRIORITY_FULL, "asc", true, false, false);
    }

    search = async (node: J.NodeInfo, prop: string, searchText: string, searchType: string, description: string,
        searchRoot: string, fuzzy: boolean, caseSensitive: boolean, page: number, recursive: boolean,
        sortField: string, sortDir: string, requirePriority: boolean, requireAttachment: boolean, deleteMatches: boolean): Promise<boolean> => {
        return new Promise<boolean>(async (resolve, reject) => {
            const res = await S.rpcUtil.rpc<J.NodeSearchRequest, J.NodeSearchResponse>("nodeSearch", {
                searchRoot,
                page,
                nodeId: node ? node.id : null, // for user searchTypes this node can be null
                searchText,
                sortDir,
                sortField,
                searchProp: prop,
                fuzzy,
                caseSensitive,
                searchType,
                searchDefinition: "",
                timeRangeType: null,
                recursive,
                requirePriority,
                requireAttachment,
                deleteMatches
            });

            if (res.success && deleteMatches) {
                S.util.showMessage("Matches were deleted.", "Warning");
                resolve(true);
            }

            if (res.searchResults && res.searchResults.length > 0) {
                resolve(true);
                dispatch("RenderSearchResults", s => {
                    S.domUtil.focusId(C.TAB_SEARCH);
                    S.tabUtil.tabScroll(C.TAB_SEARCH, 0);
                    const data = SearchTab.inst;
                    if (!data) return;

                    s.highlightText = searchText;

                    data.openGraphComps = [];

                    data.props.results = res.searchResults;
                    data.props.page = page;
                    data.props.searchType = searchType;
                    data.props.description = description;
                    data.props.node = node;
                    data.props.searchText = searchText;
                    data.props.fuzzy = fuzzy;
                    data.props.caseSensitive = caseSensitive;
                    data.props.recursive = recursive;
                    data.props.sortField = sortField;
                    data.props.requirePriority = requirePriority;
                    data.props.requireAttachment = requireAttachment;
                    data.props.sortDir = sortDir;
                    data.props.prop = prop;
                    data.props.endReached = !res.searchResults || res.searchResults.length < J.ConstantInt.ROWS_PER_PAGE;

                    S.tabUtil.selectTabStateOnly(data.id);

                    // DO NOT DELETE
                    // This was an experiment an it does work, but it only highlights one thing at a time, when I
                    // was hoping it would highlight ALL search results at once. So I think CTRL-F is superior.
                    // if (searchText) {
                    //     setTimeout(() => (window as any).find(searchText, false), 1000); //window.find
                    // }
                });
            }
            else {
                new MessageDlg("No search results found.", "Search", null, null, false, 0, null).open();
                resolve(false);
            }
        });
    }

    showDocument = async (node: J.NodeInfo, growPage: boolean) => {
        node = node || S.nodeUtil.getHighlightedNode();

        if (!node) {
            S.util.showMessage("Select a node to render a document", "Document View");
            return;
        }

        // set startNode to the last node we had at the end of the current document
        let startNode: J.NodeInfo = null;
        if (growPage) {
            const info = DocumentTab.inst.props as DocumentRSInfo;
            if (info.results?.length > 0) {
                startNode = info.results[info.results.length - 1];
            }
        }

        const res = await S.rpcUtil.rpc<J.RenderDocumentRequest, J.RenderDocumentResponse>("renderDocument", {
            rootId: node.id,
            startNodeId: startNode ? startNode.id : node.id,
            includeComments: getAs().userPrefs.showReplies
        });

        if (!res.searchResults || res.searchResults.length === 0) {
            dispatch("RenderDocumentResults", s => {
                DocumentTab.inst.openGraphComps = [];
                const info = DocumentTab.inst.props as DocumentRSInfo;
                info.endReached = true;
            });
            return;
        }

        dispatch("RenderDocumentResults", s => {
            S.domUtil.focusId(C.TAB_DOCUMENT);
            if (!DocumentTab.inst) return;
            DocumentTab.inst.openGraphComps = [];
            const info = DocumentTab.inst.props as DocumentRSInfo;

            if (!growPage) {
                info.endReached = false;
                S.tabUtil.tabScroll(C.TAB_DOCUMENT, 0);
            }

            // set 'results' if this is the top of page being rendered, or else append results if we
            // were pulling down more items at the end of the doc.
            info.results = growPage ? info.results.concat(res.searchResults) : res.searchResults;
            info.node = node;
            S.tabUtil.selectTabStateOnly(DocumentTab.inst.id);
        });
    }

    /* prop = mtm (modification time) | ctm (create time) */
    timeline = async (node: J.NodeInfo, prop: string, timeRangeType: string, timelineDescription: string, page: number, recursive: boolean) => {

        /* this code AND other similar code needs a way to lockin the node, here so it can't change during pagination
        including when the page==0 because user is just jumping to beginning. Need a specific param for saying
        it's ok to reset node or not */
        node = node || S.nodeUtil.getHighlightedNode();

        if (!node) {
            S.util.showMessage("No node is selected to 'timeline' under.", "Timeline");
            return;
        }

        const res = await S.rpcUtil.rpc<J.NodeSearchRequest, J.NodeSearchResponse>("nodeSearch", {
            searchRoot: null,
            page,
            nodeId: node.id,
            searchText: "",
            sortDir: "DESC",
            sortField: prop,
            searchProp: null,
            fuzzy: false,
            caseSensitive: false,
            searchDefinition: "timeline",
            searchType: null,
            timeRangeType,
            recursive,
            requirePriority: false,
            requireAttachment: false,
            deleteMatches: false
        });

        if (page === 0 && (!res.searchResults || res.searchResults.length === 0)) {
            S.util.showMessage("Nothing found", "Timeline");
            return;
        }

        dispatch("RenderTimelineResults", s => {
            S.domUtil.focusId(C.TAB_TIMELINE);
            S.tabUtil.tabScroll(C.TAB_TIMELINE, 0);
            if (!TimelineTab.inst) return;

            TimelineTab.inst.openGraphComps = [];
            const info = TimelineTab.inst.props as TimelineRSInfo;

            info.results = res.searchResults;
            info.description = timelineDescription;
            info.prop = prop;
            info.timeRangeType = timeRangeType;
            info.recursive = recursive;
            info.node = node;
            info.endReached = !res.searchResults || res.searchResults.length < J.ConstantInt.ROWS_PER_PAGE;
            info.page = page;

            S.tabUtil.selectTabStateOnly(TimelineTab.inst.id);
        });
    }

    removeNodeById = (id: string, ust: AppState) => {
        ust.tabData.forEach(td => td.nodeDeleted(ust, id));
    }

    refreshFeed = async () => {
        if (FeedTab.inst) {
            FeedTab.inst.props.page = 0;
            FeedTab.inst.props.refreshCounter++;
        }

        // If user enters the url for a fediverse page we try to load it and display it.
        const searchText = FeedTab.inst.props.searchTextState.getValue();
        if (searchText?.indexOf("https://") === 0) {
            this.loadSingleFeedItemByUrl(searchText);
            return;
        }

        await promiseDispatch("RefreshFeed", s => {
            FeedTab.inst.props.feedLoading = true;
            if (!searchText) {
                s.displayFeedSearch = false;
            }
        });

        this.feed(FeedTab.inst.props.page, searchText, false);
    }

    loadSingleFeedItemByUrl = async (url: string) => {
        const res = await S.rpcUtil.rpc<J.GetActPubObjectRequest, J.GetActPubObjectResponse>("loadActPubObject", {
            url
        });

        if (res.success) {
            dispatch("RenderFeedResults", s => {
                FeedTab.inst.openGraphComps = [];

                // once user requests their stuff, turn off the new messages count indicator.
                if (FeedTab.inst.props.feedFilterToMe) {
                    s.myNewMessageCount = 0;
                }

                s.nostrNewMessageCount = 0;

                FeedTab.inst.props.feedResults = [res.node];
                FeedTab.inst.props.feedEndReached = true;
                FeedTab.inst.props.feedDirty = false;
                FeedTab.inst.props.feedLoading = false;

                S.tabUtil.tabScroll(C.TAB_FEED, 0);
                S.tabUtil.selectTabStateOnly(C.TAB_FEED);

                S.domUtil.focusId(C.TAB_FEED);
            });
        }
    }

    /* growResults==true is the "infinite scrolling" support */
    feed = async (page: number, searchText: string, growResults: boolean) => {
        const ast = getAs();
        if (!FeedTab.inst) {
            return;
        }

        const loadFriendsTags: boolean = ast.friendHashTags === null;

        const res = await S.rpcUtil.rpc<J.NodeFeedRequest, J.NodeFeedResponse>("nodeFeed", {
            page,
            nodeId: FeedTab.inst.props.feedFilterRootNode?.id,
            toMe: FeedTab.inst.props.feedFilterToMe,
            myMentions: FeedTab.inst.props.feedFilterMyMentions,
            fromMe: FeedTab.inst.props.feedFilterFromMe,
            toUser: FeedTab.inst.props.feedFilterToUser,
            toPublic: FeedTab.inst.props.feedFilterToPublic,
            localOnly: FeedTab.inst.props.feedFilterLocalServer,
            name: FeedTab.inst.props.name,
            fromFriends: FeedTab.inst.props.feedFilterFriends,
            nsfw: ast.isAnonUser ? false : ast.userPrefs.nsfw, // never show anonymous users NSFW content.
            searchText,
            friendsTagSearch: FeedTab.inst.props.friendsTagSearch,
            loadFriendsTags,
            applyAdminBlocks: FeedTab.inst.props.applyAdminBlocks,
            protocol: ast.protocolFilter
        });

        dispatch("RenderFeedResults", s => {
            FeedTab.inst.openGraphComps = [];
            // s.feedResults = S.quanta.removeRedundantFeedItems(res.searchResults || []);

            // once user requests their stuff, turn off the new messages count indicator.
            if (FeedTab.inst.props.feedFilterToMe) {
                s.myNewMessageCount = 0;
            }
            s.nostrNewMessageCount = 0;

            let scrollToTop = true;

            // if scrolling in new results grow the existing array
            if (growResults) {
                if (FeedTab.inst?.props?.feedResults && res?.searchResults && FeedTab.inst.props.feedResults.length < C.MAX_DYNAMIC_ROWS) {
                    // create a set for duplicate detection
                    const idSet: Set<string> = new Set<string>();

                    // load set for known children.
                    FeedTab.inst.props.feedResults.forEach(child => idSet.add(child.id));

                    scrollToTop = false;
                    FeedTab.inst.props.feedResults = FeedTab.inst.props.feedResults.concat(res.searchResults.filter(child => !idSet.has(child.id)));
                }
                else {
                    FeedTab.inst.props.feedResults = res.searchResults;
                }
            }
            // else we have a fresh array (reset the array)
            else {
                FeedTab.inst.props.feedResults = res.searchResults;
            }

            FeedTab.inst.props.feedEndReached = res.endReached;
            FeedTab.inst.props.feedDirty = false;
            FeedTab.inst.props.feedLoading = false;

            if (res.friendHashTags) {
                s.friendHashTags = res.friendHashTags;
            }

            if (scrollToTop) {
                S.tabUtil.tabScroll(C.TAB_FEED, 0);
                S.tabUtil.selectTabStateOnly(C.TAB_FEED);
            }

            S.domUtil.focusId(C.TAB_FEED);
        });
    }

    showFollowers = async (page: number, userName: string) => {
        const ast = getAs();
        if (ast.isAnonUser) return;
        userName = userName || ast.userName;

        const res = await S.rpcUtil.rpc<J.GetFollowersRequest, J.GetFollowersResponse>("getFollowers", {
            page,
            targetUserName: userName
        });

        if (res.searchResults?.length > 0) {
            dispatch("RenderSearchResults", s => {
                S.domUtil.focusId(C.TAB_FOLLOWERS);
                S.tabUtil.tabScroll(C.TAB_FOLLOWERS, 0);
                const data = FollowersTab.inst;
                if (!data) return;
                const info = data.props as FollowersRSInfo;

                info.results = res.searchResults;
                info.page = page;
                info.searchType = null;
                info.description = null;
                info.node = null;
                info.searchText = null;
                info.fuzzy = false;
                info.caseSensitive = false;
                info.prop = null;
                info.endReached = !res.searchResults || res.searchResults.length < J.ConstantInt.ROWS_PER_PAGE;
                info.showingFollowersOfUser = userName;

                S.tabUtil.selectTabStateOnly(data.id);
            });
        }
        else {
            S.util.showMessage("No search results found.", "Followers");
        }
    }

    showFollowing = async (page: number, userName: string) => {
        const ast = getAs();
        if (ast.isAnonUser) return;
        userName = userName || ast.userName;

        const res = await S.rpcUtil.rpc<J.GetFollowingRequest, J.GetFollowingResponse>("getFollowing", {
            page,
            targetUserName: userName
        });

        if (res.searchResults && res.searchResults.length > 0) {
            dispatch("RenderSearchResults", s => {
                S.domUtil.focusId(C.TAB_FOLLOWING);
                S.tabUtil.tabScroll(C.TAB_FOLLOWING, 0);
                const data = FollowingTab.inst;
                if (!data) return;
                const info = data.props as FollowingRSInfo;

                info.results = res.searchResults;
                info.page = page;
                info.searchType = null;
                info.description = null;
                info.node = null;
                info.searchText = null;
                info.fuzzy = false;
                info.caseSensitive = false;
                info.prop = null;
                info.endReached = !res.searchResults || res.searchResults.length < J.ConstantInt.ROWS_PER_PAGE;
                info.showingFollowingOfUser = userName;

                S.tabUtil.selectTabStateOnly(data.id);
            });
        }
        else {
            S.util.showMessage("No search results found.", "Following");
        }
    }

    /*
     * Renders a single line of search results on the search results page.
     */
    renderSearchResultAsListItem = (node: J.NodeInfo, tabData: TabIntf<any>, index: number, rowCount: number,
        isParent: boolean, allowAvatars: boolean, jumpButton: boolean, allowHeader: boolean,
        allowFooter: boolean, showThreadButton: boolean, outterClass: string, outterClassHighlight: string,
        extraStyle: any): Comp => {
        const ast = getAs();
        if (!node) return;
        const prefix = tabData.id;

        // render with info bar, etc always, if this is a threaview or freed tab.
        const isFeed = tabData.id === C.TAB_THREAD || tabData.id === C.TAB_FEED || tabData.id === C.TAB_REPLIES;
        if (isFeed && allowFooter) {
            allowFooter = true;
        }

        /* If there's a parent on this node it's a 'feed' item and this parent is what the user was replyig to so we display it just above the
        item we are rendering */
        let parentItem: Comp = null;
        if (node.parent) {
            parentItem = this.renderSearchResultAsListItem(node.parent, tabData, index, rowCount, true, allowAvatars, jumpButton, allowHeader, allowFooter, showThreadButton, outterClass, outterClassHighlight, extraStyle);
        }

        const content = new NodeCompContent(node, tabData, true, true, prefix, true, false, false, null);
        let clazz = isFeed ? "feedNode" : "resultsNode";
        if (S.render.enableRowFading && S.render.fadeInId === node.id && S.render.allowFadeInId) {
            S.render.fadeInId = null;
            S.render.allowFadeInId = false;
            clazz += " fadeInRowBkgClz";
            S.quanta.fadeStartTime = new Date().getTime();
        }

        if (isParent) {
            clazz += " inactiveFeedRowParent";
        }
        else {
            if (parentItem) {
                clazz += " inactiveFeedRow";
            }
        }

        const allowDelete = tabData.id !== C.TAB_DOCUMENT;

        let boostComp: Div = null;
        if (node.boostedNode) {
            const boostContent = new NodeCompContent(node.boostedNode, tabData, true, true, prefix + "-boost", true, false, true, "feedBoost");

            let allowBoostFooter = isFeed;
            if (isFeed) {
                allowBoostFooter = true;
            }
            boostComp = new Divc({
                onClick: async () => {
                    S.util.updateNodeHistory(node.boostedNode, true);

                    // after updating state we need this to ensure this click also focused this window.
                    S.domUtil.focusId(tabData.id);
                },
                className: "boostRowOnFeed"
            }, [
                allowHeader ? new NodeCompRowHeader(node, node.boostedNode, true, false, tabData, jumpButton, showThreadButton, true, allowDelete) : null,
                allowHeader ? new Clearfix() : null,
                boostContent,
                allowBoostFooter ? new NodeCompRowFooter(node.boostedNode) : null,
                allowBoostFooter ? new Clearfix() : null
            ])
        }

        // ------------------------------------
        // DO NOT DELETE (This can display actual linkedNodes inline, but I decided for now we should just
        // render the link text (clickable links) and not embed any content like this)
        // let linkedNodesComp: Div = null;
        // if (node.linkedNodes) {
        //     // all the styles etc in here need to be changed from boost to nodeLinks
        //     node.linkedNodes.forEach(n => {
        //         const linkContent = new NodeCompContent(n, tabData, true, true, prefix + "-boost", true, false, true, "feedBoost");

        //         let allowFooter = isFeed;
        //         if (isFeed) {
        //             allowFooter = ast.showAllRowDetails.has(n.id);
        //         }
        //         linkedNodesComp = new Divc({ className: "boostRow" }, [
        //             allowHeader ? new NodeCompRowHeader(n, true, false, tabData, jumpButton, showThreadButton, true, allowDelete) : null,
        //             linkContent,
        //             allowFooter ? new NodeCompRowFooter(n) : null,
        //             allowFooter ? new Clearfix() : null
        //         ])
        //     });
        // }
        // ------------------------------------

        // this divClass goes on the parent if we have a parentItem, or else on the 'itemDiv' itself if we don't
        let divClass: string = ast.highlightSearchNodeId === node.id ? outterClassHighlight : outterClass;
        divClass = divClass || "";

        const attrs: any = {
            // yes the 'tabData.id' looks odd here as a class, and it's only used for lookups for scrolling logic.
            className: clazz + (parentItem ? "" : (" " + divClass)) + " " + tabData.id,
            id: S.tabUtil.makeDomIdForNode(tabData, node.id),
            [C.NODE_ID_ATTR]: node.id,
            onClick: async () => {
                S.util.updateNodeHistory(node, true);

                // after updating state we need this to ensure this click also focused this window.
                S.domUtil.focusId(tabData.id);
            }
        };

        if (extraStyle) {
            attrs.style = extraStyle;
        }

        // special case, if node is owned by admin and we're not admin, never show header, unless the ALLOW flag is true
        if (!C.ALLOW_ADMIN_NODE_HEADERS && node.owner === J.PrincipalName.ADMIN && ast.userName !== J.PrincipalName.ADMIN) {
            allowHeader = false;
        }

        const itemDiv = new Divc(attrs, [
            S.render.renderBoostHeader(node, false),
            allowHeader && !node.boostedNode ? new NodeCompRowHeader(null, node, true, false, tabData, jumpButton, showThreadButton, false, allowDelete) : null,
            allowHeader && !node.boostedNode ? new Clearfix() : null,
            content,
            boostComp,
            S.render.renderLinks(node),
            allowFooter ? new NodeCompRowFooter(node) : null,
            allowFooter ? new Clearfix() : null
        ]);

        // if we have a parentItem wrap it and 'itemDiv' in a container Div
        if (parentItem) {
            return new Divc({
                className: isParent ? "userFeedItemParent" : divClass,
                // the "_p_" differentiates the parent from the 'div' which is just "_" delimeter (see above)
                id: tabData.id + "_p_" + node.id
            }, [parentItem, itemDiv]);
        }
        // othwersize 'div' itself is what we need to return, without an unnecessary div wrapping it.
        else {
            return itemDiv;
        }
    }

    clickSearchNode = async (id: string) => {
        await S.view.jumpToId(id);

        dispatch("RenderSearchResults", s => {
            s.highlightSearchNodeId = id;
        });
    }

    searchAndReplace = async (recursive: boolean, nodeId: string, search: string, replace: string) => {
        const res = await S.rpcUtil.rpc<J.SearchAndReplaceRequest, J.SearchAndReplaceResponse>("searchAndReplace", {
            recursive,
            nodeId,
            search,
            replace
        });

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
        S.util.showMessage(res.message, "Success");
    }

    /* If target is non-null we only return shares to that particlar person (or public) */
    findShares = (shareTarget: string = null, accessOption: string = null) => {
        const focusNode = S.nodeUtil.getHighlightedNode();
        if (!focusNode) {
            return;
        }

        let type = "all";
        if (accessOption === J.PrivilegeType.READ) {
            type = "read-only";
        }
        else if (accessOption === J.PrivilegeType.WRITE) {
            type = "appendable";
        }

        this.findSharedNodes(focusNode, 0, type, shareTarget, accessOption);
    }
}
