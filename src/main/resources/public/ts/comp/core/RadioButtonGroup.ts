import { ReactNode } from "react";
import { Comp } from "../base/Comp";
import { CompIntf } from "../base/CompIntf";

export class RadioButtonGroup extends Comp {

    constructor(initialButtons: CompIntf[] = null, moreClasses: string = "") {
        super(null);
        this.attribs.className = moreClasses;
        this.setChildren(initialButtons);

        initialButtons.forEach(row => {
            if (row?.attribs?.checked === "checked") {
                this.attribs.selected = (<any>row.attribs).name;
            }
        });
    }

    override compRender = (): ReactNode => {
        return this.tag("div");
    }
}
