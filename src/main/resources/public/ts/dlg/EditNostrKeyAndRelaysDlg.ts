import { dispatch, getAs } from "../AppContext";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { Validator } from "../Validator";
import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Diva } from "../comp/core/Diva";
import { TextArea } from "../comp/core/TextArea";

export class EditNostrKeyAndRelaysDlg extends DialogBase {
    userTextState: Validator = new Validator();
    nostrRelayState: Validator = new Validator();

    constructor() {
        super("Nostr Relays", "appModalContMediumWidth");
        // this.onMount(() => this.userTextField?.focus());
        this.nostrRelayState.setValue(getAs().userProfile.relays);
    }

    renderDlg(): CompIntf[] {
        return [
            new Diva([
                new TextArea("Nostr Relays", { rows: 8 }, this.nostrRelayState, null, false, 8),
                new ButtonBar([
                    new Button("Save", this.save, null, "btn-primary"),
                    new Button("Cancel", this.close, null, "btn-secondary float-end")
                ], "marginTop")
            ])
        ];
    }

    save = async () => {
        const res = await S.rpcUtil.rpc<J.SaveNostrSettingsRequest, J.SaveNostrEventResponse>("saveNostrSettings", {
            target: null,
            key: null,
            relays: this.nostrRelayState.getValue()
        });
        if (res.success) {
            dispatch("setUserProfileRelays", s => {
                s.userProfile.relays = this.nostrRelayState.getValue();
            });
        }
        this.close();
    }
}
