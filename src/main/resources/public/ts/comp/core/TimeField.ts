import * as I from "../../Interfaces";
import { Validator } from "../../Validator";
import { Div } from "./Div";
import { Divc } from "./Divc";
import { Input } from "./Input";

export class TimeField extends Div implements I.ValueIntf {
    input: Input;

    constructor(private valState: Validator, private extraClass: string = null) {
        super(null);
    }

    // Overriding base class so we can focus the correct part of this composite component.
    override focus(): void {
        this.onMount(() => this.input?.focus());
    }

    setValue(value: string): void {
        this.valState.setValue(value);
    }

    getValue(): string {
        return this.valState.getValue();
    }

    override preRender(): boolean {
        this.setChildren([
            new Divc({
                className: this.extraClass + " timeField"
            }, [
                this.input = new Input({
                    className: "form-control preTextField",
                    type: "time"
                }, this.valState.v)
            ])
        ]);
        return true;
    }
}
