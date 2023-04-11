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

    override domRemoveEvent = () => {
        NavPanelDlg.inst = null;
    }
}

PubSub.sub(C.PUBSUB_closeNavPanel, (payload: string) => {
    NavPanelDlg.inst?.close();
    NavPanelDlg.inst = null;
});
