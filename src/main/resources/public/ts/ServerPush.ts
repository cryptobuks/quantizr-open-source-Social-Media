import { dispatch, getAs } from "./AppContext";
import { Constants as C } from "./Constants";
import { MessageDlg } from "./dlg/MessageDlg";
import * as J from "./JavaIntf";
import { S } from "./Singletons";
import { FeedTab } from "./tabs/data/FeedTab";
import { TimelineTab } from "./tabs/data/TimelineTab";

// reference: https://www.baeldung.com/spring-server-sent-events
// See also: AppController.java#serverPush

export class ServerPush {
    eventSource: EventSource;

    close = (): any => {
        if (this.eventSource) {
            this.eventSource.close();
            this.eventSource = null;
        }
    }

    init = (): any => {
        // if already inititlized do nothing
        if (this.eventSource) return;

        console.log("ServerPush.init");
        this.eventSource = new EventSource(S.rpcUtil.getRpcPath() + "serverPush");

        // DO NOT DELETE.
        // eventSource.onmessage = e => {
        // };

        this.eventSource.onopen = (e: any) => {
            // onsole.log("ServerPush.onopen" + e);
        };

        this.eventSource.onerror = (e: any) => {
            // console.log("ServerPush.onerror:" + e);
        };

        this.eventSource.addEventListener("sessionTimeout", async (e: any) => {
            S.quanta.authToken = null;
            S.quanta.userName = null;
            if (S.quanta.loggingOut) return;
            let message = "";
            const editorData = await S.localDB.getVal(C.STORE_EDITOR_DATA);
            if (editorData?.nodeId && editorData?.content) {
                message = "<p><p>Click to resume editing.";
            }

            new MessageDlg("Your session has ended due to inactivity." + message, S.quanta.configRes.brandingAppName,
                () => {
                    history.go(0);
                }, null, false, 0, "appModalContTinyWidth"
            ).open();
        });

        this.eventSource.addEventListener("nodeEdited", (e: any) => {
            const obj: J.FeedPushInfo = JSON.parse(e.data);
            const nodeInfo = obj.nodeInfo;

            if (nodeInfo) {
                dispatch("RenderTimelineResults", s => {
                    const data = TimelineTab.inst;
                    if (!data) return;

                    if (data.props.results) {
                        // remove this nodeInfo if it's already in the results.
                        data.props.results = data.props.results.filter((ni: J.NodeInfo) => ni.id !== nodeInfo.id);

                        // now add to the top of the list.
                        data.props.results.unshift(nodeInfo);
                    }
                });
            }
        });

        this.eventSource.addEventListener("feedPush", (e: any) => {
            const data: J.FeedPushInfo = JSON.parse(e.data);
            this.feedPushItem(data.nodeInfo);
        }, false);

        this.eventSource.addEventListener("ipsmPush", (e: any) => {
            const data: J.IPSMPushInfo = JSON.parse(e.data);
            this.ipsmPushItem(data.payload);
        }, false);

        // This is where we receive signing requests pushed from the server to be signed by the browser and pushed back up.
        this.eventSource.addEventListener("sigPush", async (e: any) => {
            const data: J.NodeSigPushInfo = JSON.parse(e.data);
            await S.crypto.generateAndSendSigs(data);
        }, false);

        this.eventSource.addEventListener("newNostrUsersPush", async (e: any) => {
            const data: J.NewNostrUsersPushInfo = JSON.parse(e.data);
            // console.log("got newNostrUsersPush: " + S.util.prettyPrint(data));
            await S.nostr.loadUserMetadata(data);
        }, false);

        this.eventSource.addEventListener("pushPageMessage", (e: any) => {
            const data: J.PushPageMessage = JSON.parse(e.data);
            if (data.usePopup) {
                S.util.showMessage(data.payload, "Admin Message");
            }
            else {
                S.util.showPageMessage(data.payload);
            }
        }, false);

        this.eventSource.addEventListener("newInboxNode", (e: any) => {
            // const obj: J.NotificationMessage = JSON.parse(e.data);
            // console.log("Incomming Push (NotificationMessage): " + S.util.prettyPrint(obj));
            // new InboxNotifyDlg("Your Inbox has updates!").open();
        }, false);
    }

    forceFeedItem = (nodeInfo: J.NodeInfo) => {
        if (!nodeInfo) return;
        FeedTab.inst.props.feedResults = FeedTab.inst.props.feedResults || [];

        const itemFoundIdx = FeedTab.inst.props.feedResults.findIndex(item => item.id === nodeInfo.id);
        const updatesExistingItem = itemFoundIdx !== -1;

        if (S.props.isEncrypted(nodeInfo)) {
            nodeInfo.content = "[Encrypted]";
        }

        // if updates existing item we refresh it even if autoRefresh is off
        if (updatesExistingItem) {
            FeedTab.inst.props.feedResults[itemFoundIdx] = nodeInfo;
        }
        else {
            FeedTab.inst.props.feedResults.unshift(nodeInfo);
            // scan for any nodes in feedResults where nodeInfo.parent.id is found in the list nodeInfo.id, and
            // then remove the nodeInfo.id from the list because it would be redundant in the list.
            // s.feedResults = S.quanta.removeRedundantFeedItems(s.feedResults);
        }
    }

    ipsmPushItem = (payload: string) => {
        // IPSM currently disabled
        // const feedData: TabIntf = S.tabUtil.getTabDataById(state, C.TAB_IPSM);
        // if (!feedData) return;

        // dispatch("RenderIPSMFeedResults", s => {
        //     feedData.props.events = feedData.props.events || [];

        //     // add to head of array (rev-chron view)
        //     feedData.props.events.unshift(payload);
        // });
    }

    feedPushItem = (nodeInfo: J.NodeInfo) => {
        if (!nodeInfo || !FeedTab.inst) return;
        const isMine = S.props.isMine(nodeInfo);

        if (S.props.isEncrypted(nodeInfo)) {
            nodeInfo.content = "[Encrypted]";
        }

        const ast = getAs();

        /* Ignore changes comming in during edit if we're editing on feed tab (inline)
         which will be fine because in this case when we are done editing we always
         process all the accumulated feedDirtyList items. */
        if (ast.activeTab === C.TAB_FEED && ast.editNode) {
            FeedTab.inst.props.feedDirtyList = FeedTab.inst.props.feedDirtyList || [];
            FeedTab.inst.props.feedDirtyList.push(nodeInfo);
            return;
        }

        dispatch("RenderFeedResults", s => {
            FeedTab.inst.props.feedResults = FeedTab.inst.props.feedResults || [];
            const itemFoundIdx = FeedTab.inst.props.feedResults.findIndex(item => item.id === nodeInfo.id);
            const updatesExistingItem = itemFoundIdx !== -1;

            // if updates existing item we refresh it even if autoRefresh is off
            if (updatesExistingItem) {
                S.render.fadeInId = nodeInfo.id;
                FeedTab.inst.props.feedResults[itemFoundIdx] = nodeInfo;
            }
            else if (s.userPrefs.autoRefreshFeed) {
                // NOTE: It would be also possible to call delayedRefreshFeed() here instead, but for now
                // I think we can just display any messages we get pushed in, and not try to query the server
                // again just for performance reasons.
                // S.srch.delayedRefreshFeed(s);

                // this is a slight hack to cause the new rows to animate their background, but it's ok, and I plan to leave it like this
                S.render.fadeInId = nodeInfo.id;
                FeedTab.inst.props.feedResults.unshift(nodeInfo);

                if (!isMine) {
                    S.util.showSystemNotification("New Message", "From " + nodeInfo.owner + ": " + nodeInfo.content);
                }

                // scan for any nodes in feedResults where nodeInfo.parent.id is found in the list nodeInfo.id, and
                // then remove the nodeInfo.id from the list because it would be redundant in the list.
                // s.feedResults = S.quanta.removeRedundantFeedItems(s.feedResults);
            }
            // or finally if autoRefresh is off we just set feedDirty, and it's up to the user to click refresh
            // button themselves.
            else {
                if (!isMine) {
                    S.util.showSystemNotification("New Message", "From " + nodeInfo.owner + ": " + nodeInfo.content);
                }

                /* note: we could que up the incomming nodeInfo, and then avoid a call to the server but for now we just
                keep it simple and only set a dirty flag */
                FeedTab.inst.props.feedDirty = true;
            }
        });
    }
}
