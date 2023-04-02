import { CompIntf } from "../base/CompIntf";
import { Div } from "./Div";

/* Diva = Div with Array only */
export class Diva extends Div {
    constructor(children: CompIntf[] = null) {
        super(null);
        this.setChildren(children);
    }
}
