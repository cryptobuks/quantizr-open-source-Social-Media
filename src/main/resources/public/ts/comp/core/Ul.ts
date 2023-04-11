import { ReactNode } from "react";
import { Comp } from "../base/Comp";

export class Ul extends Comp {

    constructor(public content: string = "", attribs: Object = {}, children: Comp[] = null) {
        super(attribs);
        this.setChildren(children);
    }

    override compRender = (): ReactNode => {
        return this.tag("ul", null, this.getChildrenWithFirst(this.content));
    }
}
