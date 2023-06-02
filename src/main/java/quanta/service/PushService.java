
package quanta.service;

import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;
import quanta.config.ServiceBase;
import quanta.config.SessionContext;
import quanta.instrument.PerfMon;
import quanta.model.NodeInfo;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.response.FeedPushInfo;
import quanta.response.NodeEditedPushInfo;
import quanta.response.ServerPushInfo;
import quanta.util.Convert;

@Component
public class PushService extends ServiceBase {
	
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PushService.class);
	static final int MAX_FEED_ITEMS = 25;

	/* Notify all users being shared to on this node, or everyone if the node is public. */
	public void pushNodeUpdateToBrowsers(MongoSession ms, HashSet<Integer> sessionsPushed, SubNode node) {
		exec.run(() -> {
			// log.debug("Pushing to browsers: id=" + node.getIdStr());
			/* get list of userNames this node is shared to (one of them may be 'public') */
			List<String> usersSharedTo = auth.getUsersSharedTo(ms, node);
			// if node has no sharing we're done here
			if (usersSharedTo == null) {
				return;
			}
			// put user names in a hash set for faster performance
			HashSet<String> usersSharedToSet = new HashSet<>();
			usersSharedToSet.addAll(usersSharedTo);
			/* Scan all sessions and push message to the ones that need to see it */
			for (SessionContext sc : SessionContext.getAllSessions(true, false)) {
				// if we know we already just pushed to this session, we can skip it in here.
				if (sessionsPushed != null && sessionsPushed.contains(sc.hashCode())) {
					// log.debug("Skipping push: " + sc.hashCode() + " to " + sc.getUserName());
					continue;
				}
				/* Anonymous sessions won't have userName and can be ignored */
				if (sc.getUserName() == null) continue;
				/*
				 * push if the sc user is in the shared set or this session is OURs
				 * 
				 * Having "public" option here (pushing public nodes to everyone) floods in too many messages to be
				 * practical, becuase the ActivityPub deamon that reads messages pushes them. Handling that firehose
				 * of posts is a cool feature but will take some time to think thru the usability issues, so for now
				 * we only live-push messages to the browser that created them (the guy who did the save), and the
				 * people a node is specifically shared to.
				 */
				if (sc.getRootId().equals(node.getOwner().toHexString()) ||  // node owned by this 'sc' user
				// usersSharedToSet.contains("public") ||
				usersSharedToSet.contains(sc.getUserName())) {
					/* build our push message payload */
					NodeInfo info = convert.convertToNodeInfo(false, sc, ms, node, false,  //
					Convert.LOGICAL_ORDINAL_IGNORE, false, false, true,  //
					false, true, true, null, false);
					if (info != null) {
						FeedPushInfo pushInfo = new FeedPushInfo(info);
						// push notification message to browser
						// log.debug("Pushing to user: " + sc.getUserName());
						push.sendServerPushInfo(sc, pushInfo);
					}
				}
			}
		});
	}

	/*
	 * Send a push to all users who are monitoring this node or any ancestor of it. This will be the
	 * users who have opened some ancestor node as their "Feed Node" (viewing feed of that specific
	 * node. This means 'viewing that node as a chat room')
	 */
	public void pushNodeToBrowsers(MongoSession ms, HashSet<Integer> sessionsPushed, SubNode node) {
		// log.debug("Push to monitoring Browsers: node.content=" + node.getContent());
		/* Scan all sessions and push message to the ones that need to see it */
		for (SessionContext sc : SessionContext.getAllSessions(true, false)) {
			/* Anonymous sessions won't have userName and can be ignored */
			if (sc.getUserName() == null) continue;
			// log.debug("Pushing NODE to SessionContext: hashCode=" + sc.hashCode() + " user=" +
			// sc.getUserName() + " token="
			// + sc.getUserToken() + "\nJSON: " + XString.prettyPrint(node));
			// if this node starts with the 'watchingPath' of the user that means the node is a descendant of
			// the watching path
			if (node.getPath() != null && sc.getWatchingPath() != null && node.getPath().startsWith(sc.getWatchingPath())) {
				/* build our push message payload */
				NodeInfo info = convert.convertToNodeInfo(false, sc, ms, node, false, Convert.LOGICAL_ORDINAL_IGNORE, false, false, true, false, true, true, null, false);
				if (info != null) {
					FeedPushInfo pushInfo = new FeedPushInfo(info);
					// push notification message to browser
					sendServerPushInfo(sc, pushInfo);
					if (sessionsPushed != null) {
						sessionsPushed.add(sc.hashCode());
					}
				}
			}
		}
	}

	/* Notify all browser timelines if they have new info */
	public void pushTimelineUpdateToBrowsers(MongoSession ms, NodeInfo nodeInfo) {
		/* Scan all sessions and push message to the ones that need to see it */
		for (SessionContext sc : SessionContext.getAllSessions(true, false)) {
			/* Anonymous sessions can be ignored */
			if (sc.getUserName() == null) continue;
			/*
			 * Nodes whose path starts with "timeline path", are subnodes of (or descendants of) the timeline
			 * node and therefore will be sent to their respecitve browsers
			 */
			if (sc.getTimelinePath() == null || !nodeInfo.getPath().startsWith(sc.getTimelinePath())) {
				continue;
			}
			NodeEditedPushInfo pushInfo = new NodeEditedPushInfo(nodeInfo);
			sendServerPushInfo(sc, pushInfo);
		}
	}

	@PerfMon(category = "push")
	public void sendServerPushInfo(SessionContext sc, ServerPushInfo info) {
		// If user is currently logged in we have a session here.
		if (sc == null) return;
		exec.run(() -> {
			SseEmitter pushEmitter = sc.getPushEmitter();
			/*
			 * Note: Each session has it's own pushEmitter, so this will not be a bottleck, and is desirable
			 * even probably to be sure each session is only doing one emit at a time.
			 */
			synchronized (pushEmitter) {
				// log.debug("Pushing to User: " + sc.getUserName());
				try {
					SseEventBuilder event =  //
					//
					//
					SseEmitter.event().data(info).id(String.valueOf(info.hashCode())).name(info.getType());
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
				Exception ex) {
					log.error("FAILED Pushing to Session User: " + sc.getUserName());
					pushEmitter.completeWithError(ex);
				}
			}
		});
	}
}
