import { getAs } from "../AppContext";
import { Checkbox } from "../comp/core/Checkbox";
import { Div } from "../comp/core/Div";
import { Diva } from "../comp/core/Diva";
import { Icon } from "../comp/core/Icon";
import { Span } from "../comp/core/Span";
import { Constants as C } from "../Constants";
import { PubSub } from "../PubSub";
import { S } from "../Singletons";
import { CompIntf } from "./base/CompIntf";
import { Divc } from "./core/Divc";

export class HistoryPanel extends Div {

    constructor() {
        super(null, {
            id: C.ID_RHS + "_hist",
            className: "nodeHistoryPanel"
        });
    }

    override preRender(): boolean {
        const ast = getAs();

        if (ast.nodeHistory.length === 0) {
            this.setChildren(null);
            return;
        }
        const children = [];
        children.push(new Diva([
            new Span("History", { className: "historyPanelTitle" }),
            new Checkbox("Lock", { className: "lockFont marginBottom float-end" }, {
                setValue: (checked: boolean) => ast.nodeHistoryLocked = checked,
                getValue: (): boolean => ast.nodeHistoryLocked
            }, "form-switch formCheckInlineNoMargin")
        ]));

        ast.nodeHistory.forEach(h => {
            if (!h.content) return;
            let parentDropTarg: CompIntf;
            let parentIcon: Icon;

            const type = S.plugin.getType(h.type);
            if (type) {
                const iconClass = type.getIconClass();
                if (iconClass) {
                    parentIcon = new Icon({
                        className: iconClass + " histTypeIcon",
                        title: "Node Type: " + type.getName()
                    });
                }
            }

            const dragProps = {};
            if (ast.userPrefs.editMode && !ast.editNode && !ast.inlineEditId) {
                S.domUtil.setNodeDragHandler(dragProps, h.id);
            }

            children.push(parentDropTarg = new Divc({
                id: h.id + "_hist",
                [C.NODE_ID_ATTR]: h.id,
                onClick: this.jumpToId,
                className: "nodeHistoryItem",
                ...dragProps
            }, [
                parentIcon,
                new Span(h.content, null, null, true)
            ]));

            S.domUtil.makeDropTarget(parentDropTarg.attribs, h.id);
        });
        this.setChildren(children);
        return true;
    }

    /* We use the standard trick of storing the ID on the dom so we can avoid unnecessary function scopes */
    jumpToId = (evt: any) => {
        const id = S.domUtil.getPropFromDom(evt, C.NODE_ID_ATTR);
        PubSub.pub(C.PUBSUB_closeNavPanel);
        S.view.jumpToId(id);
    }
}
