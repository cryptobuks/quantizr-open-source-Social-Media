import { ReactNode } from "react";
import { Comp } from "../base/Comp";

interface LS { // Local State
    text: string;
}

export class TextContent extends Comp {

    constructor(text: string, classes: string = null, public preformatted: boolean = false) {
        super(null);
        this.attribs.className = classes || "alert alert-secondary";
        this.setText(text);
    }

    setText = (text: string) => {
        this.mergeState<LS>({ text });
    }

    override compRender = (): ReactNode => {
        const state = this.getState<LS>();

        // todo-2: Not sure I want to keep detecting HTML this way, because we can just use
        // the HTML class explicity when we need to support HTML
        if (state.text && state.text.indexOf("<") !== -1) {
            this.attribs.dangerouslySetInnerHTML = Comp.getDangerousHtml(state.text);
            return this.tag(this.preformatted ? "pre" : "div");
        }
        else {
            // console.log("Building (TextContent) react element: " + this.attribs.id);
            return this.tag(this.preformatted ? "pre" : "div", null, [state.text]);
        }
    }
}
