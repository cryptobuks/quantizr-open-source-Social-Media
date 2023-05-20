package quanta.mongo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.actpub.APConst;
import quanta.actpub.model.APOMention;
import quanta.actpub.model.APObj;
import quanta.config.NodePath;
import quanta.config.ServiceBase;
import quanta.config.SessionContext;
import quanta.exception.NodeAuthFailedException;
import quanta.exception.base.RuntimeEx;
import quanta.instrument.PerfMon;
import quanta.model.AccessControlInfo;
import quanta.model.PrivilegeInfo;
import quanta.model.client.Attachment;
import quanta.model.client.Constant;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.model.client.PrincipalName;
import quanta.model.client.PrivilegeType;
import quanta.mongo.model.AccessControl;
import quanta.mongo.model.SubNode;
import quanta.util.ThreadLocals;
import quanta.util.XString;

/**
 * Service for checking authorization for access to nodes. Checks what users are accessing what
 * nodes and checks their privileges againts the ACL on the Nodes.
 */
@Component
@Slf4j
public class MongoAuth extends ServiceBase {
	private static final boolean verbose = false;

	private static final Object adminSessionLck = new Object();
	private static MongoSession adminSession;

	private static final Object anonSessionLck = new Object();
	private static MongoSession anonSession;

	private static final ConcurrentHashMap<String, SubNode> userNodesById = new ConcurrentHashMap<>();

	public MongoSession getAdminSession() {
		if (adminSession != null) {
			return adminSession;
		}

		synchronized (adminSessionLck) {
			if (adminSession == null) {
				SubNode root = read.getDbRoot();
				adminSession = new MongoSession(PrincipalName.ADMIN.s(), root == null ? null : root.getId());
			}
			return adminSession;
		}
	}

	public MongoSession getAnonSession() {
		if (anonSession != null) {
			return anonSession;
		}

		synchronized (anonSessionLck) {
			if (anonSession == null) {
				anonSession = new MongoSession(PrincipalName.ANON.s(), null);
			}
			return anonSession;
		}
	}

	public String getAccountPropById(MongoSession ms, String accountId, String prop) {
		String propVal = null;
		SubNode accntNode = null;

		// try to get the node from the cache of nodes
		accntNode = userNodesById.get(accountId);

		// if we found the node get property from it to return.
		if (accntNode != null) {
			propVal = accntNode.getStr(prop);
		}
		// else we have to lookup the node from the DB, and then cache it if found
		else {
			accntNode = read.getNode(ms, accountId);
			if (accntNode != null) {
				propVal = accntNode.getStr(prop);
				userNodesById.put(accountId, accntNode);
			}
		}

		return propVal;
	}

	/*
	 * Returns a list of all user names that are shared to on this node, including "public" if any are
	 * public.
	 */
	@PerfMon(category = "auth")
	public List<String> getUsersSharedTo(MongoSession ms, SubNode node) {
		List<String> userNames = null;

		List<AccessControlInfo> acList = getAclEntries(ms, node);
		if (acList != null) {
			for (AccessControlInfo info : acList) {
				String userNodeId = info.getPrincipalNodeId();
				String name = null;

				if (PrincipalName.PUBLIC.s().equals(userNodeId)) {
					name = PrincipalName.PUBLIC.s();
				} //
				else if (userNodeId != null) {
					SubNode accountNode = read.getNode(ms, userNodeId);
					if (accountNode != null) {
						name = accountNode.getStr(NodeProp.USER);
					}
				}

				if (name != null) {
					// lazy create the list
					if (userNames == null) {
						userNames = new LinkedList<>();
					}
					userNames.add(name);
				}
			}
		}
		return userNames;
	}

	/*
	 * When a child is created under a parent we want to default the sharing on the child so that
	 * there's an explicit share to the owner of the parent (minus any share to 'child' user that may be
	 * in the parent Acl, because that would represent 'child' node sharing to himself which is never
	 * done)
	 */
	public void setDefaultReplyAcl(SubNode parent, SubNode child) {

		// if parent or child is null or parent is an ACCOUNT node do nothing here.
		if (parent == null || parent.isType(NodeType.ACCOUNT) || child == null)
			return;

		// log.debug("childNode: " + child.getIdStr() + " being created under " + parent.getIdStr());
		/*
		 * Special case of replying to (appending under) a FRIEND-type node is always to make this a private
		 * message to the user that friend node represents
		 */
		if (parent.isType(NodeType.FRIEND)) {
			// get user prop from node
			String userName = parent.getStr(NodeProp.USER);

			// if we have a userProp, find the account node for the user
			if (userName != null) {
				SubNode accountNode = arun.run(as -> read.getUserNodeByUserName(as, userName));
				if (accountNode != null) {
					child.putAc(accountNode.getIdStr(), new AccessControl(null, APConst.RDWR));
				}
			}
		}
		/*
		 * otherwise if not a FRIEND node we just share to the owner of the parent node
		 */
		else {
			// add `parent.owner` to the ACL
			child.putAc(parent.getOwner().toHexString(), new AccessControl(null, APConst.RDWR));

			if (nostr.isNostrNode(parent)) {
				processNostrForNewNode(parent, child);
			} else {
				processActPubForNewNode(parent, child);
			}
		}
	}

	private void processNostrForNewNode(SubNode parent, SubNode child) {
		ArrayList<ArrayList<String>> tags = (ArrayList) parent.getObj(NodeProp.NOSTR_TAGS.s(), ArrayList.class);

		shareToAllNostrUsers(tags, child);
	}

	public void shareToAllNostrUsers(ArrayList<ArrayList<String>> tags, SubNode child) {
		if (tags == null)
			return;

		// Scan all "p" (people) tags to include sharing for them.
		for (ArrayList<String> itm : tags) {
			if ("p".equals(itm.get(0))) {

				// get pub key of the user
				String pubKey = itm.get(1);

				// lookup the SubNode for the account, and we might get back a Foreign account node or Local node,
				// doesn't matter.
				SubNode nostrAccnt = arun.run(as -> nostr.getAccountByNostrPubKey(as, pubKey));

				// if we found the account with that key and it's not our own account
				if (nostrAccnt != null && !nostrAccnt.getOwner().equals(child.getOwner())) {
					// add the ACL for this user
					child.putAc(nostrAccnt.getOwner().toHexString(), new AccessControl(null, APConst.RDWR));
				}
			}
		}
	}

	private void processActPubForNewNode(SubNode parent, SubNode child) {
		/*
		 * We also extract the 'mentions' out of any 'tag' array that might be on this node, so we can
		 * default the content of the post as `@mention1 @mention2 #mention3...` for all the people being
		 * mentioned in the tags array
		 */
		HashMap<String, APObj> tags = apub.parseTags(parent);

		// if no content, and the parent isn't our own node
		if (StringUtils.isEmpty(child.getContent()) && !auth.ownedByThreadUser(parent)) {
			SubNode parentUserNode = arun.run(as -> read.getNode(as, parent.getOwner()));

			if (parentUserNode != null) {
				String user = parentUserNode.getStr(NodeProp.USER);

				String url = parentUserNode.getStr(NodeProp.ACT_PUB_ACTOR_ID);
				if (url == null) {
					url = prop.getProtocolHostAndPort() + APConst.ACTOR_PATH + "/" + user;
				}

				tags.put("@" + user, new APOMention(url, "@" + user));
			}
		}

		if (tags.size() > 0) {
			String content = "";
			for (String key : tags.keySet()) {
				if (key.startsWith("@")) {
					content += key + " ";
				}
			}

			/*
			 * This will put a string of all mentioned users right in the text of the message so they can see
			 * who will be replied to or remove users they don't want replied to.
			 */
			child.setContent(content);
		}
	}

	public boolean isAllowedUserName(String userName) {
		userName = userName.trim();
		return !userName.equalsIgnoreCase(PrincipalName.ADMIN.s()) && //
				!userName.equalsIgnoreCase(PrincipalName.PUBLIC.s()) && //
				!userName.equalsIgnoreCase(PrincipalName.ANON.s());
	}

	/*
	 * Adds authorization to any Criteria and returns the updated criteria. Filters to get only nodes
	 * that are publi OR are shared to the current user. For single node lookups we won't need this
	 * because we can do it the old way and be faster, but for queries of multiple records (page
	 * rendering, timeline, search, etc.) we need to build this security right into the query using this
	 * method.
	 */
	public Criteria addSecurityCriteria(MongoSession ms, Criteria crit) {
		// NOTE: If we're the admin we return criteria without adding security.
		if (ms.isAdmin())
			return crit;

		List<Criteria> orCriteria = new LinkedList<>();

		SessionContext sc = ThreadLocals.getSC();
		SubNode myAcntNode = null;

		// note, anonymous users end up keeping myAcntNode null here. anon will nave null rootID here.
		if (sc.getRootId() != null) {
			myAcntNode = read.getNode(ms, sc.getRootId());
		}

		// node is public
		orCriteria.add(Criteria.where(SubNode.AC + "." + PrincipalName.PUBLIC.s()).ne(null));

		// if we have a user add their privileges in addition to public.
		if (myAcntNode != null) {
			// or node is shared to us
			orCriteria.add(Criteria.where(SubNode.AC + "." + myAcntNode.getOwner().toHexString()).ne(null));

			// or node is OWNED by us
			orCriteria.add(Criteria.where(SubNode.OWNER).is(myAcntNode.getOwner()));

			// or node was Transferred by us
			orCriteria.add(Criteria.where(SubNode.XFR).is(myAcntNode.getOwner()));
		}

		if (orCriteria.size() > 0) {
			if (crit == null) {
				crit = new Criteria();
			}
			// crit = crit.orOperator(orCriteria);
			crit = crit.andOperator(new Criteria().orOperator(orCriteria));
		}
		return crit;
	}

	public void ownerAuth(SubNode node) {
		ownerAuth(null, node);
	}

	@PerfMon(category = "auth")
	public void ownerAuth(MongoSession ms, SubNode node) {
		if (node == null) {
			throw new RuntimeEx("Auth Failed. Node did not exist.");
		}
		if (node.adminUpdate)
			return;

		if (ms == null) {
			ms = ThreadLocals.getMongoSession();
		}

		if (ms == null) {
			// when we get here it normally means we should've called "arun.exec" to manage
			// the thread instead of justs passing in an 'ms' or null
			throw new RuntimeException("ThreadLocals doesn't have session.");
		}

		if (ms.isAdmin()) {
			return;
		}

		if (ms.getUserNodeId() == null) {
			throw new RuntimeException("session has no userNode: " + XString.prettyPrint(ms));
		}

		if (!ms.getUserNodeId().equals(node.getOwner())) {
			log.error("Unable to save Node (expected ownerId " + ms.getUserNodeId().toHexString() + "): "
					+ XString.prettyPrint(node));
			throw new NodeAuthFailedException();
		}
	}

	public void requireAdmin(MongoSession ms) {
		if (!ms.isAdmin())
			throw new RuntimeEx("auth fail");
	}

	public void authForChildNodeCreate(MongoSession ms, SubNode node) {
		try {
			auth(ms, node, PrivilegeType.WRITE);
		} catch (RuntimeException e) {
			log.debug("session: " + ms.getUserName() + " tried to create a node under nodeId " + node.getIdStr()
					+ " and was refused.");
			throw e;
		}
	}

	@PerfMon(category = "auth")
	public void auth(MongoSession ms, SubNode node, PrivilegeType... privs) {
		// during server init no auth is required.
		if (node == null || !MongoRepository.fullInit) {
			return;
		}

		// this adminUpdate flag is specifically for the purpose if disabling auth checks
		if (node.adminUpdate)
			return;

		if (verbose)
			log.trace("auth: " + node.getPath());

		if (ms.isAdmin()) {
			if (verbose)
				log.trace("you are admin. auth success.");
			return; // admin can do anything. skip auth
		}

		auth(ms, node, Arrays.asList(privs));
	}

	public boolean ownedByThreadUser(SubNode node) {
		return node != null && node.getOwner() != null && node.getOwner().toHexString().equals(ThreadLocals.getSC().getRootId());
	}

	/*
	 * The way know a node is an account node is that it is its id matches its' owner. Self owned node.
	 * This is because the very definition of the 'owner' on any given node is the ID of the user's root
	 * node of the user who owns it
	 */
	public boolean isAnAccountNode(MongoSession ms, SubNode node) {
		return node.getIdStr().equals(node.getOwner().toHexString());
	}

	/* Returns true if this user on this session has privType access to 'node' */
	public void auth(MongoSession ms, SubNode node, List<PrivilegeType> priv) {
		// during server init no auth is required.
		if (node == null || !MongoRepository.fullInit) {
			return;
		}

		// admin has full power over all nodes
		if (ms.isAdmin()) {
			if (verbose)
				log.trace("auth granted. you're admin.");
			return;
		}

		if (verbose)
			log.trace("auth path " + node.getPath() + " for " + ms.getUserName());

		if (priv == null || priv.size() == 0) {
			throw new RuntimeEx("privileges not specified.");
		}

		if (node.getOwner() == null) {
			throw new RuntimeEx("node had no owner: " + node.getIdStr());
		}

		if (ms.getUserNodeId() != null) {
			// if this session user is the owner of this node, then they have full power
			if (ms.getUserNodeId().equals(node.getOwner())) {
				if (verbose)
					log.trace("allow: user " + ms.getUserName() + " owns node. accountId: " + node.getOwner().toHexString());
				return;
			}

			if (ms.getUserNodeId().equals(node.getTransferFrom())) {
				if (verbose)
					log.trace("allow: user " + ms.getUserName() + " is transferring node. accountId: "
							+ node.getTransferFrom().toHexString());
				return;
			}
		}

		String sessionUserNodeId = ms.getUserNodeId() != null ? ms.getUserNodeId().toHexString() : null;
		// log.debug("nodeAuth userAcctId=" + sessionUserNodeId);
		if (nodeAuth(node, sessionUserNodeId, priv)) {
			if (verbose)
				log.trace("nodeAuth success");
			return;
		}

		// Don't log this. It happens in normal flow cases.
		// log.error("Unauthorized access. NodeId=" + node.getId() + " path=" + node.getPath() + " by user:
		// " + ms.getUserName()
		// + "\n" + ExUtil.getStackTrace(null));

		throw new NodeAuthFailedException();
	}

	/*
	 * NOTE: It is the normal flow that we expect sessionUserNodeId to be null for any anonymous
	 * requests and this is fine because we are basically going to only be pulling 'public' acl to
	 * check, and this is by design.
	 */
	public boolean nodeAuth(SubNode node, String sessionUserNodeId, List<PrivilegeType> privs) {
		// log.debug("nodeAuth on node: " + XString.prettyPrint(node));
		HashMap<String, AccessControl> acl = node.getAc();
		if (acl == null) {
			// log.debug("no acls.");
			return false;
		}
		String allPrivs = "";

		AccessControl ac = (sessionUserNodeId == null ? null : acl.get(sessionUserNodeId));
		String privsForUserId = ac != null ? ac.getPrvs() : null;
		if (privsForUserId != null) {
			allPrivs += privsForUserId;
			// log.debug("Privs for this user: " + privsForUserId);
		}

		/*
		 * We always add on any privileges assigned to the PUBLIC when checking privs for this user, because
		 * the auth equivalent is really the union of this set.
		 */
		AccessControl acPublic = acl.get(PrincipalName.PUBLIC.s());
		String privsForPublic = acPublic != null ? acPublic.getPrvs() : null;
		if (privsForPublic != null) {
			if (allPrivs.length() > 0) {
				allPrivs += ",";
			}
			allPrivs += privsForPublic;
			// log.debug("Public Privs: " + privsForPublic);
		}

		if (allPrivs.length() > 0) {
			for (PrivilegeType priv : privs) {
				// log.debug("Checking for priv: " + priv.name);
				if (allPrivs.indexOf(priv.name) == -1) {
					/* if any priv is missing we fail the auth */
					// log.debug("priv missing. failing auth.");
					return false;
				}
			}
			/* if we looped thru all privs ok, auth is successful */
			return true;
		}
		return false;
	}

	public List<AccessControlInfo> getAclEntries(MongoSession ms, SubNode node) {
		HashMap<String, AccessControl> aclMap = node.getAc();
		if (aclMap == null) {
			return null;
		}

		/*
		 * I'd like this to not be created unless needed but that pesky lambda below needs a 'final' thing
		 * to work with.
		 */
		List<AccessControlInfo> ret = new LinkedList<>();

		aclMap.forEach((k, v) -> {
			AccessControlInfo acei = createAccessControlInfo(ms, k, v.getPrvs());
			if (acei != null) {
				ret.add(acei);
			}
		});
		return ret.size() == 0 ? null : ret;
	}

	public AccessControlInfo createAccessControlInfo(MongoSession ms, String principalId, String authType) {
		String displayName = null;
		String principalName = null;
		String publicKey = null;
		String avatarVer = null;
		String foreignAvatarUrl = null;
		String nostrNpub = null;
		String nostrRelays = null;

		/* If this is a share to public we don't need to lookup a user name */
		if (principalId.equalsIgnoreCase(PrincipalName.PUBLIC.s())) {
			principalName = PrincipalName.PUBLIC.s();
		}
		/* else we need the user name */
		else {
			SubNode principalNode = read.getNode(ms, principalId, false, null);
			if (principalNode == null) {
				return null;
			}
			principalName = principalNode.getStr(NodeProp.USER);
			displayName = principalNode.getStr(NodeProp.DISPLAY_NAME);
			publicKey = principalNode.getStr(NodeProp.USER_PREF_PUBLIC_KEY);
			nostrNpub = principalNode.getStr(NodeProp.NOSTR_USER_NPUB);
			nostrRelays = principalNode.getStr(NodeProp.NOSTR_RELAYS);

			// This will be null if it's a local node, and this is fine
			foreignAvatarUrl = principalNode.getStr(NodeProp.USER_ICON_URL);

			if (foreignAvatarUrl == null) {
				Attachment att = principalNode.getAttachment(Constant.ATTACHMENT_PRIMARY.s(), false, false);
				avatarVer = att != null ? att.getBin() : null;
			}
		}

		AccessControlInfo info = new AccessControlInfo(displayName, principalName, principalId, publicKey, nostrNpub, nostrRelays,
				avatarVer, foreignAvatarUrl);
		info.addPrivilege(new PrivilegeInfo(authType));
		return info;
	}

	// ========================================================================
	// SubGraphByAclUser (query and count)
	// ========================================================================

	/*
	 * Finds all subnodes that have a share targeting the sharedToAny (account node IDs of a person
	 * being shared with), regardless of the type of share 'rd,rw'. To find public shares pass 'public'
	 * in sharedTo instead
	 */
	public Iterable<SubNode> searchSubGraphByAclUser(MongoSession ms, String pathToSearch, List<String> sharedToAny, Sort sort,
			int limit, ObjectId ownerIdMatch) {

		Query q = subGraphByAclUser_query(ms, pathToSearch, sharedToAny, ownerIdMatch);
		if (q == null)
			return null;

		if (sort != null) {
			q.with(sort);
		}

		q.limit(limit);
		return mongoUtil.find(q);
	}

	/*
	 * counts all subnodes that have a share targeting the sharedTo (account node ID of a person being
	 * shared with), regardless of the type of share 'rd,rw'. To find public shares pass 'public' in
	 * sharedTo instead
	 */
	public long countSubGraphByAclUser(MongoSession ms, String pathToSearch, List<String> sharedToAny, ObjectId ownerIdMatch) {
		Query q = subGraphByAclUser_query(ms, pathToSearch, sharedToAny, ownerIdMatch);
		if (q == null)
			return 0L;
		Long ret = ops.count(q, SubNode.class);
		return ret;
	}

	private Query subGraphByAclUser_query(MongoSession ms, String pathToSearch, List<String> sharedToAny, ObjectId ownerIdMatch) {
		// this will be node.getPath() to search under the node, or null for searching
		// under all user content.
		if (pathToSearch == null) {
			pathToSearch = NodePath.USERS_PATH;
		}

		Query q = new Query();
		Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexRecursiveChildrenOfPath(pathToSearch));

		if (sharedToAny != null && sharedToAny.size() > 0) {
			List<Criteria> orCriteria = new LinkedList<>();
			for (String share : sharedToAny) {
				orCriteria.add(Criteria.where(SubNode.AC + "." + share).ne(null));
			}

			crit = crit.andOperator(new Criteria().orOperator(orCriteria));
		}

		if (ownerIdMatch != null) {
			crit = crit.and(SubNode.OWNER).is(ownerIdMatch);
		}

		q.addCriteria(crit);
		return q;
	}

	// ========================================================================
	// SubGraphByAcl (query and count)
	// ========================================================================

	/*
	 * Finds nodes that have any sharing on them at all. This query is secure and can't leak any
	 * incorrect nodes to wrong users because ownerIdMatch forces only the owner running the query to
	 * see their own nodes only
	 */
	public Iterable<SubNode> searchSubGraphByAcl(MongoSession ms, int skip, String pathToSearch, ObjectId ownerIdMatch, Sort sort,
			int limit) {
		Query q = subGraphByAcl_query(ms, pathToSearch, ownerIdMatch);

		if (sort != null) {
			q.with(sort);
		}

		if (skip > 0) {
			q.skip(skip);
		}

		q.limit(limit);
		return mongoUtil.find(q);
	}

	/* Finds nodes that have any sharing on them at all */
	public long countSubGraphByAcl(MongoSession ms, String pathToSearch, ObjectId ownerIdMatch) {
		Query q = subGraphByAcl_query(ms, pathToSearch, ownerIdMatch);
		return ops.count(q, SubNode.class);
	}

	public Query subGraphByAcl_query(MongoSession ms, String pathToSearch, ObjectId ownerIdMatch) {
		Query q = new Query();

		if (pathToSearch == null) {
			pathToSearch = NodePath.USERS_PATH;
		}

		/*
		 * This regex finds all that START WITH path, have some characters after path, before the end of the
		 * string. Without the trailing (.+)$ we would be including the node itself in addition to all its
		 * children.
		 */
		Criteria criteria = Criteria.where(SubNode.PATH).regex(mongoUtil.regexRecursiveChildrenOfPath(pathToSearch)) //
				.and(SubNode.AC).ne(null);

		if (ownerIdMatch != null) {
			criteria = criteria.and(SubNode.OWNER).is(ownerIdMatch);
		}

		q.addCriteria(criteria);
		return q;
	}

	/*
	 * Parses all mentions (like '@bob@server.com', or '@bob' for local user) in the node content text
	 * and adds them (if not existing) to the node sharing on the node, which ensures the person
	 * mentioned has visibility of this node and that it will also appear in their FEED.
	 */
	public void saveMentionsToACL(HashMap<String, APObj> tags, MongoSession ms, SubNode node) {
		if (tags == null) {
			return;
		}
		boolean acChanged = false;
		HashMap<String, AccessControl> ac = node.getAc();

		// make sure all parsed toUserNamesSet user names are saved into the node acl */
		for (String userName : tags.keySet()) {
			APObj val = tags.get(userName);

			// ignore of this is something else like a Hashtag
			if (!(val instanceof APOMention))
				continue;

			userName = XString.stripIfStartsWith(userName, "@");
			SubNode acctNode = read.getUserNodeByUserName(ms, userName);

			/*
			 * If this is a foreign 'mention' user name that is not imported into our system, we auto-import
			 * that user now
			 */
			if (StringUtils.countMatches(userName, "@") == 1) {
				/*
				 * todo-2: WARNING: this sets off a chain reaction of fediverse crawling!! Unless/until you invent
				 * some way to stop that (or decide you WANT a FediCrawler) then keep this commented out. Don't
				 * delete this code until you think this thru more.
				 */
				// if (no(acctNode)) {
				// acctNode = actPub.loadForeignUserByUserName(session, userName);
				// }
				apub.userEncountered(userName, false);
			}

			if (acctNode != null) {
				String acctNodeId = acctNode.getIdStr();
				if (ac == null || !ac.containsKey(acctNodeId)) {
					/*
					 * Lazy create 'ac' so that the net result of this method is never to assign non null when it could
					 * be left null
					 */
					if (ac == null) {
						ac = new HashMap<String, AccessControl>();
					}
					acChanged = true;
					ac.put(acctNodeId, new AccessControl(null, APConst.RDWR));
				}
			} else {
				log.debug("Mentioned user not found: " + userName);
			}
		}

		if (acChanged) {
			node.setAc(ac);

			// for now this saving is being done at a higher layer up (method that calls this one), and is
			// always run.
			// update.save(ms, node);
		}
	}
}
