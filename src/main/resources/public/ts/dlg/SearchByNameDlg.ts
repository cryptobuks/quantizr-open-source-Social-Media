import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Diva } from "../comp/core/Diva";
import { TextField } from "../comp/core/TextField";
import { DialogBase } from "../DialogBase";
import { S } from "../Singletons";
import { Validator, ValidatorRuleName } from "../Validator";

export class SearchByNameDlg extends DialogBase {
    static defaultSearchText: string = "";

    searchTextField: TextField;
    searchTextState: Validator = new Validator("", [
        { name: ValidatorRuleName.REQUIRED }
    ]);

    constructor() {
        super("Search Node Names", "appModalContMediumWidth");
        this.onMount(this.searchTextField?.focus);
        this.searchTextState.setValue(SearchByNameDlg.defaultSearchText);
        this.validatedStates = [this.searchTextState];
    }

    renderDlg(): CompIntf[] {
        return [
            new Diva([
                this.searchTextField = new TextField({ label: "Node Name", enter: this.search, val: this.searchTextState }),
                new ButtonBar([
                    new Button("Search", this.search, null, "btn-primary"),
                    new Button("Close", this.close, null, "btn-secondary float-end")
                ], "marginTop")
            ])
        ];
    }

    search = async () => {
        if (!this.validate()) {
            return;
        }

        SearchByNameDlg.defaultSearchText = this.searchTextState.getValue();

        const desc = "Node Name: " + SearchByNameDlg.defaultSearchText;
        const success = await S.srch.search(null, "node.name", SearchByNameDlg.defaultSearchText, null, desc, null, false,
            false, 0, true, "mtm", "DESC", false, false, false);
        if (success) {
            this.close();
        }
    }
}
