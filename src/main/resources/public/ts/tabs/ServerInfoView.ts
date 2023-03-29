import { getAs } from "../AppContext";
import { AppTab } from "../comp/AppTab";
import { Button } from "../comp/core/Button";
import { Div } from "../comp/core/Div";
import { Heading } from "../comp/core/Heading";
import { Pre } from "../comp/core/Pre";
import { TabIntf } from "../intf/TabIntf";
import { S } from "../Singletons";

export class ServerInfoView extends AppTab<any, ServerInfoView> {

    constructor(data: TabIntf<any, ServerInfoView>) {
        super(data);
        data.inst = this;
    }

    preRender(): boolean {
        const ast = getAs();

        this.setChildren([
            new Div(null, { className: "marginTop" }, [

                ast.serverInfoCommand === "getServerInfo" ? new Button("Refresh", () => {
                    S.view.runServerCommand("getServerInfo", null, "Info View", null);
                }, { className: "float-end" }) : null,

                new Heading(3, ast.serverInfoTitle),
                new Pre(ast.serverInfoText, { className: "serverInfoText" })
            ])
        ]);
        return true;
    }
}
