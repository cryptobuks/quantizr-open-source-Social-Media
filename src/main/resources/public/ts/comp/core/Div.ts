import { ReactNode } from "react";
import { Comp } from "../base/Comp";
import { CompIntf } from "../base/CompIntf";

interface LS { // Local State
    content?: string;
}

export class Div extends Comp {
    constructor(public content: string = "", attribs: Object = {}, children: CompIntf[] = null, private rawHtml: boolean = false) {
        super(attribs);
        this.setChildren(children);
        this.mergeState<LS>({ content });
    }

    override compRender = (): ReactNode => {
        if (this.rawHtml) {
            this.attribs.dangerouslySetInnerHTML = Comp.getDangerousHtml(this.content);
            return this.tag("div");
        }
        else {
            return this.tag("div", null, this.getChildrenWithFirst(this.getState<LS>().content));
        }
    }
}
