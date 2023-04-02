import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Clearfix } from "../comp/core/Clearfix";
import { Diva } from "../comp/core/Diva";
import { TextContent } from "../comp/core/TextContent";
import { DialogBase } from "../DialogBase";
import { S } from "../Singletons";

export class InboxNotifyDlg extends DialogBase {

    static CLOSE_TIMEOUT: number = 2500;

    constructor(private text: string, private nodeId: string) {
        super("Notification", "appModalContNarrowWidth");

        S.util.showSystemNotification("New Message", text);

        // setTimeout(() => {
        //     this.onMount(() => {
        //         this.close();
        //     });
        // }, InboxNotifyDlg.CLOSE_TIMEOUT);
    }

    renderDlg(): CompIntf[] {
        return [
            new Diva([
                new TextContent(this.text),
                new ButtonBar([
                    this.nodeId ? new Button("Go to Node", () => {
                        this.close();
                        S.nav.openContentNode(this.nodeId, false);
                    }) : null,
                    new Button("Close", this.close, null, "btn-secondary float-end")
                ], "marginTop"),
                new Clearfix() // required in case only ButtonBar children are float-end, which would break layout
            ])
        ];
    }
}
