import { AppState } from "../AppState";
import { AppTab } from "../comp/AppTab";
import { Div } from "../comp/core/Div";
import { OpenGraphPanel } from "../comp/OpenGraphPanel";
import * as J from "../JavaIntf";

// todo-1: we should make this a base class instead of interface so that the classes that
// derive from it won't have to create their own instances of all data members but can
// inheret default ones (especially scrollPos, openGraphComps, etc)
//
// PT=PropsType, TT=TabType
export interface TabIntf<PT = any, TT = any> {
    // display name shown on the tab
    name: string;

    // help tooltip for menu
    tooltip: string;

    /* DOM ID of the tab button itself, but also the unique identifier for the tab. Note: even if there are perhaps
     multiple different instances of the same AppTab-derived class each one will need to have a unique id. This means
    we can in the future support multiple SearchView tabs opened simultaneously, each with a different ID of course.

    This 'id' is used as an 'identification-only' class name on each of the rows/nodes displayed on any tab
    */
    id: string;
    scrollPos: number;
    inst?: TT; // AppTab;

    // used for re-scrolling screen back to same place after the page layout may have changed due to 'edit mode' or 'info mode'
    // turning on/off or other places.
    topmostVisibleElmId: string;

    constructView(data: TabIntf): AppTab;
    getTabSubOptions(): Div;

    // controls whether to show tab button or not.
    isVisible(): boolean;

    // tabs are required to be able to search their known nodes and find any that are found, or null if not found
    findNode(nodeId: string): J.NodeInfo;
    nodeDeleted(ust: AppState, nodeId: string): void;
    replaceNode(ust: AppState, newNode: J.NodeInfo): void;
    processNode(ust: AppState, func: (node: J.NodeInfo) => void): void;

    props: PT;

    openGraphComps: OpenGraphPanel[];
}
