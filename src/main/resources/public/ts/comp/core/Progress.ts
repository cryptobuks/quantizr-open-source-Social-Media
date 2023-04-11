import { ReactNode } from "react";
import { Comp } from "../base/Comp";

export class Progress extends Comp {

    constructor() {
        super({
            className: "progress-bar progress-bar-striped progress-bar-animated",
            role: "progressbar",
            "aria-valuenow": "100",
            "aria-valuemin": "0",
            "aria-valuemax": "100",
            style: { width: "100%" }
        });
    }

    override compRender = (): ReactNode => {
        return this.tag("div");
    }
}
