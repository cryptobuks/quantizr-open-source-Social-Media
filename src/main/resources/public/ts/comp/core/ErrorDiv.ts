import { ReactNode } from "react";
import { State } from "../../State";
import { Comp } from "../base/Comp";

interface LS { // Local State
    error: string;
}

export class ErrorDiv extends Comp {

    constructor(s: State) {
        super(null, s);
        this.attribs.className = "validationError";
    }

    override compRender = (): ReactNode => {
        return this.tag("div", null, this.getChildrenWithFirst(this.getState<LS>().error));
    }
}
