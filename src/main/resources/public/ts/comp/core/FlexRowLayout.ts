import { ReactNode } from "react";
import { Comp } from "../base/Comp";
import { CompIntf } from "../base/CompIntf";

/* WARNING: If you are setting percentage widths that add up to 100%, be sure to NOT use any margins at all, but
use padding instead, because margins will make the width percentages be off and cause undesired wrapping */
export class FlexRowLayout extends Comp {
    constructor(public comps: CompIntf[] = null, moreClasses: string = "", attribs: any = {}) {
        super(attribs);
        this.attribs.className = "flexRowLayout " + moreClasses;
    }

    override compRender = (): ReactNode => {
        return this.tag("div", this.attribs, this.comps);
    }
}
