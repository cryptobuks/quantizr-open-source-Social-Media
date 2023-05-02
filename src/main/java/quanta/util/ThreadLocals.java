package quanta.util;

import java.util.HashMap;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.bson.types.ObjectId;
import lombok.extern.slf4j.Slf4j;
import quanta.config.SessionContext;
import quanta.exception.NodeAuthFailedException;
import quanta.instrument.PerfMonEvent;
import quanta.model.client.NostrUserInfo;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.response.base.ResponseBase;

/**
 * Thread Local Storage
 * 
 * All thread locals are stored in this class.
 * 
 * Note: We opt for ThreadLocals instead of a Spring Bean with Request scope, so that we can
 * decouple from Web Requests, and have these variables available on a *any* thread even if it's a
 * worker or deamon thread that isn't an actual Web Request.
 */
@Slf4j 
public class ThreadLocals {
	private static final InheritableThreadLocal<HttpServletResponse> servletResponse = new InheritableThreadLocal<>();
	private static final InheritableThreadLocal<HttpSession> httpSession = new InheritableThreadLocal<>();
	private static final InheritableThreadLocal<SessionContext> sessionContext = new InheritableThreadLocal<>();
	private static final InheritableThreadLocal<ResponseBase> response = new InheritableThreadLocal<>();
	private static final InheritableThreadLocal<MongoSession> session = new InheritableThreadLocal<>();
	private static final InheritableThreadLocal<String> reqBearerToken = new InheritableThreadLocal<>();
	private static final InheritableThreadLocal<String> reqSig = new InheritableThreadLocal<>();
	private static final InheritableThreadLocal<HashMap<String, NostrUserInfo>> newNostrUsers = new InheritableThreadLocal<>();

	/*
	 * Each thread will set this when a root event is created and any other events that get created,
	 * will be added as top level children under it. Currently we don't do a hierarchy, but just one
	 * level of containment
	 */
	private static final InheritableThreadLocal<PerfMonEvent> rootEvent = new InheritableThreadLocal<>();

	/*
	 * dirtyNodes is where we accumulate the set of nodes that will all be updated after processing is
	 * done using the api.sessionSave() call. This is a way to not have to worry about doing SAVES on
	 * every object that is touched during the processing of a thread/request.
	 */
	private static final InheritableThreadLocal<HashMap<ObjectId, SubNode>> dirtyNodes = new InheritableThreadLocal<>();

	// We judiciously add only *some* nodes to this cache that we know are safe to cache because 
	// they're able to be treated as readonly during the context of the thread.
	private static final InheritableThreadLocal<HashMap<ObjectId, SubNode>> cachedNodes = new InheritableThreadLocal<>();

	/*
	 * todo-2: This is to allow our ExportJsonService.resetNode importer to work. This is importing
	 * nodes that should be all self contained as an acyclical-directed graph (i.e. tree) and there's no
	 * risk of nodes without parents, but they MAY be out of order so that the children of some nodes
	 * may appear in the JSON being imported BEFORE their parents (which would cause the parent check to
	 * fail, up until the full node graph has been imported), and so I'm creating this hack to globally
	 * disable the check during the import only.
	 */
	private static final InheritableThreadLocal<Boolean> parentCheckEnabled = new InheritableThreadLocal<>();

	public static void removeAll() {
		httpSession.remove();
		sessionContext.remove();
		servletResponse.remove();
		response.remove();
		reqBearerToken.remove();
		reqSig.remove();
		rootEvent.remove();

		getDirtyNodes().clear();
		getCachedNodes().clear();
		getNewNostrUsers().clear();
		setParentCheckEnabled(true);
		session.remove();
	}

	public static void requireAdmin() {
		if (!getSC().isAdmin()) {
			throw ExUtil.wrapEx("admin only function.");
		}
	}

	public static void requireAdminThread() {
		MongoSession as = ThreadLocals.getMongoSession();
		if (as == null || !as.isAdmin()) {
			throw new NodeAuthFailedException();
		}
	}

	// public static ThreadLocalsContext getContext() {
	// 	// log.debug("getting context from thread: " + Thread.currentThread().getName());
	// 	ThreadLocalsContext ctx = new ThreadLocalsContext();
	// 	ctx.threadId = Thread.currentThread().getId();
	// 	ctx.httpSession = getHttpSession();
	// 	ctx.sessionContext = getSC();
	// 	return ctx;
	// }

	// public static void setContext(ThreadLocalsContext ctx) {
	// 	// log.debug("setting context into thread: " + Thread.currentThread().getName());
	// 	setHttpSession(ctx.httpSession);
	// 	if (ctx.sessionContext != null) {
	// 		setSC(ctx.sessionContext.cloneForThread());
	// 	}
	// }

	public static void setHttpSession(HttpSession session) {
		httpSession.set(session);
	}

	public static HttpSession getHttpSession() {
		return httpSession.get();
	}

	public static void setSC(SessionContext sc) {
		sessionContext.set(sc);
		initMongoSession(sc);
	}

	public static void initMongoSession(SessionContext sc) {
		MongoSession ms = new MongoSession(sc.getUserName(), sc.getUserNodeId());
		setMongoSession(ms);
	}

	/*
	 * todo-1: We need a way to detect when some code has accidentally called this from a deamon thread
	 * where there won't be any session context, rather than letting it result in a NPE that we have to
	 * trace back to this cause.
	 */
	public static SessionContext getSC() {
		return sessionContext.get();
	}

	public static void setServletResponse(HttpServletResponse res) {
		servletResponse.set(res);
	}

	public static HttpServletResponse getServletResponse() {
		return servletResponse.get();
	}

	public static void setRootEvent(PerfMonEvent res) {
		rootEvent.set(res);
	}

	public static PerfMonEvent getRootEvent() {
		return rootEvent.get();
	}

	public static void setResponse(ResponseBase res) {
		response.set(res);
	}

	public static ResponseBase getResponse() {
		return response.get();
	}

	public static void setReqBearerToken(String token) {
		reqBearerToken.set(token);
	}

	public static String getReqBearerToken() {
		return reqBearerToken.get();
	}

	public static void setReqSig(String sig) {
		reqSig.set(sig);
	}

	public static String getReqSig() {
		return reqSig.get();
	}

	public static void setParentCheckEnabled(Boolean val) {
		parentCheckEnabled.set(val);
	}

	public static Boolean getParentCheckEnabled() {
		if (parentCheckEnabled.get() == null)
			return false;
		return parentCheckEnabled.get();
	}

	public static void clearDirtyNodes() {
		getDirtyNodes().clear();
	}

	public static HashMap<ObjectId, SubNode> getDirtyNodes() {
		if (dirtyNodes.get() == null) {
			dirtyNodes.set(new HashMap<ObjectId, SubNode>());
		}
		return dirtyNodes.get();
	}

	public static void setDirtyNodes(HashMap<ObjectId, SubNode> dn) {
		dirtyNodes.set(dn);
	}

	public static HashMap<String, NostrUserInfo> getNewNostrUsers() {
		if (newNostrUsers.get() == null) {
			newNostrUsers.set(new HashMap<String, NostrUserInfo>());
		}
		return newNostrUsers.get();
	}

	public static void cacheNode(SubNode node) {
		if (node == null || node.getId() == null) {
			return;
		}

		getCachedNodes().put(node.getId(), node);
	}

	public static SubNode getCachedNode(ObjectId id) {
		return getCachedNodes().get(id);
	}

	public static HashMap<ObjectId, SubNode> getCachedNodes() {
		if (cachedNodes.get() == null) {
			cachedNodes.set(new HashMap<ObjectId, SubNode>());
		}
		return cachedNodes.get();
	}

	public static void setCachedNodes(HashMap<ObjectId, SubNode> cn) {
		cachedNodes.set(cn);
	}

	public static boolean hasDirtyNodes() {
		return getDirtyNodes().size() > 0;
	}

	public static int getDirtyNodeCount() {
		return getDirtyNodes().size();
	}

	public static boolean hasDirtyNode(ObjectId nodeId) {
		return getDirtyNodes().containsKey(nodeId);
	}

	public static void dumpDirtyNodes() {
		if (getDirtyNodes().size() == 0) {
			log.debug("No dirty nodes.");
			return;
		}

		log.debug("Dirty Nodes...");
		getDirtyNodes().forEach((key, value) -> {
			if (!key.toHexString().equals(value.getIdStr())) {
				throw new RuntimeException(
						"Node originally cached as ID " + key.toHexString() + " now has key" + value.getIdStr());
			}
			log.debug("    " + key.toHexString());
		});
	}

	/*
	 * Sets 'node' to dirty thus guaranteeing any changes made to it, even if made later on in the
	 * request, are guaranteed to be saved to the DB.
	 */
	public static void dirty(SubNode node) {
		if (node == null || node.getId() == null) {
			return;
		}

		SubNode nodeFound = getDirtyNodes().get(node.getId());

		/*
		 * If we are setting this node to dirty, but we already see another copy of the same nodeId in
		 * memory, this is a problem and will mean whichever node happens to be saved 'last' will overwrite,
		 * so this *may* result in data loss.
		 * 
		 * Normally NodeIterator.java, and all places we ready from the DB should be wrapped in a way as to
		 * let the dirty nodes be correctly referenced, so this message should never get printed.
		 */
		if (nodeFound != null) {
			// Not checking this, because it can happen in normal code flow
			// if (nodeFound.hashCode() != node.hashCode()) {
			// 	ExUtil.warn("WARNING: multiple instances of objectId " + node.getIdStr() + " are in memory.");
			// }
			return;
		}

		// log.debug("Setting Dirty: " + node.getIdStr());
		getDirtyNodes().put(node.getId(), node);
	}

	/* Opposite of dirty */
	public static void clean(SubNode node) {
		// log.debug("Removing from Dirty: " + node.getIdStr());
		getDirtyNodes().remove(node.getId());
	}


	public static void setMongoSession(MongoSession ms) {
		session.set(ms);
	}

	public static MongoSession ensure(MongoSession ms) {
		MongoSession ret = ms != null ? ms : session.get();

		// this should never happen, but if we didn't have a mongoSession here make one to return
		if (ret == null && getSC() != null) {
			ret = new MongoSession(getSC().getUserName(), getSC().getUserNodeId());
		}
		return ret;
	}

	public static MongoSession getMongoSession() {
		return session.get();
	}
}
