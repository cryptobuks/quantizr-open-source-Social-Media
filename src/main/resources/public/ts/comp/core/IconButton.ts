import { ReactNode } from "react";
import { getAs } from "../../AppContext";
import { Comp } from "../base/Comp";
import { Img } from "./Img";
import { Italic } from "./Italic";
import { Span } from "./Span";
import { Spinner } from "./Spinner";

interface LS { // Local State
    visible: boolean;
    disabled: boolean;
    waiting: boolean;
}

export class IconButton extends Comp {

    constructor(public iconClass: string = "", public text: string, attribs: Object = {}, specialClasses: string = "btn-secondary", private toggle: string = "", private imageUrl: string = null) {
        super(attribs);
        this.attribs.type = "button";
        specialClasses = specialClasses || "";

        // tolerate caller not providing a btn- class and add secondary as default.
        if (specialClasses.indexOf("btn-") === -1) {
            specialClasses = "btn-secondary " + specialClasses;
        }

        this.attribs.className = "btn align-middle clickable " + specialClasses +
            (getAs().mobileMode ? " mobileIconButton" : "");
        this.mergeState({ visible: true });
    }

    replaceWithWaitIcon = (): void => {
        this.mergeState({ waiting: true });
    }

    override compRender = (): ReactNode => {
        const state = this.getState<LS>();

        // this gets activated when the user clicks an infinite scrolling button, so it turns into a spinner
        // while loading more records
        if (state.waiting) {
            return new Spinner("bigMargin").compRender();
        }

        this.attribs.style = { display: (state.visible && !state.disabled ? "" : "none") };

        return this.tag("button", null, [
            this.imageUrl ? new Img({
                key: this.getId("img_"),
                src: this.imageUrl
            }) : null,
            new Italic({
                key: this.getId("i_"),
                className: "fa " + this.iconClass + (this.toggle === "on" ? " iconToggleOn" : " iconToggleOff")
            }),
            this.text ? new Span(this.text, {
                key: this.getId("t_"),
                className: "iconButtonFont"
            }) : null
        ]);
    }
}
