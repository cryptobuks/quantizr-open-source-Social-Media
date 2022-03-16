import { ResultSetInfo } from "../ResultSetInfo";
import { CompIntf } from "../comp/base/CompIntf";
import { OpenGraphPanel } from "../comp/OpenGraphPanel";
import { AppState } from "../AppState";
import { Div } from "../comp/core/Div";

export interface TabIntf<PropsType = any> {
    // display name shown on the tab
    name: string;

    /* DOM ID of the tab button itself, but also the unique identifier for the tab. Note: even if there are perhaps
     multiple different instances of the same AppTab-derived class each one will need to have a unique id. This means
    we can in the future support multiple SearchView tabs opened simultaneously, each with a different ID of course */
    id: string;
    scrollPos: number;

    // todo-0: is there a common base class for this instead of just CompIntf?
    inst?: CompIntf;

    constructView(data: TabIntf): CompIntf;
    getTabSubOptions(state: AppState): Div;

    // controls whether to show tab button or not.
    isVisible(): boolean;

    rsInfo: ResultSetInfo;
    props: PropsType;

    openGraphComps: OpenGraphPanel[];
}
