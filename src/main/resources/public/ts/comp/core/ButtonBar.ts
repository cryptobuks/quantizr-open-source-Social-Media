import { ReactNode } from "react";
import { Comp } from "../base/Comp";
import { Divc } from "./Divc";

export class ButtonBar extends Comp {

    // wrapperClass can be 'text-center' for centering.
    /* WARNING: There's a flawed edge case where if you have only ONE button, and it happens to be 'float-end' then
     the button bar ends up taking up ZERO height, and breaks the layout. The solution is to add a "new Clearfix()"
     after the ButtonBar (below the ButtonBar) */
    constructor(buttons: Comp[] = null, private wrapperClass: string = null, private extraClass: string = null) {
        super(null);
        this.setChildren(buttons);
    }

    override compRender = (): ReactNode => {
        const props = {
            className: "btn-group btnGroup flex-wrap " + (this.extraClass ? this.extraClass : ""),
            role: "group"
        };

        if (this.wrapperClass) {
            return this.tag("div", {
                className: this.wrapperClass
            },
                [new Divc(props, this.getChildren())]);
        }
        else {
            return this.tag("div", props);
        }
    }
}
