import { store } from "../AppRedux";
import { AppState } from "../AppState";
import { AppTab } from "../comp/AppTab";
import { Heading } from "../comp/core/Heading";
import { Html } from "../comp/core/Html";
import { LogViewIntf } from "../intf/LogViewIntf";
import { TabIntf } from "../intf/TabIntf";
import { Log } from "../Log";

export class LogView extends AppTab implements LogViewIntf {
    static logs: string = "";

    constructor(data: TabIntf) {
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

    preRender(): void {
        const state: AppState = store.getState();
        this.attribs.className = this.getClass(state);

        this.setChildren([
            new Heading(3, "Log", { className: "logView" }),
            new Html("<pre>" + LogView.logs + "</pre>")
        ]);
    }

    // Opens the tab, querying the info from the server to update
    open = (readOnly: boolean, userId: string): any => {
    }

    // close(): void {
    //     // const state: AppState = store.getState();
    //     // dispatch({
    //     //     type: "Action_InitUserProfile",
    //     //     state,
    //     //     update: (s: AppState): void => {
    //     //         s.activeTab = C.TAB_MAIN;
    //     //         s.userProfile = null;
    //     //     }
    //     // });
    // }
}
