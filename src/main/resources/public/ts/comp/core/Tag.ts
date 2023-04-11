import { ReactNode } from "react";
import { Comp } from "../base/Comp";
import { CompIntf } from "../base/CompIntf";

export class Tag extends Comp {

    constructor(public tagName: string, attribs: Object = {}, children: CompIntf[] = null) {
        super(attribs);
        this.setChildren(children);
    }

    override compRender = (): ReactNode => {
        return this.tag(this.tagName);
    }
}
