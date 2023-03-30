import { getAs } from "../AppContext";
import { AppTab } from "../comp/AppTab";
import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { Clearfix } from "../comp/core/Clearfix";
import { Div } from "../comp/core/Div";
import { IconButton } from "../comp/core/IconButton";
import { TabHeading } from "../comp/core/TabHeading";
import { Constants as C } from "../Constants";
import { TabIntf } from "../intf/TabIntf";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { ThreadRSInfo } from "../ThreadRSInfo";

export class ThreadView<PT extends ThreadRSInfo> extends AppTab<PT, ThreadView<PT>> {

    constructor(data: TabIntf<PT, ThreadView<PT>>) {
        super(data);
        data.inst = this;
    }

    preRender(): boolean {
        const ast = getAs();
        const results = this.data?.props?.results;
        if (!results) return;

        /*
         * Number of rows that have actually made it onto the page to far. Note: some nodes get filtered out on the
         * client side for various reasons.
         */
        let rowCount = 0;
        let i = 0;
        const children: CompIntf[] = [
            this.headingBar = new TabHeading([
                new Div(this.data.name + " / Hierarchy", { className: "tabTitle" }),
                new IconButton("fa-arrow-left", null, {
                    onClick: () => {
                        const ast = getAs();
                        if (ast.threadViewFromTab === C.TAB_MAIN) {
                            // the jumpToId is the best way to get to a node on the main tab.
                            S.view.jumpToId(ast.threadViewNodeId);
                        }
                        else {
                            S.tabUtil.selectTab(ast.threadViewFromTab);
                            setTimeout(() => {
                                const data: TabIntf = S.tabUtil.getAppTabData(ast.threadViewFromTab);
                                data.inst?.scrollToNode(ast.threadViewNodeId);
                            }, 700);
                        }
                    },
                    title: "Go back..."
                }, "bigMarginLeft "),
                !this.data.props.endReached ? new Button("More History...", () => { this.moreHistory() },
                    { className: "float-end tinyMarginBottom" }, "btn-primary") : null,

                new Clearfix()
            ]),
            this.data.props.description ? new Div(this.data.props.description) : null
        ];

        const jumpButton = ast.isAdminUser || !this.data.props.searchType;

        results.forEach(node => {
            const clazzName = ast.repliesViewNodeId === node.id ? "threadFeedItemTarget" : "threadFeedItem";
            const highlightClazzName = ast.repliesViewNodeId === node.id ? "threadFeedItemHighlightTarget" : "threadFeedItemHighlight";

            const c = this.renderItem(node, i, rowCount, jumpButton, clazzName, highlightClazzName);
            if (c) {
                children.push(c);
            }

            if (node.children) {
                const subComps: CompIntf[] = [];
                node.children.forEach(child => {
                    const c = this.renderItem(child, i, rowCount, jumpButton, "threadFeedSubItem", "threadFeedItemHighlight");
                    if (c) {
                        subComps.push(c);
                    }
                });
                children.push(new Div(null, null, subComps));
            }

            i++;
            rowCount++;
        });

        this.setChildren(children);
        return true;
    }

    moreHistory = () => {
        const results = this.data?.props?.results;
        if (!results || results.length === 0) return;
        S.srch.showThreadAddMore(results[0].id);
    }

    /* overridable (don't use arrow function) */
    renderItem(node: J.NodeInfo, i: number, rowCount: number, jumpButton: boolean, clazz: string, highlightClazz: string): CompIntf {
        return S.srch.renderSearchResultAsListItem(node, this.data, i, rowCount, false,
            true, jumpButton, true, true, false, clazz, highlightClazz, null);
    }
}
