package quanta.mongo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;
import org.apache.commons.codec.digest.DigestUtils;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.data.mongodb.core.index.TextIndexDefinition.TextIndexDefinitionBuilder;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import com.mongodb.bulk.BulkWriteResult;
import lombok.extern.slf4j.Slf4j;
import quanta.config.NodeName;
import quanta.config.NodePath;
import quanta.config.ServiceBase;
import quanta.instrument.PerfMon;
import quanta.model.client.Attachment;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.model.client.PrincipalName;
import quanta.model.client.PrivilegeType;
import quanta.mongo.model.AccessControl;
import quanta.mongo.model.FediverseName;
import quanta.mongo.model.SubNode;
import quanta.request.SignupRequest;
import quanta.util.Const;
import quanta.util.ExUtil;
import quanta.util.ImageUtil;
import quanta.util.ThreadLocals;
import quanta.util.XString;
import quanta.util.val.IntVal;
import quanta.util.val.Val;

/**
 * Verious utilities related to MongoDB persistence
 */
@Component
@Slf4j
public class MongoUtil extends ServiceBase {
	private static HashSet<String> testAccountNames = new HashSet<>();
	private static final Random rand = new Random();

	public static SubNode allUsersRootNode = null;
	public static SubNode localUsersNode = null;
	public static SubNode remoteUsersNode = null;

	/*
	 * removed lower-case 'r' and 'p' since those are 'root' and 'pending' (see setPendingPath), and we
	 * need very performant way to translate from /r/p to /r path and vice verse
	 * 
	 * removed R and L because we have /r/usr/R and /r/usr/L for remote and local users.
	 */
	static final String PATH_CHARS = "0123456789ABCDEFGHIJKMNOPQSTUVWXYZabcdefghijklmnoqstuvwxyz";

	/*
	 * The set of nodes in here MUST be known to be from an UNFILTERED and COMPLETE SubGraph query or
	 * else this WILL result in DATA LOSS!
	 * 
	 * Note: rootNode will not be included in 'nodes'.
	 * 
	 * Most places we do a call like this: Iterable<SubNode> results = read.getSubGraph(ms, node, null,
	 * 0); We will be better off to filterOutOrphans from the returned list before processing it.
	 *
	 */
	public LinkedList<SubNode> filterOutOrphans(MongoSession ms, SubNode rootNode, Iterable<SubNode> nodes) {
		LinkedList<SubNode> ret = new LinkedList<>();

		// log.debug("Removing Orphans.");
		HashSet<String> paths = new HashSet<>();

		// this just helps us avoide redundant delete attempts
		HashSet<String> pathsRemoved = new HashSet<>();

		// log.debug("ROOT PTH: " + rootNode.getPath() + " content: " + rootNode.getContent());
		paths.add(rootNode.getPath());

		// Add all the paths
		for (SubNode node : nodes) {
			// log.debug("PTH: " + node.getPath() + " content: " + node.getContent());
			paths.add(node.getPath());
		}

		// now identify all nodes that don't have a parent in the list
		for (SubNode node : nodes) {
			String parentPath = node.getParentPath();

			// if parentPath not in paths this is an orphan
			if (!paths.contains(parentPath)) {
				// log.debug("ORPHAN: " + parentPath);

				// if we haven't alread seen this parent path and deleted under it.
				if (!pathsRemoved.contains(parentPath)) {
					pathsRemoved.add(parentPath);

					// Since we know this parent doesn't exist we can delete all nodes that fall under it
					// which would remove ALL siblings that are also orphans. Using this kind of pattern:
					// ${parantPath}/* (that is, we append a slash and then find anything starting with that)
					delete.deleteUnderPath(ms, parentPath);
					// NOTE: we can also go ahead and DELETE these orphans as found (from the DB)
				}
			}
			// otherwise add to our output results.
			else {
				ret.add(node);
			}
		}
		return ret;
	}

	/**
	 * This find method should wrap ALL queries so that we can run our code inside this NodeIterable
	 * wrapper which will detect any query results that reference objects cached in memory and point to
	 * the in-memory copy of the object during iterating.
	 * 
	 * NOTE: All security checks are done external to this method.
	 */
	public List<SubNode> find(Query q) {
		return ops.find(q, SubNode.class);
	}

	/**
	 * NOTE: All security checks are done external to this method.
	 */
	public SubNode findOne(Query q) {
		return ops.findOne(q, SubNode.class);
	}

	/**
	 * Runs the mongo 'findById' but if it finds a node that's already in memory we return the memory
	 * object.
	 * 
	 * NOTE: All security checks are done external to this method.
	 */
	@PerfMon
	public SubNode findById(ObjectId objId) {
		if (objId == null)
			return null;

		// NOTE: For AOP Instrumentation we have to call thru the bean proxy ref, not 'this'
		return mongoUtil.ops_findById(objId);
	}

	@PerfMon
	public SubNode ops_findById(ObjectId objId) {
		return ops.findById(objId, SubNode.class);
	}


	public SubNode findByIdNoCache(ObjectId objId) {
		return ops.findById(objId, SubNode.class);
	}

	/*
	 * Takes a path like "/a/b/" OR "/a/b" and finds any random longer path that's not currently used.
	 * Note that since we don't require to end with "/" this function can be extending an existing leaf
	 * name, or if the path does end with "/", then it has the effect of finding a new leaf from
	 * scratch.
	 */
	@PerfMon
	public String findAvailablePath(String path) {
		// log.debug("findAvailablePath on: " + path);

		/*
		 * If the path we want doesn't exist at all we can use it, so check that case first, but only if we
		 * don't have a path ending with slash because that means we KNOW we need to always find a new child
		 * regardless of any existing ones
		 */
		if (!path.endsWith("/") && pathIsAvailable(path)) {
			return path;
		}

		int tries = 0;
		while (true) {
			/*
			 * Append one random char to path. Statistically if we keep adding characters it becomes
			 * exponentially more likely we find an unused path.
			 */
			path += PATH_CHARS.charAt(rand.nextInt(PATH_CHARS.length()));

			/*
			 * if we encountered two misses, start adding two characters per iteration (at least), because this
			 * node has lots of children
			 */
			if (tries >= 2) {
				path += PATH_CHARS.charAt(rand.nextInt(PATH_CHARS.length()));
			}

			// after 3 fails get even more aggressive with 3 new chars per loop here.
			if (tries >= 3) {
				path += PATH_CHARS.charAt(rand.nextInt(PATH_CHARS.length()));
			}

			// after 4 fails get even more aggressive with 4 new chars per loop here.
			if (tries >= 4) {
				path += PATH_CHARS.charAt(rand.nextInt(PATH_CHARS.length()));
			}

			if (pathIsAvailable(path)) {
				return path;
			}
			tries++;
		}
	}

	public boolean pathIsAvailable(String path) {
		Criteria orCriteria = new Criteria();

		/*
		 * Or criteria here says if the exact 'path' exists or any node starting with "${path}/" exists even
		 * as an orphan (which can definitely happen) then this path it not available. So even orphaned
		 * nodes can keep us from being able to consider a path 'available for use'
		 */
		orCriteria.orOperator(//
				Criteria.where(SubNode.PATH).is(path), //
				Criteria.where(SubNode.PATH).regex(mongoUtil.regexRecursiveChildrenOfPath(path)));

		Query q = new Query(orCriteria);
		return !ops.exists(q, SubNode.class);
	}

	/*
	 * We create these users just so there's an easy way to start doing multi-user testing (sharing
	 * nodes from user to user, etc) without first having to manually register users.
	 */
	public void createTestAccounts() {
		/*
		 * The testUserAccounts is a comma delimited list of user accounts where each user account is a
		 * colon-delimited list like username:password:email.
		 */
		final List<String> testUserAccountsList = XString.tokenize(prop.getTestUserAccounts(), ",", true);
		if (testUserAccountsList == null) {
			return;
		}

		arun.run(as -> {
			for (String accountInfo : testUserAccountsList) {
				log.debug("Verifying test Account: " + accountInfo);

				final List<String> accountInfoList = XString.tokenize(accountInfo, ":", true);
				if (accountInfoList == null || accountInfoList.size() != 3) {
					log.debug("Invalid User Info substring: " + accountInfo);
					continue;
				}

				String userName = accountInfoList.get(0);

				SubNode ownerNode = read.getUserNodeByUserName(as, userName);
				if (ownerNode == null) {
					log.debug("userName not found: " + userName + ". Account will be created.");
					SignupRequest signupReq = new SignupRequest();
					signupReq.setUserName(userName);
					signupReq.setPassword(accountInfoList.get(1));
					signupReq.setEmail(accountInfoList.get(2));

					user.signup(signupReq, true);
				} else {
					log.debug("account exists: " + userName);
				}

				/*
				 * keep track of these names, because some API methods need to know if a given account is a test
				 * account
				 */
				testAccountNames.add(userName);
			}
			return null;
		});
	}

	public static boolean isTestAccountName(String userName) {
		return testAccountNames.contains(userName);
	}

	/*
	 * Make node either start with /r/p/ or ensure that it does NOT start with /r/p
	 * 
	 * 'p' means pending, and indicates user has not yet saved a new node they're currently editing, and
	 * if they cancel the node gets orphaned and eventually cleaned up by the system automatically.
	 */
	public void setPendingPath(SubNode node, boolean pending) {
		String pendingPath = NodePath.PENDING_PATH + "/";
		String rootPath = NodePath.ROOT_PATH + "/";

		// ensure node starts with /r/p
		if (pending && !node.getPath().startsWith(pendingPath)) {
			node.setPath(node.getPath().replace(rootPath, pendingPath));
		}
		// ensure node starts with /r and not /r/p
		else if (!pending && node.getPath().startsWith(pendingPath)) {
			// get pendingPath out of the path, first
			String path = node.getPath().replace(pendingPath, rootPath);
			path = findAvailablePath(path);
			node.setPath(path);
		}
	}

	/* Root path will start with '/' and then contain no other slashes */
	public boolean isRootPath(String path) {
		return path.startsWith("/") && path.substring(1).indexOf("/") == -1;
	}


	public String getHashOfPassword(String password) {
		if (password == null)
			return null;
		return DigestUtils.sha256Hex(password).substring(0, 20);
	}

	public void convertDb(MongoSession ms) {
		// processAllNodes(session);
	}

	public void processAllNodes(MongoSession ms) {
		// Val<Long> nodesProcessed = new Val<Long>(0L);

		// Query query = new Query();
		// Criteria criteria = Criteria.where(SubNode.FIELD_ACL).ne(null);
		// query.addCriteria(criteria);

		// saveSession(session);
		// Iterable<SubNode> iter = find(query);

		// iter.forEach((node) -> {
		// nodesProcessed.setVal(nodesProcessed.getVal() + 1);
		// if (nodesProcessed.getVal() % 1000 == 0) {
		// log.debug("reSave count: " + nodesProcessed.getVal());
		// }

		// // /*
		// // * NOTE: MongoEventListener#onBeforeSave runs in here, which is where some
		// of
		// // * the workload is done that pertains ot this reSave process
		// // */
		// save(session, node, true, false);
		// });
	}

	/* Returns true if there were actually some encryption keys removed */
	public boolean removeAllEncryptionKeys(SubNode node) {
		HashMap<String, AccessControl> aclMap = node.getAc();
		if (aclMap == null) {
			return false;
		}

		Val<Boolean> keysRemoved = new Val<>(false);
		aclMap.forEach((String key, AccessControl ac) -> {
			if (ac.getKey() != null) {
				ac.setKey(null);
				keysRemoved.setVal(true);
			}
		});

		return keysRemoved.getVal();
	}

	public boolean isImageAttachment(Attachment att) {
		return att != null && ImageUtil.isImageMime(att.getMime());
	}

	public int dump(String message, Iterable<SubNode> iter) {
		int count = 0;
		log.debug("    " + message);
		for (SubNode node : iter) {
			log.debug("    DUMP node: " + XString.prettyPrint(node));
			count++;
		}
		log.debug("DUMP count=" + count);
		return count;
	}

	public void rebuildIndexes(MongoSession ms) {
		dropAllIndexes(ms);
		createAllIndexes(ms);
	}

	// DO NOT DELETE:
	// Leaving this here for future reference for any DB-conversions.
	// This code was for removing dupliate apids and renaming a property
	public void preprocessDatabase(MongoSession ms) {
		// NO LONGER NEEDED.
		// This was a one time conversion to get the DB updated to the newer shorter path parts.
		// shortenPathParts(session);
	}

	// DO NOT DELETE - LEAVE AS EXAMPLE (for similar future needs)
	// This is the code I used to convert paths /r/usr to /r/usr/L (local) and /r/usr/R (remote)
	public void processAccounts(MongoSession ms) {
		dropAllIndexes(ms);

		HashSet<String> localPathPart = new HashSet<>();
		HashSet<String> remotePathPart = new HashSet<>();

		log.debug("pre scan...");
		IntVal scanCount = new IntVal();
		LinkedList<SubNode> toDel = new LinkedList<>();
		ops.stream(new Query(), SubNode.class).forEachRemaining(node -> {
			String path = node.getPath();
			if (path.equals(NodePath.LOCAL_USERS_PATH) || path.equals(NodePath.REMOTE_USERS_PATH)
					|| path.startsWith(NodePath.LOCAL_USERS_PATH + "/") || path.startsWith(NodePath.REMOTE_USERS_PATH + "/")) {
				toDel.add(node);
			}
			scanCount.inc();
			if (scanCount.getVal() % 5000 == 0) {
				log.debug("scanCount: " + scanCount.getVal());
			}
		});

		log.debug("pre scan...deleting: " + toDel.size());;
		for (SubNode node : toDel) {
			delete.delete(ms, node);
		}

		Iterable<SubNode> accntNodes =
				read.findSubNodesByType(ms, MongoUtil.allUsersRootNode, NodeType.ACCOUNT.s(), false, null, null);

		for (SubNode acctNode : accntNodes) {
			String userName = acctNode.getStr(NodeProp.USER.s());
			if (userName == null)
				continue;

			String path = acctNode.getPath();
			log.debug("ProcUser: " + userName);
			String shortPart = XString.stripIfStartsWith(path, NodePath.USERS_PATH + "/");

			if (userName.contains("@")) {
				// log.debug("Short Piece: Remote: " + shortPart);
				remotePathPart.add(shortPart);
			} else {
				// log.debug("Short Piece: Local: " + shortPart);
				localPathPart.add(shortPart);
			}
		}

		ThreadLocals.clearDirtyNodes();
		// we might have wiped these above, so ensure we have them.
		ensureUsersLocalAndRemotePath(ms);
		update.saveSession(ms);

		// ---------------------------------

		SubNode localCheck = read.getNode(ms, "/r/usr/L");
		if (localCheck == null) {
			log.debug("localCheck failed");
			return;
		} else {
			log.debug("Local Check: " + XString.prettyPrint(localCheck));
		}

		// -----------------------------------
		SubNode remoteCheck = read.getNode(ms, "/r/usr/R");
		if (remoteCheck == null) {
			log.debug("remoteCheck failed");
			return;
		} else {
			log.debug("Remote Check: " + XString.prettyPrint(remoteCheck));
		}

		ThreadLocals.clearDirtyNodes();

		// ----------------------------------

		Val<BulkOperations> bops = new Val<>();
		IntVal opCount = new IntVal();
		IntVal total = new IntVal();

		// stream every node
		ops.stream(new Query(), SubNode.class).forEachRemaining(node -> {
			String path = node.getPath();
			String newPath = null;
			if (!path.startsWith(NodePath.USERS_PATH + "/") || //
					path.startsWith(NodePath.LOCAL_USERS_PATH + "/") || path.startsWith(NodePath.REMOTE_USERS_PATH + "/"))
				return;

			String shortPart = XString.stripIfStartsWith(path, NodePath.USERS_PATH + "/");
			String shortPiece = XString.truncAfterFirst(shortPart, "/");

			// if this is a local node
			if (localPathPart.contains(shortPiece)) {
				newPath = NodePath.LOCAL_USERS_PATH + "/" + shortPart;
				// log.debug("LOCAL PATH [" + path + "] => [" + newPath + "]");
			} //
			else if (remotePathPart.contains(shortPiece)) {
				newPath = NodePath.REMOTE_USERS_PATH + "/" + shortPart;
				// log.debug("REMOT PATH [" + path + "] => [" + newPath + "]");
			} else {
				// log.debug("IGNORE PATH [" + path + "]");
				return;
			}

			Query query = new Query().addCriteria(new Criteria("id").is(node.getId()));
			Update update = new Update().set(SubNode.PATH, newPath);

			if (!bops.hasVal()) {
				bops.setVal(ops.bulkOps(BulkMode.UNORDERED, SubNode.class));
			}

			bops.getVal().updateOne(query, update);
			opCount.inc();
			total.inc();

			if (opCount.getVal() > Const.MAX_BULK_OPS) {
				BulkWriteResult results = bops.getVal().execute();
				log.debug("Bulk updated: " + results.getModifiedCount() + " total=" + total.getVal());
				bops.setVal(null);
				opCount.setVal(0);
			}
		});

		if (bops.hasVal()) {
			BulkWriteResult results = bops.getVal().execute();
			log.debug("Final Bulk updated: " + results.getModifiedCount() + " total=" + total.getVal());
		}

		createAllIndexes(ms);
	}

	/*
	 * This process finds all nodes that are remote-outbox loaded items (i.e. have an 'apid' prop), and
	 * for any that have Public sharing set the sharing on it to RDRW
	 * 
	 * This code is being kept as an example, but is no longer itself needed.
	 */
	public void fixSharing(MongoSession ms) {
		log.debug("Processing fixSharing");

		// WARNING: use 'ops.strea' (findAll will be out of memory error on prod)

		// Iterable<SubNode> nodes = ops.findAll(SubNode.class);
		// int counter = 0;

		// for (SubNode node : nodes) {
		// // essentially this converts any 'rd' to 'rdrw', or if 'rdrw' already then nothing is done.
		// if (ok(node.getStr(NodeProp.OBJECT_ID)) && AclService.isPublic(ms, node)) {
		// acl.makePublicAppendable(ms, node);
		// }

		// if (ThreadLocals.getDirtyNodeCount() > 200) {
		// update.saveSession(ms);
		// }

		// if (++counter % 2000 == 0) {
		// log.debug("fixShare: " + String.valueOf(counter));
		// }
		// }

		// log.debug("fixSharing completed.");
	}

	/*
	 * todo-2: need to make the system capable of doing this logic during a "Full Maintenance"
	 * operation, like right after a DB compaction etc. Also the current code just updates path ONLY if
	 * it's currently null rather than what maintenance would do which is additionally look up the
	 * parent to verify the path IS indeed the correct parent.
	 */
	public void setParentNodes(MongoSession ms) {
		// WARNING: use 'ops.stream' (findAll will be out of memory error on prod)
		// log.debug("Processing setParentNodes");
		// Iterable<SubNode> nodes = ops.findAll(SubNode.class);
		// int counter = 0;

		// for (SubNode node : nodes) {

		// // If this node is on a 'pending path' (user has never clicked 'save' to save it), then we always
		// // need to set it's parent to NULL or else it will be visible in queries we don't want to see it.
		// if (ok(node.getPath()) && node.getPath().startsWith(NodePath.PENDING_PATH + "/") &&
		// ok(node.getParent())) {
		// node.setParent(null);
		// continue;
		// }

		// // this is what the MongoListener does....
		// mongoUtil.validateParent(node, null);

		// if (ThreadLocals.getDirtyNodeCount() > 200) {
		// update.saveSession(ms);
		// }

		// if (++counter % 1000 == 0) {
		// log.debug("SPN: " + String.valueOf(counter));
		// }
		// }

		// log.debug("setParentNodes completed.");
	}

	// Alters all paths parts that are over 10 characters long, on all nodes
	public void shortenPathParts(MongoSession ms) {
		// WARNING: use 'ops.strea' (findAll will be out of memory error on prod)
		// int lenLimit = 10;
		// Iterable<SubNode> nodes = ops.findAll(SubNode.class);
		// HashMap<String, Integer> set = new HashMap<>();
		// int idx = 0;

		// for (SubNode node : nodes) {
		// StringTokenizer t = new StringTokenizer(node.getPath(), "/", false);

		// while (t.hasMoreTokens()) {
		// String part = t.nextToken().trim();
		// if (part.length() < lenLimit)
		// continue;

		// if (no(set.get(part))) {
		// Integer x = idx++;
		// set.put(part, x);
		// }
		// }
		// }

		// nodes = ops.findAll(SubNode.class);
		// int maxPathLen = 0;

		// for (SubNode node : nodes) {
		// StringTokenizer t = new StringTokenizer(node.getPath(), "/", true);
		// StringBuilder fullPath = new StringBuilder();

		// while (t.hasMoreTokens()) {
		// String part = t.nextToken().trim();

		// // if delimiter, or short parths, just take them as is
		// if (part.length() < lenLimit) {
		// fullPath.append(part);
		// }
		// // if path part find it's unique integer, and insert
		// else {
		// Integer partIdx = set.get(part);

		// // if the database changed underneath it we just take that as another new path part
		// if (no(partIdx)) {
		// partIdx = idx++;
		// set.put(part, partIdx);
		// }
		// fullPath.append(String.valueOf(partIdx));
		// }
		// }

		// // log.debug("fullPath: " + fullPath);
		// if (fullPath.length() > maxPathLen) {
		// maxPathLen = fullPath.length();
		// }
		// node.setPath(fullPath.toString());
		// ops.save(node);
		// }
		// log.debug("PATH PROCESSING DONE: maxPathLen=" + maxPathLen);
	}

	public void createAllIndexes(MongoSession ms) {
		preprocessDatabase(ms);
		log.debug("checking all indexes.");

		// DO NOT DELETE. This is able to check contstraint volations.
		// read.dumpByPropertyMatch(NodeProp.USER.s(), "adam");

		log.debug("Creating FediverseName unique index.");
		ops.indexOps(FediverseName.class).ensureIndex(new Index().on(FediverseName.NAME, Direction.ASC).unique());

		createUniqueIndex(ms, SubNode.class, SubNode.PATH);

		// Other indexes that *could* be added but we don't, just as a performance enhancer is
		// Unique node names: Key = node.owner+node.name (or just node.name for admin)
		// Unique Friends: Key = node.owner+node.friendId? (meaning only ONE Friend type node per user
		// account)

		createPartialUniqueIndex(ms, "unique-apid", SubNode.class, SubNode.PROPS + "." + NodeProp.OBJECT_ID.s());

		createPartialIndex(ms, "unique-replyto", SubNode.class, SubNode.PROPS + "." + NodeProp.INREPLYTO.s());

		createPartialUniqueIndexForType(ms, "unique-user-acct", SubNode.class, SubNode.PROPS + "." + NodeProp.USER.s(),
				NodeType.ACCOUNT.s());

		/*
		 * DO NOT DELETE: This is a good example of how to cleanup the DB of all constraint violations prior
		 * to adding some new constraint. And this one was for making sure the "UniqueFriends" Index could
		 * be built ok. You can't create such an index until violations of it are already removed.
		 */
		// delete.removeFriendConstraintViolations(ms);

		createUniqueFriendsIndex(ms);
		createUniqueNodeNameIndex(ms);

		// DO NOT DELETE
		// I had done this temporarily to fix a constraint violation
		// dropIndex(ms, SubNode.class, "unique-friends");
		// dropIndex(ms, SubNode.class, "unique-node-name");

		/*
		 * NOTE: Every non-admin owned noded must have only names that are prefixed with "UserName--" of the
		 * user. That is, prefixed by their username followed by two dashes.
		 */
		createIndex(ms, SubNode.class, SubNode.NAME);
		createIndex(ms, SubNode.class, SubNode.TYPE);

		createIndex(ms, SubNode.class, SubNode.OWNER);
		createIndex(ms, SubNode.class, SubNode.XFR);
		createIndex(ms, SubNode.class, SubNode.ORDINAL);

		createIndex(ms, SubNode.class, SubNode.MODIFY_TIME, Direction.DESC);
		createIndex(ms, SubNode.class, SubNode.CREATE_TIME, Direction.DESC);
		createTextIndexes(ms, SubNode.class);

		logIndexes(ms, SubNode.class);

		log.debug("finished checking all indexes.");
	}

	/*
	 * Creates an index which will guarantee no duplicate friends can be created for a given user. Note
	 * this one index also makes it impossible to have the same user both blocked and followed because
	 * those are both saved as FRIEND nodes on the tree and therefore would violate this constraint
	 * which is exactly what we want.
	 */
	public void createUniqueFriendsIndex(MongoSession ms) {
		log.debug("Creating unique friends index.");
		auth.requireAdmin(ms);
		String indexName = "unique-friends";

		try {
			ops.indexOps(SubNode.class).ensureIndex(//
					new Index().on(SubNode.OWNER, Direction.ASC) //
							.on(SubNode.PROPS + "." + NodeProp.USER_NODE_ID.s(), Direction.ASC) //
							.unique() //
							.named(indexName) //
							.partial(PartialIndexFilter.of(Criteria.where(SubNode.TYPE).is(NodeType.FRIEND.s()))));
		} catch (Exception e) {
			ExUtil.error(log, "Failed to create partial unique index: " + indexName, e);
		}
	}

	/* Creates an index which will guarantee no duplicate node names can exist, for any user */
	public void createUniqueNodeNameIndex(MongoSession ms) {
		log.debug("createUniqueNodeNameIndex()");
		auth.requireAdmin(ms);
		String indexName = "unique-node-name";

		try {
			ops.indexOps(SubNode.class).ensureIndex(//
					new Index().on(SubNode.OWNER, Direction.ASC) //
							.on(SubNode.NAME, Direction.ASC) //
							.unique() //
							.named(indexName) //
							.partial(PartialIndexFilter.of(Criteria.where(SubNode.NAME).gt(""))));
		} catch (Exception e) {
			ExUtil.error(log, "Failed to create partial unique index: " + indexName, e);
		}
	}

	public void dropAllIndexes(MongoSession ms) {
		log.debug("dropAllIndexes");
		auth.requireAdmin(ms);
		ops.indexOps(SubNode.class).dropAllIndexes();
	}

	public void dropIndex(MongoSession ms, Class<?> clazz, String indexName) {
		try {
			auth.requireAdmin(ms);
			log.debug("Dropping index: " + indexName);
			ops.indexOps(clazz).dropIndex(indexName);
		} catch (Exception e) {
			ExUtil.error(log, "exception in dropIndex: " + indexName, e);
		}
	}

	public void logIndexes(MongoSession ms, Class<?> clazz) {
		StringBuilder sb = new StringBuilder();
		sb.append("INDEXES LIST\n:");
		List<IndexInfo> indexes = ops.indexOps(clazz).getIndexInfo();
		for (IndexInfo idx : indexes) {
			List<IndexField> indexFields = idx.getIndexFields();
			sb.append("INDEX EXISTS: " + idx.getName() + "\n");
			for (IndexField idxField : indexFields) {
				sb.append("    " + idxField.toString() + "\n");
			}
		}
		log.debug(sb.toString());
	}

	/*
	 * WARNING: I wote this but never tested it, nor did I ever find any examples online. Ended up not
	 * needing any compound indexes (yet)
	 */
	public void createPartialUniqueIndexComp2(MongoSession ms, String name, Class<?> clazz, String property1, String property2) {
		auth.requireAdmin(ms);

		try {
			// Ensures unuque values for 'property' (but allows duplicates of nodes missing the property)
			ops.indexOps(clazz).ensureIndex(//
					new Index().on(property1, Direction.ASC) //
							.on(property2, Direction.ASC) //
							.unique() //
							.named(name) //
							// Note: also instead of exists, something like ".gt('')" would probably work too
							.partial(PartialIndexFilter.of(Criteria.where(property1).exists(true).and(property2).exists(true))));
			log.debug("Index verified: " + name);
		} catch (Exception e) {
			ExUtil.error(log, "Failed to create partial unique index: " + name, e);
		}
	}

	/*
	 * NOTE: Properties like this don't appear to be supported: "prp['ap:id'].value", but prp.apid
	 * works,
	 */
	public void createPartialIndex(MongoSession ms, String name, Class<?> clazz, String property) {
		log.debug("Ensuring partial index named: " + name);
		auth.requireAdmin(ms);

		try {
			// Ensures unque values for 'property' (but allows duplicates of nodes missing the property)
			ops.indexOps(clazz).ensureIndex(//
					new Index().on(property, Direction.ASC) //
							.named(name) //
							// Note: also instead of exists, something like ".gt('')" would probably work too
							.partial(PartialIndexFilter.of(Criteria.where(property).exists(true))));
			log.debug("Index verified: " + name);
		} catch (Exception e) {
			ExUtil.error(log, "Failed to create partial unique index: " + name, e);
		}
	}

	/*
	 * NOTE: Properties like this don't appear to be supported: "prp['ap:id'].value", but prp.apid
	 * works,
	 */
	public void createPartialUniqueIndex(MongoSession ms, String name, Class<?> clazz, String property) {
		log.debug("Ensuring unique partial index named: " + name);
		auth.requireAdmin(ms);

		try {
			// Ensures unque values for 'property' (but allows duplicates of nodes missing the property)
			ops.indexOps(clazz).ensureIndex(//
					new Index().on(property, Direction.ASC) //
							.unique() //
							.named(name) //
							// Note: also instead of exists, something like ".gt('')" would probably work too
							.partial(PartialIndexFilter.of(Criteria.where(property).exists(true))));
			log.debug("Index verified: " + name);
		} catch (Exception e) {
			ExUtil.error(log, "Failed to create partial unique index: " + name, e);
		}
	}

	public void createPartialUniqueIndexForType(MongoSession ms, String name, Class<?> clazz, String property, String type) {
		log.debug("Ensuring unique partial index (for type) named: " + name);
		auth.requireAdmin(ms);

		try {
			// Ensures unque values for 'property' (but allows duplicates of nodes missing the property)
			ops.indexOps(clazz).ensureIndex(//
					new Index().on(property, Direction.ASC) //
							.unique() //
							.named(name) //
							// Note: also instead of exists, something like ".gt('')" would probably work too
							.partial(PartialIndexFilter.of(//
									Criteria.where(SubNode.TYPE).is(type) //
											.and(property).exists(true))));
		} catch (Exception e) {
			ExUtil.error(log, "Failed to create partial unique index: " + name, e);
		}
	}

	public void createUniqueIndex(MongoSession ms, Class<?> clazz, String property) {
		log.debug("Ensuring unique index on: " + property);
		try {
			auth.requireAdmin(ms);
			ops.indexOps(clazz).ensureIndex(new Index().on(property, Direction.ASC).unique());
		} catch (Exception e) {
			ExUtil.error(log, "Failed in createUniqueIndex: " + property, e);
		}
	}

	public void createIndex(MongoSession ms, Class<?> clazz, String property) {
		log.debug("createIndex: " + property);
		try {
			auth.requireAdmin(ms);
			ops.indexOps(clazz).ensureIndex(new Index().on(property, Direction.ASC));
		} catch (Exception e) {
			ExUtil.error(log, "Failed in createIndex: " + property, e);
		}
	}

	public void createIndex(MongoSession ms, Class<?> clazz, String property, Direction dir) {
		log.debug("createIndex: " + property + " dir=" + dir);
		try {
			auth.requireAdmin(ms);
			ops.indexOps(clazz).ensureIndex(new Index().on(property, dir));
		} catch (Exception e) {
			ExUtil.error(log, "Failed in createIndex: " + property + " dir=" + dir, e);
		}
	}

	/*
	 * DO NOT DELETE.
	 * 
	 * I tried to create just ONE full text index, and i get exceptions, and even if i try to build a
	 * text index on a specific property I also get exceptions, so currently i am having to resort to
	 * using only the createTextIndexes() below which does the 'onAllFields' option which DOES work for
	 * some readonly
	 */
	// public void createUniqueTextIndex(MongoSession session, Class<?> clazz,
	// String property) {
	// requireAdmin(session);
	//
	// TextIndexDefinition textIndex = new
	// TextIndexDefinitionBuilder().onField(property).build();
	//
	// /* If mongo will not allow dupliate checks of a text index, i can simply take
	// a HASH of the
	// content text, and enforce that's unique
	// * and while i'm at it secondarily use it as a corruption check.
	// */
	// /* todo-2: haven't yet run my test case that verifies duplicate tree paths
	// are indeed
	// rejected */
	// DBObject dbo = textIndex.getIndexOptions();
	// dbo.put("unique", true);
	// dbo.put("dropDups", true);
	//
	// ops.indexOps(clazz).ensureIndex(textIndex);
	// }

	public void createTextIndexes(MongoSession ms, Class<?> clazz) {
		log.debug("creatingText Indexes.");
		auth.requireAdmin(ms);

		try {
			TextIndexDefinition textIndex = new TextIndexDefinitionBuilder()//
					// note: Switching BACK to "all fields" because of how Mastodon mangles hashtags like this:
					// "#<span>tag</span> making the only place we can find "#tag" as an actual string be inside
					// the properties array attached to each node.
					.onAllFields()

					// Using 'none' as default language allows `stop words` to be indexed, which are words usually
					// not searched for like "and, of, the, about, over" etc, however if you index without stop words
					// that also means searching for these basic words in the content fails. But if you do index them
					// (by using "none" here) then the index will be larger.
					// .withDefaultLanguage("none")

					// .onField(SubNode.CONTENT) //
					// .onField(SubNode.TAGS) //
					.build();

			ops.indexOps(clazz).ensureIndex(textIndex);
			log.debug("createTextIndex successful.");
		} catch (Exception e) {
			log.debug("createTextIndex failed.");
		}
	}

	public void dropCollection(MongoSession ms, Class<?> clazz) {
		auth.requireAdmin(ms);
		ops.dropCollection(clazz);
	}

	/*
	 * Matches all children at a path which are at exactly one level deeper into the tree than path.
	 * 
	 * In other words path '/abc/def' is a child of '/abc/' and is considered a direct child, whereas
	 * '/abc/def/ghi' is a level deeper and NOT considered a direct child of '/abc'
	 */
	public String regexDirectChildrenOfPath(String path) {
		path = XString.stripIfEndsWith(path, "/");

		// NOTES:
		// - The leftmost caret (^) matches path to first part of the string (i.e. starts with 'path')
		// - The caret inside the ([]) means "not" containing the '/' char.
		// - \\/ is basically just '/' (escaped properly)
		// - The '*' means we match the "not /" condition one or more times.

		// legacy version (asterisk ouside group)
		return "^" + Pattern.quote(path) + "\\/([^\\/])*$";

		// This version also works (node the '*' location), but testing didn't show any performance
		// difference
		// return "^" + Pattern.quote(path) + "\\/([^\\/]*)$";
	}

	/*
	 * Matches all children under path regardless of tree depth. In other words, this matches the entire
	 * subgraph under path.
	 * 
	 * In other words path '/abc/def' is a child of '/abc/' and is considered a match and ALSO
	 * '/abc/def/ghi' which is a level deeper and is also considered a match
	 */
	public String regexRecursiveChildrenOfPath(String path) {
		path = XString.stripIfEndsWith(path, "/");

		// Based on this page:
		// https://docs.mongodb.com/manual/reference/operator/query/regex/#index-use
		// It looks like this might be the best performance here:
		return "^" + Pattern.quote(path) + "\\/";

		// Legacy implementation
		// return "^" + Pattern.quote(path) + "\\/(.+)$";
	}

	public boolean isChildOf(SubNode parent, SubNode child) {
		return child.getParentPath().equals(parent.getPath());
	}

	public String regexRecursiveChildrenOfPathIncludeRoot(String path) {
		path = XString.stripIfEndsWith(path, "/");
		return "^" + Pattern.quote(path) + "\\/|^" + Pattern.quote(path) + "$";
	}

	@PerfMon(category = "mongoUtil")
	public SubNode createUser(MongoSession ms, String newUserName, String email, String password, boolean automated,
			Val<SubNode> postsNodeVal, boolean forceRemoteUser) {
		SubNode userNode = read.getUserNodeByUserName(ms, newUserName);
		if (userNode != null) {
			throw new RuntimeException("User already existed: " + newUserName);
		}

		// if (PrincipalName.ADMIN.s().equals(user)) {
		// throw new RuntimeEx("createUser should not be called fror admin
		// user.");
		// }

		auth.requireAdmin(ms);
		// todo-2: is user validated here (no invalid characters, etc. and invalid
		// flowpaths tested?)
		SubNode parentNode = newUserName.contains("@") || forceRemoteUser ? remoteUsersNode : localUsersNode;
		userNode = create.createNode(ms, parentNode, NodeType.ACCOUNT.s(), null, CreateNodeLocation.LAST, true);
		parentNode.setHasChildren(true);

		ObjectId id = new ObjectId();
		userNode.setId(id);
		userNode.setOwner(id);
		userNode.set(NodeProp.USER, newUserName);
		userNode.set(NodeProp.EMAIL, email);
		userNode.set(NodeProp.PWD_HASH, getHashOfPassword(password));
		userNode.set(NodeProp.USER_PREF_EDIT_MODE, false);
		userNode.set(NodeProp.USER_PREF_RSS_HEADINGS_ONLY, true);
		userNode.set(NodeProp.USER_PREF_SHOW_REPLIES, Boolean.TRUE);
		userNode.set(NodeProp.BIN_TOTAL, 0);
		userNode.set(NodeProp.LAST_LOGIN_TIME, 0);
		userNode.set(NodeProp.BIN_QUOTA, Const.DEFAULT_USER_QUOTA);
		userNode.set(NodeProp.ALLOWED_FEATURES, "0");

		userNode.setContent("### Account: " + newUserName);
		userNode.touch();

		if (!automated) {
			userNode.set(NodeProp.SIGNUP_PENDING, true);
		}
		update.save(ms, userNode);

		// ensure we've pre-created this node.
		SubNode postsNode = read.getUserNodeByType(ms, null, userNode, "### Posts", NodeType.POSTS.s(),
				Arrays.asList(PrivilegeType.READ.s()), NodeName.POSTS);
		if (postsNodeVal != null) {
			postsNodeVal.setVal(postsNode);
		}

		if (!nostr.isNostrUserName(newUserName)) {
			user.ensureUserHomeNodeExists(ms, newUserName, "### " + user + "'s Node", NodeType.NONE.s(), NodeName.HOME);
		}

		update.save(ms, userNode);
		return userNode;
	}

	/*
	 * Initialize admin user account credentials into repository if not yet done. This should only get
	 * triggered the first time the repository is created, the first time the app is started.
	 * 
	 * The admin node is also the repository root node, so it owns all other nodes, by the definition of
	 * they way security is inheritive.
	 */
	public void createAdminUser(MongoSession ms) {
		String adminUser = prop.getMongoAdminUserName();

		SubNode adminNode = read.getUserNodeByUserName(ms, adminUser);
		if (adminNode == null) {
			adminNode = snUtil.ensureNodeExists(ms, "/", NodePath.ROOT, null, "Root", NodeType.REPO_ROOT.s(), true, null, null);

			adminNode.set(NodeProp.USER, PrincipalName.ADMIN.s());
			adminNode.set(NodeProp.USER_PREF_EDIT_MODE, false);
			adminNode.set(NodeProp.USER_PREF_RSS_HEADINGS_ONLY, true);
			adminNode.set(NodeProp.USER_PREF_SHOW_REPLIES, Boolean.TRUE);
			update.save(ms, adminNode);

			/*
			 * If we just created this user we know the session object here won't have the adminNode id in it
			 * yet and it needs to for all subsequent operations.
			 */
			ms.setUserNodeId(adminNode.getId());
		}

		allUsersRootNode = snUtil.ensureNodeExists(ms, NodePath.ROOT_PATH, NodePath.USER, null, "Users", null, true, null, null);

		ensureUsersLocalAndRemotePath(ms);
		createPublicNodes(ms);
	}

	public void ensureUsersLocalAndRemotePath(MongoSession ms) {
		localUsersNode =
				snUtil.ensureNodeExists(ms, NodePath.USERS_PATH, NodePath.LOCAL, null, "Local Users", null, true, null, null);
		remoteUsersNode =
				snUtil.ensureNodeExists(ms, NodePath.USERS_PATH, NodePath.REMOTE, null, "Remote Users", null, true, null, null);
	}

	public void createPublicNodes(MongoSession ms) {
		log.debug("creating Public Nodes");
		Val<Boolean> created = new Val<>(Boolean.FALSE);
		SubNode publicNode =
				snUtil.ensureNodeExists(ms, NodePath.ROOT_PATH, NodePath.PUBLIC, null, "Public", null, true, null, created);

		if (created.getVal()) {
			acl.addPrivilege(ms, null, publicNode, PrincipalName.PUBLIC.s(), null, Arrays.asList(PrivilegeType.READ.s()), null);
		}
		/////////////////////////////////////////////////////////
		created = new Val<>(Boolean.FALSE);

		// create home node (admin owned node named 'home').
		snUtil.ensureNodeExists(ms, NodePath.PENDING_PATH, null, null, "Pending Edits", null, true, null, created);

		/////////////////////////////////////////////////////////
		created = new Val<>(Boolean.FALSE);

		// create home node (admin owned node named 'home').
		SubNode publicHome = snUtil.ensureNodeExists(ms, NodePath.ROOT_PATH + "/" + NodePath.PUBLIC, NodeName.HOME, NodeName.HOME,
				"Public Home", null, true, null, created);

		// make node public
		acl.addPrivilege(ms, null, publicHome, PrincipalName.PUBLIC.s(), null, Arrays.asList(PrivilegeType.READ.s()), null);

		publicHome.set(NodeProp.UNPUBLISHED, true);

		log.debug("Public Home Node exists at id: " + publicHome.getId() + " path=" + publicHome.getPath());
	}
}
