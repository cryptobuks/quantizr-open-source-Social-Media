import { getAs } from "../AppContext";
import { Checkbox } from "../comp/core/Checkbox";
import { Div } from "../comp/core/Div";
import { Diva } from "../comp/core/Diva";
import { Img } from "../comp/core/Img";
import { Constants as C } from "../Constants";
import { EditNodeDlg } from "../dlg/EditNodeDlg";
import { UserProfileDlg } from "../dlg/UserProfileDlg";
import * as J from "../JavaIntf";
import { PubSub } from "../PubSub";
import { S } from "../Singletons";
import { SettingsTab } from "../tabs/data/SettingsTab";
import { CompIntf } from "./base/CompIntf";
import { Divc } from "./core/Divc";
import { HorizontalLayout } from "./core/HorizontalLayout";
import { Icon } from "./core/Icon";
import { Span } from "./core/Span";
import { HistoryPanel } from "./HistoryPanel";
import { TabPanelButtons } from "./TabPanelButtons";

export class RightNavPanel extends Div {
    private static scrollPos: number = 0;
    static historyExpanded: boolean = false;
    public static inst: RightNavPanel = null;

    constructor() {
        const ast = getAs();
        super(null, {
            id: C.ID_RHS,
            className: ast.mobileMode ? "mobileRHSPanel" : null,
            // tabIndex is required or else scrolling by arrow keys breaks.
            tabIndex: "3"
        });
        RightNavPanel.inst = this;
    }

    override getScrollPos = (): number => {
        return RightNavPanel.scrollPos;
    }

    override setScrollPos = (pos: number): void => {
        RightNavPanel.scrollPos = pos;
    }

    override preRender(): boolean {
        const ast = getAs();

        if (!ast.mobileMode) {
            let panelCols = ast.userPrefs.mainPanelCols || 6;
            if (panelCols < 4) panelCols = 4;
            if (panelCols > 8) panelCols = 8;
            let rightCols = 4;

            if (panelCols >= 6) {
                rightCols--;
            }
            if (panelCols >= 8) {
                rightCols--;
            }
            this.attribs.className = "col-" + rightCols + " rightNavPanel customScrollbar";
        }

        const avatarImg = this.makeRHSAvatarDiv();
        let displayName = ast.displayName ? ast.displayName : (!ast.isAnonUser ? ast.userName : null);

        if (displayName && ast.node) {
            displayName = S.util.insertActPubTags(displayName, ast.node);

            // If user had nothing left after insertion after ":tags:" replacement in their display name, then display their userName
            displayName = displayName || ast.node.owner;
        }

        const allowEditMode = !ast.isAnonUser;
        const fullScreenViewer = S.util.fullscreenViewerActive();

        // const clipboardPasteButton = state.userPrefs.editMode ? new Icon({
        //     className: "fa fa-clipboard fa-lg marginRight clickable",
        //     onClick: () => {
        //         PubSub.pub(C.PUBSUB_closeNavPanel);
        //         // todo-3: would be nice if this detected an image and save as attachment.
        //         S.edit.saveClipboardToChildNode("~" + J.NodeType.NOTES);
        //     },
        //     title: "Save clipboard"
        // }) : null;

        const addNoteButton = !ast.isAnonUser && !ast.mobileMode ? new Icon({
            className: "fa fa-sticky-note stickyNote fa-lg marginRight clickable",
            onClick: async () => {
                PubSub.pub(C.PUBSUB_closeNavPanel);
                let content = null;
                if (S.util.ctrlKeyCheck()) {
                    content = await (navigator as any)?.clipboard?.readText();

                    if (!content) {
                        const blob = await S.util.readClipboardFile();
                        if (blob) {
                            EditNodeDlg.pendingUploadFile = blob;
                        }
                    }
                }
                S.edit.addNode(null, "~" + J.NodeType.NOTES, null, false, content, null, null, null, false);
            },
            title: "Create new Private Note\n(Hold down CTRL key to attach from clipboard)"
        }) : null;

        if (addNoteButton) {
            S.domUtil.setDropHandler(addNoteButton.attribs, (evt: DragEvent) => {
                for (const item of evt.dataTransfer.items) {
                    // console.log("DROP(c) kind=" + item.kind + " type=" + item.type);

                    if (item.kind === "file") {
                        EditNodeDlg.pendingUploadFile = item.getAsFile();
                        S.edit.addNode(null, "~" + J.NodeType.NOTES, null, false, null, null, null, null, false);
                        return;
                    }
                }
            });
        }

        const textToSpeech = !ast.isAnonUser && !ast.mobileMode ? new Icon({
            className: "fa fa-volume-up fa-lg marginRight clickable",

            // This mouseover stuff is compensating for the fact that when the onClick gets called
            // it's a problem that by then the text selection "might" have gotten lost. This can happen.
            onMouseOver: () => { S.quanta.selectedForTts = window.getSelection().toString(); },
            onMouseOut: () => { S.quanta.selectedForTts = null; },
            onClick: () => S.speech.speakSelOrClipboard(false),
            title: "Text-to-Speech: Selected text or clipboard"
        }) : null;

        if (textToSpeech) {
            S.domUtil.setDropHandler(textToSpeech.attribs, (evt: DragEvent) => {
                for (const item of evt.dataTransfer.items) {
                    // console.log("DROP(c) kind=" + item.kind + " type=" + item.type);
                    if (item.kind === "string") {
                        item.getAsString(async (s) => S.speech.speakText(s));
                        return;
                    }
                }
            });
        }

        const loginSignupDiv = ast.isAnonUser && !ast.mobileMode ? new Divc({ className: "float-end" }, [
            // Not showing login on this panel in mobileMode, because it's shown at top of page instead
            new Span("Login", {
                className: "signupLinkText",
                onClick: () => {
                    PubSub.pub(C.PUBSUB_closeNavPanel);
                    S.user.userLogin();
                }
            }),

            new Span("Signup", {
                className: "signupLinkText float-end",
                onClick: () => {
                    PubSub.pub(C.PUBSUB_closeNavPanel);
                    S.user.userSignup();
                }
            })
        ]) : null;

        this.setChildren([
            new Divc({ className: "float-left" }, [
                new HorizontalLayout([
                    avatarImg,
                    new Diva([
                        new Divc({ className: "marginBottom" }, [
                            !ast.isAnonUser ? new Span(displayName, {
                                className: "clickable marginRight",
                                onClick: () => {
                                    PubSub.pub(C.PUBSUB_closeNavPanel);
                                    new UserProfileDlg(null).open();
                                }
                            }) : null,

                            !ast.isAnonUser ? new Icon({
                                className: "fa fa-gear fa-lg marginRight clickable",
                                onClick: () => {
                                    PubSub.pub(C.PUBSUB_closeNavPanel);
                                    SettingsTab.tabSelected = true;
                                    S.tabUtil.selectTab(C.TAB_SETTINGS);
                                },
                                title: "Edit Account Settings"
                            }) : null,

                            textToSpeech || addNoteButton ? new Span(null, { className: "float-end" }, [
                                textToSpeech,
                                addNoteButton]) : null
                        ]),
                        loginSignupDiv,

                        (allowEditMode && !fullScreenViewer) ? new Checkbox("Edit", { title: "Create posts, edit, and delete content" }, {
                            setValue: (checked: boolean) => S.edit.setEditMode(checked),
                            getValue: (): boolean => ast.userPrefs.editMode
                        }, "form-switch formCheckInlineNoMargin") : null,

                        !fullScreenViewer ? new Checkbox("Info", { title: "Display of avatars, timestamps, etc." }, {
                            setValue: (checked: boolean) => S.edit.setShowMetaData(checked),
                            getValue: (): boolean => ast.userPrefs.showMetaData
                        }, "form-switch formCheckInlineNoMargin") : null
                    ])
                ], "horizontalLayoutCompCompact fullWidth"),
                // new Divc({ className: "marginBottom" }, [
                //     new ButtonBar([
                //         clipboardPasteButton,
                //         addNoteButton,
                //         displayName && !state.isAnonUser ? new IconButton("fa-database", null, {
                //             title: "Go to your Account Root Node",
                //             onClick: e => S.nav.navHome(state)
                //         }) : null
                //     ])
                // ]),
                !ast.isAnonUser || ast.mobileMode ? new TabPanelButtons(true, ast.mobileMode ? "rhsMenuMobile" : "rhsMenu") : null,

                ast.nodeHistory?.length > 0 && !ast.isAnonUser ? new HistoryPanel() : null
            ])
        ]);
        return true;
    }

    makeRHSAvatarDiv = (): CompIntf => {
        const ast = getAs();
        let src: string = null;
        if (!ast.userProfile) return null;

        // if ActivityPub icon exists, we know that's the one to use.
        if (ast.userProfile.apIconUrl) {
            src = ast.userProfile.apIconUrl;
        }
        else {
            src = S.render.getAvatarImgUrl(ast.userProfile.userNodeId, ast.userProfile.avatarVer);
        }

        if (src) {
            const attr: any = {
                className: "profileImageRHS",
                src
            };

            if (!ast.isAnonUser) {
                attr.onClick = () => {
                    PubSub.pub(C.PUBSUB_closeNavPanel);
                    new UserProfileDlg(null).open();
                };
                attr.title = "Click to edit your Profile Info";
            }

            // Note: we DO have the image width/height set on the node object (node.width, node.hight) but we don't need it for anything currently
            return new Img(attr);
        }
        else {
            return null;
        }
    }
}
