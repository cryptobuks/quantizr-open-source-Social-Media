import { dispatch, getAs } from "../AppContext";
import { Div } from "../comp/core/Div";
import { Img } from "../comp/core/Img";
import { Span } from "../comp/core/Span";
import { Constants as C } from "../Constants";
import { MenuPanel } from "../MenuPanel";
import { S } from "../Singletons";
import { FeedTab } from "../tabs/data/FeedTab";
import { Divc } from "./core/Divc";
import { Icon } from "./core/Icon";
import { TabPanelButtons } from "./TabPanelButtons";

export class LeftNavPanel extends Div {
    private static scrollPos: number = 0;
    public static inst: LeftNavPanel = null;

    constructor() {
        super(null, {
            id: C.ID_LHS,
            // tabIndex is required or else scrolling by arrow keys breaks.
            tabIndex: "1"
        });

        const ast = getAs();

        let cols = ast.userPrefs.mainPanelCols || 6;
        if (cols < 4) cols = 4;
        if (cols > 8) cols = 8;

        let leftCols = 4;
        if (cols >= 5) {
            leftCols--;
        }
        if (cols >= 7) {
            leftCols--;
        }

        this.attribs.className = "col-" + leftCols + " leftNavPanel customScrollbar";
        LeftNavPanel.inst = this;
    }

    override getScrollPos = (): number => {
        return LeftNavPanel.scrollPos;
    }

    override setScrollPos = (pos: number): void => {
        LeftNavPanel.scrollPos = pos;
    }

    override preRender(): boolean {
        const ast = getAs();

        let myMessages = ast.myNewMessageCount > 0
            ? (ast.myNewMessageCount + " new posts") : "";

        const nostrMessages = ast.nostrNewMessageCount > 0
            ? (ast.nostrNewMessageCount + " new posts") : "";

        // todo-2: this is a hack to keep the new incomming "chat" messages (Node Feed) from tricking
        // user into clicking on it which takes them AWAY from the chat. We do this by setting messages to null
        // if feedFilterRoodNode is non-null which means user is in a node chat. I should consider having
        // a "Chat" tab that's separate from the "Feed" tab. Maybe the ChatView should be subclass of FeedView?
        if (FeedTab.inst?.props?.feedFilterRootNode) {
            myMessages = null;
        }

        this.setChildren([
            new Divc({ id: "appLHSHeaderPanelId", className: "lhsHeaderPanel" }, [
                !ast.isAnonUser || ast.anonShowLHSMenu ? new Img({
                    className: "leftNavLogoImg",
                    src: "/branding/logo-50px-tr.jpg",
                    onClick: S.util.loadAnonPageHome,
                    title: "Go to Portal Home Node"
                }) : null,
                // todo-2: need to add a similar message over to the 'logoText' that's active for mobile
                // which is in a different class.
                new Span(null, { className: "float-end" }, [
                    ast.nostrQueryRunning ? new Span("Waiting for Nostr...", {
                        className: "nostrWaitNote"
                    }) : null,
                    myMessages && !nostrMessages ? new Span(myMessages, {
                        className: "newMessagesNote",
                        onClick: S.nav.showMyNewMessages,
                        title: "Show your new messages"
                    }) : null,
                    nostrMessages ? new Span(nostrMessages, {
                        className: "newNostrMessagesNote",
                        onClick: S.srch.refreshFeed,
                        title: "Show new Nostr messages"
                    }) : null,
                    ast.userName && ast.isAnonUser ? new Icon({
                        className: "fa fa-bars fa-2x clickable",
                        onClick: () => {
                            dispatch("ToggleLHS", s => {
                                s.anonShowLHSMenu = !s.anonShowLHSMenu;
                            })
                        },
                        title: "Show Menu"
                    }) : null
                ])
            ]),
            ast.isAnonUser ? null : new MenuPanel(),

            // if anon user
            ast.isAnonUser && ast.anonShowLHSMenu ? new TabPanelButtons(true, ast.mobileMode ? "rhsMenuMobile" : "rhsMenu") : null
        ]);
        return true;
    }
}
