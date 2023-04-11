import { ReactNode } from "react";
import { getAs } from "../../AppContext";
import { Comp } from "../base/Comp";

export class Icon extends Comp {

    constructor(attribs: Object = null, private label: string = null) {
        super(attribs);
        this.attribs.className += getAs().mobileMode ? " mobileIcon" : "";
    }

    override compRender = (): ReactNode => {
        return this.tag("i", null, [this.label]);
    }
}
