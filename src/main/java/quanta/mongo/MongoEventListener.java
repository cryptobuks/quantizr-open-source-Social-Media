package quanta.mongo;

import java.util.Calendar;
import java.util.Date;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterConvertEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterLoadEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.data.mongodb.core.mapping.event.BeforeDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.EventPublisher;
import quanta.actpub.ActPubCache;
import quanta.config.NodePath;
import quanta.exception.NodeAuthFailedException;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.mongo.model.SubNode;
import quanta.service.AclService;
import quanta.util.SubNodeUtil;
import quanta.util.ThreadLocals;
import quanta.util.XString;

/**
 * Listener that MongoDB driver hooks into so we can inject processing into various phases of the
 * persistence (reads/writes) of the MongoDB objects.
 * 
 * Listener Lifecycle Events:
 * 
 * onBeforeConvert: Called in MongoTemplate insert, insertList, and save operations before the
 * object is converted to a Document by a MongoConverter.
 * 
 * onBeforeSave: Called in MongoTemplate insert, insertList, and save operations before inserting or
 * saving the Document in the database.
 * 
 * onAfterSave: Called in MongoTemplate insert, insertList, and save operations after inserting or
 * saving the Document in the database.
 * 
 * onAfterLoad: Called in MongoTemplate find, findAndRemove, findOne, and getCollection methods
 * after the Document has been retrieved from the database.
 * 
 * onAfterConvert: Called in MongoTemplate find, findAndRemove, findOne, and getCollection methods
 * after the Document has been retrieved from the database was converted to a POJO.
 */
@Component
@Slf4j 
public class MongoEventListener extends AbstractMongoEventListener<SubNode> {
	private static final boolean verbose = false;

	@Autowired
	protected MongoTemplate ops;

	@Autowired
	private MongoRead read;

	@Autowired
	private MongoAuth auth;

	@Autowired
	private MongoUtil mongoUtil;

	@Autowired
	private SubNodeUtil snUtil;

	@Autowired
	private EventPublisher publisher;

	@Autowired
	private ActPubCache apCache;

	@Autowired
	private AclService acl;

	/**
	 * What we are doing in this method is assigning the ObjectId ourselves, because our path must
	 * include this id at the very end, since the path itself must be unique. So we assign this prior to
	 * persisting so that when we persist everything is perfect.
	 * 
	 * WARNING: updating properties on 'node' in here has NO EFFECT. Always update dbObj only!
	 */
	@Override
	public void onBeforeSave(BeforeSaveEvent<SubNode> event) {
		// super.onBeforeSave(event);  todo-1: this needed?
		SubNode node = event.getSource();
		log.trace("MDB save: " + node.getPath() + " thread: " + Thread.currentThread().getName());

		// log.debug("onBeforeSave: "+XString.prettyPrint(node));

		Document dbObj = event.getDocument();
		ObjectId id = node.getId();
		boolean isNew = false;

		/*
		 * Note: There's a special case in MongoApi#createUser where the new User root node ID is assigned
		 * there, along with setting that on the owner property so we can do one save and have both updated
		 */
		if (id == null) {
			id = new ObjectId();
			node.setId(id);
			isNew = true;
			// log.debug("New Node ID generated: " + id);
		}
		dbObj.put(SubNode.ID, id);

		// Extra protection to be sure accounts and repo root can't have any sharing
		if (NodeType.ACCOUNT.s().equals(node.getType()) || NodeType.REPO_ROOT.s().equals(node.getType())) {
			node.setAc(null);
			dbObj.remove(SubNode.AC);
		}

		// home nodes are always unpublished
		if ("home".equalsIgnoreCase(node.getName())) {
			node.set(NodeProp.UNPUBLISHED, true);
			dbObj.put(SubNode.PROPS, node.getProps());
		}

		if (node.getOrdinal() == null) {
			node.setOrdinal(0L);
			dbObj.put(SubNode.ORDINAL, 0L);
		}

		// log.debug("onBeforeSave: ID: " + node.getIdStr());

		// DO NOT DELETE
		/*
		 * If we ever add a unique-index for "Name" (not currently the case), then we'd need something like
		 * this to be sure each node WOULD have a unique name.
		 */
		// if (StringUtils.isEmpty(node.getName())) {
		// node.setName(id.toHexString())
		// }

		/* if no owner is assigned... */
		if (node.getOwner() == null) {
			/*
			 * if we are saving the root node, we make it be the owner of itself. This is also the admin owner,
			 * and we only allow this to run during initialiation when the server may be creating the database,
			 * and is not yet processing user requests
			 */
			if (node.getPath().equals(NodePath.ROOT_PATH) && !MongoRepository.fullInit) {
				ThreadLocals.requireAdminThread();
				dbObj.put(SubNode.OWNER, id);
				node.setOwner(id);
			} else {
				if (auth.getAdminSession() != null) {
					ObjectId ownerId = auth.getAdminSession().getUserNodeId();
					dbObj.put(SubNode.OWNER, ownerId);
					node.setOwner(ownerId);
					log.debug("Assigning admin as owner of node that had no owner (on save): " + id);
				}
			}
		}

		Date now = null;

		/* If no create/mod time has been set, then set it */
		if (node.getCreateTime() == null) {
			if (now == null) {
				now = Calendar.getInstance().getTime();
			}
			dbObj.put(SubNode.CREATE_TIME, now);
			node.setCreateTime(now);
		}

		if (node.getModifyTime() == null) {
			if (now == null) {
				now = Calendar.getInstance().getTime();
			}
			dbObj.put(SubNode.MODIFY_TIME, now);
			node.setModifyTime(now);
		}

		/*
		 * New nodes can be given a path where they will allow the ID to play the role of the leaf 'name'
		 * part of the path
		 */
		// log.debug("onBeforeSave: " + node.getPath() + " content=" + node.getContent() + " id=" +
		// node.getIdStr());
		if (node.getPath().endsWith("/?")) {
			String path = mongoUtil.findAvailablePath(XString.removeLastChar(node.getPath()));
			// log.debug("Actual Path Saved: " + path);
			dbObj.put(SubNode.PATH, path);
			node.setPath(path);
			isNew = true;
		}

		// make sure root node can never have any sharing.
		if (node.getPath().equals(NodePath.ROOT_PATH) && node.getAc() != null) {
			dbObj.put(SubNode.AC, null);
			node.setAc(null);
		}

		if (!node.getPath().startsWith(NodePath.PENDING_PATH + "/") && ThreadLocals.getParentCheckEnabled()
				&& (isNew || node.verifyParentPath)) {
			read.checkParentExists(null, node.getPath());
		}

		saveAuthByThread(node, isNew);

		/* Node name not allowed to contain : or ~ */
		String nodeName = node.getName();
		if (nodeName != null) {
			nodeName = nodeName.replace(":", "-");
			nodeName = nodeName.replace("~", "-");
			nodeName = nodeName.replace("/", "-");

			// Warning: this is not a redundant null check. Some code in this block CAN set
			// to null.
			if (nodeName != null) {
				dbObj.put(SubNode.NAME, nodeName);
				node.setName(nodeName);
			}
		}

		if (snUtil.removeDefaultProps(node)) {
			dbObj.put(SubNode.PROPS, node.getProps());
		}

		if (node.getAc() != null) {
			/*
			 * we need to ensure that we never save an empty Acl, but null instead, because some parts of the
			 * code assume that if the AC is non-null then there ARE some shares on the node.
			 * 
			 * This 'fix' only started being necessary I think once I added the safeGetAc, and that check ends
			 * up causing the AC to contain an empty object sometimes
			 */
			if (node.getAc().size() == 0) {
				node.setAc(null);
				dbObj.put(SubNode.AC, null);
			}
			// Remove any share to self because that never makes sense
			else {
				if (node.getOwner() != null) {
					if (node.getAc().remove(node.getOwner().toHexString()) != null) {
						dbObj.put(SubNode.AC, node.getAc());
					}
				}
			}
		}

		// Since we're saving this node already make sure none of our setters above left it flagged
		// as dirty or it might unnecessarily get saved twice.
		ThreadLocals.clean(node);

		// log.debug(
		// "MONGO EVENT BeforeSave: Node=" + node.getContent() + " EditMode=" +
		// node.getBool(NodeProp.USER_PREF_EDIT_MODE));
	}

	@Override
	public void onAfterSave(AfterSaveEvent<SubNode> event) {
		// super.onAfterSave(event); todo-1: this needed?
		SubNode node = event.getSource();

		// update cache during save
		if (node != null) {
			apCache.saveNotify(node);
		}

		String dbRoot = NodePath.ROOT_PATH;
		if (dbRoot.equals(node.getPath())) {
			read.setDbRoot(node);
		}

		// log.debug("MongoListener SAVED hashCode: " + node.hashCode() + "  " + XString.prettyPrint(node));
		// "\n" + ExUtil.getStackTrace(null));
	}

	@Override
	public void onAfterLoad(AfterLoadEvent<SubNode> event) {
		// super.onAfterLoad(event); todo-1: this needed?

		// Document dbObj = event.getDocument();
		// String id = dbObj.getObjectId(SubNode.ID).toHexString();
		// log.debug("onAfterLoad: id=" + id);
	}

	@Override
	public void onAfterConvert(AfterConvertEvent<SubNode> event) {
		// super.onAfterConvert(event); todo-1: this needed?
		SubNode node = event.getSource();
		// log.debug("MongoEventListener.onAfterConvert: " + node.getContent());
		if (node.getOwner() == null) {
			if (auth.getAdminSession() != null) {
				node.setOwner(auth.getAdminSession().getUserNodeId());
				log.debug("Assigning admin as owner of node that had no owner (on load): " + node.getIdStr());
			}
		}

		// Extra protection to be sure accounts and repo root can't have any sharing
		if (NodeType.ACCOUNT.s().equals(node.getType()) || NodeType.REPO_ROOT.s().equals(node.getType())) {
			node.setAc(null);
		}

		// home nodes are always unpublished
		if ("home".equalsIgnoreCase(node.getName())) {
			node.set(NodeProp.UNPUBLISHED, true);
		}

		node.fixAttachments();
		node.verifyParentPath = StringUtils.isEmpty(node.getPath());

		if (ThreadLocals.hasDirtyNode(node.getId())) {
			log.warn("DIRTY READ: " + node.getIdStr());
		}

		// log.debug("MONGO EVENT AfterConvert: Node=" + node.getContent() + " EditMode="
		// + node.getBool(NodeProp.USER_PREF_EDIT_MODE));

		// log.debug("onAfterConvert: "+XString.prettyPrint(node));
	}

	@Override
	public void onBeforeDelete(BeforeDeleteEvent<SubNode> event) {
		// super.onBeforeDelete(event); todo-1: this needed?
		if (!MongoRepository.fullInit)
			return;
		Document doc = event.getDocument();

		if (doc != null) {
			Object id = doc.get("_id");
			if (id instanceof ObjectId) {
				SubNode node = ops.findById(id, SubNode.class);
				if (node != null) {
					log.trace("MDB del: " + node.getPath());
					auth.ownerAuth(node);
					ThreadLocals.clean(node);
				}

				publisher.getPublisher().publishEvent(new MongoDeleteEvent(id));
			}
		}
	}

	/* To save a node you must own the node and have WRITE access to it's parent */
	public void saveAuthByThread(SubNode node, boolean isNew) {
		// during server init no auth is required.
		if (!MongoRepository.fullInit) {
			return;
		}
		if (node.adminUpdate)
			return;

		if (verbose)
			log.trace("saveAuth in MongoListener");

		MongoSession ms = ThreadLocals.getMongoSession();
		if (ms != null) {
			if (ms.isAdmin())
				return;

			// Must have write privileges to this node.
			auth.ownerAuth(node);

			// only if this is creating a new node do we need to check that the parent will allow it
			if (isNew) {
				SubNode parent = read.getParent(ms, node);
				if (parent == null)
					throw new RuntimeException("unable to get node parent: " + node.getParentPath());

				auth.authForChildNodeCreate(ms, parent);
				if (acl.isAdminOwned(parent) && !ms.isAdmin()) {
					throw new NodeAuthFailedException();
				}
			}
		}
	}
}
