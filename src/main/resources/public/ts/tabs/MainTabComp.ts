import { getAs } from "../AppContext";
import { AppTab } from "../comp/AppTab";
import { CompIntf } from "../comp/base/CompIntf";
import { BreadcrumbsPanel } from "../comp/BreadcrumbsPanel";
import { Clearfix } from "../comp/core/Clearfix";
import { Div } from "../comp/core/Div";
import { Divc } from "../comp/core/Divc";
import { Heading } from "../comp/core/Heading";
import { Html } from "../comp/core/Html";
import { Icon } from "../comp/core/Icon";
import { Progress } from "../comp/core/Progress";
import { TabHeading } from "../comp/core/TabHeading";
import { NodeCompMainList } from "../comp/node/NodeCompMainList";
import { NodeCompMainNode } from "../comp/node/NodeCompMainNode";
import { NodeCompParentNodes } from "../comp/node/NodeCompParentNodes";
import { Constants as C } from "../Constants";
import { TabIntf } from "../intf/TabIntf";
import { S } from "../Singletons";

export class MainTabComp extends AppTab<any, MainTabComp> {

    constructor(data: TabIntf<any, MainTabComp>) {
        super(data, null);
        this.attribs.key = "mainTabCompKey";
        data.inst = this;
    }

    override preRender(): boolean {
        const ast = getAs();

        let contentDiv: CompIntf = null;
        if (S.quanta.configRes.loadNostrId) {
            contentDiv = new Progress();
        }
        else if (S.quanta.configRes.urlIdFailMsg) {
            contentDiv = new Heading(4, S.quanta.configRes.urlIdFailMsg);
        }
        else if (!ast.node) {
            contentDiv = null;
        }
        else {
            // DO NOT DELETE (currently we have the 'show parents' button that ALWAYS needs to be available so we
            // will always render the BreadcrumbesPanel)
            // let renderableCrumbs = 0;
            // if (state.breadcrumbs) {
            //     state.breadcrumbs.forEach(bc => {
            //         if (bc.id !== state.node.id && bc.id !== state.userProfile.userNodeId) {
            //             renderableCrumbs++;
            //         }
            //     });
            // }

            contentDiv = new Divc({
                // This visibility setting makes the main content not visible until final scrolling is complete
                className: ast.rendering ? "compHidden" : "compVisible"
            }, [
                !ast.mobileMode ? new BreadcrumbsPanel() : null,
                ast.pageMessage ? new Html(ast.pageMessage, { className: "alert alert-info float-end" }) : null,
                ast.pageMessage ? new Clearfix() : null,

                // if we have some parents to display...
                ast.node.parents?.length > 0 ? new NodeCompParentNodes(this.data) : null,

                new Divc({ className: ast.userPrefs.editMode ? "appTabPaneEditMode" : null }, [
                    new NodeCompMainNode(this.data),
                    new NodeCompMainList(this.data)
                ])
            ]);
        }

        let header: Div = null;
        this.setChildren([
            // We only show the primary (tree view) header if user is NOT logged in, so we can post
            // blogs and other content of that sort which don't need to say "Quanta" (branding name) at top
            ast.isAnonUser || !ast.node ? null : (this.headingBar = new TabHeading([
                new Divc({ className: "tinyMarginTop float-end" }, [
                    !ast.isAnonUser ? new Icon({
                        className: "fa fa-book fa-lg buttonBarIcon",
                        title: "Show Document View",
                        [C.NODE_ID_ATTR]: ast.node.id,
                        onClick: S.nav.openDocumentView
                    }) : null,

                    new Icon({
                        className: "fa fa-search fa-lg buttonBarIcon",
                        title: "Search Subnodes",
                        [C.NODE_ID_ATTR]: ast.node.id,
                        onClick: S.nav.runSearch
                    }),

                    !ast.isAnonUser ? new Icon({
                        className: "fa fa-clock-o fa-lg buttonBarIcon",
                        title: "View Timeline (by Mod Time)",
                        [C.NODE_ID_ATTR]: ast.node.id,
                        onClick: S.nav.runTimeline
                    }) : null
                ]),
                header = new Div(S.quanta.configRes.brandingAppName, {
                    className: "tabTitle headerUploadPanel",
                    title: "Drop Files here to upload"
                })
            ])),
            contentDiv
        ]);

        if (header) {
            S.domUtil.setDropHandler(header.attribs, (evt: DragEvent) => {
                if (evt.dataTransfer.files) {
                    S.domUtil.uploadFilesToNode([...evt.dataTransfer.files], "[auto]", true);
                }
            });
        }
        // if we're not showing the header we do at least need some margin at the top
        else {
            this.attribs.className += " mediumPaddingTop";
        }
        return true;
    }
}
