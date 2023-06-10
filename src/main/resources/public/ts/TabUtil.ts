import { dispatch, getAs, promiseDispatch } from "./AppContext";
import { AppState } from "./AppState";
import { AppTab } from "./comp/AppTab";
import { Constants as C } from "./Constants";
import { TabIntf } from "./intf/TabIntf";
import { PubSub } from "./PubSub";
import { S } from "./Singletons";
import { AdminTab } from "./tabs/data/AdminTab";
import { DocumentTab } from "./tabs/data/DocumentTab";
import { FeedTab } from "./tabs/data/FeedTab";
import { FollowersTab } from "./tabs/data/FollowersTab";
import { FollowingTab } from "./tabs/data/FollowingTab";
import { IPFSTab } from "./tabs/data/IPFSTab";
import { MainTab } from "./tabs/data/MainTab";
import { RepliesTab } from "./tabs/data/RepliesTab";
import { RSSTab } from "./tabs/data/RSSTab";
import { SearchTab } from "./tabs/data/SearchTab";
import { ServerInfoTab } from "./tabs/data/ServerInfoTab";
import { SettingsTab } from "./tabs/data/SettingsTab";
import { SharesTab } from "./tabs/data/SharesTab";
import { ThreadTab } from "./tabs/data/ThreadTab";
import { TimelineTab } from "./tabs/data/TimelineTab";
import { TrendingTab } from "./tabs/data/TrendingTab";
import { TTSTab } from "./tabs/data/TTSTab";

export class TabUtil {
    selectTab = (tabName: string) => {
        if (!tabName) return;
        /* if tab is already active no need to update state now

        SOME codepaths like (openNode) are currently relying on selectTab
        to cause the dispatch/update, even when tab isn't changing, so need
        to find all those before we can optimize here to ignore setting to same tab.
        */
        dispatch("SelectTab", s => {
            if (tabName === C.TAB_MAIN && !s.node) {
                S.nav.navToMyAccntRoot();
            }
            else {
                this.tabChanging(s.activeTab, tabName);
                s.activeTab = tabName;
            }
        });
    }

    makeDomIdForNode = (tabData: TabIntf<any>, id: string) => {
        // todo-0: for legacy reasons we have a different id formatting for main tree, and this inconsistent is bad and needs to be fixed.
        if (tabData.id == C.TAB_MAIN) {
            return S.nav._UID_ROWID_PREFIX + id;
        }
        return tabData.id + "_" + id;
    }

    /* Does a select tab that's safe within a dispatch (i.e. doesn't itself dispatch) */
    selectTabStateOnly = (tabName: string) => {
        const ast = getAs();
        if (tabName === C.TAB_MAIN && !ast.node) {

            // we need to run immediately but in a timer so it doesn't happen in this call stack and trigger
            // an error that we did a dispatch in a dispatch.
            setTimeout(() => {
                S.nav.navToMyAccntRoot();
            }, 1);
        }
        else {
            this.tabChanging(ast.activeTab, tabName);
            dispatch("SetActiveTab", s => s.activeTab = tabName);
        }
    }

    createAppTabs = async () => {
        await promiseDispatch("initTabs", s => {
            s.tabData = [
                new MainTab(),
                new DocumentTab(),
                new SearchTab(),
                new SharesTab(),
                new TimelineTab(),
                new FollowersTab(),
                new FollowingTab(),
                new FeedTab(),

                // DO NOT DELETE
                // The IPSMView will be repurposed as a server event log viewer
                // {
                //     name: "IPSM Console",
                //     id: C.TAB_IPSM,
                //     isVisible: () => {
                //         let ast = getAst();
                //         return ast.ipsmActive;
                //     },
                //     constructView: (data: TabIntf) => new IPSMView(s, data),
                //     rsInfo: null,
                //     scrollPos: 0,
                //     // need typesafe props (todo-2)
                //     props: {
                //         events: null // for now string[]
                //     }
                // },

                new RepliesTab(),
                new ThreadTab(),
                new TrendingTab(),
                new ServerInfoTab(),
                new TTSTab(),
                new RSSTab(),
                new IPFSTab(),
                new SettingsTab(),
                new AdminTab()

                // this is throwing a react error, but we don't need this now anyaay
                // {
                //     name: "Log",
                //     id: C.TAB_LOG,
                //     isVisible: () => {
                //         // this function needs to get the state itself.
                //         let ast = getAst();
                //         return ast.isAdminUser;
                //     },
                //     constructView: (data: TabIntf) => new LogView(data),
                //     rsInfo: null,
                //     props: {}
                // }
            ];
        });
    }

    getActiveTabComp = (): AppTab => {
        const ast = getAs();
        if (!ast.tabData) return null;
        const data = ast.tabData.find(d => d.id === ast.activeTab);
        return data ? data.inst : null;
    }

    // ast param is important because sometimes we do pass in an 'ast' that's not 'committed' yet
    // for this method to use, and in that case we can't call getAs() to get state
    getAppTabData = (tabId: string, ast: AppState = null): TabIntf => {
        ast = ast || getAs();
        if (!ast.tabData) return null;
        return ast.tabData.find(d => d.id === tabId);
    }

    tabScroll = (tabName: string, pos: number) => {
        if (C.DEBUG_SCROLLING) {
            console.log("Scrolling tab " + tabName + " to offset " + pos);
        }
        const data = getAs().tabData.find(d => d.id === tabName);
        if (data) {
            data.scrollPos = pos;
        }
    }

    // WARNING: This won't apply to (or work) for feed view which has different prop than 'results'
    resultSetHasData = (id: string) => {
        const ast = getAs();
        const data = ast.tabData.find(d => d.id === id);
        return data?.props?.results?.length > 0;
    }

    /* This function manages persisting the scroll position when switching
    from one tab to another, to automatically restore the scroll position that was
    last scroll position on any given tab */
    tabChanging = (prevTab: string, newTab: string) => {

        /* Don't run any code here if we aren't actually changing tabs */
        if (prevTab && newTab && prevTab === newTab) {
            return;
        }
        PubSub.pub(C.PUBSUB_tabChanging, newTab);
    }
}
