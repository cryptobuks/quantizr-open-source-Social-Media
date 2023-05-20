import { getAs } from "../AppContext";
import { Div } from "../comp/core/Div";
import { TabIntf } from "../intf/TabIntf";
import { S } from "../Singletons";
import { Comp } from "./base/Comp";

/* NOTE: All classes derived from AppTab should have each top-level (in the vertical dimension) item having
a class 'data.id' tacked on to it. This class is expected to be there for managing scrolling, and also the
IDs on each of those elements needs to be repeatable across all renders. */

// PT=Properties Type, TT=Tab Type
export class AppTab<PT = any, TT = any> extends Div {
    headingBar: Comp = null;

    constructor(public data: TabIntf<PT, TT>, private extraEditModeClass: string = null) {
        super(null, {
            id: data.id,
            // tabIndex is required or else scrolling by arrow keys breaks.
            tabIndex: "2"
        });

        this.attribs.className = this.getClass();
    }

    getClass = (): string => {
        const ast = getAs();
        const className = (ast.mobileMode ? "appTabPaneMobile " : "appTabPane ") + "customScrollbar " +
            (ast.userPrefs.editMode && this.extraEditModeClass ? (this.extraEditModeClass) : "") +
            (ast.activeTab === this.getId() ? " visible" : " invisible");
        return className;
    }

    override getScrollPos = (): number => {
        return this.data.scrollPos;
    }

    override setScrollPos = (pos: number): void => {
        this.data.scrollPos = pos;
    }

    scrollToNode = (nodeId: string): void => {
        if (!nodeId) return;
        const elm = S.domUtil.domElm(S.tabUtil.makeDomIdForNode(this.data, nodeId));
        if (elm) {
            this.scrollToElm(elm);
        }
    }

    scrollToElm = (elm: HTMLElement): void => {
        if (!elm) return;

        // Mobile mode doesn't use 'sticky' header in the tab, so we can scroll to the
        // exact location of offsetTop, without taking into account any sticky header height.
        if (getAs().mobileMode) {
            this.setScrollTop(elm.offsetTop);
        }
        else {
            // headingBar is not fixed height so we get it's hight in realtime here.
            const headingBarHeight = this.headingBar?.getRef()?.offsetHeight || 0;

            // we scroll up the additional 12 pixels just to make a slight gap between top row border
            // and heading border, because it's slightly better looking that way.
            let top = elm.offsetTop - headingBarHeight - 12;
            if (top < 0) top = 0;

            // If we were gonna scroll somewhere near the top of the page go ahead and scroll to the
            // the top and the node we're interested in will be no lower than the middle of the screen.
            if (top < window.innerHeight / 2) {
                top = 0;
            }

            if (this.data.scrollPos <= top && top + elm.offsetHeight < this.data.scrollPos + window.innerHeight) {
                // if we get here, the entire 'elm' should be visible on the page already
                // console.log("no scroll needed.");
            }
            else {
                this.setScrollTop(top);
            }
        }
    }
}
