import { ReactNode } from "react";
import { Value } from "../../Value";
import { ValueIntf } from "../../Interfaces";
import { State } from "../../State";
import { Comp } from "../base/Comp";
import { CheckboxInput } from "./CheckboxInput";
import { Label } from "./Label";

export class RadioButton extends Comp {

    constructor(public label: string, public checked: boolean, public groupName: string, attribs: any, private valueIntf: ValueIntf,
        private layoutClass: string = null) {
        super(attribs, new State());
        valueIntf = this.valueIntf || new Value<string>(this, "val");
    }

    override compRender = (): ReactNode => {
        let cbInput = null;
        return this.tag("span", {
            className: "form-check " + (this.layoutClass || "")
        }, [
            cbInput = new CheckboxInput({
                name: this.groupName,
                type: "radio",
                label: this.label,
                value: this.getId("val-"),
                className: "form-check-input clickable"
            }, null, this.valueIntf),
            new Label(this.label || "", {
                htmlFor: cbInput.getId(),
                className: "form-check-label radioLabel"
            })
        ]);
    }
}
