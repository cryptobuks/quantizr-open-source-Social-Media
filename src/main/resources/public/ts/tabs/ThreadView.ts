import { getAs } from "../AppContext";
import { AppTab } from "../comp/AppTab";
import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { Clearfix } from "../comp/core/Clearfix";
import { Div } from "../comp/core/Div";
import { Diva } from "../comp/core/Diva";
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
        let i = 0;
        const children: CompIntf[] = [
            this.headingBar = new TabHeading([
                new Div(this.data.name, { className: "tabTitle" }),
                new IconButton("fa-arrow-left", null, {
                    onClick: () => {
                        const ast = getAs();
                        if (ast.threadViewFromTab === C.TAB_MAIN) {
                            // the jumpToId is the best way to get to a node on the main tab.
                            S.view.jumpToId(ast.threadViewFromNode.id);
                        }
                        else {
                            S.tabUtil.selectTab(ast.threadViewFromTab);
                            setTimeout(() => {
                                const data: TabIntf = S.tabUtil.getAppTabData(ast.threadViewFromTab);
                                if (ast.threadViewFromNode && data.inst) {
                                    data.inst.scrollToNode(ast.threadViewFromNode.id);
                                }
                            }, 500);
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
                children.push(new Diva(subComps));
            }

            i++;
            rowCount++;
        });

        this.setChildren(children);
        return true;
    }

    moreHistory = () => {
        // todo: this is slightly inefficient but just load the whole thread here, and the server will notice it
        // it's dead ended on a nostr node and query for more of them
        S.srch.showThread(getAs().threadViewFromNode);
    }

    /* overridable (don't use arrow function) */
    renderItem(node: J.NodeInfo, i: number, rowCount: number, jumpButton: boolean, clazz: string, highlightClazz: string): CompIntf {
        return S.srch.renderSearchResultAsListItem(node, this.data, i, rowCount, false,
            true, jumpButton, true, true, false, clazz, highlightClazz, null);
    }
}
