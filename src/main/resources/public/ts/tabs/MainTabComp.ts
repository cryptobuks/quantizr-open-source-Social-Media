import { useAppState } from "../AppContext";
import { AppTab } from "../comp/AppTab";
import { BreadcrumbsPanel } from "../comp/BreadcrumbsPanel";
import { Clearfix } from "../comp/core/Clearfix";
import { Div } from "../comp/core/Div";
import { FlexRowLayout } from "../comp/core/FlexRowLayout";
import { Html } from "../comp/core/Html";
import { NodeCompMainList } from "../comp/node/NodeCompMainList";
import { NodeCompMainNode } from "../comp/node/NodeCompMainNode";
import { NodeCompParentNodes } from "../comp/node/NodeCompParentNodes";
import { TabIntf } from "../intf/TabIntf";
import { S } from "../Singletons";

declare const g_brandingAppName: string;
declare const g_urlIdFailMsg: string;

export class MainTabComp extends AppTab {

    constructor(data: TabIntf) {
        super(data, null);
        this.attribs.key = "mainTabCompKey";
        data.inst = this;
    }

    preRender(): void {
        const ast = useAppState();
        this.attribs.className = this.getClass(ast);

        let contentDiv: Div = null;
        if (g_urlIdFailMsg) {
            contentDiv = new Div(g_urlIdFailMsg);
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

            contentDiv = new Div(null, {
                // This visibility setting makes the main content not visible until final scrolling is complete
                className: ast.rendering ? "compHidden" : "compVisible"
            }, [
                !ast.mobileMode ? new BreadcrumbsPanel() : null,
                ast.pageMessage ? new Html(ast.pageMessage, { className: "alert alert-info float-end" }) : null,
                ast.pageMessage ? new Clearfix() : null,

                // if we have some parents to display...
                ast.node.parents?.length > 0 ? new NodeCompParentNodes(ast, this.data) : null,

                new Div(null, { className: ast.userPrefs.editMode ? "my-tab-pane-editmode" : null }, [
                    new NodeCompMainNode(ast, this.data),
                    new NodeCompMainList(this.data)
                ])
            ]);
        }

        let header: Div = null;
        this.setChildren([
            // todo-0: WARNING: headingBar has to be a child of the actual scrollable panel for stickyness to work.
            // We only show the primary (tree view) header if user is NOT logged in, so we can post
            // blogs and other content of that sort which don't need to say "Quanta" (branding name) at top
            ast.isAnonUser ? null : new FlexRowLayout([
                header = new Div(g_brandingAppName, {
                    className: "tabTitle headerUploadPanel",
                    onClick: () => S.util.loadAnonPageHome(),
                    title: "Go to Portal Home Node\n\n(or Drop Files here to upload)"
                })
            ], "headingBar"),
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
    }
}
