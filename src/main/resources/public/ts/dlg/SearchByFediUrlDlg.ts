import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Diva } from "../comp/core/Diva";
import { TextField } from "../comp/core/TextField";
import { DialogBase } from "../DialogBase";
import { S } from "../Singletons";
import { Validator, ValidatorRuleName } from "../Validator";

export class SearchByFediUrlDlg extends DialogBase {

    static defaultSearchText: string = "";
    searchTextField: TextField;
    searchTextState: Validator = new Validator("", [
        { name: ValidatorRuleName.REQUIRED }
    ]);

    constructor() {
        super("Fediverse URL", "appModalContMediumWidth");
        this.onMount(() => this.searchTextField?.focus());
        this.searchTextState.setValue(SearchByFediUrlDlg.defaultSearchText);
        this.validatedStates = [this.searchTextState];
    }

    renderDlg(): CompIntf[] {
        return [
            new Diva([
                this.searchTextField = new TextField({ label: "URL", enter: this.search, val: this.searchTextState }),
                new ButtonBar([
                    new Button("Search", this.search, null, "btn-primary"),
                    new Button("Close", this.close, null, "btn-secondary float-end")
                ], "marginTop")
            ])
        ];
    }

    search = () => {
        if (!this.validate()) {
            return;
        }

        const url = this.searchTextState.getValue();
        S.srch.loadSingleFeedItemByUrl(url);
        this.close();
    }
}
