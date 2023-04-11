import { ReactNode } from "react";
import { getAs } from "../AppContext";
import { Div } from "../comp/core/Div";
import { Constants as C } from "../Constants";
import { MenuPanelState } from "../Interfaces";
import { PubSub } from "../PubSub";
import { S } from "../Singletons";
import { Comp } from "./base/Comp";
import { CompIntf } from "./base/CompIntf";
import { Divc } from "./core/Divc";

export class Menu extends Comp {
    static userClickedMenu: boolean = false;

    constructor(public menuPanelState: MenuPanelState, public name: string, public menuItems: CompIntf[], private func: Function = null, private floatRightComp: CompIntf = null) {
        super({ id: "menu_" + S.util.hashOfString(name), className: "menuCard" });
    }

    override compRender = (): ReactNode => {
        const ast = getAs();
        const expanded = this.menuPanelState.expanded.has(this.name);

        this.setChildren([
            new Div(this.name, {
                className: (expanded ? "menuHeadingExpanded" : "menuHeading") + (ast.mobileMode ? " mobileMenuText" : ""),
                id: this.getId("heading"),
                onClick: () => {
                    PubSub.pub(C.PUBSUB_menuExpandChanged, { op: "toggle", name: this.name });
                    Menu.userClickedMenu = true;
                    if (this.func) {
                        this.func();
                    }
                }
            }
                , [expanded ? this.floatRightComp : null]),

            expanded ? new Divc({
                id: this.getId("itemsCont"),
                className: "menuCardBody"
            }, [
                new Divc({
                    id: this.getId("items"),
                    className: "menuPanelItems"
                }, this.menuItems)]) : null
        ]);
        return this.tag("div");
    }
}
