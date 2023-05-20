import { CompIntf } from "../comp/base/CompIntf";
import { RightNavPanel } from "../comp/RightNavPanel";
import { Constants as C } from "../Constants";
import { DialogBase, DialogMode } from "../DialogBase";
import { PubSub } from "../PubSub";

export class NavPanelDlg extends DialogBase {
    static inst: NavPanelDlg = null;
    constructor() {
        super("Navigation", "appModalNavPanel", true, DialogMode.POPUP, true);
        NavPanelDlg.inst = this;
    }

    renderDlg(): CompIntf[] {
        return [new RightNavPanel()];
    }
}

PubSub.sub(C.PUBSUB_closeNavPanel, (payload: string) => {
    if (NavPanelDlg.inst) {
        NavPanelDlg.inst.close();
        NavPanelDlg.inst = null;
    }
});
