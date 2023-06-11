import { Comp } from "../comp/base/Comp";
import { TabIntf } from "../intf/TabIntf";
import { SharesRSInfo } from "../SharesRSInfo";
import { S } from "../Singletons";
import { ResultSetView } from "./ResultSetView";

export class SharedNodesResultSetView<PT extends SharesRSInfo> extends ResultSetView<PT, SharedNodesResultSetView<PT>> {

    constructor(data: TabIntf<PT, SharedNodesResultSetView<PT>>) {
        super(data);
        data.inst = this;
        this.showContentHeading = false;
    }

    override pageChange(delta: number): void {
        let page = this.data.props.page;
        if (delta !== null) {
            page = delta === 0 ? 0 : this.data.props.page + delta;
        }

        S.srch.findSharedNodes(this.data.props.node,
            page,
            this.data.props.shareNodesType,
            this.data.props.shareTarget,
            this.data.props.accessOption);
    }

    override extraPagingComps = (): Comp[] => {
        return null;
    }

    override getFloatRightHeaderComp = (): Comp => {
        return null;
    }
}
