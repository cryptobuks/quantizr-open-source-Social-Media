import { getAs } from "../../AppContext";
import { Comp } from "../../comp/base/Comp";
import { ButtonBar } from "../../comp/core/ButtonBar";
import { Div } from "../../comp/core/Div";
import { IconButton } from "../../comp/core/IconButton";
import { Constants as C } from "../../Constants";
import { TabIntf } from "../../intf/TabIntf";
import * as J from "../../JavaIntf";
import { S } from "../../Singletons";

export class NodeCompMainList extends Div {
    constructor(private tabData: TabIntf<any>) {
        super(null, { key: "nodeCompMaiList" });
    }

    override preRender(): boolean {
        const ast = getAs();

        const children: Comp[] = [];
        if (ast.node?.children) {
            this.addPaginationButtons(children, ast.endReached, "", true);

            const orderByProp = S.props.getPropStr(J.NodeProp.ORDER_BY, ast.node);

            const isMineOrImAdmin = ast.isAdminUser || S.props.isMine(ast.node);
            const allowNodeMove: boolean = !orderByProp && isMineOrImAdmin;
            children.push(S.render.renderChildren(ast.node, this.tabData, 1, allowNodeMove));

            this.addPaginationButtons(children, ast.endReached, "marginTop marginBottom", false);
        }

        this.setChildren(children);
        return true;
    }

    addPaginationButtons = (children: Comp[], endReached: boolean, moreClasses: string, pageTop: boolean) => {
        let firstButton: IconButton;
        let prevButton: IconButton;
        let moreButton: IconButton;
        let prevNodeButton: IconButton;
        let nextNodeButton: IconButton;
        const firstChild = S.edit.getFirstChildNode();

        if (firstChild && firstChild.logicalOrdinal > 1) {
            firstButton = new IconButton("fa-angle-double-left", null, {
                onClick: () => S.view.firstPage(),
                title: "First Page"
            });
        }

        if (firstChild && firstChild.logicalOrdinal > 0) {
            prevButton = new IconButton("fa-angle-left", null, {
                onClick: () => S.view.prevPage(),
                title: "Previous Page"
            });
        }

        if (!endReached) {
            moreButton = new IconButton("fa-angle-right", "More", {
                onClick: (event: Event) => {
                    event.stopPropagation();
                    event.preventDefault();
                    S.view.nextPage();
                },
                title: "Next Page"
            });

            const buttonCreateTime: number = new Date().getTime();

            if (C.TREE_INFINITE_SCROLL && !pageTop) {
                // If nextButton is the one at the bottom of the page we watch it so we can dynamically load in
                // new content when it scrolls info view. What's happening here is that once
                // the nextButton scrolls into view, we load in more nodes!
                moreButton.onMount((elm: HTMLElement) => {
                    const observer = new IntersectionObserver(entries => {
                        /* We have to STILL check these conditions because this observer can be getting called any time
                         and these conditions will always apply about control if we want to grow page or not. */
                        const ast = getAs();

                        if (!ast.editNode) {
                            entries.forEach(entry => {
                                if (entry.isIntersecting) {
                                    // if this button comes into visibility within 2 seconds of it being created
                                    // that means it was rendered visible without user scrolling so in this case
                                    // we want to disallow the auto loading
                                    if (new Date().getTime() - buttonCreateTime < 2000) {
                                        observer.disconnect();
                                    }
                                    // otherwise the 'more' button came into view because the user had to have
                                    // scrolled to it, so we scroll in the new nodes to display (infinite scrolling)
                                    else {
                                        moreButton.replaceWithWaitIcon();
                                        S.view.growPage();
                                    }
                                }
                            });
                        }
                    });
                    observer.observe(elm);
                });
            }
        }
        else {
            // -----------------------
            // DO NOT DELETE!!
            // This code works perfecly but I realized I never use it AND more importantly a "Next Sibling on Tree" (and Prev)
            // is going to be too confusing to users, because they're too accustomed to "vertical" rather than "horizontal"
            // navigation so to speak...and next-sibiliong is definitely sort of "horizontal[ish]"
            // There are TWO places in the code I've removed this.
            //
            // const pageRootType = S.plugin.getType(ast.node.type);
            // if (!pageTop && !(pageRootType && pageRootType.isSpecialAccountNode()) && !S.nav.displayingRepositoryRoot(ast)) {
            //     prevNodeButton = new IconButton("fa-chevron-circle-left", "Prev", {
            //         onClick: S.nav.navToPrev,
            //         title: "Prev"
            //     });

            //     nextNodeButton = new IconButton("fa-chevron-circle-right", "Next", {
            //         onClick: S.nav.navToNext,
            //         title: "Next"
            //     });
            // }
        }

        if (firstButton || prevButton || moreButton) {
            children.push(new ButtonBar([firstButton, prevButton, moreButton], "marginBottom text-center " + moreClasses));
        }

        if (prevNodeButton || nextNodeButton) {
            children.push(new ButtonBar([prevNodeButton, nextNodeButton], "marginBottom text-center " + moreClasses));
        }
    }
}
