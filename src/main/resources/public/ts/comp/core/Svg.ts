import { ReactNode } from "react";
import { Comp } from "../base/Comp";
import { CompIntf } from "../base/CompIntf";

interface LS { // Local State
    content: string;
}

export class Svg extends Comp {

    constructor(content: string = "", attribs: Object = {}, children: CompIntf[] = null) {
        super(attribs);
        this.setChildren(children);
        this.mergeState<LS>({ content });
    }

    override compRender = (): ReactNode => {
        return this.tag("svg", null, this.getChildrenWithFirst(this.getState<LS>().content));
    }
}
