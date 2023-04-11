import { getAs } from "../../AppContext";
import { Comp } from "../../comp/base/Comp";
import { Button } from "../../comp/core/Button";
import { Div } from "../../comp/core/Div";
import { Constants as C } from "../../Constants";
import { TabIntf } from "../../intf/TabIntf";
import * as J from "../../JavaIntf";
import { S } from "../../Singletons";
import { Divc } from "../core/Divc";
import { NodeCompRow } from "./NodeCompRow";

export class NodeCompTableRowLayout extends Div {

    constructor(public node: J.NodeInfo, private tabData: TabIntf<any>, public level: number, public layout: string, public allowNodeMove: boolean, private allowHeaders: boolean) {
        super(null, { className: "nodeGridTable" });
    }

    override preRender(): boolean {
        const ast = getAs();
        const nodesToMove = ast.nodesToMove;
        let curRow = new Divc({ className: "nodeGridRow" });
        const children: Comp[] = [];
        const childCount: number = this.node.children.length;
        let rowCount: number = 0;
        let maxCols = 2;

        if (this.layout === "c2") {
            maxCols = 2;
        }
        else if (this.layout === "c3") {
            maxCols = 3;
        }
        else if (this.layout === "c4") {
            maxCols = 4;
        }
        else if (this.layout === "c5") {
            maxCols = 5;
        }
        else if (this.layout === "c6") {
            maxCols = 6;
        }

        const cellWidth = 100 / maxCols;
        const allowInsert = S.props.isWritableByMe(this.node);
        let curCols = 0;
        let lastNode: J.NodeInfo = null;
        let rowIdx = 0;

        // This boolean helps us keep from putting two back to back vertical spaces which would otherwise be able to happen.
        let inVerticalSpace = false;

        this.node.children?.forEach(n => {
            if (!n) return;
            const comps: Comp[] = [];

            if (!(nodesToMove && nodesToMove.find(id => id === n.id))) {

                if (this.debug && n) {
                    console.log("RENDER ROW[" + rowIdx + "]: node.id=" + n.id);
                }

                const type = S.plugin.getType(n.type);

                // special case where we aren't in edit mode, and we run across a markdown type with blank content AND no attachment, then don't even render it.
                if (type?.getTypeName() === J.NodeType.NONE && !n.content && !ast.userPrefs.editMode && !S.props.hasBinary(n)) {
                }
                else {
                    lastNode = n;
                    if (n.children && !inVerticalSpace) {
                        comps.push(new Divc({ className: "verticalSpace" }));
                    }
                    const row: Comp = new NodeCompRow(n, this.tabData, type, rowIdx, childCount, rowCount + 1, this.level, true, this.allowNodeMove, this.allowHeaders, true, false, null, false);
                    inVerticalSpace = false;
                    comps.push(row);
                }

                rowCount++;
                // if we have any children on the node they will always have been loaded to be displayed so display them
                // This is the linline children
                if (n.children) {
                    comps.push(S.render.renderChildren(n, this.tabData, this.level + 1, this.allowNodeMove));
                    comps.push(new Divc({ className: "verticalSpace" }));
                    inVerticalSpace = true;
                }

                const curCol = new Divc({
                    className: "nodeGridCell",
                    style: {
                        width: cellWidth + "%",
                        maxWidth: cellWidth + "%"
                    }
                }, comps);

                curRow.addChild(curCol);

                if (++curCols === maxCols) {
                    children.push(curRow);
                    curRow = new Divc({ className: "tableRow" });
                    curCols = 0;
                }
            }
            rowIdx++;
        });

        // the last row might not have filled up yet but add it still
        if (curCols > 0) {
            children.push(curRow);
        }

        const isMine = S.props.isMine(ast.node);

        /* I'll leave this block here, for future reference, but it's dead code. If editMode is on we never do the
        table layout but show each node as if it were vertical layout instead */
        if (isMine && this.allowHeaders && allowInsert && !ast.isAnonUser && ast.userPrefs.editMode) {
            const attribs = {};
            if (ast.userPrefs.editMode) {
                S.render.setNodeDropHandler(attribs, lastNode);
            }

            if (this.level <= 1) {
                children.push(new Button(null, () => {
                    if (lastNode) {
                        S.edit.insertNode(lastNode.id, J.NodeType.NONE, 1 /* isFirst ? 0 : 1 */, ast);
                    } else {
                        S.edit.newSubNode(null, ast.node.id);
                    }
                }, {
                    title: "Insert new node"
                }, "btn-secondary marginLeft marginTop", "fa-plus"));

                const userCanPaste = (S.props.isMine(lastNode) || ast.isAdminUser) && lastNode.id !== ast.userProfile?.userNodeId;
                if (!!ast.nodesToMove && userCanPaste) {
                    children.push(new Button("Paste Here", S.edit.pasteSelNodes_Inline, { [C.NODE_ID_ATTR]: lastNode.id }, "btn-secondary pasteButton marginLeft"));
                }
            }
        }
        this.setChildren(children);
        return true;
    }
}
