import { getAs } from "../../AppContext";
import { CompIntf } from "../../comp/base/CompIntf";
import { Button } from "../../comp/core/Button";
import { Clearfix } from "../../comp/core/Clearfix";
import { Div } from "../../comp/core/Div";
import { IconButton } from "../../comp/core/IconButton";
import { Constants as C } from "../../Constants";
import { EditNodeDlg } from "../../dlg/EditNodeDlg";
import { TabIntf } from "../../intf/TabIntf";
import { NodeActionType, TypeIntf } from "../../intf/TypeIntf";
import * as J from "../../JavaIntf";
import { S } from "../../Singletons";
import { Divc } from "../core/Divc";
import { NodeCompButtonBar } from "./NodeCompButtonBar";
import { NodeCompContent } from "./NodeCompContent";
import { NodeCompRowFooter } from "./NodeCompRowFooter";
import { NodeCompRowHeader } from "./NodeCompRowHeader";

export class NodeCompRow extends Div {

    /* we have this flag so we can turn off buttons to troubleshoot performance. */
    static showButtonBar: boolean = true;

    // isLinkedNode means this node is rendered as a 'sub render' of some other node like it's a boost for example, and we're rendering the
    // content of the boost inside the node that boosted it. And the node that is rendering the boost will have it passed in as 'internalComp'
    constructor(public node: J.NodeInfo, public tabData: TabIntf<any>, private type: TypeIntf, //
        public index: number, public count: number, public rowCount: number, public level: number,
        public isTableCell: boolean, public allowNodeMove: boolean, private allowHeaders: boolean,
        public allowInlineInsertButton: boolean, private isLinkedNode: boolean, private internalComp: Div,
        private renderingBoost: boolean) {
        super(null, {
            id: S.nav._UID_ROWID_PREFIX + node.id
            // WARNING: Leave this tabIndex here. it's required for focsing/scrolling
            // tabIndex: "-1"
        });

        const ast = getAs();
        /* If we're in edit mode allow dragging. Note nodes with subOrdinals can't be dragged */
        if ((!type || type.subOrdinal() === -1) && ast.userPrefs.editMode && !ast.editNode && !ast.inlineEditId) {
            S.domUtil.setNodeDragHandler(this.attribs, node.id)
        }
    }

    override preRender(): boolean {
        const ast = getAs();

        if (this.allowHeaders) {
            this.attribs[C.NODE_ID_ATTR] = this.node.id;
            this.attribs.onClick = S.nav.clickTreeNode;
        }

        let insertInlineButton = null;
        const isPageRootNode = ast.node && this.node.id === ast.node.id;

        if (this.allowHeaders && ast.userPrefs.editMode) {
            let insertAllowed = true;

            /* if we are at level one that means state.node is the parent of 'this.node' so that's what determines if we
            can insert or not */
            if (this.level === 1) {
                const parentType = S.plugin.getType(ast.node.type);
                if (parentType) {
                    insertAllowed = ast.isAdminUser || parentType.allowAction(NodeActionType.insert, ast.node);
                }
            }

            const isMine = S.props.isMine(ast.node);

            if (isMine && this.allowInlineInsertButton && !isPageRootNode && this.level === 1 && insertAllowed) {

                let insertButton: Button = null;
                insertInlineButton = new Divc({ className: "marginLeft" }, [
                    insertButton = new Button(null, () => {
                        S.edit.insertNode(this.node.id, J.NodeType.NONE, 0 /* isFirst ? 0 : 1 */, ast);
                    }, {
                        title: "Insert new node" + (this.isTableCell ? " (above this one)" : "")
                    }, "btn-secondary " + (this.isTableCell ? "" : "plusButtonFloatRight"), "fa-plus")
                ]);

                S.domUtil.setDropHandler(insertButton.attribs, (evt: DragEvent) => {
                    for (const item of evt.dataTransfer.items) {
                        // console.log("DROP(d) kind=" + item.kind + " type=" + item.type);
                        if (item.kind === "file") {
                            EditNodeDlg.pendingUploadFile = item.getAsFile();
                            S.edit.insertNode(this.node.id, J.NodeType.NONE, 0 /* isFirst ? 0 : 1 */, ast);
                            return;
                        }
                    }
                });
            }
        }

        let buttonBar = null;
        if (this.allowHeaders && NodeCompRow.showButtonBar && !ast.inlineEditId) {
            buttonBar = new NodeCompButtonBar(this.node, this.allowNodeMove, this.isTableCell ? [insertInlineButton] : null, null);
        }

        let layoutClass = this.isTableCell ? "nodeGridItem" //
            : (this.tabData.id === C.TAB_MAIN && ast.userPrefs.editMode && //
                S.util.showMetaData(ast, this.node) ? "nodeTableRowEdit" : "nodeTableRow");
        layoutClass += " " + this.tabData.id;

        // const layout = S.props.getPropStr(J.NodeProp.LAYOUT, this.node);
        const isInlineChildren = !!S.props.getPropStr(J.NodeProp.INLINE_CHILDREN, this.node);

        let allowHeader: boolean = false;
        // special case, if node is owned by admin and we're not admin, never show header
        if (!C.ALLOW_ADMIN_NODE_HEADERS && this.node.owner === J.PrincipalName.ADMIN && ast.userName !== J.PrincipalName.ADMIN) {
            // leave allowHeader false.
        }
        else {
            allowHeader = this.allowHeaders && S.util.showMetaData(ast, this.node) && (this.type == null || this.type?.getAllowRowHeader())
        }

        // if this node has children as columnar layout, and is rendering as the root node of a page or a node that is expanded inline,
        // that means there will be a grid below this node so we don't show the border (bottom divider line) because it's more attractive not to.
        if (this.isTableCell) {
        }
        // else if (layout && layout.indexOf("c") === 0 && (isInlineChildren || this.node.id === state.node.id)) {
        // }
        else {
            // special class if BOTH edit and info is on
            if (allowHeader && this.tabData.id === C.TAB_MAIN && ast.userPrefs.editMode && //
                S.util.showMetaData(ast, this.node)) {
                layoutClass += this.node.boostedNode ? " rowBorderEditInfoBoost" : " rowBorderEditInfo";
            }
            // else if either is on
            else if (ast.userPrefs.editMode || S.util.showMetaData(ast, this.node)) {
                layoutClass += " rowBorderEdit";
            }
            else if (isInlineChildren && this.node.hasChildren && !isPageRootNode) {
                layoutClass += " rowBorderInlineChildren";
            }
            else {
                layoutClass += " row-border";
            }
        }

        const indentLevel = this.isTableCell ? 0 : this.level;
        const focusNode = S.nodeUtil.getHighlightedNode();
        const selected: boolean = (focusNode && focusNode.id === this.node.id);

        if (this.isLinkedNode) {
            this.attribs.className = "boostRow";
        }
        else {
            this.attribs.className = (layoutClass || "") + (selected ? " activeRow" : " inactiveRow");
            const style = indentLevel > 0 ? { marginLeft: "" + ((indentLevel - 1) * 30) + "px" } : null;
            this.attribs.style = style;
        }

        if (S.render.enableRowFading && S.render.fadeInId === this.node.id && S.render.allowFadeInId) {
            S.render.fadeInId = null;
            S.render.allowFadeInId = false;
            this.attribs.className += " fadeInRowBkgClz";
            S.quanta.fadeStartTime = new Date().getTime();
        }

        let header: NodeCompRowHeader = null;
        let jumpButton: CompIntf = null;

        if (allowHeader && !this.node.boostedNode) {
            // slight special case for now until Document View knows how to delete all the subchilren and not
            // show orphans on the page when something is deleted. Other panels don't have this problem
            const allowDelete = this.tabData.id !== C.TAB_DOCUMENT;
            const showJumpButton = this.tabData.id !== C.TAB_MAIN;
            header = new NodeCompRowHeader(null, this.node, true, true, this.tabData, showJumpButton, true, false, allowDelete);
        }
        else {
            const targetId = S.props.getPropStr(J.NodeProp.TARGET_ID, this.node);
            if (targetId) {
                jumpButton = new IconButton("fa-arrow-right", null, {
                    onClick: () => S.view.jumpToId(targetId),
                    title: "Jump to the Node"
                }, "btn-secondary float-end");
            }
        }

        // if editMode is on, an this isn't the page root node
        if (ast.userPrefs.editMode) {
            S.render.setNodeDropHandler(this.attribs, this.node);
        }

        this.setChildren([
            this.isTableCell ? null : insertInlineButton,
            this.renderingBoost ? null : S.render.renderBoostHeader(this.node, true),
            S.render.renderLinkLabel(this.node.id),
            header,
            buttonBar,
            buttonBar ? new Clearfix() : null,
            jumpButton,
            new NodeCompContent(this.node, this.tabData, true, true, null, null, true, this.isLinkedNode, null),
            this.internalComp,
            S.render.renderLinks(this.node),
            this.allowHeaders ? new NodeCompRowFooter(this.node) : null,
            this.allowHeaders ? new Clearfix() : null
        ]);
        return true;
    }
}
