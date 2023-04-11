import { getAs } from "../../AppContext";
import { TabIntf } from "../../intf/TabIntf";
import { S } from "../../Singletons";
import { Div } from "../core/Div";
import { Icon } from "../core/Icon";
import { NodeCompContent } from "./NodeCompContent";

export class NodeCompParentNodes extends Div {

    constructor(public tabData: TabIntf<any>) {
        super(null, {
            id: "parent_" + getAs().node.id
            // WARNING: Leave this tabIndex here. it's required for focsing/scrolling
            // tabIndex: "-1"
        });
    }

    override preRender(): boolean {
        const ast = getAs();

        /* Currently our "renderNode" will only ever load a single parent, so we just pull the first element
         from 'parents' array, but the system architecture is such that if we ever want to display
         more than one parent we can implement that easily */
        const node = ast.node?.parents?.length > 0 ? ast.node?.parents[0] : null;

        if (!node) {
            this.setChildren(null);
            return false;
        }

        this.attribs.className = "parentNodeContentStyle";
        const showCloseParentsIcon = ast.userPrefs.showParents && ast.node.parents?.length > 0;

        this.setChildren([
            // state.userPrefs.showMetaData ? new NodeCompRowHeader(node, true, true, false, false, true, false) : null,
            showCloseParentsIcon ? new Icon({
                className: "fa fa-level-up fa-lg showParentsIcon float-end",
                title: "Toggle: Show Parent on page",
                onClick: S.edit.toggleShowParents
            }) : null,

            // hard-coding 'false' isLinkedNode here. Should only effect styling on openGraphPanel in boosted items,
            // so not worried about that here, for now.
            new NodeCompContent(node, this.tabData, false, true, null, null, true, false, null)
        ]);
        return true;
    }
}
