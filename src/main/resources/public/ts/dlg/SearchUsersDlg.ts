import {
    Event
} from "nostr-tools";
import { getAs } from "../AppContext";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { Val } from "../Val";
import { Validator } from "../Validator";
import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Diva } from "../comp/core/Diva";
import { RadioButton } from "../comp/core/RadioButton";
import { RadioButtonGroup } from "../comp/core/RadioButtonGroup";
import { TextArea } from "../comp/core/TextArea";
import { TextField } from "../comp/core/TextField";
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

        const adminOptions = new RadioButtonGroup([
            getAs().isAdminUser ? new RadioButton("All Users", false, "optionsGroup", null, {
                setValue: (checked: boolean) => {
                    if (checked) {
                        this.mergeState<LS>({ searchType: J.Constant.SEARCH_TYPE_USER_ALL });
                    }
                },
                getValue: (): boolean => this.getState<LS>().searchType === J.Constant.SEARCH_TYPE_USER_ALL
            }) : null,
            new RadioButton("Local User", true, "optionsGroup", null, {
                setValue: (checked: boolean) => {
                    if (checked) {
                        this.mergeState<LS>({ searchType: J.Constant.SEARCH_TYPE_USER_LOCAL });
                    }
                },
                getValue: (): boolean => this.getState<LS>().searchType === J.Constant.SEARCH_TYPE_USER_LOCAL
            }),
            new RadioButton("Foreign User", false, "optionsGroup", null, {
                setValue: (checked: boolean) => {
                    if (checked) {
                        this.mergeState<LS>({ searchType: J.Constant.SEARCH_TYPE_USER_FOREIGN });
                    }
                },
                getValue: (): boolean => this.getState<LS>().searchType === J.Constant.SEARCH_TYPE_USER_FOREIGN
            }),
            new RadioButton("Nostr User (hex, npub, or NIP-05)", false, "optionsGroup", null, {
                setValue: (checked: boolean) => {
                    if (checked) {
                        this.mergeState<LS>({ searchType: J.Constant.SEARCH_TYPE_USER_NOSTR });
                    }
                },
                getValue: (): boolean => isNostr
            })
        ], "marginBottom marginTop");

        return [
            new Diva([
                this.searchTextField = new TextField({ label: "User", enter: this.search, val: this.searchTextState }),
                isNostr ? new TextArea("Nostr Relays", { rows: 5 }, this.nostrRelayState, null, false, 5) : null,
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
            const nostrEvent = new Val<Event>();
            try {
                S.rpcUtil.incRpcCounter();
                await S.nostr.readUserMetadataEx(SearchUsersDlg.defaultSearchText,
                    SearchUsersDlg.defaultNostrRelay, true, nostrEvent);
            }
            finally {
                S.rpcUtil.decRpcCounter();
            }
            this.close();

            if (nostrEvent.val) {
                new UserProfileDlg(null, nostrEvent.val.pubkey).open();
            }
            else {
                S.util.showMessage("Unable to load user info", "Warning");
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
