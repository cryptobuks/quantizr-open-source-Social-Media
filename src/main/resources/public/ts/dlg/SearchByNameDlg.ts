import { getAppState } from "../AppRedux";
import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Div } from "../comp/core/Div";
import { TextField } from "../comp/core/TextField";
import { DialogBase } from "../DialogBase";
import { S } from "../Singletons";
import { ValidatedState, ValidatorRuleName } from "../ValidatedState";

export class SearchByNameDlg extends DialogBase {
    static defaultSearchText: string = "";

    searchTextField: TextField;
    searchTextState: ValidatedState<any> = new ValidatedState<any>("", [
        { name: ValidatorRuleName.REQUIRED }
    ]);

    constructor() {
        super("Search Node Names", "app-modal-content-medium-width");
        this.onMount((elm: HTMLElement) => this.searchTextField?.focus());
        this.searchTextState.setValue(SearchByNameDlg.defaultSearchText);
        this.validatedStates = [this.searchTextState];
    }

    renderDlg(): CompIntf[] {
        return [
            new Div(null, null, [
                this.searchTextField = new TextField({ label: "Node Name", enter: this.search, val: this.searchTextState }),
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

        if (!S.util.ajaxReady("searchNodes")) {
            return;
        }

        SearchByNameDlg.defaultSearchText = this.searchTextState.getValue();

        let desc = "Node Name: " + SearchByNameDlg.defaultSearchText;
        S.srch.search(null, "node.name", SearchByNameDlg.defaultSearchText, getAppState(), null, desc, false,
            false, 0, true, "mtm", "DESC", false, this.close);
    }
}
