import { getAs } from "../AppContext";
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
                this.searchTextField = new TextField({ label: "Nostr Resource", enter: this.search, val: this.searchTextState }),
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
        let event = null;
        try {
            S.rpcUtil.incRpcCounter();
            const find = S.nostr.translateNip19(SearchByNostrDlg.defaultSearchText);
            const relays = S.nostr.getRelays(getAs().userProfile.relays);
            event = await S.nostr.getEvent(find, null, relays);
            if (event) {
                const res = await S.nostr.persistEvents([event]);
                if (res?.eventNodeIds?.length > 0) {
                    const desc = "For ID: " + SearchByNostrDlg.defaultSearchText;
                    await S.srch.search(null, "node.id", res.eventNodeIds[0], null, desc, null, false,
                        false, 0, true, null, null, false, false, false);
                }
            }
        }
        finally {
            S.rpcUtil.decRpcCounter();
        }

        if (event) {
            console.log("EVENT FOUND: " + S.util.prettyPrint(event));
            this.close();
        }
        else {
            S.util.showMessage("Nothing was found.", "Search");
        }
    }
}
