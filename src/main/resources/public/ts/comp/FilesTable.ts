import { ReactNode } from "react";
import { Comp } from "./base/Comp";

export class FilesTable extends Comp {

    constructor(attribs: Object = {}) {
        super(attribs);
    }

    override compRender = (): ReactNode => {
        return this.tag("table");
    }
}
