import * as I from "../../Interfaces";
import { Validator } from "../../Validator";
import { Div } from "./Div";
import { Divc } from "./Divc";
import { Input } from "./Input";

export class DateField extends Div implements I.ValueIntf {
    input: Input;

    constructor(private valState: Validator) {
        super(null);
    }

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
                className: "dateField"
            }, [
                this.input = new Input({
                    className: "form-control preTextField",
                    type: "date"
                }, this.valState.v)
            ])
        ]);
        return true;
    }
}
