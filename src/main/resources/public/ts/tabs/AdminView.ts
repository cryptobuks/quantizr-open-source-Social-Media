import { promiseDispatch } from "../AppContext";
import { AppTab } from "../comp/AppTab";
import { Div } from "../comp/core/Div";
import { Divc } from "../comp/core/Divc";
import { Heading } from "../comp/core/Heading";
import { TabHeading } from "../comp/core/TabHeading";
import { SignupDlg } from "../dlg/SignupDlg";
import { TabIntf } from "../intf/TabIntf";
import { S } from "../Singletons";
import { TrendingView } from "./TrendingView";

export class AdminView extends AppTab<any, AdminView> {

    constructor(data: TabIntf<any, AdminView>) {
        super(data);
        data.inst = this;
    }

    static readJSONfromURL = () => {
        // This is an analytical tool, and doesn't need to be pretty so we just use the browser to ask for an input string.
        const url = window.prompt("ActivityPub Object URL: ");
        if (url) {
            S.view.runServerCommand("getActPubJson", url, "ActivityPub Object JSON", "");
        }
    }

    // DO NOT DELETE
    // Experimental IPSM Console will be repurposed as a live log window of server events for the Admin user.
    static setIpsmActive = async () => {

        await promiseDispatch("enableIpsm", s => {
            s.ipsmActive = true;
            setTimeout(() => {
                // S.tabUtil.selectTab(C.TAB_IPSM);
            }, 250);
        });

        S.util.saveUserPrefs(s => s.userPrefs.enableIPSM = true);
    };

    sectionTitle(title: string): Heading {
        return new Heading(4, title, { className: "settingsSectionTitle alert alert-primary" });
    }

    override preRender(): boolean {
        this.setChildren([
            this.headingBar = new TabHeading([
                new Div("Admin Console", { className: "tabTitle" })
            ]),

            new Divc({ className: "marginLeft" }, [
                this.sectionTitle("Analytics"),
                // /// new MenuItem("Backup DB", () => S.view.runServerCommand("BackupDb", "Backup DB Response", null, state)), //
                this.settingsLink("Server Info", () => S.view.runServerCommand("getServerInfo", null, "Info View", null)), //
                this.settingsLink("View Session Activity", () => S.view.runServerCommand("getSessionActivity", null, "Session Activity", null)), //
                this.settingsLink("Performance Report", () => window.open(S.util.getHostAndPort() + "/performance-report", "_blank")), //

                this.sectionTitle("Utils"),
                this.settingsLink("Create User", () => { new SignupDlg(true).open(); }), //
                this.settingsLink("Toggle Daemons", () => S.view.runServerCommand("toggleDaemons", null, "Toggle Daemons", null)), //
                this.settingsLink("Toggle AuditFilter", () => S.view.runServerCommand("toggleAuditFilter", null, "Toggle AuditFilter", null)), //
                this.settingsLink("Send Restart Warning", () => S.view.runServerCommand("sendAdminNote", null, "Admin Note", null)), //
                this.settingsLink("Refresh RSS Cache", () => S.view.runServerCommand("refreshRssCache", null, "Refresh RSS Cache", null)), //
                this.settingsLink("Refresh Trending Cache", () => {
                    S.view.runServerCommand("refreshTrendingCache", null, "Refresh Trending Cache", null);

                    // setting 'loaded' to false will trigger a call to the server to get the updated trending info.
                    TrendingView.loaded = false;
                }), //
                this.settingsLink("Insert Book: War and Peace", () => S.edit.insertBookWarAndPeace()),

                this.sectionTitle("Database"),
                this.settingsLink("Validate", () => S.view.runServerCommand("validateDb", null, "Validate DB Response", null)), //
                this.settingsLink("Repair", () => S.view.runServerCommand("repairDb", null, "Repair DB Response", null)), //
                this.settingsLink("Compact DB & Cleanup Pins", () => S.view.runServerCommand("compactDb", null, "Compact DB Response", null)), //
                this.settingsLink("Run DB Conversion", () => S.view.runServerCommand("runConversion", null, "Run DB Conversion", null)), //
                this.settingsLink("Rebuild Indexes", () => S.view.runServerCommand("rebuildIndexes", null, "Rebuild Indexes Response", null)), //
                this.settingsLink("Lucene: Refresh", () => S.view.runServerCommand("refreshLuceneIndex", null, null, null)),
                this.settingsLink("Delete Node (w/ Orphans)", () => S.view.runServerCommand("deleteLeavingOrphans", null, "Delete node leaving orphans", null)), //

                this.sectionTitle("ActivityPub"),
                this.settingsLink("Fediverse Users", () => window.open(S.util.getHostAndPort() + "/fediverse-users", "_blank")), //
                this.settingsLink("Get JSON from URL", AdminView.readJSONfromURL), //
                this.settingsLink("Refresh Fediverse", () => S.view.runServerCommand("refreshFediverseUsers", null, "Refresh Fediverse Users", null)), //
                this.settingsLink("Refresh AP Accts", () => S.view.runServerCommand("refreshAPAccounts", null, "Refresh AP Accounts", null)), //
                this.settingsLink("ActPub Maintenance", () => S.view.runServerCommand("actPubMaintenance", null, "ActPub Maintenance Response", null)), //
                this.settingsLink("Crawl Fediverse", () => S.view.runServerCommand("crawlUsers", null, "ActPub Crawl Response", null)),

                this.sectionTitle("Nostr"),
                this.settingsLink("Nostr Maintenance", () => S.view.runServerCommand("nostrMaintenance", null, "Nostr Maintenance Response", null)), //
                this.settingsLink("Nostr Update Feed", () => S.view.runServerCommand("nostrQueryUpdate", null, "Nostr Update Feed", null)),

                this.sectionTitle("Testing"),
                this.settingsLink("IPFS PubSub", () => S.view.runServerCommand("ipfsPubSubTest", null, "PubSub Test", null)), //
                this.settingsLink("Send Email", () => S.util.sendTestEmail()),
                this.settingsLink("Server Log Text", () => S.util.sendLogText()),
                this.settingsLink("Notification Display", () => S.util.showSystemNotification("Test Title", "This is a test message")),
                this.settingsLink("WebCrypto Encryption", async () => {
                    await S.crypto.encryptionTest();
                    S.util.showMessage("Crypto Test Complete. Check browser console for output.", "Note", true);
                }),
                this.settingsLink("WebCrypto Signatures", async () => {
                    await S.crypto.signatureTest();
                    S.util.showMessage("Crypto Test Complete. Check browser console for output.", "Note", true);
                }),
                this.settingsLink("Text to Speech", async () => {
                    const tts = window.speechSynthesis;
                    // /// let voices = tts.getVoices();
                    // /// for (let i = 0; i < voices.length; i++) {
                    // ///     let voice = voices[i];
                    // ///     // Google UK English Female (en-GB)
                    // ///     console.log("Voice: " + voice.name + " (" + voice.lang + ") " + (voice.default ? "<-- Default" : ""));
                    // /// }

                    /* WARNING: speechSynthesis seems to crash very often and leave hung processes, eating up CPU, at least
                    on my Ubuntu 18.04, machine, so for now any TTS development is on hold. */
                    const sayThis = new SpeechSynthesisUtterance("Wow. Browsers now support Text to Speech driven by JavaScript");
                    tts.speak(sayThis);
                })
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
