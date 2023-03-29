import { CompIntf } from "../comp/base/CompIntf";
import { Div } from "../comp/core/Div";
import { Spinner } from "../comp/core/Spinner";
import { DialogBase } from "../DialogBase";

export class ProgressDlg extends DialogBase {

    constructor() {
        super("Loading...", "appModalContTinyWidth");
    }

    renderDlg(): CompIntf[] {
        return [
            new Div(null, { className: "progressSpinner" }, [new Spinner()])
        ];
    }
}
