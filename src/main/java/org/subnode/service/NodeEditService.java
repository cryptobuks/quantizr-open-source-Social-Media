package org.subnode.service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.subnode.actpub.ActPubFollowing;
import org.subnode.actpub.ActPubService;
import org.subnode.actpub.ActPubUtil;
import org.subnode.actpub.model.APList;
import org.subnode.config.NodeName;
import org.subnode.exception.base.RuntimeEx;
import org.subnode.model.NodeInfo;
import org.subnode.model.PropertyInfo;
import org.subnode.model.client.NodeProp;
import org.subnode.model.client.NodeType;
import org.subnode.model.client.PrincipalName;
import org.subnode.model.client.PrivilegeType;
import org.subnode.mongo.AdminRun;
import org.subnode.mongo.CreateNodeLocation;
import org.subnode.mongo.MongoAuth;
import org.subnode.mongo.MongoCreate;
import org.subnode.mongo.MongoRead;
import org.subnode.mongo.MongoSession;
import org.subnode.mongo.MongoUpdate;
import org.subnode.mongo.MongoUtil;
import org.subnode.mongo.model.AccessControl;
import org.subnode.mongo.model.SubNode;
import org.subnode.request.AppDropRequest;
import org.subnode.request.CreateSubNodeRequest;
import org.subnode.request.DeletePropertyRequest;
import org.subnode.request.InsertNodeRequest;
import org.subnode.request.SaveNodeRequest;
import org.subnode.request.SearchAndReplaceRequest;
import org.subnode.request.SplitNodeRequest;
import org.subnode.request.TransferNodeRequest;
import org.subnode.request.UpdateHeadingsRequest;
import org.subnode.response.AppDropResponse;
import org.subnode.response.CreateSubNodeResponse;
import org.subnode.response.DeletePropertyResponse;
import org.subnode.response.InsertNodeResponse;
import org.subnode.response.SaveNodeResponse;
import org.subnode.response.SearchAndReplaceResponse;
import org.subnode.response.SplitNodeResponse;
import org.subnode.response.TransferNodeResponse;
import org.subnode.response.UpdateHeadingsResponse;
import org.subnode.types.TypeBase;
import org.subnode.types.TypePluginMgr;
import org.subnode.util.AsyncExec;
import org.subnode.util.Convert;
import org.subnode.util.SubNodeUtil;
import org.subnode.util.ThreadLocals;
import org.subnode.util.Util;
import org.subnode.util.ValContainer;
import org.subnode.util.XString;

/**
 * Service for editing content of nodes. That is, this method updates property values of nodes. As
 * the user is using the application and moving, copy+paste, or editing node content this is the
 * service that performs those operations on the server, directly called from the HTML 'controller'
 */
@Component
public class NodeEditService {
	private static final Logger log = LoggerFactory.getLogger(NodeEditService.class);

	@Autowired
	private Convert convert;

	@Autowired
	private MongoUtil util;

	@Autowired
	private MongoCreate create;

	@Autowired
	private MongoRead read;

	@Autowired
	private MongoUpdate update;

	@Autowired
	private AdminRun arun;

	@Autowired
	private ActPubService apService;

	@Autowired
	private ActPubFollowing apFollowing;

	@Autowired
	private ActPubUtil apUtil;

	@Autowired
	private AclService aclService;

	@Autowired
	private MongoAuth auth;

	@Autowired
	private IPFSService ipfsService;

	@Autowired
	private AsyncExec asyncExec;

	@Autowired
	private TypePluginMgr typePluginMgr;

	@Autowired
	private PushService pushService;

	@Autowired
	private SubNodeUtil snUtil;

	/*
	 * Creates a new node as a *child* node of the node specified in the request. Should ONLY be called
	 * by the controller that accepts a node being created by the GUI/user
	 */
	public CreateSubNodeResponse createSubNode(MongoSession session, CreateSubNodeRequest req) {
		// log.debug("createSubNode");
		CreateSubNodeResponse res = new CreateSubNodeResponse();
		session = ThreadLocals.ensure(session);

		boolean linkBookmark = "linkBookmark".equals(req.getPayloadType());
		String nodeId = req.getNodeId();
		boolean makePublic = false;
		SubNode node = null;

		/*
		 * If this is a "New Post" from the Feed tab we get here with no ID but we put this in user's
		 * "My Posts" node
		 */
		if (nodeId == null && !linkBookmark) {
			node = read.getUserNodeByType(session, null, null, "### " + ThreadLocals.getSC().getUserName() + "'s Public Posts",
					NodeType.POSTS.s(), Arrays.asList(PrivilegeType.READ.s()), NodeName.POSTS);

			if (node != null) {
				nodeId = node.getId().toHexString();
				makePublic = true;
			}
		}

		/* Node still null, then try other ways of getting it */
		if (node == null && !linkBookmark) {
			if (nodeId.equals("~" + NodeType.NOTES.s())) {
				node = read.getUserNodeByType(session, session.getUserName(), null, "### Notes", NodeType.NOTES.s(), null, null);
			} else {
				node = read.getNode(session, nodeId);
			}
		}

		TypeBase plugin = typePluginMgr.getPluginByType(req.getTypeName());
		if (plugin != null) {
			ValContainer<SubNode> vcNode = new ValContainer<>(node);
			plugin.createSubNode(session, vcNode, req, linkBookmark);
			node = vcNode.getVal();
		}

		if (node == null) {
			throw new RuntimeException("unable to locate parent for insert");
		}

		auth.authForChildNodeCreate(session, node);
		CreateNodeLocation createLoc = req.isCreateAtTop() ? CreateNodeLocation.FIRST : CreateNodeLocation.LAST;

		String parentHashTags = parseHashTags(node.getContent());
		if (parentHashTags.length() > 0) {
			parentHashTags = "\n\n" + parentHashTags + "\n";
		}

		SubNode newNode =
				create.createNode(session, node, null, req.getTypeName(), 0L, createLoc, req.getProperties(), null, true);

		if (req.isPendingEdit()) {
			util.setPendingPath(newNode, true);
		}

		newNode.setContent(parentHashTags + (req.getContent() != null ? req.getContent() : ""));
		newNode.touch();

		if (NodeType.BOOKMARK.s().equals(req.getTypeName())) {
			newNode.setProp(NodeProp.TARGET_ID.s(), req.getNodeId());
		}

		if (req.isTypeLock()) {
			newNode.setProp(NodeProp.TYPE_LOCK.s(), Boolean.valueOf(true));
		}

		// if a user to share to (a Direct Message) is provided, add it.
		if (req.getShareToUserId() != null) {
			HashMap<String, AccessControl> ac = new HashMap<>();
			ac.put(req.getShareToUserId(), new AccessControl(null, PrivilegeType.READ.s() + "," + PrivilegeType.WRITE.s()));
			newNode.setAc(ac);
		}
		// else maybe public.
		else if (makePublic) {
			aclService.addPrivilege(session, newNode, PrincipalName.PUBLIC.s(),
					Arrays.asList(PrivilegeType.READ.s(), PrivilegeType.WRITE.s()), null);
		}
		// else add default sharing
		else {
			// we always determine the access controls from the parent for any new nodes
			auth.setDefaultReplyAcl(null, node, newNode);

			String cipherKey = node.getStrProp(NodeProp.ENC_KEY.s());
			if (cipherKey != null) {
				res.setEncrypt(true);
			}
		}

		update.save(session, newNode);
		res.setNewNode(
				convert.convertToNodeInfo(ThreadLocals.getSC(), session, newNode, true, false, -1, false, false, false, false));

		log.debug("createSubNode complete");
		res.setSuccess(true);
		return res;
	}

	/*
	 * Takes a message that may have some hashtags in it and returns a string with all those hashtags in
	 * it space delimited
	 */
	public String parseHashTags(String message) {
		if (message == null)
			return "";
		StringBuilder tags = new StringBuilder();

		// prepare so that newlines are compatable with out tokenizing
		message = message.replace("\n", " ");
		message = message.replace("\r", " ");

		/*
		 * Mastodon jams a bunch of html together like this for example: #<span>bot</span> So we replace
		 * that html with spaces to make the tokenizer work. However I think it also stores tags in
		 * structured JSON?
		 */
		message = message.replace("#<span>", "#");
		message = message.replace("<span>", " ");
		message = message.replace("</span>", " ");
		message = message.replace("<", " ");
		message = message.replace(">", " ");

		List<String> words = XString.tokenize(message, " ", true);
		if (words != null) {
			for (String word : words) {
				// be sure there aren't multiple pound signs other than just the first
				// character.
				if (word.length() > 2 && word.startsWith("#") && StringUtils.countMatches(word, "#") == 1) {
					if (tags.length() > 0) {
						tags.append(" ");
					}
					tags.append(word);
				}
			}
		}
		return tags.toString();
	}

	public SubNode createFriendNode(MongoSession session, SubNode parentFriendsList, String userToFollow) {

		SubNode userNode = read.getUserNodeByUserName(session, userToFollow, false);
		if (userNode != null) {
			List<PropertyInfo> properties = new LinkedList<>();
			properties.add(new PropertyInfo(NodeProp.USER.s(), userToFollow));
			properties.add(new PropertyInfo(NodeProp.USER_NODE_ID.s(), userNode.getId().toHexString()));

			SubNode newNode = create.createNode(session, parentFriendsList, null, NodeType.FRIEND.s(), 0L,
					CreateNodeLocation.LAST, properties, parentFriendsList.getOwner(), true);
			newNode.setProp(NodeProp.TYPE_LOCK.s(), Boolean.valueOf(true));

			String userToFollowActorId = userNode.getStrProp(NodeProp.ACT_PUB_ACTOR_ID.s());
			if (userToFollowActorId != null) {
				newNode.setProp(NodeProp.ACT_PUB_ACTOR_ID.s(), userToFollowActorId);
			}

			String userToFollowActorUrl = userNode.getStrProp(NodeProp.ACT_PUB_ACTOR_URL.s());
			if (userToFollowActorUrl != null) {
				newNode.setProp(NodeProp.ACT_PUB_ACTOR_URL.s(), userToFollowActorUrl);
			}

			apUtil.log("Saved Friend Node (as a Follow): " + XString.prettyPrint(newNode));
			update.save(session, newNode);

			return newNode;
		} else {
			throw new RuntimeException("User not found: " + userToFollow);
		}
	}

	public AppDropResponse appDrop(MongoSession session, AppDropRequest req) {
		AppDropResponse res = new AppDropResponse();
		session = ThreadLocals.ensure(session);
		String data = req.getData();
		String lcData = data.toLowerCase();

		// for now we only support dropping of links onto our window. I threw in
		// 'file://' but i have no idea
		// if that's going to work or not (yet)
		if (!lcData.startsWith("http://") && !lcData.startsWith("https://") && !lcData.startsWith("file://")) {
			log.info("Drop even ignored: " + data);
			res.setMessage("Sorry, can't drop that there.");
			return res;
		}

		SubNode linksNode =
				read.getUserNodeByType(session, session.getUserName(), null, "### Notes", NodeType.NOTES.s(), null, null);

		if (linksNode == null) {
			log.warn("unable to get linksNode");
			return null;
		}

		SubNode newNode =
				create.createNode(session, linksNode, null, NodeType.NONE.s(), 0L, CreateNodeLocation.LAST, null, null, true);

		String title = lcData.startsWith("http") ? Util.extractTitleFromUrl(data) : null;
		String content = title != null ? "#### " + title + "\n" : "";
		content += data;
		newNode.setContent(content);
		newNode.touch();
		update.save(session, newNode);

		res.setMessage("Drop Accepted: Created link to: " + data);
		return res;
	}

	/*
	 * Creates a new node that is a sibling (same parent) of and at the same ordinal position as the
	 * node specified in the request. Should ONLY be called by the controller that accepts a node being
	 * created by the GUI/user
	 */
	public InsertNodeResponse insertNode(MongoSession session, InsertNodeRequest req) {
		InsertNodeResponse res = new InsertNodeResponse();
		session = ThreadLocals.ensure(session);
		String parentNodeId = req.getParentId();
		log.debug("Inserting under parent: " + parentNodeId);
		SubNode parentNode = read.getNode(session, parentNodeId);
		if (parentNode == null) {
			throw new RuntimeException("Unable to find parent note to insert under: " + parentNodeId);
		}

		auth.authForChildNodeCreate(session, parentNode);
		SubNode newNode = create.createNode(session, parentNode, null, req.getTypeName(), req.getTargetOrdinal(),
				CreateNodeLocation.ORDINAL, null, null, true);

		if (req.getInitialValue() != null) {
			newNode.setContent(req.getInitialValue());
		} else {
			newNode.setContent("");
		}
		newNode.touch();

		// '/r/p/' = pending (nodes not yet published, being edited created by users)
		if (req.isPendingEdit()) {
			util.setPendingPath(newNode, true);
		}

		// we always copy the access controls from the parent for any new nodes
		auth.setDefaultReplyAcl(null, parentNode, newNode);

		update.save(session, newNode);
		res.setNewNode(
				convert.convertToNodeInfo(ThreadLocals.getSC(), session, newNode, true, false, -1, false, false, false, false));

		// if (req.isUpdateModTime() && !StringUtils.isEmpty(newNode.getContent()) //
		// // don't evern send notifications when 'admin' is the one doing the editing.
		// && !PrincipalName.ADMIN.s().equals(sessionContext.getUserName())) {
		// outboxMgr.sendNotificationForNodeEdit(newNode, sessionContext.getUserName());
		// }

		res.setSuccess(true);
		return res;
	}

	public SaveNodeResponse saveNode(MongoSession _session, SaveNodeRequest req) {
		SaveNodeResponse res = new SaveNodeResponse();
		// log.debug("Controller saveNode: " + Thread.currentThread().getName());

		_session = ThreadLocals.ensure(_session);
		final MongoSession session = _session;

		NodeInfo nodeInfo = req.getNode();
		final String nodeId = nodeInfo.getId();

		// log.debug("saveNode. nodeId=" + XString.prettyPrint(nodeInfo));
		SubNode node = read.getNode(session, nodeId);
		auth.ownerAuth(session, node);

		if (node == null) {
			throw new RuntimeEx("Unable find node to save: nodeId=" + nodeId);
		}

		/* Remember the initial ipfs link */
		String initIpfsLink = node.getStrProp(NodeProp.IPFS_LINK);

		/*
		 * The only purpose of this limit is to stop hackers from using up lots of space, because our only
		 * current quota is on attachment file size uploads
		 */
		if (nodeInfo.getContent() != null && nodeInfo.getContent().length() > 64 * 1024) {
			throw new RuntimeEx("Max text length is 64K");
		}

		node.setContent(nodeInfo.getContent());
		node.touch();
		node.setType(nodeInfo.getType());

		/*
		 * if node name is empty or not valid (canot have ":" in the name) set it to null quietly
		 */
		if (StringUtils.isEmpty(nodeInfo.getName())) {
			node.setName(null);
		}
		// if we're setting node name to a different node name
		else if (nodeInfo.getName() != null && nodeInfo.getName().length() > 0 && !nodeInfo.getName().equals(node.getName())) {

			// todo-1: do better name validation here.
			if (nodeInfo.getName().contains(":")) {
				throw new RuntimeEx("Node names can only contain alpha numeric characters");
			}
			String nodeName = nodeInfo.getName().trim();

			// if not admin we have to lookup the node with "userName:nodeName" format
			if (!ThreadLocals.getSC().isAdmin()) {
				nodeName = ThreadLocals.getSC().getUserName() + ":" + nodeName;
			}

			/*
			 * We don't use unique index on node name, because it's not worth the performance overhead, so we
			 * have to do the uniqueness check ourselves here manually
			 */
			SubNode nodeByName = read.getNodeByName(session, nodeName);
			if (nodeByName != null) {
				throw new RuntimeEx("Node name is already in use. Duplicates not allowed.");
			}

			node.setName(nodeInfo.getName().trim());
		}

		if (nodeInfo.getProperties() != null) {
			for (PropertyInfo property : nodeInfo.getProperties()) {

				if ("[null]".equals(property.getValue())) {
					node.deleteProp(property.getName());
				} else {
					/*
					 * save only if server determines the property is savable. Just protection. Client shouldn't be
					 * trying to save stuff that is illegal to save, but we have to assume the worst behavior from
					 * client code, for security and robustness.
					 */
					if (session.isAdmin() || SubNodeUtil.isSavableProperty(property.getName())) {
						// log.debug("Property to save: " + property.getName() + "=" +
						// property.getValue());
						node.setProp(property.getName(), property.getValue());
					} else {
						/**
						 * TODO: This case indicates that data was sent unnecessarily. fix! (i.e. make sure this block
						 * cannot ever be entered)
						 */
						log.debug("Ignoring unneeded save attempt on unneeded prop: " + property.getName());
					}
				}
			}
		}

		// If removing encryption, remove it from all the ACL entries too.
		String encKey = node.getStrProp(NodeProp.ENC_KEY.s());
		if (encKey == null) {
			util.removeAllEncryptionKeys(node);
		}
		/* if node is currently encrypted */
		else {
			res.setAclEntries(auth.getAclEntries(session, node));
		}

		/*
		 * If we have an IPFS attachment and there's no IPFS_REF property that means it should be pinned.
		 * (REF means 'referenced' and external to our server).
		 */
		String ipfsLink = node.getStrProp(NodeProp.IPFS_LINK);
		if (ipfsLink != null) {

			// if there's no 'ref' property this is not a foreign reference, which means we
			// DO pin this.
			if (node.getStrProp(NodeProp.IPFS_REF.s()) == null) {
				/*
				 * Only if this is the first ipfs link ever added, or is a new link, then we need to pin and update
				 * user quota
				 */
				if (initIpfsLink == null || !initIpfsLink.equals(ipfsLink)) {
					arun.run(sess -> {
						// don't pass the actual node into here, because it runs in a separate thread and would be
						// a concurrency problem.
						ipfsService.ipfsAsyncPinNode(sess, node.getId());
						return null;
					});
				}
			}
			// otherwise we don't pin it.
			else {
				/*
				 * Don't do this removePin. Leave this comment here as a warning of what NOT to do! We can't simply
				 * remove the CID from our IPFS database because some node stopped using it, because there may be
				 * many other users/nodes potentially using it, so we let the releaseOrphanIPFSPins be our only way
				 * pins ever get removed, because that method does a safe and correct delete of all pins that are
				 * truly no longer in use by anyone
				 */
				// ipfs.removePin(ipfsLink);
			}
		}

		/*
		 * If the node being saved is currently in the pending area /p/ then we publish it now, and move it
		 * out of pending.
		 */
		util.setPendingPath(node, false);

		final String sessionUserName = ThreadLocals.getSC().getUserName();

		/* Send notification to local server or to remote server when a node is added */
		if (!StringUtils.isEmpty(node.getContent()) //
				// don't send notifications when 'admin' is the one doing the editing.
				&& !PrincipalName.ADMIN.s().equals(sessionUserName)) {

			arun.run(s -> {
				HashSet<Integer> sessionsPushed = new HashSet<>();

				// push any chat messages that need to go out.
				pushService.pushNodeToMonitoringBrowsers(s, sessionsPushed, node);

				SubNode parent = read.getNode(session, node.getParentPath(), false);
				if (parent != null) {
					auth.saveMentionsToNodeACL(s, node);

					if (node.getAc() != null) {

						// Get the inReplyTo from the parent property (foreign node) or if not found generate one based on
						// what the local server version of it is.
						String inReplyTo = parent.getStrProp(NodeProp.ACT_PUB_OBJ_URL);
						if (inReplyTo == null) {
							inReplyTo = snUtil.getIdBasedUrl(parent);
						}

						APList attachments = apService.createAttachmentsList(node);
						String nodeUrl = snUtil.getIdBasedUrl(node);

						apService.sendNotificationForNodeEdit(s, inReplyTo, snUtil.cloneAcl(node), attachments, node.getContent(),
								nodeUrl);
						pushService.pushNodeUpdateToBrowsers(s, sessionsPushed, node);
					}
					return null;
				}
				return null;
			});
		}

		NodeInfo newNodeInfo =
				convert.convertToNodeInfo(ThreadLocals.getSC(), session, node, true, false, -1, false, false, true, false);
		res.setNode(newNodeInfo);

		// todo-1: for now we only push nodes if public, up to browsers rather than doing a specific check
		// to send only to users who should see it.
		if (AclService.isPublic(session, node)) {
			pushService.pushTimelineUpdateToBrowsers(session, newNodeInfo);
		}

		res.setSuccess(true);
		return res;
	}

	/*
	 * Whenever a friend node is saved, we send the "following" request to the foreign ActivityPub
	 * server
	 */
	public void updateSavedFriendNode(SubNode node) {
		String userNodeId = node.getStrProp(NodeProp.USER_NODE_ID.s());

		final String friendUserName = node.getStrProp(NodeProp.USER.s());
		if (friendUserName != null) {
			// if a foreign user, update thru ActivityPub.
			if (friendUserName.contains("@") && !ThreadLocals.getSC().isAdmin()) {
				apUtil.log("calling setFollowing=true, to post follow to foreign server.");
				String followerUser = ThreadLocals.getSC().getUserName();
				apFollowing.setFollowing(followerUser, friendUserName, true);
			}

			/*
			 * when user first adds, this friendNode won't have the userNodeId yet, so add if not yet existing
			 */
			if (userNodeId == null) {
				/*
				 * A userName containing "@" is considered a foreign Fediverse user and will trigger a WebFinger
				 * search of them, and a load/update of their outbox
				 */
				if (friendUserName.contains("@")) {
					asyncExec.run(ThreadLocals.getContext(), () -> {
						arun.run(s -> {
							if (!ThreadLocals.getSC().isAdmin()) {
								apService.getAcctNodeByUserName(s, friendUserName);
							}

							/*
							 * The only time we pass true to load the user into the system is when they're being added as a
							 * friend.
							 */
							apService.userEncountered(friendUserName, true);
							return null;
						});
					});
				}

				ValContainer<SubNode> userNode = new ValContainer<SubNode>();
				arun.run(s -> {
					userNode.setVal(read.getUserNodeByUserName(s, friendUserName));
					return null;
				});

				if (userNode.getVal() != null) {
					userNodeId = userNode.getVal().getId().toHexString();
					node.setProp(NodeProp.USER_NODE_ID.s(), userNodeId);
				}
			}
		}
	}

	/*
	 * Removes the property specified in the request from the node specified in the request
	 */
	public DeletePropertyResponse deleteProperty(MongoSession session, DeletePropertyRequest req) {
		DeletePropertyResponse res = new DeletePropertyResponse();
		session = ThreadLocals.ensure(session);
		String nodeId = req.getNodeId();
		SubNode node = read.getNode(session, nodeId);
		auth.ownerAuthByThread(node);

		String propertyName = req.getPropName();
		node.deleteProp(propertyName);
		update.save(session, node);
		res.setSuccess(true);
		return res;
	}

	/*
	 * When user pastes in a large amount of text and wants to have this text broken out into individual
	 * nodes they can pass into here and double spaces become splitpoints, and this splitNode method
	 * will break it all up into individual nodes.
	 */
	public SplitNodeResponse splitNode(MongoSession session, SplitNodeRequest req) {
		SplitNodeResponse res = new SplitNodeResponse();
		session = ThreadLocals.ensure(session);
		String nodeId = req.getNodeId();

		// log.debug("Splitting node: " + nodeId);
		SubNode node = read.getNode(session, nodeId);
		SubNode parentNode = read.getParent(session, node);

		auth.ownerAuth(session, node);
		String content = node.getContent();
		boolean containsDelim = content.contains(req.getDelimiter());

		/*
		 * If split will have no effect, just return as if successful.
		 */
		if (!containsDelim) {
			res.setSuccess(true);
			return res;
		}

		String[] contentParts = StringUtils.splitByWholeSeparator(content, req.getDelimiter());
		SubNode parentForNewNodes = null;
		long firstOrdinal = 0;

		/*
		 * When inserting inline all nodes go in right where the original node is, in order below it as
		 * siblings
		 */
		if (req.getSplitType().equalsIgnoreCase("inline")) {
			parentForNewNodes = parentNode;
			firstOrdinal = node.getOrdinal();
		}
		/*
		 * but for a 'child' insert all new nodes are inserted as children of the original node, starting at
		 * the top (ordinal), regardless of whether this node already has any children or not.
		 */
		else {
			parentForNewNodes = node;
			firstOrdinal = 0L;
		}

		int numNewSlots = contentParts.length - 1;
		if (numNewSlots > 0) {
			create.insertOrdinal(session, parentForNewNodes, firstOrdinal, numNewSlots);
			update.save(session, parentForNewNodes);
		}

		int idx = 0;
		for (String part : contentParts) {
			// log.debug("ContentPart[" + idx + "] " + part);
			part = part.trim();
			if (idx == 0) {
				node.setContent(part);
				node.touch();
				update.save(session, node);
			} else {
				SubNode newNode = create.createNode(session, parentForNewNodes, null, firstOrdinal + idx,
						CreateNodeLocation.ORDINAL, false);
				newNode.setContent(part);
				newNode.touch();
				update.save(session, newNode);
			}
			idx++;
		}

		res.setSuccess(true);
		return res;
	}

	public TransferNodeResponse transferNode(MongoSession session, TransferNodeRequest req) {
		TransferNodeResponse res = new TransferNodeResponse();
		session = ThreadLocals.ensure(session);
		int transfers = 0;
		String nodeId = req.getNodeId();

		log.debug("Transfer node: " + nodeId);
		SubNode node = read.getNode(session, nodeId);
		auth.ownerAuth(session, node);

		SubNode toUserNode = read.getUserNodeByUserName(auth.getAdminSession(), req.getToUser());
		if (toUserNode == null) {
			throw new RuntimeEx("User not found: " + req.getToUser());
		}

		SubNode fromUserNode = null;
		if (!StringUtils.isEmpty(req.getFromUser())) {
			fromUserNode = read.getUserNodeByUserName(auth.getAdminSession(), req.getFromUser());
			if (fromUserNode == null) {
				throw new RuntimeEx("User not found: " + req.getFromUser());
			}
		}

		// if user doesn't specify a 'from' then we set ownership of ALL nodes.
		if (fromUserNode == null) {
			node.setOwner(toUserNode.getOwner());
			transfers++;
		} else {
			if (transferNode(session, node, fromUserNode.getOwner(), toUserNode.getOwner())) {
				transfers++;
			}
		}

		if (req.isRecursive()) {
			for (SubNode n : read.getSubGraph(session, node, null, 0)) {
				// log.debug("Node: path=" + path + " content=" + n.getContent());
				if (fromUserNode == null) {
					n.setOwner(toUserNode.getOwner());
					transfers++;
				} else {
					if (transferNode(session, n, fromUserNode.getOwner(), toUserNode.getOwner())) {
						transfers++;
					}
				}
			}
		}

		if (transfers > 0) {
			update.saveSession(auth.getAdminSession());
		}

		res.setMessage(String.valueOf(transfers) + " nodes were transferred.");
		res.setSuccess(true);
		return res;
	}

	/* Returns true if a transfer was done */
	public boolean transferNode(MongoSession session, SubNode node, ObjectId fromUserObjId, ObjectId toUserObjId) {
		/* is this a node we are transferring */
		if (fromUserObjId.equals(node.getOwner())) {
			node.setOwner(toUserObjId);
			return true;
		}
		return false;
	}

	/*
	 * This makes ALL the headings of all the sibling nodes match the heading level of the req.nodeId
	 * passed in.
	 */
	public UpdateHeadingsResponse updateHeadings(MongoSession session, UpdateHeadingsRequest req) {
		UpdateHeadingsResponse res = new UpdateHeadingsResponse();
		session = ThreadLocals.ensure(session);

		SubNode node = read.getNode(session, req.getNodeId(), true);
		String content = node.getContent();
		if (content != null) {
			content = content.trim();
			int baseLevel = XString.getHeadingLevel(content);

			SubNode parent = read.getParent(session, node);
			if (parent != null) {
				for (SubNode n : read.getChildren(session, parent)) {
					updateHeadingsForNode(session, n, baseLevel);
				}
				update.saveSession(session);
			}
		}
		return res;
	}

	private void updateHeadingsForNode(MongoSession session, SubNode node, int level) {
		if (node == null)
			return;

		String nodeContent = node.getContent();
		String content = nodeContent;
		if (content == null)
			return;

		// if this node starts with a heading (hash marks)
		if (content.startsWith("#") && content.indexOf(" ") < 7) {
			int spaceIdx = content.indexOf(" ");
			if (spaceIdx != -1) {

				// strip the pre-existing hashes off
				content = content.substring(spaceIdx + 1);

				/*
				 * These strings (pound sign headings) could be generated dynamically, but this switch with them
				 * hardcoded is more performant
				 */
				switch (level) {
					case 0: // this will be the root node (user selected node)
						break;
					case 1:
						if (!nodeContent.startsWith("# ")) {
							node.setContent("# " + content);
						}
						break;
					case 2:
						if (!nodeContent.startsWith("## ")) {
							node.setContent("## " + content);
						}
						break;
					case 3:
						if (!nodeContent.startsWith("### ")) {
							node.setContent("### " + content);
						}
						break;
					case 4:
						if (!nodeContent.startsWith("#### ")) {
							node.setContent("#### " + content);
						}
						break;
					case 5:
						if (!nodeContent.startsWith("##### ")) {
							node.setContent("##### " + content);
						}
						break;
					case 6:
						if (!nodeContent.startsWith("###### ")) {
							node.setContent("###### " + content);
						}
						break;
					default:
						break;
				}
			}
		}
	}

	public SearchAndReplaceResponse searchAndReplace(MongoSession session, SearchAndReplaceRequest req) {
		SearchAndReplaceResponse res = new SearchAndReplaceResponse();
		session = ThreadLocals.ensure(session);
		int replacements = 0;
		String nodeId = req.getNodeId();

		// log.debug("searchingAndReplace node: " + nodeId);
		SubNode node = read.getNode(session, nodeId);
		auth.ownerAuth(session, node);

		if (replaceText(session, node, req.getSearch(), req.getReplace())) {
			replacements++;
		}

		if (req.isRecursive()) {
			for (SubNode n : read.getSubGraph(session, node, null, 0)) {
				if (replaceText(session, n, req.getSearch(), req.getReplace())) {
					replacements++;
				}
			}
		}

		if (replacements > 0) {
			update.saveSession(session);
		}

		res.setMessage(String.valueOf(replacements) + " nodes were updated.");
		res.setSuccess(true);
		return res;
	}

	private boolean replaceText(MongoSession session, SubNode node, String search, String replace) {
		String content = node.getContent();
		if (content == null)
			return false;
		if (content.contains(search)) {
			node.setContent(content.replace(search, replace));
			node.touch();
			return true;
		}
		return false;
	}
}
