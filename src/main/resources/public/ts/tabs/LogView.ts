import { AppTab } from "../comp/AppTab";
import { Heading } from "../comp/core/Heading";
import { Html } from "../comp/core/Html";
import { LogViewIntf } from "../intf/LogViewIntf";
import { TabIntf } from "../intf/TabIntf";
import { Log } from "../Log";

export class LogView extends AppTab<any, LogView> implements LogViewIntf {
    static logs: string = "";

    constructor(data: TabIntf<any, LogView>) {
        super(data);
        data.inst = this;

        Log.logView = this;

        // For some reason I can't get the console.log override to work.
        // (function() {
        //     let oldLog = console.log;
        //     console.log = function (msg) {
        //         LogView.logs += msg;
        //         LogView.logs += "\n";
        //         oldLog.apply(console, arguments);
        //     };
        // })();
    }

    log = (msg: string): any => {
        LogView.logs += msg;
        LogView.logs += "\n";
    }

    override preRender(): boolean {
        this.setChildren([
            new Heading(3, "Log", { className: "logView" }),
            new Html("<pre>" + LogView.logs + "</pre>")
        ]);
        return true;
    }

    // Opens the tab, querying the info from the server to update
    open = (readOnly: boolean, userId: string): any => {
    }
}
