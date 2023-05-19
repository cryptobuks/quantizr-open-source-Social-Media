import { getAs } from "../../AppContext";
import { AppState } from "../../AppState";
import { Div } from "../../comp/core/Div";
import { OpenGraphPanel } from "../../comp/OpenGraphPanel";
import { Constants as C } from "../../Constants";
import { TabIntf } from "../../intf/TabIntf";
import * as J from "../../JavaIntf";
import { TTSView } from "../TTSView";

export class TTSTab implements TabIntf<any> {
    name = "Text-to-Speech";
    tooltip = "Listen to text";
    id = C.TAB_TTS;
    scrollPos = 0;
    props = {};
    openGraphComps: OpenGraphPanel[] = [];
    topmostVisibleElmId: string = null;

    static inst: TTSTab = null;
    static ttsTabSelected: boolean = false;

    constructor() {
        TTSTab.inst = this;
    }

    isVisible = () => {
        return getAs().ttsRan || TTSTab.ttsTabSelected;
    };

    constructView = (data: TabIntf) => new TTSView(data);
    getTabSubOptions = (): Div => { return null; };

    findNode = (nodeId: string): J.NodeInfo => {
        return null;
    }

    nodeDeleted = (ust: AppState, nodeId: string): void => {
    }

    replaceNode = (ust: AppState, newNode: J.NodeInfo): void => {
    }

    processNode = (ust: AppState, func: (node: J.NodeInfo) => void): void => {
    }
}
