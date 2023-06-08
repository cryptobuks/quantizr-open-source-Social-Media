import { ReactNode } from "react";
import { asyncDispatch, getAs } from "../AppContext";
import { S } from "../Singletons";
import { Div } from "../comp/core/Div";
import { Comp } from "./base/Comp";
import { CompIntf } from "./base/CompIntf";
import { Divc } from "./core/Divc";

export class Menu extends Comp {
    constructor(public name: string, public menuItems: CompIntf[], private func: Function = null, private floatRightComp: CompIntf = null) {
        super({ id: "menu_" + S.util.hashOfString(name), className: "menuCard" });
    }

    override compRender = (): ReactNode => {
        const ast = getAs();
        const expanded = getAs().expandedMenus.has(this.name);

        this.setChildren([
            new Div(this.name, {
                className: (expanded ? "menuHeadingExpanded" : "menuHeading") + (ast.mobileMode ? " mobileMenuText" : ""),
                id: this.getId("heading"),
                onClick: () => {
                    asyncDispatch("ToggleExpansion", s => S.nav.changeMenuExpansion(s, "toggle", this.name));
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
