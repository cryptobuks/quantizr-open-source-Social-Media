import { getAs, promiseDispatch } from "../AppContext";
import { Div } from "../comp/core/Div";
import { Constants as C } from "../Constants";
import { AppTab } from "./AppTab";
import { CompIntf } from "./base/CompIntf";
import { Divc } from "./core/Divc";

export class TabPanel extends Div {
    static inst: TabPanel;

    constructor(private customTopComp: CompIntf = null) {
        super(null, { id: C.ID_TAB });
        TabPanel.inst = this;
        const ast = getAs();

        if (ast.mobileMode) {
            this.attribs.className = "col-12 tabPanelMobile";
        }
        else {
            const panelCols = ast.userPrefs.mainPanelCols || 6;
            this.attribs.className = "col-" + panelCols + " tabPanel";
        }
    }

    setVisibility = async (visible: boolean): Promise<void> => {
        return promiseDispatch("SetTabPanelVis", s => {
            s.tabPanelVisible = visible;
        });
    }

    override preRender(): boolean {
        const ast = getAs();

        if (!ast.tabPanelVisible) {
            // not sure why, but this had no effect, we're ok without it, but
            // would be nicer if we could hide the comp during preparing final scrolling.
            this.attribs.className += " compHidden";
        }

        this.setChildren([
            this.customTopComp,
            new Divc({
                className: "row tabContent",
                role: "main",
                id: "tabPanelContentId"
            }, this.buildTabs())
        ]);
        return true;
    }

    buildTabs = (): AppTab[] => {
        const ast = getAs();
        const tabs = ast.tabData.map(tab => {
            if (tab.isVisible() && tab.id === ast.activeTab) {
                return tab.constructView(tab);
            }
            return null;
        }).filter(c => !!c);
        return tabs;
    }
}
