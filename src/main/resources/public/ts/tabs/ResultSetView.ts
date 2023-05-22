import { getAs } from "../AppContext";
import { AppTab } from "../comp/AppTab";
import { Comp } from "../comp/base/Comp";
import { CompIntf } from "../comp/base/CompIntf";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Clearfix } from "../comp/core/Clearfix";
import { Div } from "../comp/core/Div";
import { IconButton } from "../comp/core/IconButton";
import { Span } from "../comp/core/Span";
import { TabHeading } from "../comp/core/TabHeading";
import { TextContent } from "../comp/core/TextContent";
import { Constants as C } from "../Constants";
import { DialogMode } from "../DialogBase";
import { EditNodeDlg } from "../dlg/EditNodeDlg";
import { TabIntf } from "../intf/TabIntf";
import * as J from "../JavaIntf";
import { ResultSetInfo } from "../ResultSetInfo";
import { S } from "../Singletons";

export abstract class ResultSetView<PT extends ResultSetInfo, TT extends AppTab> extends AppTab<PT, TT> {

    allowTopMoreButton: boolean = true;
    allowHeader: boolean = true;
    allowFooter: boolean = true;
    showContentHeading: boolean = true;
    pagingContainerClass: string = "marginBottom marginTop"; // used to have 'text-center' to center buttons

    constructor(data: TabIntf<PT, TT>, private showRoot: boolean = true, private showPageNumber: boolean = true, private infiniteScrolling = false) {
        super(data);
        if (infiniteScrolling && showPageNumber) {
            throw new Error("page numbering incompatable with infinite scrolling")
        }
    }

    override preRender(): boolean {
        const ast = getAs();
        const results = this.data?.props?.results;
        if (!results) {
            this.setChildren([new Div("Nothing found.")]);
            return true;
        };

        /*
         * Number of rows that have actually made it onto the page to far. Note: some nodes get filtered out on the
         * client side for various reasons.
         */
        let rowCount = 0;

        let content = null;
        if (this.showContentHeading && //
            this.data.props.prop !== "node.id" && //
            this.data.props.prop !== "node.name") {
            content = this.data.props.node ? S.nodeUtil.getShortContent(this.data.props.node) : null;
        }

        const children: CompIntf[] = [
            this.headingBar = new TabHeading([
                // include back button if we have a central node this panel is about.
                this.renderHeading(),

                (ast.searchViewFromTab || this.data.props.node) && this.showContentHeading
                    ? new IconButton("fa-arrow-left", "", {
                        onClick: () => {
                            if (this.data.props.node) {
                                S.view.jumpToId(this.data.props.node.id);
                            }
                            else if (ast.searchViewFromTab) {
                                S.tabUtil.selectTab(ast.searchViewFromTab);
                                setTimeout(() => {
                                    const data: TabIntf = S.tabUtil.getAppTabData(ast.searchViewFromTab);
                                    if (ast.searchViewFromNode && data.inst) {
                                        data.inst.scrollToNode(ast.searchViewFromNode.id);
                                    }
                                }, 500);
                            }
                        },
                        title: "Back to Folders View"
                    }, "bigMarginLeft") : null,
                this.getFloatRightHeaderComp()
            ]),
            this.showRoot && content ? new TextContent(content, "resultsContentHeading alert alert-secondary") : null,
            this.data.props.description ? new Div(this.data.props.description) : null
        ];

        // this shows the page number. not needed. used for debugging.
        // children.push(new Div("" + data.rsInfo.page + " endReached=" + data.rsInfo.endReached));
        this.addPaginationBar(children, false, this.allowTopMoreButton, true);

        let i = 0;
        const jumpButton = ast.isAdminUser || !this.data.props.searchType;

        results.forEach(node => {
            if (ast.nodesToMove && ast.nodesToMove.find(n => n === node.id)) return;
            if (ast.editNode && ast.editNode.id === node.id && ast.editNodeOnTab === this.data.id) {
                children.push(EditNodeDlg.embedInstance || //
                    new EditNodeDlg(ast.editEncrypt, ast.editShowJumpButton, DialogMode.EMBED));
            }
            else {
                const c = this.renderItem(node, i, rowCount, jumpButton);
                if (c) {
                    if (ast.userPrefs.editMode && !ast.editNode && !ast.inlineEditId) {
                        S.domUtil.setNodeDragHandler(c.attribs, node.id);
                        S.domUtil.makeDropTarget(c.attribs, node.id);
                    }
                    children.push(c);
                }
            }
            i++;
            rowCount++;
        });

        this.addPaginationBar(children, true, true, false);
        this.setChildren(children);
        return true;
    }

    /* overridable (don't use arrow function) */
    renderHeading(): CompIntf {
        return new Div(this.data.name, { className: "tabTitle" });
    }

    /* overridable (don't use arrow function) */
    // Note: It's important to have 'this.data.id' as a classname on every item, even though it's not for styling,
    // it's essentially to support DOM finding.
    renderItem(node: J.NodeInfo, i: number, rowCount: number, jumpButton: boolean): CompIntf {
        const ast = getAs();
        const allowHeader = this.allowHeader && (S.util.showMetaData(ast, node) || ast.userPrefs.editMode);
        return S.srch.renderSearchResultAsListItem(node, this.data, i, rowCount, false, true,
            jumpButton, allowHeader, this.allowFooter, true, "userFeedItem",
            "userFeedItemHighlight", null);
    }

    addPaginationBar = (children: CompIntf[], allowInfiniteScroll: boolean, allowMoreButton: boolean, isTopBar: boolean) => {
        let moreButton: IconButton = null;
        if (!this.data.props.endReached && allowMoreButton) {
            moreButton = new IconButton("fa-angle-right", "More", {
                onClick: () => this.pageChange(1),
                title: "Next Page"
            })

            if (allowInfiniteScroll && this.infiniteScrolling && C.FEED_INFINITE_SCROLL) {
                const buttonCreateTime: number = new Date().getTime();
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
                                    moreButton.replaceWithWaitIcon()
                                    // console.log("Loading more...");
                                    this.pageChange(1);
                                }
                            }
                        });
                    });
                    observer.observe(elm);
                });
            }
        }

        const extraPagingComps = isTopBar ? this.extraPagingComps() : null;

        children.push(
            this.showPageNumber ? new Span("Pg. " + (this.data.props.page + 1), { className: "float-end" }) : null,
            new ButtonBar([
                isTopBar ? new IconButton("fa-refresh", null, {
                    onClick: () => this.pageChange(null),
                    title: "Refresh View"
                }) : null,
                this.data.props.page > 1 ? new IconButton("fa-angle-double-left", null, {
                    onClick: () => this.pageChange(0),
                    title: "First Page"
                }) : null,
                this.data.props.page > 0 ? new IconButton("fa-angle-left", null, {
                    onClick: () => this.pageChange(-1),
                    title: "Previous Page"
                }) : null,
                moreButton,
                ...(extraPagingComps || []),
                this.data.props.endReached && !isTopBar && this.showPageNumber ? new Span("*** Last Page ***", { className: "bigMarginLeft" }) : null
            ], this.pagingContainerClass));

        children.push(new Clearfix());
    }

    abstract pageChange(delta: number): void;
    abstract extraPagingComps(): Comp[];
    abstract getFloatRightHeaderComp(): Comp;
}
