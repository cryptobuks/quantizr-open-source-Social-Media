import { ReactNode } from "react";
import { getAs } from "../AppContext";
import { Div } from "../comp/core/Div";
import { Span } from "../comp/core/Span";
import { S } from "../Singletons";
import { CompIntf } from "./base/CompIntf";

interface LS { // Local State
    visible: boolean;
    enabled: boolean;
    content: string;
}

export class MenuItem extends Div {

    constructor(public name: string, public clickFunc: Function, enabled: boolean = true, private stateFunc: Function = null,
        private floatRightComp: CompIntf = null) {
        super(name);
        this.onClick = this.onClick.bind(this);
        this.mergeState({ visible: true, enabled });
    }

    override compRender = (): ReactNode => {
        const state: LS = this.getState<LS>();
        const enablement = state.enabled ? {} : { disabled: "disabled" };
        const enablementClass = state.enabled ? "mainMenuItemEnabled" : "disabled mainMenuItemDisabled";

        const prefix = this.stateFunc && this.stateFunc() ? (S.render.CHAR_CHECKMARK + " ") : "";
        this.setChildren([
            new Span(S.render.parseEmojis(prefix + state.content), null, null, true),
            this.floatRightComp
        ]);

        return this.tag("div", {
            ...this.attribs,
            ...enablement,
            ...{
                style: { display: (state.visible ? "" : "none") },
                className: "listGroupMenuItem list-group-item-action " + enablementClass + "  listGroupTransparent" +
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
