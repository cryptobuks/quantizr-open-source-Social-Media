import { AppTab } from "../comp/AppTab";
import { Comp } from "../comp/base/Comp";
import { CompIntf } from "../comp/base/CompIntf";
import { Div } from "../comp/core/Div";
import { Diva } from "../comp/core/Diva";
import { Divc } from "../comp/core/Divc";
import { TabIntf } from "../intf/TabIntf";

export class IPSMView extends AppTab<any, IPSMView> {
    constructor(data: TabIntf<any, IPSMView>) {
        super(data);
        data.inst = this;
    }

    override preRender(): boolean {
        const children: Comp[] = [];

        children.push(new Diva([
            new Divc({ className: "marginTop" }, [
                this.renderHeading()
            ]),
            new Div("Realtime IPFS PubSub events from ipsm-heartbeat topic...")
        ]));

        if (this.data.props?.events) {
            this.data.props.events.forEach((e: string) => children.push(new Div(e, { className: "ipsmFeedItem" })));
        }

        this.setChildren([new Divc({ className: "feedView" }, children)]);
        return true;
    }

    /* overridable (don't use arrow function) */
    renderHeading(): CompIntf {
        return new Div("IPSM Console", { className: "tabTitle" });
    }
}
