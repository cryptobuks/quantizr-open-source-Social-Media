import { Comp } from "../comp/base/Comp";
import { CompIntf } from "../comp/base/CompIntf";
import { Div } from "../comp/core/Div";
import { FollowingRSInfo } from "../FollowingRSInfo";
import { TabIntf } from "../intf/TabIntf";
import { S } from "../Singletons";
import { ResultSetView } from "./ResultSetView";

export class FollowingResultSetView<PT extends FollowingRSInfo> extends ResultSetView<PT, FollowingResultSetView<PT>> {

    constructor(data: TabIntf<PT, FollowingResultSetView<PT>>) {
        super(data);
        this.allowHeader = false;
        this.allowFooter = false;
        data.inst = this;
        this.showContentHeading = false;
    }

    pageChange(delta: number): void {
        let page = this.data.props.page;

        // Yes the check against null IS required. Don't change.
        if (delta !== null) {
            page = delta === 0 ? 0 : this.data.props.page + delta;
        }
        S.srch.showFollowing(page, this.data.props.showingFollowingOfUser);
    }

    override renderHeading(): CompIntf {
        return new Div("@" + this.data.props.showingFollowingOfUser + " is Following...", { className: "tabTitle" });
    }

    extraPagingComps = (): Comp[] => {
        return null;
    }

    getFloatRightHeaderComp = (): Comp => {
        return null;
    }
}
