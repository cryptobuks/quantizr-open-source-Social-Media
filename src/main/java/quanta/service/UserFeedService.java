package quanta.service;

import static quanta.util.Util.no;
import static quanta.util.Util.ok;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Component;
import quanta.config.NodeName;
import quanta.config.NodePath;
import quanta.config.ServiceBase;
import quanta.config.SessionContext;
import quanta.model.NodeInfo;
import quanta.model.client.Constant;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.model.client.PrincipalName;
import quanta.model.client.PrivilegeType;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.CheckMessagesRequest;
import quanta.request.NodeFeedRequest;
import quanta.response.CheckMessagesResponse;
import quanta.response.NodeFeedResponse;
import quanta.util.ExUtil;
import quanta.util.ThreadLocals;
import quanta.util.Val;

@Component
public class UserFeedService extends ServiceBase {
	private static final Logger log = LoggerFactory.getLogger(UserFeedService.class);

	static final int MAX_FEED_ITEMS = 25;

	// DO NOT DELETE (part of example to keep below)
	// private static List<String> excludeTypes = Arrays.asList( //
	// NodeType.FRIEND.s(), //
	// NodeType.POSTS.s(), //
	// NodeType.ACT_PUB_POSTS.s());

	public CheckMessagesResponse checkMessages(MongoSession ms, CheckMessagesRequest req) {
		SessionContext sc = ThreadLocals.getSC();
		CheckMessagesResponse res = new CheckMessagesResponse();

		if (sc.isAnonUser())
			return res;

		String pathToSearch = NodePath.USERS_PATH;

		Query q = new Query();
		Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexRecursiveChildrenOfPath(pathToSearch)); //

		// limit to just markdown types (no type)
		// todo-1: Is there a faster way to accomplish the filtering we need based on type and WHY are we
		// filtering based on type?
		// i guess the reason was becasue we're searching ROOT_OF_ALL_USERS and need to avoid special
		// (system defined) user's nodes.
		// but we can probably do some kind of hack/hijack and make those special system nodes hijack the
		// priority value or something which
		// we can filter out by saying "not equal to special node priority"...becasue we have a priority
		// index already.
		// Will it hurt performance to have a "system=true" node prop to detect these? ...would definitely
		// be ONE MORE index.
		// IMPORTANT: this code is in OTHER PLACES in the app too! ...the in(a, b, ...) clause.
		// IMPORTANT: Feed queries in general will eventually have a "Comments" checkbox so that in a pure
		// corporate collab
		// use case users can view the core document content v.s. the core and comments.
		crit = crit.and(SubNode.TYPE).in(NodeType.NONE.s(), NodeType.COMMENT.s());

		// DO NOT DELETE (keep as example)
		// This pattern is what is required when you have multiple conditions added to a single field.
		// .andOperator(Criteria.where(SubNode.FIELD_TYPE).ne(NodeType.FRIEND.s()), //
		// Criteria.where(SubNode.FIELD_TYPE).ne(NodeType.POSTS.s()), //
		// Criteria.where(SubNode.FIELD_TYPE).ne(NodeType.ACT_PUB_POSTS.s()));

		SubNode searchRoot = read.getNode(ms, sc.getRootId());

		Long lastActiveLong = searchRoot.getInt(NodeProp.LAST_ACTIVE_TIME);
		if (lastActiveLong == 0) {
			return res;
		}

		/* new nodes since last active time */
		crit = crit.and(SubNode.MODIFY_TIME).gt(new Date(lastActiveLong));
		String myId = searchRoot.getOwner().toHexString();

		crit = crit.and(SubNode.AC + "." + myId).ne(null);

		q.addCriteria(crit);
		long count = ops.count(q, SubNode.class);
		res.setNumNew((int) count);
		return res;
	}

	/*
	 * Generated content of the "Feed" for a user.
	 * 
	 * Note: When 'req.toUser' is set we query actually for the bidiretional conversatio of us to that
	 * person or that person to us queried in a single list.
	 */
	public NodeFeedResponse generateFeed(MongoSession ms, NodeFeedRequest req) {
		/*
		 * if bidirectional means query for the conversation between me and the other person (both senders),
		 * and we do that always for now when toUser is present.
		 */
		boolean bidirectional = StringUtils.isNotEmpty(req.getToUser());

		/*
		 * Set this flag to generate large resultset of all nodes in root, just for exercising this method
		 * without 'real' data.
		 */
		boolean testQuery = false;
		SessionContext sc = ThreadLocals.getSC();
		NodeFeedResponse res = new NodeFeedResponse();

		String pathToSearch = testQuery ? NodePath.ROOT_PATH : NodePath.USERS_PATH;
		boolean doAuth = true;

		/*
		 * if we're doing a 'feed' under a specific root node this is like the 'chat feature' and is
		 * normally only called for a chat-room type node.
		 */
		if (ok(req.getNodeId())) {
			// Get the chat room node (root of the chat room query)
			SubNode rootNode = read.getNode(ms, req.getNodeId());
			if (no(rootNode)) {
				throw new RuntimeException("Node not found: " + req.getNodeId());
			}
			pathToSearch = rootNode.getPath();

			/* if the chat root is public disable all auth logic in this method */
			if (AclService.isPublic(ms, rootNode)) {
				// do nothing, for now.
			}
			/*
			 * If chat node is NOT public we try to check our read auth on it and if not this will throw an
			 * exception which is the correct flow here
			 */
			else {
				try {
					auth.auth(ms, rootNode, PrivilegeType.READ, PrivilegeType.WRITE);
				} catch (Exception e) {
					sc.setWatchingPath(null);
					throw e;
				}
			}
			/*
			 * Then we set the public chat to indicate to the rest of the code below not to do any further
			 * authorization, becasue the way ACL works on chart rooms is if a person is authorized to READ
			 * (what about WRITE? todo-2: probably should make the above read and write) the actual CHAT NODE
			 * itself (the root of the chat nodes) then they are known to be granted access to all children
			 */
			doAuth = false;
			sc.setWatchingPath(pathToSearch);
		} else {
			sc.setWatchingPath(null);
		}

		int counter = 0;
		List<Criteria> orCriteria = new LinkedList<>();

		/*
		 * 2: should the 'friends' and 'public' options be mutually exclusive?? If someone's looking for all
		 * public nodes why "OR" into that any friends?
		 */
		if (!testQuery && doAuth && req.getToPublic()) {
			orCriteria.add(Criteria.where(SubNode.AC + "." + PrincipalName.PUBLIC.s()).ne(null));
		}

		SubNode myAcntNode = null;

		// includes shares TO me (but not in the context of a 'bidirectional' query)
		if (!testQuery && doAuth && req.getToMe()) {
			myAcntNode = read.getNode(ms, sc.getRootId());

			if (ok(myAcntNode)) {
				orCriteria.add(Criteria.where(SubNode.AC + "." + myAcntNode.getOwner().toHexString()).ne(null));

				SubNode _myAcntNode = myAcntNode;
				MongoSession _s = ms;
				long lastActiveTime = sc.getLastActiveTime();
				// do this work in async thread to make this query more performant
				exec.run(() -> {
					/*
					 * setting last active time to this current time, will stop the GUI from showing the user an
					 * indication that they have new messages, because we know they're querying messages NOW, so this is
					 * a way to reset
					 * 
					 * todo-1: don't do this? It's better to just have a 'read' button like Pleroma does?
					 */
					_myAcntNode.set(NodeProp.LAST_ACTIVE_TIME, lastActiveTime);
					update.save(_s, _myAcntNode);
				});
			}
		}
		List<NodeInfo> searchResults = new LinkedList<>();
		res.setSearchResults(searchResults);

		Query q = new Query();

		// initialize criteria using the Path to select the correct sub-graph of the tree
		Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexRecursiveChildrenOfPath(pathToSearch)); //

		// DO NOT DELETE (keep as an example of how to do this)
		// if (no(req.getNodeId() )) {
		// criteria = criteria.and(SubNode.FIELD_TYPE).nin(excludeTypes);
		// }

		// limit to just markdown types (no type), and comments
		// IMPORTANT: see long comment above where we have similar type filtering.
		crit = crit.and(SubNode.TYPE).in(NodeType.NONE.s(), NodeType.COMMENT.s());

		boolean allowBadWords = true;

		// add the criteria for sensitive flag
		if (!req.getNsfw()) {
			crit = crit.and(SubNode.PROPS + "." + NodeProp.ACT_PUB_SENSITIVE).is(null);
			allowBadWords = false;
		}

		// Don't show UNPUBLISHED nodes. The whole point of having the UNPUBLISHED feature for nodes is so
		// we
		// can do this criteria right here and not show those in feeds.
		crit = crit.and(SubNode.PROPS + "." + NodeProp.UNPUBLISHED).is(null);

		/*
		 * Save the 'string' representations for blocked user ids for use below, to mask out places where
		 * users may be following a user that will effectively be blocked
		 */
		HashSet<String> blockedIdStrings = new HashSet<>();
		HashSet<ObjectId> blockedUserIds = new HashSet<>();
		boolean allowNonEnglish = true;

		if (!bidirectional) {
			/*
			 * this logic makes it so that any feeds using 'public' checkbox will have the admin-blocked users
			 * removed from it.
			 */
			if (req.getToPublic() && req.isApplyAdminBlocks()) {
				getBlockedUserIds(blockedUserIds, PrincipalName.ADMIN.s());

				allowNonEnglish = false;
				allowBadWords = false;
			}

			// Add criteria for blocking users using the 'not in' list (nin)
			getBlockedUserIds(blockedUserIds, null);
			if (blockedUserIds.size() > 0) {
				crit = crit.and(SubNode.OWNER).nin(blockedUserIds);
			}

			for (ObjectId blockedId : blockedUserIds) {
				blockedIdStrings.add(blockedId.toHexString());
			}
		}

		/*
		 * for bidirectional we do an OR of "us to them" and "them to us" kind of sharing to the other user,
		 * to result in what will end up being all conversations between us and the other person mixed into
		 * a single rev-chron.
		 */
		if (bidirectional) {
			SubNode toUserNode = read.getUserNodeByUserName(ms, req.getToUser(), false);

			if (no(myAcntNode)) {
				myAcntNode = read.getNode(ms, sc.getRootId());
			}

			if (ok(myAcntNode)) {
				// sharing from us to the other user.
				orCriteria.add(
						// where node is owned by us.
						Criteria.where(SubNode.OWNER).is(myAcntNode.getOwner()) //
								// and the node has any sharing on it.
								.and(SubNode.AC + "." + toUserNode.getId().toHexString()).ne(null));

				// sharing from the other user to us.
				if (bidirectional) {
					orCriteria.add(
							// where node is owned by us.
							Criteria.where(SubNode.OWNER).is(toUserNode.getOwner()) //
									// and the node has any sharing on it.
									.and(SubNode.AC + "." + myAcntNode.getId().toHexString()).ne(null));
				}
			}
		}

		if (!testQuery && doAuth && req.getFromMe()) {
			if (no(myAcntNode)) {
				myAcntNode = read.getNode(ms, sc.getRootId());
			}

			if (ok(myAcntNode)) {
				orCriteria.add(
						// where node is owned by us.
						Criteria.where(SubNode.OWNER).is(myAcntNode.getOwner()) //
								// and the node has any sharing on it.
								.and(SubNode.AC).ne(null));
			}
		}

		if (!testQuery && doAuth && req.getFromFriends()) {
			List<SubNode> friendNodes = user.getSpecialNodesList(ms, null, NodeType.FRIEND_LIST.s(), null, true);
			if (ok(friendNodes)) {
				List<ObjectId> friendIds = new LinkedList<>();

				for (SubNode friendNode : friendNodes) {
					// the USER_NODE_ID property on friends nodes contains the actual account ID of this friend.
					String userNodeId = friendNode.getStr(NodeProp.USER_NODE_ID);

					// if we have a userNodeId and they aren't in the blocked list.
					if (ok(userNodeId) && !blockedIdStrings.contains(userNodeId)) {
						friendIds.add(new ObjectId(userNodeId));
					}
				}

				if (friendIds.size() > 0) {
					orCriteria.add(Criteria.where(SubNode.OWNER).in(friendIds));
				}
			}
		}

		if (orCriteria.size() > 0) {
			crit = crit.orOperator((Criteria[]) orCriteria.toArray(new Criteria[orCriteria.size()]));
		}

		// use attributedTo proptery to determine whether a node is 'local' (posted by this server) or not.
		if (req.getLocalOnly()) {
			// todo-1: should be checking apid property instead?
			crit = crit.and(SubNode.PROPS + "." + NodeProp.ACT_PUB_OBJ_ATTRIBUTED_TO.s()).is(null);
		}

		// exclude all user's home nodes from appearing in the results. When a user signs up they'll get
		// something like
		// a node with text "Clay's Node" created and it will be empty, and we don't need them showing up in
		// the feeds.
		crit = crit.and(SubNode.NAME).ne(NodeName.HOME);

		if (!StringUtils.isEmpty(req.getSearchText())) {
			TextCriteria textCriteria = TextCriteria.forDefaultLanguage();
			String text = req.getSearchText();
			/*
			 * If searching for a tag name or a username, be smart enough to enclose it in quotes for user,
			 * because if we don't then searches for "#mytag" WILL end up finding also just instances of mytag
			 * (not a tag) which is incorrect.
			 */
			if ((text.startsWith("#") || text.startsWith("@")) && !text.contains(" ")) {
				text = "\"" + text + "\"";
			}

			textCriteria.matching(text);
			textCriteria.caseSensitive(false);
			q.addCriteria(textCriteria);
		}

		q.addCriteria(crit);

		// if we have a node id this is like a chat room type, and so we sort by create time.
		if (ok(req.getNodeId())) {
			q.with(Sort.by(Sort.Direction.DESC, SubNode.CREATE_TIME));
		} else {
			q.with(Sort.by(Sort.Direction.DESC, SubNode.MODIFY_TIME));
		}

		// we get up to 2x the max item so that if large numbers of them are being filtered,
		// we can still return a page of results hopefully
		q.limit(MAX_FEED_ITEMS * 2);

		if (req.getPage() > 0) {
			q.skip(MAX_FEED_ITEMS * req.getPage());
		}

		Iterable<SubNode> iter = mongoUtil.find(q);

		int skipped = 0;
		for (SubNode node : iter) {
			/*
			 * todo-1: We could theoretically pre-calculate the 'isEnglish' and 'hasBadWords' state at the time
			 * the node is saved, and ONLY set properties when they are NOT english or are bad. This way we
			 * could avoid the hack of checking the isEnglish and hasBadWords here, and we'd be able to write a
			 * query that can do this filtering based on property existence.
			 * 
			 * I'll leave this as is for now, because it's not a big problem, afaik, although the 'skipped'
			 * counting we're doing could eventually lead to pagination problems if we get to where MOST items
			 * are skipped and not accepted.
			 */
			if (!allowNonEnglish && !english.isEnglish(node.getContent())) {
				// log.debug("Ignored nonEnglish: node.id=" + node.getIdStr() + " Content: " + node.getContent());
				skipped++;
				continue;
			}

			if (!allowBadWords && english.hasBadWords(node.getContent())) {
				skipped++;
				continue;
			}

			// for the curated feed ignore valueless super short messages that also have no attachment.
			if (Constant.FEED_PUB.s().equals(req.getName())) {
				if ((StringUtils.isEmpty(node.getContent()) || node.getContent().length() < 10) //
						&& no(node.getAttachments())) {
					skipped++;
					continue;
				}
			}

			Val<SubNode> boostedNodeVal = null;
			if (!allowNonEnglish || !allowBadWords) {
				String boostTargetId = node.getStr(NodeProp.BOOST);
				if (ok(boostTargetId)) {
					SubNode boostedNode = read.getNode(ms, boostTargetId);

					// once we searched for the node, we want to have boostedNodeVal non-null, to propagate the result,
					// even if boostedNode is null here, indicating it's not found.
					boostedNodeVal = new Val<>(boostedNode);

					if (ok(boostedNode)) {
						// if the owner of the boosted node is a blocked user and we're querying public nodes and with
						// applyAdminBlocks in effect then skip this post.
						if (req.getToPublic() && req.isApplyAdminBlocks() && blockedUserIds.contains(boostedNode.getOwner())) {
							skipped++;
							continue;
						}

						if (!allowNonEnglish && !english.isEnglish(boostedNode.getContent())) {
							// log.debug("Ignored nonEnglish: node.id=" + node.getIdStr() + " Content: " + node.getContent());
							skipped++;
							continue;
						}

						if (!allowBadWords && english.hasBadWords(boostedNode.getContent())) {
							skipped++;
							continue;
						}
					}
				}
			}

			try {
				NodeInfo info = convert.convertToNodeInfo(false, sc, ms, node, true, false, counter + 1, false, false, false,
						false, true, true, boostedNodeVal);
				if (ok(info)) {
					searchResults.add(info);

					if (searchResults.size() >= MAX_FEED_ITEMS) {
						break;
					}
				}
			} catch (Exception e) {
				ExUtil.error(log, "convertToNodeInfo", e);
			}
		}

		if (searchResults.size() < MAX_FEED_ITEMS - skipped) {
			res.setEndReached(true);
		}

		res.setSuccess(true);
		// log.debug("search results count: " + counter);
		return res;
	}

	/*
	 * Blocked from the perspective of 'userName', and a null userName here indicates, current session
	 * user.
	 */
	public void getBlockedUserIds(HashSet<ObjectId> set, String userName) {
		arun.run(as -> {
			List<SubNode> nodeList = user.getSpecialNodesList(as, null, NodeType.BLOCKED_USERS.s(), userName, false);
			if (no(nodeList))
				return null;

			for (SubNode node : nodeList) {
				String userNodeId = node.getStr(NodeProp.USER_NODE_ID);
				// log.debug("BLOCKED: " + userNodeId);
				ObjectId oid = new ObjectId(userNodeId);
				set.add(oid);
			}
			return null;
		});
	}
}
