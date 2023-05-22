import { getAs } from "../AppContext";
import { AppTab } from "../comp/AppTab";
import { CompIntf } from "../comp/base/CompIntf";
import { Div } from "../comp/core/Div";
import { Diva } from "../comp/core/Diva";
import { IconButton } from "../comp/core/IconButton";
import { TabHeading } from "../comp/core/TabHeading";
import { Constants as C } from "../Constants";
import { TabIntf } from "../intf/TabIntf";
import * as J from "../JavaIntf";
import { RepliesRSInfo } from "../RepliesRSInfo";
import { S } from "../Singletons";

export class RepliesView<PT extends RepliesRSInfo> extends AppTab<PT, RepliesView<PT>> {

    constructor(data: TabIntf<PT, RepliesView<PT>>) {
        super(data);
        data.inst = this;
    }

    override preRender(): boolean {
        const ast = getAs();
        const results = this.data?.props?.results;
        if (!results) return false;

        /*
         * Number of rows that have actually made it onto the page to far. Note: some nodes get filtered out on the
         * client side for various reasons.
         */
        let rowCount = 0;
        let i = 0;
        const children: CompIntf[] = [
            this.headingBar = new TabHeading([
                new Div(this.data.name, { className: "tabTitle" }),
                new IconButton("fa-arrow-left", null, {
                    onClick: () => {
                        const ast = getAs();
                        if (ast.repliesViewFromTab === C.TAB_MAIN) {
                            // the jumpToId is the best way to get to a node on the main tab.
                            S.view.jumpToId(ast.repliesViewNodeId);
                        }
                        else {
                            S.tabUtil.selectTab(ast.repliesViewFromTab);
                            setTimeout(() => {
                                const data: TabIntf = S.tabUtil.getAppTabData(ast.repliesViewFromTab);
                                data.inst?.scrollToNode(ast.repliesViewNodeId);
                            }, 700);
                        }
                    },
                    title: "Go back..."
                }, "bigMarginLeft ")
            ]),
            this.data.props.description ? new Div(this.data.props.description) : null
        ];

        const jumpButton = ast.isAdminUser || !this.data.props.searchType;

        results.forEach(node => {
            const clazzName = ast.threadViewFromNode.id === node.id ? "threadFeedItemTarget" : "threadFeedItem";
            const highlightClazzName = ast.threadViewFromNode.id === node.id ? "threadFeedItemHighlightTarget" : "threadFeedItemHighlight";

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
                children.push(new Diva(subComps));
            }

            i++;
            rowCount++;
        });

        this.setChildren(children);
        return true;
    }

    /* overridable (don't use arrow function) */
    renderItem(node: J.NodeInfo, i: number, rowCount: number, jumpButton: boolean, clazz: string, highlightClazz: string): CompIntf {
        return S.srch.renderSearchResultAsListItem(node, this.data, i, rowCount, false,
            true, jumpButton, true, true, false, clazz, highlightClazz, null);
    }
}
