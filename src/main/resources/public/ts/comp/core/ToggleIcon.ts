import { ReactNode } from "react";
import { Comp } from "../base/Comp";

interface LS { // Local State
    toggle?: boolean;
    className?: string;
}

export class ToggleIcon extends Comp {

    constructor(private toggleOnClass: string, private toggleOffClass: string, attribs: Object = null) {
        super(attribs);
        this.mergeState<LS>({
            className: this.attribs.className,
            toggle: false
        });
    }

    toggleClass = () => {
        const state = this.getState<LS>();
        this.mergeState<LS>({ toggle: !state.toggle });
    }

    override compRender = (): ReactNode => {
        const state = this.getState<LS>();
        this.attribs.className = state.className + " " + (state.toggle ? this.toggleOnClass : this.toggleOffClass);
        return this.tag("i");
    }
}
