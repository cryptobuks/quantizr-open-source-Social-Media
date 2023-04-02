import { CompIntf } from "../base/CompIntf";
import { Div } from "./Div";

/* Divc = Div in Compact form */
export class Divc extends Div {
    constructor(attribs: Object, children: CompIntf[] = null) {
        super(null, attribs);
        this.setChildren(children);
    }
}
