import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Checkbox } from "../comp/core/Checkbox";
import { Diva } from "../comp/core/Diva";
import { HorizontalLayout } from "../comp/core/HorizontalLayout";
import { TextField } from "../comp/core/TextField";
import { DialogBase } from "../DialogBase";
import { S } from "../Singletons";
import { Validator, ValidatorRuleName } from "../Validator";

interface LS { // Local State
    recursive: boolean;
}

export class SearchAndReplaceDlg extends DialogBase {

    searchState: Validator = new Validator("", [{ name: ValidatorRuleName.REQUIRED }]);
    replaceState: Validator = new Validator("", [{ name: ValidatorRuleName.REQUIRED }]);

    constructor() {
        super("Search and Replace", "appModalContNarrowWidth");
        this.mergeState<LS>({ recursive: true });
        this.validatedStates = [this.searchState, this.replaceState];
    }

    renderDlg(): CompIntf[] {
        return [
            new Diva([
                new TextField({ label: "Search for", val: this.searchState }),
                new TextField({ label: "Replace with", val: this.replaceState }),
                new HorizontalLayout([
                    new Checkbox("Include Sub-Nodes", null, {
                        setValue: (checked: boolean) => this.mergeState<LS>({ recursive: checked }),
                        getValue: (): boolean => this.getState<LS>().recursive
                    })
                ]),
                new ButtonBar([
                    new Button("Replace", this.replace, null, "btn-primary"),
                    new Button("Close", this.close, null, "btn-secondary float-end")
                ], "marginTop")
            ])
        ];
    }

    replace = () => {
        if (!this.validate()) {
            return;
        }

        const node = S.nodeUtil.getHighlightedNode();
        if (!node) {
            S.util.showMessage("No node was selected.", "Warning");
            return;
        }

        S.srch.searchAndReplace(this.getState<LS>().recursive, node.id, this.searchState.getValue(), this.replaceState.getValue());
        this.close();
    }
}
