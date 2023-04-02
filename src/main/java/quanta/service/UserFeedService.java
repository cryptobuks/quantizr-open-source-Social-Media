package quanta.service;

import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
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
import quanta.util.XString;

@Component
@Slf4j
public class UserFeedService extends ServiceBase {
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

		/*
		 * limit to just markdown types and comments, becasue we need to avoid everything else since we are
		 * searching from the root of all user accounts.
		 */
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

		String pathToSearch =
				testQuery ? NodePath.ROOT_PATH : (req.getLocalOnly() ? NodePath.LOCAL_USERS_PATH : NodePath.USERS_PATH);
		boolean doAuth = true;

		/*
		 * if we're doing a 'feed' under a specific root node this is like the 'chat feature' and is
		 * normally only called for a chat-room type node.
		 */
		if (req.getNodeId() != null) {
			// Get the chat room node (root of the chat room query)
			SubNode rootNode = read.getNode(ms, req.getNodeId());
			if (rootNode == null) {
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
		String searchForUserName = null;

		if (doAuth && req.getMyMentions()) {
			searchForUserName = sc.getUserName() + "@" + prop.getMetaHost();
		}
		// includes shares TO me (but not in the context of a 'bidirectional' query)
		else if (!testQuery && doAuth && req.getToMe()) {
			myAcntNode = read.getNode(ms, sc.getRootId());

			if (myAcntNode != null) {
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

		/*
		 * We block the "remote users" and "local users" by blocking any admin owned nodes, but we also just
		 * want to in general for other reasons block any admin-owned nodes from showing up in feeds. Feeds
		 * are always only about user content.
		 */
		blockedUserIds.add(auth.getAdminSession().getUserNodeId());

		boolean allowNonEnglish = false;

		if (!bidirectional) {
			/*
			 * this logic makes it so that any feeds using 'public' checkbox will have the admin-blocked users
			 * removed from it.
			 */
			// Add ADMIN BLOCKS
			if (req.getToPublic() && req.isApplyAdminBlocks()) {
				getBlockedUserIds(blockedUserIds, PrincipalName.ADMIN.s());
			}

			// Add criteria for blocking users using the 'not in' list (nin)
			// Add THIS USER BLOCKS
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

			if (myAcntNode == null) {
				myAcntNode = read.getNode(ms, sc.getRootId());
			}

			if (myAcntNode != null) {
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
			if (myAcntNode == null) {
				myAcntNode = read.getNode(ms, sc.getRootId());
			}

			if (myAcntNode != null) {
				orCriteria.add(
						// where node is owned by us.
						Criteria.where(SubNode.OWNER).is(myAcntNode.getOwner()) //
								// and the node has any sharing on it.
								.and(SubNode.AC).ne(null));
			}
		}

		if (!testQuery && doAuth && req.getFromFriends()) {
			List<ObjectId> friendIds = new LinkedList<>();
			boolean friendsProcessed = false;

			if (req.getLoadFriendsTags() || ThreadLocals.getSC().isFriendsTagsDirty()) {
				// if we're going to be scanning ALL friends then the block below that would scan for friends
				// can be bypassed, because we will have already loaded friendIds
				friendsProcessed = true;

				ThreadLocals.getSC().setFriendsTagsDirty(false);

				HashSet<String> friendsHashTagsSet = new HashSet<>();
				List<SubNode> allFriendNodes = user.getSpecialNodesList(ms, null, NodeType.FRIEND_LIST.s(), null, true, null);
				if (allFriendNodes != null) {
					for (SubNode friendNode : allFriendNodes) {
						List<String> hashTags = XString.tokenize(friendNode.getTags(), " ,", false);
						if (hashTags != null) {
							for (String hashTag : hashTags) {
								// ignore anything that happens not to be a tag
								if (hashTag.startsWith("#")) {
									friendsHashTagsSet.add(hashTag);
								}
							}
						}

						// since we're processing ALL friends we can go ahead and update friendIds here
						// but also only do that if we're not filtering for tags, or the filter is a match
						if (StringUtils.isEmpty(req.getFriendsTagSearch()) || //
								(StringUtils.isNotEmpty(friendNode.getTags())
										&& friendNode.getTags().contains(req.getFriendsTagSearch()))) {
							String userNodeId = friendNode.getStr(NodeProp.USER_NODE_ID);

							// if we have a userNodeId and they aren't in the blocked list.
							if (userNodeId != null && !blockedIdStrings.contains(userNodeId)) {
								friendIds.add(new ObjectId(userNodeId));
							}
						}
					}

					// returning an empty list when there are no tags is a meaningful result and will trigger
					// the client to update that there are no hashtags
					res.setFriendHashTags(new LinkedList<String>(friendsHashTagsSet));
				}
			}

			// if we already processed friends above, then we know we don't need to do it here. It's done.
			if (!friendsProcessed) {
				Criteria tagCriteria = null;
				if (!StringUtils.isEmpty(req.getFriendsTagSearch())) {
					tagCriteria = Criteria.where(SubNode.TAGS).regex(req.getFriendsTagSearch());
				}

				List<SubNode> friendNodes = user.getSpecialNodesList(ms, null, NodeType.FRIEND_LIST.s(), null, true, tagCriteria);
				if (friendNodes != null) {
					for (SubNode friendNode : friendNodes) {
						// the USER_NODE_ID property on friends nodes contains the actual account ID of this friend.
						String userNodeId = friendNode.getStr(NodeProp.USER_NODE_ID);

						// if we have a userNodeId and they aren't in the blocked list.
						if (userNodeId != null && !blockedIdStrings.contains(userNodeId)) {
							friendIds.add(new ObjectId(userNodeId));
						}
					}
				}
			}

			if (friendIds.size() > 0) {
				orCriteria.add(Criteria.where(SubNode.OWNER).in(friendIds));
			}
		}

		if (orCriteria.size() > 0) {
			crit = crit.orOperator((Criteria[]) orCriteria.toArray(new Criteria[orCriteria.size()]));
		}

		// exclude all user's home nodes from appearing in the results. When a user signs up they'll get
		// something like
		// a node with text "Clay's Node" created and it will be empty, and we don't need them showing up in
		// the feeds.
		crit = crit.and(SubNode.NAME).ne(NodeName.HOME);

		TextCriteria textCriteria = null;

		// Add 'Blocked Words' criteria only if we're not doing a "From Me" or "From Friends" kind of feed.
		if (!req.getFromMe() && !req.getFromFriends()) {
			// Filter USER_BLOCK_WORDS if user has defined any
			SubNode userNode = read.getNode(ms, ThreadLocals.getSC().getUserNodeId(), false);
			if (userNode != null) {
				String blockedWords = userNode.getStr(NodeProp.USER_BLOCK_WORDS);
				if (StringUtils.isNotEmpty(blockedWords)) {

					StringTokenizer t = new StringTokenizer(blockedWords, " \n\r\t,", false);
					StringBuilder regex = new StringBuilder();
					while (t.hasMoreTokens()) {
						if (regex.length() > 0) {
							regex.append("|");
						}
						regex.append(t.nextToken());
					}

					crit = crit.and(SubNode.CONTENT).not().regex(regex.toString(), "i");
				}
			}
		}

		if (!StringUtils.isEmpty(req.getSearchText())) {
			if (textCriteria == null) {
				textCriteria = TextCriteria.forDefaultLanguage();
			}
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
		}

		if (searchForUserName != null) {
			if (textCriteria == null) {
				textCriteria = TextCriteria.forDefaultLanguage();
			}
			textCriteria.matching("\"@" + searchForUserName + "\"");
		}

		if (textCriteria != null) {
			textCriteria.caseSensitive(false);
			q.addCriteria(textCriteria);
		}

		q.addCriteria(crit);

		// if we have a node id this is like a chat room type, and so we sort by create time.
		if (req.getNodeId() != null) {
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
			 * todo-2: We could theoretically pre-calculate the 'isEnglish' and 'hasBadWords' state at the time
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

			// only do the badWords blocking if it's NOT a node we own. We can never have this filter block
			// our own content.
			if (!allowBadWords && !auth.ownedByThreadUser(node) && english.hasBadWords(node.getContent())) {
				skipped++;
				continue;
			}

			// for the curated feed ignore valueless super short messages that also have no attachment, unless
			// it's a boost! Be careful boosts also have no content, but we DO want to show boosts.
			if (Constant.FEED_PUB.s().equals(req.getName())) {
				if ((StringUtils.isEmpty(node.getContent()) || node.getContent().length() < 10) //
						&& node.getAttachments() == null && node.getStr(NodeProp.BOOST) == null) {
					skipped++;
					continue;
				}
			}

			Val<SubNode> boostedNodeVal = null;

			String boostTargetId = node.getStr(NodeProp.BOOST);
			if (boostTargetId != null) {
				SubNode boostedNode = read.getNode(ms, boostTargetId, false, null);

				// if we can't find teh boostedNode, don't display this node (the boosting node) at all
				if (boostedNode == null) {
					skipped++;
					continue;
				}

				// once we searched for the node, we want to have boostedNodeVal non-null, to propagate the result,
				// even if boostedNode is null here, indicating it's not found.
				boostedNodeVal = new Val<>(boostedNode);

				// if the owner of the boosted node is a blocked user and we're querying public nodes and with
				// applyAdminBlocks in effect then skip this post.
				if (blockedUserIds.contains(boostedNode.getOwner())) {
					skipped++;
					continue;
				}

				if (!allowNonEnglish && !english.isEnglish(boostedNode.getContent())) {
					// log.debug("Ignored nonEnglish: node.id=" + node.getIdStr() + " Content: " + node.getContent());
					skipped++;
					continue;
				}

				if (!allowBadWords && !auth.ownedByThreadUser(boostedNode) && english.hasBadWords(boostedNode.getContent())) {
					skipped++;
					continue;
				}
			}

			try {
				NodeInfo info = convert.convertToNodeInfo(false, sc, ms, node, false, counter + 1, false, false, false, false,
						true, true, boostedNodeVal, false);
				if (info != null) {
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

	public LinkedList<String> getFriendsHashTags(MongoSession ms) {
		HashSet<String> friendsHashTagsSet = new HashSet<>();
		List<SubNode> allFriendNodes = user.getSpecialNodesList(ms, null, NodeType.FRIEND_LIST.s(), null, true, null);
		if (allFriendNodes != null) {
			for (SubNode friendNode : allFriendNodes) {
				List<String> hashTags = XString.tokenize(friendNode.getTags(), " ,", false);
				if (hashTags != null) {
					for (String hashTag : hashTags) {
						// ignore anything that happens not to be a tag
						if (hashTag.startsWith("#")) {
							friendsHashTagsSet.add(hashTag);
						}
					}
				}
			}

			return new LinkedList<String>(friendsHashTagsSet);
		}
		return null;
	}

	/*
	 * Blocked from the perspective of 'userName', and a null userName here indicates, current session
	 * user.
	 */
	public void getBlockedUserIds(HashSet<ObjectId> set, String userName) {
		arun.run(as -> {
			List<SubNode> nodeList = user.getSpecialNodesList(as, null, NodeType.BLOCKED_USERS.s(), userName, false, null);
			if (nodeList == null)
				return null;

			for (SubNode node : nodeList) {
				String userNodeId = node.getStr(NodeProp.USER_NODE_ID);
				if (userNodeId != null) {
					// log.debug("BLOCKED: " + userNodeId);
					set.add(new ObjectId(userNodeId));
				}
			}
			return null;
		});
	}
}
