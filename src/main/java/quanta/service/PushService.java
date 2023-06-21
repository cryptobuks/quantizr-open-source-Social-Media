package quanta.service;

import java.util.HashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;
import quanta.config.ServiceBase;
import quanta.config.SessionContext;
import quanta.model.NodeInfo;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.response.FeedPushInfo;
import quanta.response.ServerPushInfo;
import quanta.util.Convert;
import quanta.util.ThreadLocals;

@Component
public class PushService extends ServiceBase {

    private static Logger log = LoggerFactory.getLogger(PushService.class);
    static final int MAX_FEED_ITEMS = 25;

    /* Notify all users being shared to on this node, or everyone if the node is public. */
    public void pushNodeUpdateToBrowsers(MongoSession ms, HashSet<String> sessionsPushed, SubNode node) {
        // if unpublished or not a comment don't push to browsers.
        if (node.getBool(NodeProp.UNPUBLISHED) || !node.getType().equals(NodeType.COMMENT.s())) return;

        exec.run(() -> {
            boolean isPublic = AclService.isPublic(ms, node);
            // put user names in a hash set for faster performance
            HashSet<String> usersSharedToSet = new HashSet<>();

            /* get list of userNames this node is shared to (one of them may be 'public') */
            if (!isPublic) {
                List<String> usersSharedTo = auth.getUsersSharedTo(ms, node);
                // if node has no sharing we're done here
                if (usersSharedTo == null) {
                    return;
                }
                usersSharedToSet.addAll(usersSharedTo);
            }

            // if not public or shared to anyone we're done.
            if (!isPublic && usersSharedToSet.size() == 0) return;

            maybePushToBrowser(ms, sessionsPushed, node, usersSharedToSet, isPublic, ThreadLocals.getSC());

            List<SessionContext> scList = user.redisQuery("*");
            if (scList.size() > 0) {
                for (SessionContext sc : scList) {
                    //skip our own session because we already considered it first, above.
                    if (sc.getUserToken().equals(ThreadLocals.getSC().getUserToken())) continue;

                    maybePushToBrowser(ms, sessionsPushed, node, usersSharedToSet, isPublic, sc);
                }
            }
        });
    }

    private void maybePushToBrowser(
        MongoSession ms,
        HashSet<String> sessionsPushed,
        SubNode node,
        HashSet<String> usersSharedToSet,
        boolean isPublic,
        SessionContext sc
    ) {
        // if we know we already just pushed to this session, we can skip it in here.
        if (sessionsPushed != null && sessionsPushed.contains(sc.getUserToken())) {
            return;
        }

        /* Anonymous sessions won't have userName and can be ignored */
        if (sc.getUserName() == null) return;

        /*
         * We send a push to all users who are monitoring this node or any ancestor of it. This will be the
         * users who have opened some ancestor node as their "Feed Node" (viewing feed of that specific
         * node. This means 'viewing that node as a chat room')
         *
         * Nodes whose path starts with "timeline path", are subnodes of (or descendants of) the timeline
         * node and therefore will be sent to their respecitve browsers
         */
        if (
            // We don't include isPublic here, because we want don't want every message that comes into the
            // server to show up right away on the feed. We expect user to 'refresh' for that.
            // isPublic || // node is public

            node.getOwner().toHexString().equals(sc.getRootId()) || // node belongs to me
            node.getPath().startsWith(sc.getWatchingPath()) || // I'm watching path it's on
            node.getPath().startsWith(sc.getTimelinePath()) || // my timeline includes it
            (usersSharedToSet != null && usersSharedToSet.contains(sc.getUserName())) // it's shared to me
        ) {
            pushToBrowser(ms, sc, sessionsPushed, node);
        }
    }

    public void pushToBrowser(MongoSession ms, SessionContext sc, HashSet<String> sessionsPushed, SubNode node) {
        if (sessionsPushed != null && sessionsPushed.contains(sc.getUserToken())) {
            return;
        }

        /* build our push message payload */
        NodeInfo info = convert.convertToNodeInfo(
            false,
            sc,
            ms,
            node,
            false, //
            Convert.LOGICAL_ORDINAL_IGNORE,
            false,
            false,
            true, //
            false,
            true,
            true,
            null,
            false
        );

        if (info != null) {
            FeedPushInfo pushInfo = new FeedPushInfo(info);
            // push notification message to browser
            push.sendServerPushInfo(sc, pushInfo);

            if (sessionsPushed != null) {
                sessionsPushed.add(sc.getUserToken());
            }
        }
    }

    public void sendServerPushInfo(SessionContext sc, ServerPushInfo info) {
        // If user is currently logged in we have a session here.
        if (sc == null) return;
        exec.run(() -> {
            SseEmitter pushEmitter = user.getPushEmitter(sc.getUserToken());
            if (pushEmitter == null) {
                log.debug("No PushEmitter for user: " + ThreadLocals.getSC().getUserName());
                return;
            }
            /*
             * Note: Each session has it's own pushEmitter, so this will not be a bottleck, and is desirable
             * even probably to be sure each session is only doing one emit at a time.
             */
            synchronized (pushEmitter) {
                try {
                    SseEventBuilder event = SseEmitter
                        .event()
                        .data(info)
                        .id(String.valueOf(info.hashCode()))
                        .name(info.getType());
                    pushEmitter.send(event);
                } catch (
                    /*
                     * DO NOT DELETE. This way of sending also works, and I was originally doing it this way and picking
                     * up in eventSource.onmessage = e => {} on the browser, but I decided to use the builder instead
                     * and let the 'name' in the builder route different objects to different event listeners on the
                     * client. Not really sure if either approach has major advantages over the other.
                     *
                     * pushEmitter.send(info, MediaType.APPLICATION_JSON);
                     */
                    Exception ex
                ) {
                    log.error("FAILED Pushing to Session User: " + sc.getUserName());
                    pushEmitter.completeWithError(ex);
                }
            }
        });
    }
}
