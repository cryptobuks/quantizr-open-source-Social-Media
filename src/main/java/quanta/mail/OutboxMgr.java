
package quanta.mail;

import java.util.List;
import org.springframework.stereotype.Component;
import quanta.config.NodeName;
import quanta.config.NodePath;
import quanta.config.ServiceBase;
import quanta.config.SessionContext;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.mongo.CreateNodeLocation;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.response.NotificationMessage;
import quanta.util.ThreadLocals;
import quanta.util.XString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the node where we store all emails that are queued up to be sent.
 * <p>
 * The system always sends emails out in a batch operation every 30seconds or so, by emptying out
 * this queue.
 */
@Component
public class OutboxMgr extends ServiceBase {
	
	private static Logger log = LoggerFactory.getLogger(OutboxMgr.class);
	private String mailBatchSize = "10";
	private static SubNode outboxNode = null;
	private static final Object outboxLock = new Object();

	/**
	 * Adds a node into the user's "Inbox" as an indicator to them that the 'node' added needs their
	 * attention, for some reason or that someone has shared this node with them.
	 */
	public void addInboxNotification(String recieverUserName, SubNode userNode, SubNode node, String notifyMessage) {
		// if you re-enable this code be sure to add a new partialIndex on "pro."+NodeProp.TARGET_ID.s(), so the
		// findNodeByProp will be fast.
		if (true) throw new RuntimeException("currently not used.");
		arun.run(as -> {
			SubNode userInbox = read.getUserNodeByType(as, null, userNode, "### Inbox", NodeType.INBOX.s(), null, NodeName.INBOX);
			if (userInbox != null) {
				// log.debug("userInbox id=" + userInbox.getIdStr());
				/*
				 * First look to see if there is a target node already existing in this persons inbox that points to
				 * the node in question
				 */
				SubNode notifyNode = read.findNodeByProp(as, userInbox, NodeProp.TARGET_ID.s(), node.getIdStr());
				/*
				 * If there's no notification for this node already in the user's inbox then add one
				 */
				if (notifyNode == null) {
					notifyNode = create.createNode(as, userInbox, null, NodeType.INBOX_ENTRY.s(), 0L, CreateNodeLocation.FIRST, null, null, true, true);
					// trim to 280 like twitter.
					String shortContent = XString.trimToMaxLen(node.getContent(), 280) + "...";
					String content = String.format("#### New from: %s\n%s", ThreadLocals.getSC().getUserName(), shortContent);
					notifyNode.setOwner(userInbox.getOwner());
					notifyNode.setContent(content);
					notifyNode.touch();
					notifyNode.set(NodeProp.TARGET_ID, node.getIdStr());
					update.save(as, notifyNode);
				}
				/*
				 * Send push notification so the user sees live there's a new share comming in or being re-added
				 * even.
				 */
				List<SessionContext> scList = SessionContext.getSessionsByUserName(recieverUserName);
				if (scList != null) {
					for (SessionContext sc : scList) {
						push.sendServerPushInfo(sc, 
						// todo-2: fill in the two null parameters here if/when you ever bring this method back.
						new NotificationMessage("newInboxNode", node.getIdStr(), "New node shared to you.", ThreadLocals.getSC().getUserName()));
					}
				}
			}
			return null;
		});
	}

	/**
	 * Sends an email notification to the user associated with 'toUserNode' (a person's account root
	 * node), telling them that 'fromUserName' has shared a node with them, and including a link to the
	 * shared node in the email.
	 */
	public void sendEmailNotification(MongoSession ms, String fromUserName, SubNode toUserNode, SubNode node) {
		String email = toUserNode.getStr(NodeProp.EMAIL);
		String toUserName = toUserNode.getStr(NodeProp.USER);
		// log.debug("sending node notification email to: " + email);
		String nodeUrl = snUtil.getFriendlyNodeUrl(ms, node);
		String content = String.format(prop.getConfigText("brandingAppName") + " user \'%s\' shared a node to your \'%s\' account.<p>\n\n" +  //
		"%s", fromUserName, toUserName, nodeUrl);
		queueMail(ms, email, "A " + prop.getConfigText("brandingAppName") + " Node was shared to you!", content);
	}

	public void queueEmail(String recipients, String subject, String content) {
		arun.run(as -> {
			queueMail(as, recipients, subject, content);
			return null;
		});
	}

	private void queueMail(MongoSession ms, String recipients, String subject, String content) {
		SubNode outboxNode = getSystemOutbox(ms);
		SubNode outboundEmailNode = create.createNode(ms, outboxNode.getPath() + "/?", NodeType.NONE.s());
		outboundEmailNode.setOwner(ms.getUserNodeId());
		outboundEmailNode.set(NodeProp.EMAIL_CONTENT, content);
		outboundEmailNode.set(NodeProp.EMAIL_SUBJECT, subject);
		outboundEmailNode.set(NodeProp.EMAIL_RECIP, recipients);
		update.save(ms, outboundEmailNode);
		notify.setOutboxDirty();
	}

	/*
	 * Loads only up to mailBatchSize emails at a time
	 */
	public Iterable<SubNode> getMailNodes(MongoSession ms) {
		SubNode outboxNode = getSystemOutbox(ms);
		// log.debug("outbox id: " + outboxNode.getIdStr());
		int mailBatchSizeInt = Integer.parseInt(mailBatchSize);
		return read.getChildren(ms, outboxNode, null, mailBatchSizeInt, 0);
	}

	public SubNode getSystemOutbox(MongoSession ms) {
		if (OutboxMgr.outboxNode != null) {
			return OutboxMgr.outboxNode;
		}
		synchronized (outboxLock) {
			// yep it's correct threading to check the node value again once inside the lock
			if (OutboxMgr.outboxNode != null) {
				return OutboxMgr.outboxNode;
			}
			snUtil.ensureNodeExists(ms, NodePath.ROOT_PATH, NodePath.OUTBOX, null, "Outbox", null, true, null, null);
			OutboxMgr.outboxNode = snUtil.ensureNodeExists(ms, NodePath.ROOT_PATH, NodePath.OUTBOX + "/" + NodePath.SYSTEM, null, "System Messages", null, true, null, null);
			return OutboxMgr.outboxNode;
		}
	}
}
