import { CompIntf } from "../comp/base/CompIntf";
import { Divc } from "../comp/core/Divc";
import { Spinner } from "../comp/core/Spinner";
import { DialogBase } from "../DialogBase";

export class ProgressDlg extends DialogBase {

    constructor() {
        super("Loading...", "appModalContTinyWidth");
    }

    renderDlg(): CompIntf[] {
        return [
            new Divc({ className: "progressSpinner" }, [new Spinner()])
        ];
    }
}
