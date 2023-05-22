import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Diva } from "../comp/core/Diva";
import { TextField } from "../comp/core/TextField";
import { DialogBase } from "../DialogBase";
import { S } from "../Singletons";
import { Validator, ValidatorRuleName } from "../Validator";

export class SearchByNostrDlg extends DialogBase {

    static defaultSearchText: string = "";
    searchTextField: TextField;
    searchTextState: Validator = new Validator("", [
        { name: ValidatorRuleName.REQUIRED }
    ]);

    constructor() {
        super("Search Nostr", "appModalContMediumWidth");
        this.onMount(() => this.searchTextField?.focus());
        this.searchTextState.setValue(SearchByNostrDlg.defaultSearchText);
        this.validatedStates = [this.searchTextState];
    }

    renderDlg(): CompIntf[] {
        return [
            new Diva([
                this.searchTextField = new TextField({ label: "Nostr (hex or 'nostr:')", enter: this.search, val: this.searchTextState }),
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

        SearchByNostrDlg.defaultSearchText = this.searchTextState.getValue();
        const event = await S.nostr.searchId(SearchByNostrDlg.defaultSearchText)

        if (event) {
            // console.log("EVENT FOUND: " + S.util.prettyPrint(event));
            this.close();
        }
        else {
            S.util.showMessage("Nothing was found.", "Search");
        }
    }
}
