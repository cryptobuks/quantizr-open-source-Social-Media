import { getAs } from "../AppContext";
import { Clearfix } from "../comp/core/Clearfix";
import { IconButton } from "../comp/core/IconButton";
import { Img } from "../comp/core/Img";
import { Constants as C } from "../Constants";
import { DialogMode } from "../DialogBase";
import { NavPanelDlg } from "../dlg/NavPanelDlg";
import { FullScreenType } from "../Interfaces";
import { PubSub } from "../PubSub";
import { S } from "../Singletons";
import { CompIntf } from "./base/CompIntf";
import { Divc } from "./core/Divc";
import { Heading } from "./core/Heading";
import { Progress } from "./core/Progress";
import { Span } from "./core/Span";
import { FullScreenCalendar } from "./FullScreenCalendar";
import { FullScreenControlBar } from "./FullScreenControlBar";
import { FullScreenGraphViewer } from "./FullScreenGraphViewer";
import { FullScreenImgViewer } from "./FullScreenImgViewer";
import { LeftNavPanel } from "./LeftNavPanel";
import { Main } from "./Main";
import { RightNavPanel } from "./RightNavPanel";
import { TabPanel } from "./TabPanel";

export class App extends Main {

    constructor() {
        super(null, { id: "appPanelId", role: "main" });
    }

    override preRender(): boolean {
        const ast = getAs();

        if (!ast.appInitComplete) {
            this.setChildren([new Progress()]);
            return true;
        }

        /* For mobile mode we render just the topmost dialog, if dialogs exist, and don't render anything else at all */
        if (ast.mobileMode && ast.dialogStack.length > 0) {
            // eventually ONLY mobile will do this 'top-only' display, and desktop mode will have all dialog
            // divs simultaneously onscreen in background of top one.
            const dialog = ast.dialogStack[ast.dialogStack.length - 1];
            if (dialog && dialog.mode !== DialogMode.POPUP) {
                this.setChildren([dialog]);
                return true;
            }
        }

        const fullScreenViewer = this.getFullScreenViewer();
        const mobileTopBar = this.getTopMobileBar();
        this.attribs.className = "container-fluid " + (ast.mobileMode ? "mainContainerMobile" : "mainContainer");

        if (fullScreenViewer) {
            this.setChildren([
                ast.fullScreenConfig.type !== FullScreenType.CALENDAR ? new FullScreenControlBar() : null,
                new Clearfix(),
                fullScreenViewer
            ]);
        }
        else {
            if (S.quanta.configRes.requireCrypto && !S.crypto.avail) {
                this.setChildren([new Heading(4, S.quanta.configRes.brandingAppName + " requires a browser with crypto features.")]);
                return true;
            }

            this.setChildren([
                new Divc({
                    className: "row mainAppRow",
                    id: "appMainContainer"
                }, [
                    ast.mobileMode ? null : new LeftNavPanel(),
                    new TabPanel(mobileTopBar),
                    ast.mobileMode ? null : new RightNavPanel()
                ])
            ]);
        }

        if (ast.dialogStack?.length > 0) {
            this.addChildren(ast.dialogStack);
        }
        return true;
    }

    /* This is where we send an event that lets code hook into the render cycle to process whatever needs
        to be done AFTER the main render is complete, like doing scrolling for example */
    override domUpdateEvent = () => {
        PubSub.pub(C.PUBSUB_mainWindowScroll);
        PubSub.pub(C.PUBSUB_postMainWindowScroll);
    };

    getFullScreenViewer = (): CompIntf => {
        switch (getAs().fullScreenConfig.type) {
            case FullScreenType.IMAGE:
                return new FullScreenImgViewer();
            case FullScreenType.GRAPH:
                return new FullScreenGraphViewer();
            case FullScreenType.CALENDAR:
                return new FullScreenCalendar();
            default:
                return null;
        }
    }

    getTopMobileBar = (): CompIntf => {
        const ast = getAs();
        if (ast.mobileMode) {
            // DO NOT DELETE:
            // Currently we have no need to show the menu to anonymous users, but I want to keep
            // this here for future purposes in case we eventually do need this menu.
            const menuButton = ast.isAnonUser ? null : new IconButton("fa-bars", null, {
                onClick: S.nav.showMainMenu,
                id: "mainMenu"
            }, "btn-primary menuButton", "off");

            const navButton = new IconButton("fa-sitemap", null, {
                onClick: () => new NavPanelDlg().open(),
                id: "navMenu"
            }, "btn-primary menuButton", "off");

            const feedButton = new IconButton("fa-globe", null, {
                onClick: S.nav.messagesFediverse,
                id: "feedMenu"
            }, "btn-primary menuButton", "off");

            const loginButton = ast.isAnonUser ? new Span("Login", {
                className: "marginLeft clickable",
                id: "loginButton",
                onClick: S.user.userLogin
            }) : null;

            const signupButton = ast.isAnonUser ? new Span("Signup", {
                className: "marginLeft clickable",
                id: "loginButton",
                onClick: S.user.userSignup
            }) : null;

            const floatRightDiv = new Divc({ className: "float-end" }, [
                loginButton, signupButton,
                !ast.isAnonUser ? new Span(ast.userName, {
                    className: "clickable",
                    onClick: S.nav.navToMyAccntRoot
                }) : null
            ]);

            const logo = new Img({
                className: "marginRight smallLogoButton",
                src: "/branding/logo-50px-tr.jpg",
                onClick: () => S.nav.navPublicHome(),
                title: "Main application Landing Page"
            });

            return new Divc({ className: "mobileHeaderBar" }, [logo, menuButton, navButton, feedButton, floatRightDiv]);
        }
        return null;
    }

    override domPreUpdateEvent = (): void => {
        const elm = this.getRef(false);
        if (!elm) return;

        const ast = getAs();

        if (ast.highlightText) {
            S.domUtil.highlightText(elm /* document.body */, ast.highlightText);
        }
    }
}
