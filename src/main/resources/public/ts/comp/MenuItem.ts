import { ReactNode } from "react";
import { getAs } from "../AppContext";
import { S } from "../Singletons";
import { Div } from "../comp/core/Div";
import { Span } from "../comp/core/Span";
import { Comp } from "./base/Comp";
import { Checkbox } from "./core/Checkbox";
import { Tag } from "./core/Tag";

interface LS { // Local State
    visible: boolean;
    enabled: boolean;
    content: string;
}

export class MenuItem extends Div {

    constructor(public name: string, public clickFunc: Function, enabled: boolean = true, private stateFunc: Function = null,
        private treeOp: boolean = null) {
        super(name);
        this.onClick = this.onClick.bind(this);
        this.mergeState({ visible: true, enabled });
    }

    override compRender = (): ReactNode => {
        const state: LS = this.getState<LS>();
        const enablement = state.enabled ? {} : { disabled: "disabled" };
        const enablementClass = state.enabled ? "mainMenuItemEnabled" : "disabled mainMenuItemDisabled";

        let innerSpan: Comp;
        let innerClazz: string;
        if (this.stateFunc) {
            innerSpan = new Checkbox(state.content, { className: "marginRight" }, {
                setValue: (checked: boolean) => this.onClick(),
                getValue: (): boolean => this.stateFunc()
            });
            innerClazz = "listGroupMenuItemCompact";
        }
        else {
            innerSpan = new Span(state.content);
            innerClazz = "listGroupMenuItem";
        }

        this.setChildren([
            innerSpan,
            this.treeOp ? new Tag("i", {
                className: "fa fa-caret-right fa-lg float-end " + (state.enabled ? "menuIcon" : "menuIconDisabled"),
                title: "Operates on the selected Tree Nodes(s)",
            }) : null
        ]);

        return this.tag("div", {
            ...this.attribs,
            ...enablement,
            ...{
                style: { display: (state.visible ? "" : "none") },
                className: innerClazz + " list-group-item-action " + enablementClass + "  listGroupTransparent" +
                    (getAs().mobileMode ? " mobileMenuText" : ""),
                onClick: this.onClick
            }
        });
    }

    onClick(): void {
        const state = this.getState<LS>();
        if (!state.enabled) return;

        if (S.quanta.mainMenu) {
            S.quanta.mainMenu.close();
        }
        this.clickFunc();
    }
}
