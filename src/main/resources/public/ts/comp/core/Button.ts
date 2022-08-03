import { ReactNode } from "react";
import { getAppState } from "../../AppRedux";
import { S } from "../../Singletons";
import { Comp } from "../base/Comp";
import { Tag } from "./Tag";

interface LS { // Local State
    text?: string;
    enabled?: boolean;
}

export class Button extends Comp {

    constructor(text: string, callback: Function, attribs: Object = null, moreClasses: string = "btn-secondary",
        private iconClass: string = null) {
        super(attribs);
        moreClasses = moreClasses || "btn-secondary";
        this.attribs.type = "button";
        this.attribs.onClick = callback;
        this.attribs.className = this.attribs.className || "";
        this.attribs.className += " btn clickable " + moreClasses;
        this.attribs.className += getAppState().mobileMode ? " mobileButton" : "";
        console.log("Button attribs=" + S.util.prettyPrint(this.attribs));
        this.mergeState<LS>({ text, enabled: true });
    }

    setEnabled = (enabled: boolean) => {
        this.mergeState<LS>({ enabled });
    }

    setText(text: string): void {
        this.mergeState<LS>({ text });
    }

    compRender = (): ReactNode => {
        let text: string = this.getState<LS>().text;

        if (this.getState<LS>().enabled) {
            delete this.attribs.disabled;
        }
        else {
            this.attribs.disabled = "disabled";
        }

        return this.tag("button", null, [
            // We use Tag here instead of Icon, because Icon renders larger in size for mobile mode and that
            // would conflict with this button already itself sizing larger for mobile
            this.iconClass ? new Tag("i", {
                className: "fa " + this.iconClass,
                style: {
                    marginRight: text ? "6px" : "0px"
                }
            }) : null, text]);
    }
}
