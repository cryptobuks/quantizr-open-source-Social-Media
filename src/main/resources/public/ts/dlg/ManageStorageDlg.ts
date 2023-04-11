import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Html } from "../comp/core/Html";
import { PieChart } from "../comp/core/PieChart";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";

interface LS { // Local State
    title: string,
    info: string;
    binQuota: number;
    binTotal: number;
}

export class ManageStorageDlg extends DialogBase {

    constructor() {
        super("Storage Space", "appModalContNarrowWidth");
    }

    override getTitleText(): string {
        const state: any = this.getState<LS>();
        return state.title || "Storage Space";
    }

    renderDlg(): CompIntf[] {
        const state: any = this.getState<LS>();

        let data = null;
        if (state.binQuota) {
            const available = state.binQuota - state.binTotal;
            data = [
                { label: "Used: " + S.util.formatMemory(state.binTotal), value: state.binTotal, color: "#377eb8" },
                { label: "Available: " + S.util.formatMemory(available), value: available, color: "#4daf4a" }];
        }

        return [
            data ? new PieChart(250, "d3PieChart", data) : null,
            new Html(this.getState<LS>().info),

            new ButtonBar([
                new Button("Close", () => {
                    this.close();
                }, null, "btn-secondary")
            ], "marginTop")
        ];
    }

    override async preLoad(): Promise<void> {
        const res = await S.rpcUtil.rpc<J.GetUserAccountInfoRequest, J.GetUserAccountInfoResponse>("getUserAccountInfo");

        let used = "";
        if (res.binQuota <= 0) {
            res.binQuota = 20 * 1024 * 1024;
        }
        if (res.binQuota > 0) {
            if (res.binTotal < 10) {
                used = "0%";
            }
            else {
                used = (res.binTotal * 100 / res.binQuota).toFixed(1) + "%";
            }
        }

        const quota = S.util.formatMemory(res.binQuota);

        const info = //
            "<h5>Used: " + S.util.formatMemory(res.binTotal) + " (" + used + ")</h5>";

        this.mergeState<LS>({
            title: "Storage Space: " + quota,
            info,
            binQuota: res.binQuota,
            binTotal: res.binTotal
        });
    }
}
