import { getAs } from "../AppContext";
import { AppTab } from "../comp/AppTab";
import { Checkbox } from "../comp/core/Checkbox";
import { Div } from "../comp/core/Div";
import { Divc } from "../comp/core/Divc";
import { Heading } from "../comp/core/Heading";
import { HorizontalLayout } from "../comp/core/HorizontalLayout";
import { Selection } from "../comp/core/Selection";
import { TabHeading } from "../comp/core/TabHeading";
import { ChangePasswordDlg } from "../dlg/ChangePasswordDlg";
import { EditNostrKeyAndRelaysDlg } from "../dlg/EditNostrKeyAndRelaysDlg";
import { ManageCryptoKeysDlg } from "../dlg/ManageCryptoKeysDlg";
import { ManageStorageDlg } from "../dlg/ManageStorageDlg";
import { MediaRecorderDlg } from "../dlg/MediaRecorderDlg";
import { UserProfileDlg } from "../dlg/UserProfileDlg";
import { TabIntf } from "../intf/TabIntf";
import { S } from "../Singletons";

export class SettingsView extends AppTab<any, SettingsView> {

    constructor(data: TabIntf<any, SettingsView>) {
        super(data);
        data.inst = this;
    }

    sectionTitle(title: string): Heading {
        return new Heading(5, title, { className: "settingsSectionTitle alert alert-primary" });
    }

    override preRender(): boolean {
        const ast = getAs();
        const horzClass = "horizontalLayoutComp accountSettingsSection";

        this.setChildren([
            this.headingBar = new TabHeading([
                new Div("Settings", { className: "tabTitle" })
            ]),

            new Divc({ className: "accountSettingsPanel" }, [
                // -----------------------
                this.sectionTitle("Account"),
                new HorizontalLayout([
                    new Divc({ className: "accountSettingsCol" }, [
                        this.settingsLink("Logout", S.user.logout), //
                        this.settingsLink("Edit Profile", () => new UserProfileDlg(null).open()),
                        this.settingsLink("Change Password", () => new ChangePasswordDlg(null).open()),
                        this.settingsLink("Server Storage Space", () => new ManageStorageDlg().open()),
                        this.settingsLink("Clear Browser Storage", async () => {
                            await S.localDB.clearStores();
                            S.util.showMessage("Browser Storage cleared successfully");
                        })
                    ]),
                    new Divc({ className: "accountSettingsCol" }, [
                        this.settingsLink("Manage Hashtags", S.edit.editHashtags),
                        this.settingsLink("Blocked Words", S.edit.editBlockedWords),
                        S.crypto.avail ? this.settingsLink("Manage Keys", () => new ManageCryptoKeysDlg().open()) : null
                    ])
                ], horzClass),

                // -----------------------
                this.sectionTitle("View Options"),
                new HorizontalLayout([
                    new Divc({ className: "accountSettingsCol" }, [
                        new Checkbox("Sensitive Content", { className: "bigMarginLeft" }, {
                            setValue: (checked: boolean) => S.util.saveUserPrefs(s => s.userPrefs.nsfw = checked),
                            getValue: (): boolean => ast.userPrefs.nsfw
                        }),

                        new Checkbox("Parent Node", { className: "bigMarginLeft" }, {
                            setValue: async (checked: boolean) => {
                                await S.util.saveUserPrefs(s => s.userPrefs.showParents = checked);
                            },
                            getValue: (): boolean => ast.userPrefs.showParents
                        }),

                        new Checkbox("Comments", { className: "bigMarginLeft" }, {
                            setValue: async (checked: boolean) => S.edit.setShowReplies(checked),
                            getValue: (): boolean => ast.userPrefs.showReplies
                        })
                    ]),

                    new Divc({ className: "accountSettingsCol" }, [
                        new Checkbox("Properties", { className: "bigMarginLeft" }, {
                            setValue: async (checked: boolean) => S.util.saveUserPrefs(s => s.userPrefs.showProps = checked),
                            getValue: (): boolean => ast.userPrefs.showProps
                        }),

                        /* The mouse effect shows a grapical animation for each mouse click but I decided I don't like the fact
                         that I have to impose an intentional performance lag to let the animation show up, so in order to have the
                         absolute fastest snappiest response of the app, I'm just not using this mouseEffect for now but let's leave
                         the code in place for future reference. */
                        new Checkbox("Mouse Effects", { className: "bigMarginLeft" }, {
                            setValue: async (checked: boolean) => S.domUtil.setMouseEffect(checked),
                            getValue: (): boolean => S.domUtil.mouseEffect
                        })
                    ])
                ], horzClass),

                new Selection(null, "Content Width", [
                    { key: "4", val: "Very Narrow" },
                    { key: "5", val: "Narrow" },
                    { key: "6", val: "Medium" },
                    { key: "7", val: "Wide" },
                    { key: "8", val: "Very Wide" }
                ], "contentWidthSelection", "bigMarginLeft marginTop", {
                    setValue: (val: string) => S.edit.setMainPanelCols(parseInt(val)),
                    getValue: (): string => "" + getAs().userPrefs.mainPanelCols
                }),

                this.settingsLink(ast.mobileMode ? "Switch to Desktop Browser" : "Switch to Moble Browser", S.util.switchBrowsingMode, "marginTop"),

                // menuItem("Full Repository Export", "fullRepositoryExport", "
                // S.edit.fullRepositoryExport();") + //

                // -----------------------
                this.sectionTitle("Nostr"),
                new HorizontalLayout([
                    new Divc({ className: "accountSettingsCol" }, [
                        this.settingsLink("Show Private Key", S.nostr.showPrivateKey),
                        this.settingsLink("Change Private Key", S.nostr.editPrivateKey),
                        this.settingsLink("Relays", () => new EditNostrKeyAndRelaysDlg().open())
                    ])
                ], horzClass),

                // -----------------------
                this.sectionTitle("Tools"),
                new HorizontalLayout([
                    new Divc({ className: "accountSettingsCol" }, [
                        this.settingsLink("Test Microphone", () => new MediaRecorderDlg(false, false).open()), //
                        this.settingsLink("Test Web Cam", () => new MediaRecorderDlg(true, false).open())
                    ]),
                    new Divc({ className: "accountSettingsCol" }, [
                        this.settingsLink("My GEO Location", S.nav.geoLocation), //
                        this.settingsLink("About Browser", S.util.showBrowserInfo)
                    ])
                ], horzClass),

                // -----------------------
                this.sectionTitle("Danger Zone"),
                new HorizontalLayout([
                    new Divc({ className: "accountSettingsCol" }, [
                        this.settingsLink("Bulk Delete", S.edit.bulkDelete)
                    ]),
                    new Divc({ className: "accountSettingsCol" }, [
                        this.settingsLink("Close Account", S.user.closeAccount)
                    ])
                ], horzClass)
            ])
        ]);
        return true;
    }

    settingsLink = (name: string, onClick: Function, moreClasses: string = ""): Div => {
        return new Div(name, {
            className: "settingsLink " + moreClasses,
            onClick
        });
    }
}
