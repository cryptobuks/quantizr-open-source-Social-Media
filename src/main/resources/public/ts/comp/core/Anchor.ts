import { ReactNode } from "react";
import { Comp } from "../base/Comp";

export class Anchor extends Comp {
    constructor(public url: string, public content: string, attribs: Object = null, children: Comp[] = null) {
        super({ href: url, ...attribs });
        this.setChildren(children);
    }

    override compRender = (): ReactNode => {
        return this.tag("a", null, this.getChildrenWithFirst(this.content || this.url));
    }
}
