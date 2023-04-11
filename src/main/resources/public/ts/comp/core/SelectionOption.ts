import { ReactNode } from "react";
import { Comp } from "../base/Comp";

export class SelectionOption extends Comp {

    constructor(public key: string, public val: string) {
        super(null);
        this.attribs.value = this.key;
    }

    override compRender = (): ReactNode => {
        return this.tag("option", { className: "selectOption" }, [this.val]);
    }
}
