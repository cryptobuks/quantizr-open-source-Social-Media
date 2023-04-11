import { ReactNode } from "react";
import { Comp } from "../base/Comp";

export class Para extends Comp {

    constructor(public content: string = null, attribs: Object = {}) {
        super(attribs);
    }

    override compRender = (): ReactNode => {
        return this.tag("p", null, [this.content]);
    }
}
