import { getAs } from "../AppContext";
import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Diva } from "../comp/core/Diva";
import { RadioButton } from "../comp/core/RadioButton";
import { RadioButtonGroup } from "../comp/core/RadioButtonGroup";
import { TextArea } from "../comp/core/TextArea";
import { TextField } from "../comp/core/TextField";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { Validator } from "../Validator";
import { UserProfileDlg } from "./UserProfileDlg";

interface LS { // Local State
    searchType?: string;
}

export class SearchUsersDlg extends DialogBase {
    static helpExpanded: boolean = false;
    static defaultSearchText: string = "";
    static defaultNostrRelay: string = "";
    searchTextField: TextField;
    searchTextState: Validator = new Validator();
    nostrRelayState: Validator = new Validator();

    constructor() {
        super("Search Users", "appModalContMediumWidth");
        this.onMount(() => this.searchTextField?.focus());

        this.mergeState<LS>({
            searchType: J.Constant.SEARCH_TYPE_USER_LOCAL
        });
        this.searchTextState.setValue(SearchUsersDlg.defaultSearchText);
        this.nostrRelayState.setValue(SearchUsersDlg.defaultNostrRelay);
    }

    renderDlg(): CompIntf[] {
        const isNostr = this.getState<LS>().searchType === J.Constant.SEARCH_TYPE_USER_NOSTR;
        const isNostrNip05 = this.getState<LS>().searchType === J.Constant.SEARCH_TYPE_USER_NOSTR_NIP05;

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
            }),
            new RadioButton("Nostr User", false, "optionsGroup", null, {
                setValue: (checked: boolean) => {
                    if (checked) {
                        this.mergeState<LS>({ searchType: J.Constant.SEARCH_TYPE_USER_NOSTR });
                    }
                },
                getValue: (): boolean => isNostr
            }),
            new RadioButton("Nostr User (NIP-05)", false, "optionsGroup", null, {
                setValue: (checked: boolean) => {
                    if (checked) {
                        this.mergeState<LS>({ searchType: J.Constant.SEARCH_TYPE_USER_NOSTR_NIP05 });
                    }
                },
                getValue: (): boolean => isNostrNip05
            })

        ], "marginBottom marginTop");

        let userLabel;
        if (isNostr) {
            userLabel = "Nostr User (npub or hex)";
        }
        else if (isNostrNip05) {
            userLabel = "Nostr NIP-05";
        }
        else {
            userLabel = "User Name";
        }

        return [
            new Diva([
                this.searchTextField = new TextField({ label: userLabel, enter: this.search, val: this.searchTextState }),
                isNostr || isNostrNip05 ? new TextArea("Nostr Relays", { rows: 5 }, this.nostrRelayState, null, false, 5) : null,
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
        SearchUsersDlg.defaultNostrRelay = this.nostrRelayState.getValue();
        const searchType = this.getState<LS>().searchType;

        if (searchType === J.Constant.SEARCH_TYPE_USER_NOSTR) {
            if (!SearchUsersDlg.defaultNostrRelay) {
                S.util.showMessage("Nostr needs a relay.", "Warning");
                return;
            }
        }

        if (searchType === J.Constant.SEARCH_TYPE_USER_NOSTR || searchType === J.Constant.SEARCH_TYPE_USER_NOSTR_NIP05) {

            const userRelays = getAs().userProfile?.relays || "";

            const ret: J.SaveNostrEventResponse = await S.nostr.readUserMetadata(SearchUsersDlg.defaultSearchText,
                SearchUsersDlg.defaultNostrRelay + "\n" + userRelays, searchType === J.Constant.SEARCH_TYPE_USER_NOSTR_NIP05);
            // console.log("SaveNostrEventResponse: " + S.util.prettyPrint(ret));
            this.close();
            if (ret?.accntNodeIds?.length > 0) {
                new UserProfileDlg(ret.accntNodeIds[0]).open();
            }
        }
        else {
            const desc = "User " + SearchUsersDlg.defaultSearchText;
            const success = await S.srch.search(null, "", SearchUsersDlg.defaultSearchText,
                searchType,
                desc,
                null,
                false,
                false, 0, true, "mtm", "DESC", false, false, false);
            if (success) {
                this.close();
            }
        }
    }
}
