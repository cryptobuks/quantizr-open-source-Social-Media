import { getAs } from "../AppContext";
import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Diva } from "../comp/core/Diva";
import { RadioButton } from "../comp/core/RadioButton";
import { RadioButtonGroup } from "../comp/core/RadioButtonGroup";
import { TextField } from "../comp/core/TextField";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { Validator } from "../Validator";

interface LS { // Local State
    searchType?: string;
}

export class SearchUsersDlg extends DialogBase {
    static helpExpanded: boolean = false;
    static defaultSearchText: string = "";
    searchTextField: TextField;
    searchTextState: Validator = new Validator();

    constructor() {
        super("Search Users", "appModalContMediumWidth");
        this.onMount(() => this.searchTextField?.focus());

        this.mergeState<LS>({
            searchType: J.Constant.SEARCH_TYPE_USER_LOCAL
        });
        this.searchTextState.setValue(SearchUsersDlg.defaultSearchText);
    }

    renderDlg(): CompIntf[] {
        const adminOptions = new RadioButtonGroup([
            getAs().isAdminUser ? new RadioButton("All Users", false, "optionsGroup", null, {
                setValue: (checked: boolean) => {
                    if (checked) {
                        this.mergeState<LS>({ searchType: J.Constant.SEARCH_TYPE_USER_ALL });
                    }
                },
                getValue: (): boolean => this.getState<LS>().searchType === J.Constant.SEARCH_TYPE_USER_ALL
            }) : null,
            new RadioButton("Local Users", true, "optionsGroup", null, {
                setValue: (checked: boolean) => {
                    if (checked) {
                        this.mergeState<LS>({ searchType: J.Constant.SEARCH_TYPE_USER_LOCAL });
                    }
                },
                getValue: (): boolean => this.getState<LS>().searchType === J.Constant.SEARCH_TYPE_USER_LOCAL
            }),
            new RadioButton("Foreign Users", false, "optionsGroup", null, {
                setValue: (checked: boolean) => {
                    if (checked) {
                        this.mergeState<LS>({ searchType: J.Constant.SEARCH_TYPE_USER_FOREIGN });
                    }
                },
                getValue: (): boolean => this.getState<LS>().searchType === J.Constant.SEARCH_TYPE_USER_FOREIGN
            })
        ], "marginBottom marginTop");

        return [
            new Diva([
                this.searchTextField = new TextField({ label: "User Name", enter: this.search, val: this.searchTextState }),
                adminOptions,
                new ButtonBar([
                    new Button("Search", this.search, null, "btn-primary"),
                    // this Graph button will work, but why graph users? ... there are no linkages between them... yet.
                    // todo: however the VERY amazing feature of showing a true "Graph of Who is Following Who" would be
                    // possible and not even all that difficult based on the existing code already written.
                    // new Button("Graph", this.graph, null, "btn-primary"),
                    // we can steal the 'graph' from from the other dialogs when needed.
                    new Button("Close", this.close, null, "btn-secondary float-end")
                ], "marginTop")
            ])
        ];
    }

    search = async () => {
        if (!this.validate()) {
            return;
        }

        SearchUsersDlg.defaultSearchText = this.searchTextState.getValue();

        const desc = "User " + SearchUsersDlg.defaultSearchText;
        const success = await S.srch.search(null, "", SearchUsersDlg.defaultSearchText,
            this.getState<LS>().searchType,
            desc,
            null,
            false,
            false, 0, true, "mtm", "DESC", false, false, false);
        if (success) {
            this.close();
        }
    }
}
