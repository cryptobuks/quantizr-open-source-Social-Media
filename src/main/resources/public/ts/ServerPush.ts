import { dispatch, store } from "./AppRedux";
import { AppState } from "./AppState";
import { Constants as C } from "./Constants";
import { InboxNotifyDlg } from "./dlg/InboxNotifyDlg";
import { ServerPushIntf } from "./intf/ServerPushIntf";
import * as J from "./JavaIntf";
import { PubSub } from "./PubSub";
import { Singletons } from "./Singletons";

let S: Singletons;
PubSub.sub(C.PUBSUB_SingletonsReady, (s: Singletons) => {
    S = s;
});

// reference: https://www.baeldung.com/spring-server-sent-events
// See also: AppController.java#serverPush
export class ServerPush implements ServerPushIntf {
    init = (): any => {
        // console.log("ServerPush.init");

        const eventSource = new EventSource(S.util.getRpcPath() + "serverPush");

        // DO NOT DELETE.
        // eventSource.onmessage = e => {
        // };

        eventSource.onopen = (e: any) => {
            // onsole.log("ServerPush.onopen" + e);
        };

        eventSource.onerror = (e: any) => {
            // console.log("ServerPush.onerror:" + e);
        };

        eventSource.addEventListener("nodeEdited", function (e: any) {
            const obj: J.FeedPushInfo = JSON.parse(e.data);
            const nodeInfo: J.NodeInfo = obj.nodeInfo;

            if (nodeInfo) {
                dispatch("Action_RenderTimelineResults", (s: AppState): AppState => {
                    let data = s.tabData.find(d => d.id === "timelineResultSetView");
                    if (!data) return;

                    if (data.rsInfo.results) {
                        // remove this nodeInfo if it's already in the results.
                        data.rsInfo.results = data.rsInfo.results.filter((ni: J.NodeInfo) => ni.id !== nodeInfo.id);

                        // now add to the top of the list.
                        data.rsInfo.results.unshift(nodeInfo);
                    }
                    return s;
                });
            }
        });

        eventSource.addEventListener("feedPush", function (e: any) {

            const obj: J.FeedPushInfo = JSON.parse(e.data);
            // console.log("Incomming Push (FeedPushInfo): " + S.util.prettyPrint(obj));

            const nodeInfo: J.NodeInfo = obj.nodeInfo;
            if (nodeInfo) {
                // todo-1: I think this dispatch is working, but another full FeedView refresh (from actual server query too) is somehow following after also
                // so need to check and see how to avoid that.
                dispatch("Action_RenderFeedResults", (s: AppState): AppState => {
                    s.feedResults = s.feedResults || [];

                    /* If we detect the incomming change that is our own creation, we display it, but if it's not our own we
                    don't display because for now we aren't doing live feed updates since that can be a nuisance for users to have the
                    page change while they're maybe reading it */
                    if (nodeInfo.owner === s.userName) {

                        // this is a slight hack to cause the new rows to animate their background, but it's ok, and I plan to leave it like this
                        S.render.fadeInId = nodeInfo.id;
                        s.feedResults.unshift(nodeInfo);

                        // scan for any nodes in feedResults where nodeInfo.parent.id is found in the list nodeInfo.id, and
                        // then remove the nodeInfo.id from the list becasue it would be redundant in the list.
                        // s.feedResults = S.meta64.removeRedundantFeedItems(s.feedResults);
                    }
                    else {
                        /* note: we could que up the incomming nodeInfo, and then avoid a call to the server but for now we just
                        keep it simple and only set a dirty flag */
                        s.feedDirty = true;
                        if (nodeInfo.content && nodeInfo.content.startsWith(J.Constant.ENC_TAG)) {
                            nodeInfo.content = "[Encrypted]";
                        }

                        /* if the reciept of this server push makes us have new knowledge that one of our nodes
                        that didn't have children before now has children then update the state to have 'hasChildren'
                        on this node so the 'open' button will appear */
                        S.meta64.showOpenButtonOnNode(nodeInfo, s);
                        S.meta64.showSystemNotification("New Message", "From " + nodeInfo.owner + ": " + nodeInfo.content);
                    }
                    return s;
                });
            }
        }, false);

        eventSource.addEventListener("newInboxNode", function (e: any) {
            const obj: J.NotificationMessage = JSON.parse(e.data);
            // alert("Your inbox has updates.");
            // console.log("Incomming Push (NotificationMessage): " + S.util.prettyPrint(obj));
            // new InboxNotifyDlg("Your Inbox has updates!", store.getState()).open();
        }, false);
    }
}
