import { ReactNode } from "react";
import { State } from "../../State";
import { Comp } from "../base/Comp";

interface LS { // Local State
    value: string;
}

export class Input extends Comp {

    constructor(attribs: Object = {}, s?: State) {
        super(attribs, s || new State());

        this.attribs.onChange = (evt: React.ChangeEvent<HTMLInputElement>) => {
            this.mergeState<LS>({ value: evt.target.value });
        };
    }

    override compRender = (): ReactNode => {
        this.attribs.value = this.getState<LS>().value || "";
        return this.tag("input");
    }
}
