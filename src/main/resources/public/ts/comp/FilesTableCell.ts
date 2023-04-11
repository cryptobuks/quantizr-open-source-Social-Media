import { ReactNode } from "react";
import { Comp } from "./base/Comp";

export class FilesTableCell extends Comp {

    constructor(public content: string = null, attribs: Object = {}, children: Comp[] = null) {
        super(attribs);
        this.setChildren(children);
    }

    override compRender = (): ReactNode => {
        return this.tag("td", null, this.getChildrenWithFirst(this.content));
    }
}
