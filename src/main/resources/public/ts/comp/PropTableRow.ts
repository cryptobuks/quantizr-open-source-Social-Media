import { ReactNode } from "react";
import { Comp } from "./base/Comp";
import { PropTableCell } from "./PropTableCell";

export class PropTableRow extends Comp {

    constructor(attribs: Object = {}, children: PropTableCell[] = null) {
        super(attribs);
        this.setChildren(children);
    }

    override compRender = (): ReactNode => {
        return this.tag("tr");
    }
}
