import { dispatch, getAs } from "../AppContext";
import { AppTab } from "../comp/AppTab";
import { Comp } from "../comp/base/Comp";
import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Checkbox } from "../comp/core/Checkbox";
import { Clearfix } from "../comp/core/Clearfix";
import { Div } from "../comp/core/Div";
import { Diva } from "../comp/core/Diva";
import { Divc } from "../comp/core/Divc";
import { FlexRowLayout } from "../comp/core/FlexRowLayout";
import { Heading } from "../comp/core/Heading";
import { IconButton } from "../comp/core/IconButton";
import { Selection } from "../comp/core/Selection";
import { Spinner } from "../comp/core/Spinner";
import { TabHeading } from "../comp/core/TabHeading";
import { TextContent } from "../comp/core/TextContent";
import { TextField } from "../comp/core/TextField";
import { Constants as C } from "../Constants";
import { DialogMode } from "../DialogBase";
import { EditNodeDlg } from "../dlg/EditNodeDlg";
import { TabIntf } from "../intf/TabIntf";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { FeedViewProps } from "./FeedViewProps";

export class FeedView extends AppTab<FeedViewProps, FeedView> {

    constructor(data: TabIntf<FeedViewProps, FeedView>) {
        super(data);
        data.inst = this;
    }

    override preRender(): boolean {
        const ast = getAs();

        /*
         * Number of rows that have actually made it onto the page to far. Note: some nodes get filtered out on the
         * client side for various reasons.
         */
        let rowCount = 0;
        const content = this.data.props.feedFilterRootNode ? S.nodeUtil.getShortContent(this.data.props.feedFilterRootNode) : null;
        let showBookmarkIcon: boolean = false;

        // set showBookmarkIcon visible if we don't already have it bookmarked
        if (this.data.props.feedFilterRootNode && ast.bookmarks) {
            showBookmarkIcon = !ast.bookmarks.find((bookmark: J.Bookmark): boolean => {
                return bookmark.id === this.data.props.feedFilterRootNode.id;
            });
        }

        const newItems: any = null;

        // todo-1: this is giving false positives at least in Nostr feed view so for now I'll disable this.
        // if ((this.data.props.feedDirty || this.data.props.feedDirtyList) && !this.data.props.feedLoading) {
        //     newItems = new Icon({
        //         className: "fa fa-lightbulb-o fa-lg feedDirtyIcon marginRight",
        //         title: "New content available. Refresh!"
        //     });
        // }

        /* If the user has 'tagged' one or more of their Friends Nodes (by setting a tag value in the editor)
        by editing one of their friends nodes, then we show a dropdown letting the user quickly choose which
        tag/category of friends they want to see the feed of */
        let friendsTagDropDown: Selection = null;
        if (ast.friendHashTags && ast.friendHashTags.length > 0 && this.data.props.name === J.Constant.FEED_FROMFRIENDS) {
            const items: any[] = [
                { key: "", val: "All Tags" }
            ];
            for (const tag of ast.friendHashTags) {
                items.push({ key: tag, val: tag });
            }

            friendsTagDropDown = new Selection(null, null, // "Filter By Tag",
                items,
                null, "friendsTagPickerOnView", {
                setValue: (val: string) => {
                    this.data.props.friendsTagSearch = val;
                    S.srch.refreshFeed();
                },
                getValue: (): string => this.data.props.friendsTagSearch
            });
        }

        const topChildren: Comp[] = [
            new Diva([
                ast.displayFeedSearch || this.data.props.searchTextState.getValue() ? new TextField({
                    val: this.data.props.searchTextState,
                    placeholder: "Search for...",
                    enter: S.srch.refreshFeed,
                    outterClass: "marginBottom feedSearchField"
                }) : null,
                new FlexRowLayout([
                    newItems,
                    // we show this button just as an icon unless the search field is displaying
                    new IconButton("fa-search", ast.displayFeedSearch ? "Search" : null, {
                        onClick: () => {
                            if (ast.displayFeedSearch) {
                                S.srch.refreshFeed()
                            }
                            else {
                                dispatch("DisplayFeedSearch", s => {
                                    s.displayFeedSearch = true;
                                });
                            }
                        },
                        title: "Search Feed"
                    }),
                    new IconButton("fa-refresh", null, {
                        onClick: S.srch.refreshFeed,
                        title: "Refresh Feed"
                    }),
                    this.data.props.searchTextState.getValue() //
                        ? new Button("Clear", () => this.clearSearch(), { className: "feedClearButton" }) : null,
                    showBookmarkIcon ? new IconButton("fa-bookmark", null, {
                        title: "Bookmark this Chat Room",
                        onClick: () => S.edit.addBookmark(this.data.props.feedFilterRootNode)
                    }) : null,
                    new Checkbox("Auto-refresh", { className: "bigMarginLeft" }, {
                        setValue: (checked: boolean) => S.edit.setAutoRefreshFeed(checked),
                        getValue: (): boolean => getAs().userPrefs.autoRefreshFeed
                    })
                ], "flexRowAlignBottom")
            ]),
            new Clearfix(),
            content ? new TextContent(content, "resultsContentHeading alert alert-secondary") : null
        ];

        // DO NOT DELETE (we may bring this back for some future purpose)
        // topChildren.push(new CollapsiblePanel("Options", "Options", null, [
        //     this.makeFilterButtonsBar(state)
        // ], false,
        //     (state: boolean) => {
        //         this.data.props.filterExpanded = state;
        //     }, this.data.props.filterExpanded, "", "", "", "span"));
        // DO NOT DELETE, Leave for future use, but for now this isn't worth the space it takes up and is even to small to easily click.
        // if (!state.userPrefs.nsfw) {
        //     topChildren.push(new Div("[Show Sensitive Content]", {
        //         className: "clickable",
        //         onClick: async () => {
        //             await S.edit.toggleNsfw(state);
        //             S.srch.refreshFeed();
        //         }
        //     }));
        //     topChildren.push(new Clearfix());
        // }

        const children: Comp[] = [];
        children.push(new Divc({ className: "tinyMarginBottom" }, topChildren));
        const childCount = this.data.props.feedResults ? this.data.props.feedResults.length : 0;

        // if we're editing an existing item determine that before starting to render rows.
        let editingExistingItem = false;
        if (ast.editNode && ast.editNodeOnTab === C.TAB_FEED) {
            editingExistingItem = this.data.props.feedResults.findIndex(n => n.id === ast.editNode.id) !== -1;
        }

        // if editing a new post (not a reply)
        if (!editingExistingItem && ast.editNode && ast.editNodeOnTab === C.TAB_FEED && !ast.editNodeReplyToId) {
            children.push(EditNodeDlg.embedInstance || //
                new EditNodeDlg(ast.editEncrypt, ast.editShowJumpButton, DialogMode.EMBED));
        }

        if (this.data.props.feedLoading && childCount === 0) {
            children.push(new Diva([
                new Divc({
                    className: "progressSpinner"
                }, [new Spinner()])
            ]));
        }
        else if (this.data.props.refreshCounter === 0) {
            // if user has never done a refresh at all yet, do the first one for them automatically.
            if (ast.activeTab === C.TAB_FEED) {
                setTimeout(() => {
                    S.srch.refreshFeed();
                }, 100);
            }
            else {
                children.push(new Heading(4, "Refresh when ready."));
            }
        }
        else if (!this.data.props.feedResults || this.data.props.feedResults.length === 0) {
            children.push(new Div("Nothing to display."));
            if (ast.userProfile?.blockedWords) {
                children.push(new Div("Note: The 'Blocked Words' defined in your Settings can affect this view."));
            }
        }
        else {
            let i = 0;

            // holds ids of all boosts (nodes BEING boosted)
            const boosts: Set<string> = new Set<string>();

            // scan all 'feedResults' to build up boosts set of IDs
            this.data.props.feedResults.forEach(node => {
                if (node.boostedNode) {
                    boosts.add(node.boostedNode.id);
                }
            });

            // finally here's where we render the feed items
            this.data.props.feedResults.forEach(node => {
                // if this node will be showing up as a boost don't display it on the page, skip it.
                if (boosts.has(node.id)) {
                    return;
                }

                // If we're editing this item right on the feed page, render the editor instead of the row
                if (editingExistingItem && node.id === ast.editNode.id) {
                    children.push(EditNodeDlg.embedInstance || //
                        new EditNodeDlg(ast.editEncrypt, ast.editShowJumpButton, DialogMode.EMBED));
                }
                // Otherwise render the item and *maybe* an editor below it (only if we're editing a reply to the node)
                else {
                    // console.log("FEED: node id=" + node.id + " content: " + node.content);
                    children.push(S.srch.renderSearchResultAsListItem(node, this.data, i, rowCount,
                        false, true, true, true, true, true, "userFeedItem", "userFeedItemHighlight", null));
                    i++;
                    rowCount++;

                    // editing a reply inline.
                    if (ast.editNode && ast.editNodeOnTab === C.TAB_FEED && ast.editNodeReplyToId === node.id) {
                        children.push(EditNodeDlg.embedInstance || //
                            new EditNodeDlg(ast.editEncrypt, ast.editShowJumpButton, DialogMode.EMBED));
                    }
                }
            });

            // only show "More" button if we aren't currently editing. Wouldn't make sense to navigate while editing.
            if (!ast.editNode && rowCount > 0 && !this.data.props.feedEndReached) {
                const moreButton = new IconButton("fa-angle-right", "More", {
                    onClick: (event: Event) => {
                        event.stopPropagation();
                        event.preventDefault();
                        S.srch.feed(++this.data.props.page, this.data.props.searchTextState.getValue(), false);
                    }
                });
                const buttonCreateTime: number = new Date().getTime();

                if (C.FEED_INFINITE_SCROLL) {
                    if (this.data.props.feedResults?.length < C.MAX_DYNAMIC_ROWS) {
                        // When the 'more' button scrolls into view go ahead and load more records.
                        moreButton.onMount((elm: HTMLElement) => {
                            const observer = new IntersectionObserver(entries => {

                                entries.forEach((entry: any) => {
                                    if (entry.isIntersecting) {
                                        // if this button comes into visibility within 2 seconds of it being created
                                        // that means it was rendered visible without user scrolling so in this case
                                        // we want to disallow the auto loading
                                        if (new Date().getTime() - buttonCreateTime < 2000) {
                                            observer.disconnect();
                                        }
                                        else {
                                            // console.log("Loading more...")
                                            moreButton.replaceWithWaitIcon();
                                            S.srch.feed(++this.data.props.page, this.data.props.searchTextState.getValue(), true);
                                        }
                                    }
                                });
                            });
                            observer.observe(elm);
                        });
                    }
                }
                children.push(new ButtonBar([moreButton], "text-center marginTop marginBottom"));
            }
        }

        this.setChildren([
            this.headingBar = new TabHeading([
                this.renderHeading(),
                this.data.props.feedFilterRootNode ? new IconButton("fa-arrow-left", null, {
                    onClick: () => S.view.jumpToId(this.data.props.feedFilterRootNode.id),
                    title: "Back to Folders View"
                }, "bigMarginLeft ") : null,
                new Divc({ className: "float-end" }, [
                    new Selection(null, null, [
                        { key: J.Constant.NETWORK_NOSTR, val: "Nostr" },
                        { key: J.Constant.NETWORK_ACTPUB, val: "ActivityPub" }
                    ],
                        null, "protocolPickerOnView", {
                        setValue: (val: string) => {
                            dispatch("setProtocol", s => {
                                s.protocolFilter = val;
                                S.localDB.setVal(C.LOCALDB_NETWORK_SELECTION, val);
                            });
                            S.srch.refreshFeed();
                        },
                        getValue: (): string => getAs().protocolFilter
                    }),
                    ast.isAnonUser ? null : friendsTagDropDown,
                    ast.isAnonUser ? null : new Button("Post", () => S.edit.addNode(null, this.data.props.feedFilterRootNode?.id, J.NodeType.COMMENT, false, null, null, null, null, true), {
                        title: this.data.props.feedFilterRootNode?.id ? "Post to this Chat Room" : "Post something to the Fediverse!"
                    }, "attentionButton")
                ])
            ]),
            new Divc({ className: "feedView" }, children)
        ]);
        return true;
    }

    /* overridable (don't use arrow function) */
    renderHeading(): CompIntf {
        return new Div(this.data.props.feedFilterRootNode ? "Chat Room" : this.getFeedSubHeading(this.data), { className: "tabTitle" });
    }

    getFeedSubHeading = (data: TabIntf<FeedViewProps>) => {
        let subHeading = null;

        if (data.props.feedFilterToDisplayName) {
            subHeading = "Interactions with " + data.props.feedFilterToDisplayName;
        }
        else if (data.props.feedFilterToUser) {
            subHeading = "Interactions with " + data.props.feedFilterToUser;
        }
        else {
            switch (data.props.name) {
                case J.Constant.FEED_TOFROMME:
                    subHeading = "To/From Me";
                    break;

                case J.Constant.FEED_TOME:
                    subHeading = "To Me";
                    break;

                case J.Constant.FEED_MY_MENTIONS:
                    subHeading = "My Mentions";
                    break;

                case J.Constant.FEED_FROMME:
                    subHeading = "From Me";
                    break;

                case J.Constant.FEED_FROMFRIENDS:
                    subHeading = "From Friends";
                    break;

                case J.Constant.FEED_LOCAL:
                    subHeading = "Local Users";
                    break;

                case J.Constant.FEED_PUB:
                    subHeading = "Public Feed";
                    break;

                default: break;
            }
        }

        return subHeading ? subHeading : "";
    }

    clearSearch = () => {
        if (this.data.props.searchTextState.getValue()) {
            this.data.props.searchTextState.setValue("");
            S.srch.refreshFeed();
        }
    }

    // DO NOT DELETE - may be needed in the future.
    // makeFilterButtonsBar = (ast : AppState): Div => {
    //     return new Divc({ className: "marginTop" }, [
    //         ast.isAnonUser ? null : new Checkbox("Friends", {
    //             title: "Include nodes posted by your friends"
    //         }, {
    //             setValue: (checked: boolean) => {
    //                 dispatch("SetFeedFilterType", s => {
    //                     this.data.props.feedFilterFriends = checked;
    //                 });
    //             },
    //             getValue: (): boolean => this.data.props.feedFilterFriends
    //         }),

    //         state.isAnonUser ? null : new Checkbox("To Me", {
    //             title: "Include nodes shares specifically to you (by name)"
    //         }, {
    //             setValue: (checked: boolean) => {
    //                 dispatch("SetFeedFilterType", s => {
    //                     this.data.props.feedFilterToMe = checked;
    //                 });
    //             },
    //             getValue: (): boolean => this.data.props.feedFilterToMe
    //         }),

    //         state.isAnonUser ? null : new Checkbox("From Me", {
    //             title: "Include nodes created by you"
    //         }, {
    //             setValue: (checked: boolean) => {
    //                 dispatch("SetFeedFilterType", s => {
    //                     this.data.props.feedFilterFromMe = checked;
    //                 });
    //             },
    //             getValue: (): boolean => this.data.props.feedFilterFromMe
    //         }),

    //         new Checkbox("Public", {
    //             title: "Include nodes shared to 'public' (everyone)"
    //         }, {
    //             setValue: (checked: boolean) => {
    //                 dispatch("SetFeedFilterType", s => {
    //                     this.data.props.feedFilterToPublic = checked;
    //                 });
    //             },
    //             getValue: (): boolean => this.data.props.feedFilterToPublic
    //         }),

    //         new Checkbox("Local", {
    //             title: "Include only nodes from accounts on this server."
    //         }, {
    //             setValue: (checked: boolean) => {
    //                 dispatch("SetFeedFilterType", s => {
    //                     this.data.props.feedFilterLocalServer = checked;

    //                     /* to help keep users probably get what they want, set 'public' also to true as the default
    //                      any time someone clicks 'Local' because that's the likely use case */
    //                     if (checked) {
    //                         this.data.props.feedFilterToPublic = true;
    //                     }
    //                 });
    //             },
    //             getValue: (): boolean => this.data.props.feedFilterLocalServer
    //         })
    //     ]);
    // }
}
