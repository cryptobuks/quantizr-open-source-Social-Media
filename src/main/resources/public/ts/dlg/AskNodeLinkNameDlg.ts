import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Diva } from "../comp/core/Diva";
import { TextField } from "../comp/core/TextField";
import { DialogBase } from "../DialogBase";
import { Validator, ValidatorRuleName } from "../Validator";

export class AskNodeLinkNameDlg extends DialogBase {
    public nameEntered: string;

    nameState: Validator = new Validator("", [
        { name: ValidatorRuleName.REQUIRED }
    ]);

    constructor() {
        super("Node Link Name", "appModalContNarrowWidth");
        this.validatedStates = [this.nameState];
    }

    renderDlg(): CompIntf[] {
        return [
            new Diva([
                new TextField({ label: "Name", val: this.nameState })
            ]),
            new ButtonBar([
                new Button("Ok", this.save, null, "btn-primary"),
                new Button("Cancel", this.close)
            ], "marginTop")
        ];
    }

    save = () => {
        if (!this.validate()) {
            return;
        }
        this.nameEntered = this.nameState.getValue();
        this.close();
    }
}
