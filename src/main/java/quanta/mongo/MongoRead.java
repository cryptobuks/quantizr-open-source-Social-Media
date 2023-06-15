package quanta.mongo;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.CriteriaDefinition;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Component;
import quanta.config.NodeName;
import quanta.config.NodePath;
import quanta.config.ServiceBase;
import quanta.exception.base.RuntimeEx;
import quanta.instrument.PerfMon;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.model.client.PrincipalName;
import quanta.model.client.PrivilegeType;
import quanta.mongo.model.SubNode;
import quanta.service.SystemService;
import quanta.util.ThreadLocals;
import quanta.util.TreeNode;
import quanta.util.Util;
import quanta.util.XString;
import quanta.util.val.Val;

/**
 * Performs the 'create' (as in CRUD) operations for creating new nodes in MongoDB
 * <p>
 * There are many more opportunities in this class to use the ThreadLocals.nodeCache to store
 * information in the thread for use during context of one call
 */
@Component
public class MongoRead extends ServiceBase {

    private static Logger log = LoggerFactory.getLogger(MongoRead.class);
    private static final Object dbRootsLock = new Object();
    private SubNode dbRoot;
    public static Sort ordinalSort = Sort.by(Sort.Direction.ASC, SubNode.ORDINAL);

    // we call this during app init so we don't need to have thread safety here the rest of the time.
    public SubNode getDbRoot() {
        synchronized (dbRootsLock) {
            if (dbRoot == null) {
                dbRoot = findNodeByPath(null, NodePath.ROOT_PATH);
            }
            return dbRoot;
        }
    }

    public SubNode setDbRoot(SubNode node) {
        synchronized (dbRootsLock) {
            dbRoot = node;
            return dbRoot;
        }
    }

    /**
     * Gets account name from the root node associated with whoever owns 'node'
     */
    public String getNodeOwner(MongoSession ms, SubNode node) {
        if (node.getOwner() == null) {
            throw new RuntimeEx("Node has null owner: " + XString.prettyPrint(node));
        }
        SubNode userNode = getNode(ms, node.getOwner());
        return userNode.getStr(NodeProp.USER);
    }

    // Used this for troubleshooting a constraint violation on one of the partial indexes
    public void dumpByPropertyMatch(String prop, String val) {
        log.debug("Dump for: prop " + prop + "=" + val);
        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PROPS + "." + prop).is(val);
        q.addCriteria(crit);
        Iterable<SubNode> iter = opsw.find(null, q);

        for (SubNode node : iter) {
            log.debug("NODE: " + XString.prettyPrint(node));
        }
    }

    public String getParentPath(SubNode node) {
        return XString.truncAfterLast(node.getPath(), "/");
    }

    public long getChildCount(MongoSession ms, SubNode node) {
        return getChildCount(ms, node.getPath());
    }

    /*
     * returns true only if node path is KNOWN (by hasChildren at least), not to have any children.
     *
     * Beware: A false return tells us NOTHING. No gained knowledge.
     */
    public boolean noChildren(SubNode node) {
        if (SubNode.USE_HAS_CHILDREN && node != null && node.getHasChildren() != null && !node.getHasChildren().booleanValue()) {
            return true;
        }
        return false;
    }

    /*
     * returns true only if node path is KNOWN (by hasChildren at least), not to have any children.
     *
     * Beware: A false return tells us NOTHING. No gained knowledge.
     */
    public boolean noChildren(MongoSession ms, String path) {
        if (SubNode.USE_HAS_CHILDREN) {
            SubNode node = getNode(ms, path, false, null);
            // using booleanValue for clarity
            if (node != null && node.getHasChildren() != null && !node.getHasChildren().booleanValue()) {
                return true;
            }
        }
        return false;
    }

    public long getChildCount(MongoSession ms, String path) {
        // statistically I think it pays off to always try the faster way and then assume worst case is that
        // we might have warmed up the MongoDb for what the following query will need.
        if (noChildren(ms, path)) return 0;
        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(path));
        q.addCriteria(crit);
        return opsw.count(ms, q);
    }

    /*
     * we only update the hasChildren if doAuth is false, because doAuth would be a person-specific
     * exact query, unlike what the hasChildren represents which is a global
     * "any children visible to any user" thing.
     */
    public boolean hasChildren(MongoSession ms, SubNode node, boolean doAuth, boolean allowUpdate) {
        // cannot both be true
        if (doAuth && allowUpdate) {
            throw new RuntimeException("doAuth and allowUpdate are mutually exclusive.");
        }

        if (ThreadLocals.getSC() != null && ThreadLocals.getSC().readFromAdminCache()) {
            synchronized (SystemService.adminNodesCacheLock) {
                TreeNode tn = system.adminNodesCacheMap.get(node.getIdStr());
                if (tn != null) {
                    return tn.children != null;
                }
            }
        }

        // if the node knows it's children status (non-null value) return that.
        if (SubNode.USE_HAS_CHILDREN && !doAuth && node.getHasChildren() != null) {
            // calling booleanValue for clarity
            return node.getHasChildren().booleanValue();
        }
        boolean ret = hasChildrenByQuery(ms, node.getPath(), doAuth);
        if (!doAuth && allowUpdate) {
            node.setHasChildren(ret);
        }
        return ret;
    }

    public boolean hasChildrenByQuery(MongoSession ms, String path, boolean doAuth) {
        // WARNING: Leave this as a note to NOT call this optimization here. It is definitely
        // counter-productive.
        // if (noChildren(ms, path)) return false;
        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(path));
        q.addCriteria(crit);
        if (doAuth) {
            return opsw.exists(ms, q);
        } else {
            return ops.exists(q, SubNode.class);
        }
    }

    public long getNodeCount() {
        Query q = new Query();
        return opsw.count(null, q);
    }

    /* If this 'path' is known to exist and never needs to be validated, return true */
    public boolean knownPath(String path) {
        // if this is a path we KNOW exists, return false
        if (
            path == null ||
            path.length() == 0 ||
            !path.contains("/") ||
            path.equals(NodePath.PENDING_PATH) ||
            path.equals(NodePath.ROOT_PATH) ||
            path.equals(NodePath.USERS_PATH) ||
            path.equals(NodePath.LOCAL_USERS_PATH) ||
            path.equals(NodePath.REMOTE_USERS_PATH)
        ) {
            return true;
        }
        return false;
    }

    /* Checks ALL parent paths to make sure they all exist. Returns true of some parent doesn't exist */
    public boolean isOrphan(String path) {
        int sanityCheck = 0;
        // if this is a path we KNOW exists, return false
        if (knownPath(path)) return false;

        while (sanityCheck++ < 1000) {
            // get parent path
            path = XString.truncAfterLast(path, "/");
            if (knownPath(path)) return false;
            // if the parent path does not exist, we're done. Path passed in is an orphan.
            if (!pathExists(path)) {
                return true;
            }
        }
        // If we get here this is definitely a bug.
        throw new RuntimeException("isOrphan algo failure.");
    }

    /*
     * Throws an exception if the parent Node of 'path' does not exist.
     */
    public void checkParentExists(MongoSession ms, String path) {
        boolean isRootPath = mongoUtil.isRootPath(path);
        if (isRootPath) return;
        String parentPath = XString.truncAfterLast(path, "/");
        if (
            parentPath == null ||
            parentPath.equals("") ||
            parentPath.equals("/") ||
            parentPath.equals(NodePath.ROOT_PATH) ||
            parentPath.equals(NodePath.PENDING_PATH) ||
            parentPath.equals(NodePath.PENDING_PATH + "/")
        ) return;
        if (parentPath.startsWith(NodePath.PENDING_PATH + "/")) {
            parentPath = parentPath.replace(NodePath.PENDING_PATH + "/", NodePath.ROOT_PATH + "/");
        }
        // no need to check root.
        if (parentPath.equals(NodePath.ROOT_PATH) || parentPath.equals(NodePath.ROOT_PATH + "/")) {
            return;
        }
        // no need to check USERS
        if (parentPath.equals(NodePath.USERS_PATH) || parentPath.equals(NodePath.USERS_PATH + "/")) {
            return;
        }
        // no need to check REMOTE USERS
        if (parentPath.equals(NodePath.REMOTE_USERS_PATH) || parentPath.equals(NodePath.REMOTE_USERS_PATH + "/")) {
            return;
        }
        // no need to check LOCAL USERS
        if (parentPath.equals(NodePath.LOCAL_USERS_PATH) || parentPath.equals(NodePath.LOCAL_USERS_PATH + "/")) {
            return;
        }
        if (!pathExists(parentPath)) {
            throw new RuntimeEx("Attempted to add a node before its parent exists:" + parentPath);
        }
    }

    public SubNode getNodeByName(MongoSession ms, String name) {
        return getNodeByName(ms, name, true, null);
    }

    /*
     * The name can have either of two different formats:
     *
     * 1) "nodeName" (admin owned node)
     *
     * 2) "userName:nodeName" (a named node some user has created)
     */
    public SubNode getNodeByName(MongoSession ms, String name, boolean allowAuth, Val<SubNode> accntNode) {
        Query q = new Query();
        if (name == null) return null;
        // we tolerate a prefix at the FRONT of either format 1, or 2, and ignore it.
        name = XString.stripIfStartsWith(name, ":");
        ObjectId nodeOwnerId;
        int colonIdx = -1;
        SubNode userNode = null;
        /*
         * if 'name' doesn't contain a colon it's known to be just an admin-owned global named node without
         * a user prefix
         */
        if ((colonIdx = name.indexOf(":")) == -1) {
            nodeOwnerId = getDbRoot().getOwner();
        } else /*
         * If there is a colon in the name then it's of the format 'userName:nodeName'
         */{
            String userName = name.substring(0, colonIdx);
            /*
             * pass a null session here to cause adminSession to be used which is required to get a user node,
             * but it always safe to get this node this way here.
             */
            userNode = getUserNodeByUserName(allowAuth ? ms : null, userName);
            if (userNode == null) {
                log.debug("Unable to find node by: " + name);
                return null;
            }
            // set output parameter if we have one
            if (accntNode != null) {
                accntNode.setVal(userNode);
            }
            nodeOwnerId = userNode.getOwner();
            name = name.substring(colonIdx + 1);
        }
        q.addCriteria(Criteria.where(SubNode.NAME).is(name).and(SubNode.OWNER).is(nodeOwnerId));
        return opsw.findOne(allowAuth ? ms : null, q);
    }

    public SubNode getNode(MongoSession ms, String identifier) {
        // calling thru proxy for AOP here.
        return getNode(ms, identifier, true, null);
    }

    public SubNode getNode(MongoSession ms, String identifier, boolean allowAuth) {
        // calling thru proxy for AOP here.
        return getNode(ms, identifier, allowAuth, null);
    }

    /**
     * Gets a node using any of the 5 naming types:
     *
     * <pre>
     * 1) ID (hex string, no special prefix)
     * 2) path (starts with slash),
     * 3) global name (name of admin owned node, starts with colon, and only contains one colon)
     * 4) name of user owned node formatted as (":userName:nodeName")
     * 5) name of an admin-owned node formatted as ":nodeName"
     * 6) special named location, like '~sn:inbox', or '~sn:friendList' (starts with tilde)
     *    (we support just '~inbox' also as a type shorthand where the sn: is missing)
     * 7) starts with '.' indicates it's a nostrId
     * </pre>
     */
    public SubNode getNode(MongoSession ms, String identifier, boolean allowAuth, Val<SubNode> accntNode) {
        if (identifier == null) return null;
        if (identifier.equals("/")) {
            throw new RuntimeEx(
                "SubNode doesn't implement the root node. Root is implicit and never needs an actual node to represent it."
            );
        }
        SubNode ret = null;
        if (identifier.startsWith(".")) {
            ret = nostr.getNodeByNostrId(ms, identifier, allowAuth);
        } //
        else if (identifier.startsWith("~")) {
            String typeName = identifier.substring(1);
            if (!typeName.startsWith("sn:")) {
                typeName = "sn:" + typeName;
            }
            ret = getUserNodeByType(ms, ms.getUserName(), null, null, typeName, null, null);
        } //
        else if (identifier.startsWith(":")) { // Node name lookups are done by prefixing the search with a colon (:)
            ret = getNodeByName(ms, identifier.substring(1), allowAuth, accntNode);
        } //
        else if (!identifier.startsWith("/")) { // else if search doesn't start with a slash then it's a nodeId and not a path
            if (ThreadLocals.getSC() != null && ThreadLocals.getSC().readFromAdminCache()) {
                synchronized (SystemService.adminNodesCacheLock) {
                    TreeNode tn = system.adminNodesCacheMap.get(identifier);
                    if (tn != null) {
                        ret = tn.node;
                    }
                }
            }

            if (ret == null) {
                ret = opsw.findById(allowAuth ? ms : null, new ObjectId(identifier));
            }
        } else { // otherwise this is a path lookup
            ret = findNodeByPath(ms, identifier);
        }
        return ret;
    }

    public SubNode findNodeByPath(MongoSession ms, String path) {
        path = XString.stripIfEndsWith(path, "/");
        Query q = new Query();
        q.addCriteria(Criteria.where(SubNode.PATH).is(path));
        return opsw.findOne(ms, q);
    }

    public boolean pathExists(String path) {
        path = XString.stripIfEndsWith(path, "/");
        Query q = new Query();
        q.addCriteria(Criteria.where(SubNode.PATH).is(path));
        return ops.exists(q, SubNode.class);
    }

    public boolean nodeExists(MongoSession ms, ObjectId id) {
        Query q = new Query();
        q.addCriteria(Criteria.where(SubNode.ID).is(id));
        return ops.exists(q, SubNode.class);
    }

    public SubNode getNode(MongoSession ms, ObjectId objId) {
        return opsw.findById(ms, objId);
    }

    public SubNode getOwner(MongoSession ms, SubNode node, boolean allowAuth) {
        if (node == null) return null;
        return opsw.findById(allowAuth ? ms : null, node.getOwner());
    }

    /*
     * todo-2: Need to implement a save hook/callback capability in the MongoListener so we can get
     * notifications sent to any threads that are waiting to lookup a node once it exists, but we will
     * STILL probably need to DO the lookup so we don't have concurrent access threading bug.
     */
    public SubNode getNode(MongoSession ms, ObjectId objId, boolean allowAuth, int retries) {
        SubNode ret = opsw.findById(allowAuth ? ms : null, objId);

        while (ret == null && retries-- > 0) {
            Util.sleep(3000);
            ret = opsw.findById(allowAuth ? ms : null, objId);
        }
        return ret;
    }

    public SubNode getParent(MongoSession ms, SubNode node) {
        if (ThreadLocals.getSC() != null && ThreadLocals.getSC().readFromAdminCache()) {
            synchronized (SystemService.adminNodesCacheLock) {
                TreeNode tn = system.adminNodesCacheMap.get(node.getIdStr());
                /*
                 * Note this parent check for null here is consistent with the fact that node we might have just
                 * found in the cache might be the root of the cache tree which doesn't keep track of what it's
                 * parent is, so we can only return here in cases where we did find a parent
                 */
                if (tn != null && tn.parent != null) {
                    return tn.parent.node;
                }
            }
        }

        return getParent(ms, node, true);
    }

    public SubNode getParent(MongoSession ms, SubNode node, boolean allowAuth) {
        return getParentByPath(ms, node.getPath(), allowAuth);
    }

    /*
     * WARNING: This always converts a 'pending' path to a non-pending one (/r/p/ v.s. /r/)
     */
    public SubNode getParentByPath(MongoSession ms, String path, boolean allowAuth) {
        if ("/".equals(path)) {
            return null;
        }
        String parentPath = XString.truncAfterLast(path, "/");
        if (StringUtils.isEmpty(parentPath)) return null;
        String pendingPath = NodePath.PENDING_PATH + "/";
        String rootPath = NodePath.ROOT_PATH + "/";
        /*
         * If node is in pending area take the pending part out of the path to get the real parent
         */
        parentPath = parentPath.replace(pendingPath, rootPath);
        return getNode(ms, parentPath, allowAuth, null);
    }

    public List<String> getChildrenIds(MongoSession ms, SubNode node, boolean ordered, Integer limit) {
        if (noChildren(node)) {
            return Collections.<String>emptyList();
        }
        auth.auth(ms, node, PrivilegeType.READ);
        Query q = new Query();
        if (limit != null) {
            q.limit(limit.intValue());
        }
        /*
         * This regex finds all that START WITH "path/" and then end with some other string that does NOT
         * contain "/", so that we know it's not at a deeper level of the tree, but is immediate children of
         * 'node'
         *
         * ^:aa:bb:([^:])*$
         *
         * example: To find all DIRECT children (non-recursive) under path /aa/bb regex is
         * ^\/aa\/bb\/([^\/])*$ (Note that in the java string the \ becomes \\ below...)
         *
         */
        Criteria crit = Criteria
            .where(SubNode.PATH)
            .regex(mongoUtil.regexDirectChildrenOfPath(node == null ? "" : node.getPath()));
        if (ordered) {
            q.with(Sort.by(Sort.Direction.ASC, SubNode.ORDINAL));
        }
        q.addCriteria(crit);

        Iterable<SubNode> iter = opsw.find(ms, q);
        List<String> nodeIds = new LinkedList<>();

        for (SubNode n : iter) {
            nodeIds.add(n.getIdStr());
        }
        return nodeIds;
    }

    /*
     * If node is null it's path is considered empty string, and it represents the 'root' of the tree.
     * There is no actual NODE that is root node.
     */
    public Iterable<SubNode> getChildren(
        MongoSession ms,
        String path,
        Sort sort,
        Integer limit,
        int skip,
        TextCriteria textCriteria,
        Criteria moreCriteria,
        boolean preCheck
    ) {
        if (preCheck && noChildren(ms, path)) {
            return Collections.<SubNode>emptyList();
        }
        Query q = new Query();
        if (limit != null && limit.intValue() > 0) {
            q.limit(limit.intValue());
        }
        if (skip > 0) {
            q.skip(skip);
        }
        /*
         * This regex finds all that START WITH "path/" and then end with some other string that does NOT
         * contain "/", so that we know it's not at a deeper level of the tree, but is immediate children of
         * 'node'
         *
         * ^:aa:bb:([^:])*$
         *
         * example: To find all DIRECT children (non-recursive) under path /aa/bb regex is
         * ^\/aa\/bb\/([^\/])*$ (Note that in the java string the \ becomes \\ below...)
         *
         */
        Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(path));
        if (textCriteria != null) {
            q.addCriteria(textCriteria);
        }
        if (moreCriteria != null) {
            q.addCriteria(moreCriteria);
        }
        if (sort != null) {
            q.with(sort);
        }
        q.addCriteria(crit);

        return opsw.find(ms, q);
    }

    public Iterable<SubNode> getChildren(MongoSession ms, SubNode node, Sort sort, Integer limit, int skip) {
        return getChildren(ms, node, sort, limit, skip, null);
    }

    /*
     * If node is null it's path is considered empty string, and it represents the 'root' of the tree.
     * There is no actual NODE that is root node
     */
    public Iterable<SubNode> getChildren(
        MongoSession ms,
        SubNode node,
        Sort sort,
        Integer limit,
        int skip,
        Criteria moreCriteria
    ) {
        if (noChildren(node)) {
            return Collections.<SubNode>emptyList();
        }

        if (
            ThreadLocals.getSC() != null &&
            ThreadLocals.getSC().readFromAdminCache() &&
            moreCriteria == null && //
            sort != null &&
            ordinalSort.equals(sort)
        ) {
            TreeNode tn = null;
            synchronized (SystemService.adminNodesCacheLock) {
                tn = system.adminNodesCacheMap.get(node.getIdStr());
            }

            if (tn != null) {
                if (tn.children == null) {
                    return Collections.<SubNode>emptyList();
                } else {
                    List<SubNode> ret = new LinkedList<>();
                    if (skip < 0) skip = 0;
                    int idx = 0;

                    for (TreeNode c : tn.children) {
                        if (skip > 0) {
                            skip--;
                            continue;
                        }
                        if (limit <= 0 || idx++ < limit) {
                            ret.add(c.node);
                        }
                    }
                    return ret;
                }
            }
        }

        auth.auth(ms, node, PrivilegeType.READ);
        return getChildren(ms, node.getPath(), sort, limit, skip, null, moreCriteria, false);
    }

    public Iterable<SubNode> getChildren(MongoSession ms, SubNode node) {
        return getChildren(ms, node, null, null, 0);
    }

    /*
     * All we need to do here is query for children an do a "max(ordinal)" operation on that, but
     * digging the information off the web for how to do this appears to be something that may take a
     * few hours so i'm skipping it for now and just doing an inverse sort on ORDER and pulling off the
     * top one and using that for my MAX operation. AFAIK this might even be the most efficient
     * approach. Who knows.
     */
    public Long getMaxChildOrdinal(MongoSession ms, SubNode node) {
        // Do not delete this commented stuff. Can be helpful to get aggregates
        // working.
        // MatchOperation match = new
        // MatchOperation(Criteria.where("quantity").gt(quantity));
        // GroupOperation group =
        // Aggregation.group("giftCard").sum("giftCard").as("count");
        // Aggregation aggregate = Aggregation.newAggregation(match, group);
        // Order is deprecated
        // AggregationResults<Order> orderAggregate = ops.aggregate(aggregate, "order",
        // Order.class);
        // Aggregation agg = Aggregation.newAggregation(//
        // Aggregation.match(Criteria.where("quantity").gt(1)), //
        // Aggregation.group(SubNode.FIELD_ORDINAL).max().as("count"));
        //
        // AggregationResults<SubNode> results = ops.aggregate(agg, "order",
        // SubNode.class);
        // List<SubNode> orderCount = results.getMappedResults();
        if (noChildren(node)) return 0L;
        auth.auth(ms, node, PrivilegeType.READ);
        // todo-2: research if there's a way to query for just one, rather than simply
        // calling findOne at the end? What's best practice here?
        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(node.getPath()));
        q.with(Sort.by(Sort.Direction.DESC, SubNode.ORDINAL));
        q.addCriteria(crit);
        q.limit(1);
        SubNode nodeFound = opsw.findOne(ms, q);
        if (nodeFound == null) {
            return 0L;
        }
        return nodeFound.getOrdinal();
    }

    // this is NOT returning the first node with the least ordinal!!!
    public Long getMinChildOrdinal(MongoSession ms, SubNode node) {
        if (noChildren(node)) return 0L;
        auth.auth(ms, node, PrivilegeType.READ);
        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(node.getPath()));
        q.with(Sort.by(Sort.Direction.ASC, SubNode.ORDINAL));
        q.addCriteria(crit);
        q.limit(1);
        SubNode nodeFound = opsw.findOne(null, q);
        if (nodeFound == null) {
            return 0L;
        }
        return nodeFound.getOrdinal();
    }

    // if 'parent' of 'node' is known it should be passed in, or else null passed in, and parent will be
    // looked up instead
    public SubNode getSiblingAbove(MongoSession ms, SubNode node, SubNode parent) {
        if (parent == null) {
            parent = getParent(ms, node);
        }
        if (parent == null || noChildren(parent)) return null;
        auth.auth(ms, node, PrivilegeType.READ);
        if (node.getOrdinal() == null) {
            node.setOrdinal(0L);
        }
        // todo-2: research if there's a way to query for just one, rather than simply
        // calling findOne at the end? What's best practice here?
        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(node.getParentPath()));
        q.with(Sort.by(Sort.Direction.DESC, SubNode.ORDINAL));

        q.addCriteria(crit);

        // leave this example. you can do a RANGE like this.
        // query.addCriteria(Criteria.where(SubNode.FIELD_ORDINAL).lt(50).gt(20));
        q.addCriteria(Criteria.where(SubNode.ORDINAL).lt(node.getOrdinal()));
        q.limit(1);
        return opsw.findOne(null, q);
    }

    // if 'parent' of 'node' is known it should be passed in, or else null passed in, and parent will be
    // looked up instead
    public SubNode getSiblingBelow(MongoSession ms, SubNode node, SubNode parent) {
        if (parent == null) {
            parent = getParent(ms, node);
        }
        if (parent == null || noChildren(parent)) return null;
        auth.auth(ms, node, PrivilegeType.READ);
        if (node.getOrdinal() == null) {
            node.setOrdinal(0L);
        }
        // todo-2: research if there's a way to query for just one, rather than simply
        // calling findOne at the end? What's best practice here?
        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(node.getParentPath()));
        q.with(Sort.by(Sort.Direction.ASC, SubNode.ORDINAL));
        q.addCriteria(crit);

        // leave this example. you can do a RANGE like this.
        // query.addCriteria(Criteria.where(SubNode.FIELD_ORDINAL).lt(50).gt(20));
        q.addCriteria(Criteria.where(SubNode.ORDINAL).gt(node.getOrdinal()));
        q.limit(1);
        return opsw.findOne(null, q);
    }

    /*
     * Gets (recursively) all nodes under 'node', by using all paths starting with the path of that node
     */
    public Iterable<SubNode> getSubGraph(
        MongoSession ms,
        SubNode node,
        Sort sort,
        int limit,
        boolean publicOnly,
        boolean doAuth,
        Criteria moreCriteria
    ) {
        if (noChildren(node)) {
            return Collections.<SubNode>emptyList();
        }

        if (doAuth) {
            auth.auth(ms, node, PrivilegeType.READ);
        }
        Query q = new Query();
        /*
         * This regex finds all that START WITH path, have some characters after path, before the end of the
         * string. Without the trailing (.+)$ we would be including the node itself in addition to all its
         * children.
         */
        Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexRecursiveChildrenOfPath(node.getPath()));
        if (publicOnly) {
            crit = crit.and(SubNode.AC + "." + PrincipalName.PUBLIC.s()).ne(null);
        }

        q.addCriteria(crit);

        if (moreCriteria != null) {
            q.addCriteria(moreCriteria);
        }

        if (sort != null) {
            q.with(sort);
        }
        if (limit > 0) {
            q.limit(limit);
        }
        return opsw.find(doAuth ? ms : null, q);
    }

    /**
     * prop is optional and if non-null means we should search only that one field.
     *
     * timeRangeType: futureOnly, pastOnly, all
     */
    public Iterable<SubNode> searchSubGraph(
        MongoSession ms,
        SubNode node,
        String prop,
        String text,
        String sortField,
        String sortDir,
        int limit,
        int skip,
        boolean fuzzy,
        boolean caseSensitive,
        String timeRangeType,
        boolean recursive,
        boolean requirePriority,
        boolean requireAttachment
    ) {
        if (noChildren(node)) {
            return Collections.<SubNode>emptyList();
        }
        auth.auth(ms, node, PrivilegeType.READ);
        List<CriteriaDefinition> criterias = new LinkedList<>();
        Sort sort = null;
        /*
         * This regex finds all that START WITH path, have some characters after path, before the end of the
         * string. Without the trailing (.+)$ we would be including the node itself in addition to all its
         * children.
         */
        Criteria crit = null;
        if (recursive) {
            crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexRecursiveChildrenOfPath(node.getPath())); //
        } else {
            crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(node.getPath()));
        }

        criterias.add(crit);

        if (!StringUtils.isEmpty(text)) {
            if (fuzzy) {
                if (StringUtils.isEmpty(prop)) {
                    prop = SubNode.CONTENT;
                }
                if (caseSensitive) {
                    criterias.add(Criteria.where(prop).regex(text));
                } else {
                    // i==insensitive (case)
                    criterias.add(Criteria.where(prop).regex(text, "i"));
                }
            } else {
                // todo-1: take another look at these to see if any can be useful for more powerful searching.
                // .matchingAny("search term1", "search term2")
                // .matching("search term") // matches any that contain "search" OR "term"
                // .matchingPhrase("search term")
                TextCriteria textCriteria = TextCriteria.forDefaultLanguage();
                /*
                 * If searching for a pure tag name or a username (no spaces in search string), be smart enough to
                 * enclose it in quotes for user, because if we don't then searches for "#mytag" WILL end up finding
                 * also just instances of mytag (not a tag) which is incorrect.
                 */
                if ((text.startsWith("#") || text.startsWith("@")) && !text.contains(" ")) {
                    text = "\"" + text + "\"";
                }
                /*
                 * his reurns ONLY nodes containing BOTH (not any) #tag1 and #tag2 so this is definitely a MongoDb
                 * bug. (or a Lucene bug possibly to be exact), so I've confirmed it's basically impossible to do an
                 * OR search on strings containing special characters, without the special characters basically
                 * being ignored.
                 *
                 * textCriteria.matchingAny("\"#tag1\"", "\"#tag2\"");
                 */
                textCriteria.matching(text);
                textCriteria.caseSensitive(caseSensitive);
                criterias.add(textCriteria);
            }
        }

        if (requirePriority) {
            criterias.add(Criteria.where(SubNode.PROPS + ".priority").gt("0"));
        }
        if (requireAttachment) {
            criterias.add(Criteria.where(SubNode.ATTACHMENTS).ne(null));
        }

        if (!StringUtils.isEmpty(sortField)) {
            if ((SubNode.PROPS + ".date").equals(sortField) && timeRangeType != null) {
                sortDir = "DESC";
                // example date RANGE condition:
                // query.addCriteria(Criteria.where("startDate").gte(startDate).lt(endDate));
                // and this 'may' be the same:
                // Query q = new
                // Query(Criteria.where("ip").is(ip).andOperator(Criteria.where("createdDate").lt(endDate),
                // Criteria.where("createdDate").gte(startDate)));
                if ("futureOnly".equals(timeRangeType)) {
                    // because we want to show the soonest items on top, for "future" query, we have
                    // to sort in order (not rev-chron)
                    sortDir = "ASC";
                    criterias.add(Criteria.where(sortField).gt(new Date().getTime()));
                } //
                else if ("pastOnly".equals(timeRangeType)) { //
                    criterias.add(Criteria.where(sortField).lt(new Date().getTime()));
                } //
                else if ("all".equals(timeRangeType)) { // prop on the node // if showing all dates the condition here is that there at least IS a 'date'
                    criterias.add(Criteria.where(sortField).ne(null));
                }
            }
            if (!StringUtils.isEmpty(sortField)) {
                if ("contentLength".equals(sortField)) {
                    sortDir = "ASC";
                }
                sort =
                    Sort.by(
                        (sortDir != null && sortDir.equalsIgnoreCase("DESC")) ? Sort.Direction.DESC : Sort.Direction.ASC,
                        sortField
                    );
            }
        }

        /*
         * We support the special case of "contentLength" as sort order string, which is not a "real"
         * property, but a calculated one.
         *
         * For a calculated field we do an Aggregate Query operation. The purpose of this entire Aggregation
         * is to calculate contentLength on the fly so we can sort on it.
         */
        if ("contentLength".equals(sortField)) {
            if (!ms.isAdmin()) {
                Criteria secCrit = auth.getSecurity(ms);
                if (secCrit != null) {
                    criterias.add(secCrit);
                }
            }
            List<AggregationOperation> aggOps = new LinkedList<>();
            /*
             * MongoDB requires any TextCriteria (full-text search) to be the first op in the pipeline so we
             * process it first here
             */
            for (CriteriaDefinition c : criterias) {
                if (c instanceof TextCriteria) {
                    aggOps.add(Aggregation.match(c));
                }
            }
            /* Next we process all non-TextCriteria conditions */
            for (CriteriaDefinition c : criterias) {
                if (!(c instanceof TextCriteria)) {
                    aggOps.add(Aggregation.match(c));
                }
            }
            // calculate contentLength
            aggOps.add( //
                Aggregation.project().andInclude(SubNode.ALL_FIELDS).andExpression("strLenCP(cont)").as("contentLength")
            );
            /*
             * IMPORTANT: Having 'sort' before 'skip' and 'limit' is REQUIRED to get correct behavior, because
             * with aggregates we doing a step by step pipeline of processing so we need records in the correct
             * order before we do limit or skip and so the ordering of these 'ops' does that.
             */
            aggOps.add(Aggregation.sort(sort));
            aggOps.add(Aggregation.skip((long) skip));
            aggOps.add(Aggregation.limit(limit));
            Aggregation agg = Aggregation.newAggregation(aggOps);
            AggregationResults<SubNode> results = ops.aggregate(agg, SubNode.class, SubNode.class);
            return results.getMappedResults();
        } else { // Otherwise a standard Query.
            Query q = new Query();

            for (CriteriaDefinition c : criterias) {
                q.addCriteria(c);
            }
            if (sort != null) {
                q.with(sort);
            }
            if (limit > 0) {
                q.limit(limit);
            }
            if (skip > 0) {
                q.skip(skip);
            }
            return opsw.find(ms, q);
        }
    }

    /**
     * Special purpose query to get all nodes that have a "date" property.
     */
    public Iterable<SubNode> getCalendar(MongoSession ms, SubNode node) {
        if (noChildren(node)) {
            return Collections.<SubNode>emptyList();
        }
        auth.auth(ms, node, PrivilegeType.READ);
        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexRecursiveChildrenOfPath(node.getPath()));
        /*
         * this mod time condition is simply to be sure the user has 'saved' the node and not pick up new
         * node currently being crafted
         */
        crit = crit.and(SubNode.MODIFY_TIME).ne(null);
        q.addCriteria(crit);

        q.addCriteria(Criteria.where(SubNode.PROPS + "." + NodeProp.DATE).ne(null));
        return opsw.find(ms, q);
    }

    /*
     * todo-2: This is very low hanging fruit to make this a feature on the Search menu. In other words
     * implementing an "All Named Nodes" search would be trivial with this.
     */
    public Iterable<SubNode> getNamedNodes(MongoSession ms, SubNode node) {
        if (noChildren(node)) {
            return Collections.<SubNode>emptyList();
        }
        auth.auth(ms, node, PrivilegeType.READ);
        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexRecursiveChildrenOfPath(node.getPath()));
        crit = crit.and(SubNode.NAME).ne(null);
        q.addCriteria(crit);
        return opsw.find(ms, q);
    }

    /*
     * Accepts either the 'userName' or the 'userNode' for the user. It's best to pass userNode if you
     * have it, to avoid a DB query.
     */
    public SubNode getUserNodeByType(
        MongoSession ms,
        String userName,
        SubNode userNode,
        String content,
        String type,
        List<String> publicPrivs,
        String defaultName
    ) {
        if (userNode == null) {
            if (userName == null) {
                userName = ThreadLocals.getSC().getUserName();
            }
            userNode = getUserNodeByUserName(ms, userName);
        }
        if (userNode == null) {
            log.warn("userNode not found for user name: " + userName);
            return null;
        }
        SubNode node = findSubNodeByType(ms, userNode, type);
        if (node == null) {
            node = create.createNode(ms, userNode, null, type, 0L, CreateNodeLocation.LAST, null, null, true, true);
            node.setOwner(userNode.getId());
            if (content == null) {
                content = getDefaultContentForNamedNode(type);
            }
            node.setContent(content);
            node.touch();
            if (defaultName != null) {
                node.setName(defaultName);
            }
            if (publicPrivs != null) {
                acl.addPrivilege(ms, null, node, PrincipalName.PUBLIC.s(), null, publicPrivs, null);
            }
            update.save(ms, node);
        }
        /*
         * todo-2: fix this? Ensure if "sn:posts" node type does exist that it's also named 'posts' this is
         * a retrofit (data repair) here, and not the standard flow.
         */
        if (node != null && NodeType.POSTS.s().equals(type) && !NodeName.POSTS.equals(node.getName())) {
            node.setName(NodeName.POSTS);
            acl.addPrivilege(ms, null, node, PrincipalName.PUBLIC.s(), null, Arrays.asList(PrivilegeType.READ.s()), null);
            update.save(ms, node);
        }
        return node;
    }

    public String getDefaultContentForNamedNode(String type) {
        if (type.equals(NodeType.EXPORTS.s())) {
            return "### Exports";
        }
        if (type.equals(NodeType.FRIEND_LIST.s())) {
            return "### Friends List";
        }
        if (type.equals(NodeType.BLOCKED_USERS.s())) {
            return "### Blocked Users";
        }
        if (type.equals(NodeType.POSTS.s())) {
            return "### " + ThreadLocals.getSC().getUserName() + "'s Public Posts";
        }
        if (type.equals(NodeType.NOTES.s())) {
            return "### Notes";
        }
        if (type.equals(NodeType.BOOKMARK_LIST.s())) {
            return "### Bookmarks";
        }
        return "Node: " + type;
    }

    public String convertIfLocalName(String userName) {
        if (!userName.endsWith("@" + prop.getMetaHost())) {
            return userName;
        }
        int atIdx = userName.indexOf("@");
        if (atIdx == -1) return userName;
        return userName.substring(0, atIdx);
    }

    public SubNode getUserNodeByUserName(MongoSession ms, String user) {
        return getUserNodeByUserName(ms, user, true);
    }

    public SubNode getLocalUserNodeByProp(MongoSession ms, String propName, String propVal) {
        return getLocalUserNodeByProp(ms, propName, propVal, true);
    }

    public SubNode getLocalUserNodeByProp(MongoSession ms, String propName, String propVal, boolean allowAuth) {
        if (StringUtils.isEmpty(propVal)) return null;
        // Otherwise for ordinary users root is based off their username
        Query q = new Query();
        Criteria crit = //
            //
            Criteria
                .where(SubNode.PATH)
                .regex(mongoUtil.regexDirectChildrenOfPath(NodePath.LOCAL_USERS_PATH))
                .and(SubNode.PROPS + "." + propName)
                .is(propVal)
                .and(SubNode.TYPE)
                .is(NodeType.ACCOUNT.s());
        q.addCriteria(crit);
        return opsw.findOne(allowAuth ? ms : null, q);
    }

    public SubNode getUserNodeByUserName(MongoSession ms, String user, boolean allowAuth) {
        if (user == null) {
            user = ThreadLocals.getSC().getUserName();
        }
        user = user.trim();
        // if user name ends with "@quanta.wiki" for example, truncate it after the '@'
        // character, so that ONLY foreign names will have any '@' in the string.
        user = convertIfLocalName(user);
        String pathToQuery = user.contains("@") || nostr.isNostrUserName(user)
            ? NodePath.REMOTE_USERS_PATH
            : NodePath.LOCAL_USERS_PATH;
        // For the ADMIN user their root node is considered to be the entire root of the
        // whole DB
        if (PrincipalName.ADMIN.s().equalsIgnoreCase(user)) {
            return getDbRoot();
        }
        // Otherwise for ordinary users root is based off their username
        // case-insensitive lookup of username:
        Query q = new Query();
        Criteria crit = Criteria
            .where(SubNode.PATH)
            .regex(mongoUtil.regexDirectChildrenOfPath(pathToQuery))
            .and(SubNode.PROPS + "." + NodeProp.USER)
            .is(user)
            .and(SubNode.TYPE)
            .is(NodeType.ACCOUNT.s());
        q.addCriteria(crit);
        return opsw.findOne(allowAuth ? ms : null, q);
    }

    /*
     * userNode and userName correspond to the FRIEND node and not the person who OWNS the friend node.
     * The person whose friend we're trying to look up is 'ownerId' so they will be the OWNER of the
     * FriendNode.
     *
     * Note: Blocked users are also stored as a "FriendNode", but under the "blocked list"
     */
    public SubNode findFriendNode(MongoSession ms, ObjectId ownerId, SubNode userNode, String userName) {
        if (userNode == null) {
            userNode = getUserNodeByUserName(ms, userName, false);
            if (userNode == null) {
                return null;
            }
        }
        // Otherwise for ordinary users root is based off their username
        Query q = new Query();
        Criteria crit = //
            //
            Criteria
                .where(SubNode.OWNER)
                .is(ownerId)
                .and(SubNode.TYPE)
                .is(NodeType.FRIEND.s())
                .and(SubNode.PROPS + "." + NodeProp.USER_NODE_ID.s())
                .is(userNode.getIdStr());

        q.addCriteria(crit);
        SubNode ret = opsw.findOne(ms, q);
        return ret;
    }

    /*
     * Finds and returns the first node matching userName and type as direct child of 'node', or null if
     * not existing. Caller can pass userNode if its available, or else userName will be used to look it
     * up
     */
    public SubNode findNodeByUserAndType(MongoSession ms, SubNode node, SubNode userNode, String userName, String type) {
        // It's slighty more resiliant when, in this special case we don't trust the 'hasChildren'
        // flag
        // if (noChildren(node)) {
        // return null;
        // }
        if (userNode == null) {
            userNode = getUserNodeByUserName(ms, userName, false);
            if (userNode == null) {
                return null;
            }
        }
        // Otherwise for ordinary users root is based off their username
        Query q = new Query();
        Criteria crit = //
            //
            //
            Criteria
                .where(SubNode.PATH)
                .regex(mongoUtil.regexDirectChildrenOfPath(node.getPath()))
                .and(SubNode.TYPE)
                .is(type)
                .and(SubNode.PROPS + "." + NodeProp.USER_NODE_ID.s())
                .is(userNode.getIdStr());

        q.addCriteria(crit);
        SubNode ret = opsw.findOne(ms, q);
        return ret;
    }

    /*
     * Finds the first node matching 'type' under 'path' (non-recursively, direct children only)
     */
    public SubNode findSubNodeByType(MongoSession ms, SubNode node, String type) {
        if (noChildren(node)) {
            return null;
        }
        // Other wise for ordinary users root is based off their username
        Query q = new Query();
        Criteria crit = Criteria
            .where(SubNode.PATH)
            .regex(mongoUtil.regexDirectChildrenOfPath(node.getPath()))
            .and(SubNode.TYPE)
            .is(type);
        q.addCriteria(crit);
        return opsw.findOne(ms, q);
    }

    // ========================================================================
    // Typed Node Under Path
    // ========================================================================
    /*
     * Finds nodes matching 'type' under 'path' (recursively)
     */
    public Iterable<SubNode> findSubNodesByType(
        MongoSession ms,
        SubNode node,
        String type,
        boolean recursive,
        Sort sort,
        Integer limit
    ) {
        if (noChildren(node)) {
            return Collections.<SubNode>emptyList();
        }
        Query q = typedNodesUnderPath_query(ms, node, type, recursive, sort, limit);
        return opsw.find(ms, q);
    }

    /*
     * Counts nodes matching 'type' under 'path' (recursively)
     */
    public long countTypedNodesUnderPath(MongoSession ms, SubNode node, String type, Sort sort, Integer limit) {
        if (noChildren(node)) {
            return 0L;
        }
        Query q = typedNodesUnderPath_query(ms, node, type, true, sort, limit);
        return opsw.count(ms, q);
    }

    public Query typedNodesUnderPath_query(
        MongoSession ms,
        SubNode node,
        String type,
        boolean recursive,
        Sort sort,
        Integer limit
    ) {
        Query q = new Query();
        Criteria crit = Criteria
            .where(SubNode.PATH)
            .regex(
                recursive
                    ? mongoUtil.regexRecursiveChildrenOfPath(node.getPath())
                    : mongoUtil.regexDirectChildrenOfPath(node.getPath())
            )
            .and(SubNode.TYPE)
            .is(type);
        if (sort != null) {
            q.with(sort);
        }
        if (limit != null) {
            q.limit(limit);
        }
        q.addCriteria(crit);
        return q;
    }

    // ========================================================================
    /*
     * Returns one (or first) node contained directly under path (non-recursively) that has a matching
     * propName and propVal
     */
    public SubNode findNodeByProp(MongoSession ms, SubNode node, String propName, String propVal) {
        if (noChildren(node)) {
            return null;
        }
        Query q = new Query();
        Criteria crit = Criteria
            .where(SubNode.PATH)
            .regex(mongoUtil.regexDirectChildrenOfPath(node.getPath()))
            .and(SubNode.PROPS + "." + propName)
            .is(propVal);
        q.addCriteria(crit);
        SubNode ret = opsw.findOne(ms, q);
        auth.auth(ms, ret, PrivilegeType.READ);
        return ret;
    }

    /*
     * Same as findSubNodeByProp but returns multiples. Finda ALL nodes contained directly under path
     * (non-recursively) that has a matching propName and propVal
     */
    public Iterable<SubNode> findNodesByProp(MongoSession ms, String path, String propName, String propVal) {
        if (noChildren(ms, path)) {
            return Collections.<SubNode>emptyList();
        }
        Query q = new Query();
        Criteria crit = Criteria
            .where(SubNode.PATH)
            .regex(mongoUtil.regexDirectChildrenOfPath(path))
            .and(SubNode.PROPS + "." + propName)
            .is(propVal);

        q.addCriteria(crit);
        return opsw.find(ms, q);
    }

    /*
     * Returns one (or first) node that has a matching propName and propVal
     */
    public SubNode findNodeByProp(MongoSession ms, String propName, String propVal) {
        if (propVal == null) {
            return null;
        }
        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PROPS + "." + propName).is(propVal);
        q.addCriteria(crit);
        SubNode ret = opsw.findOne(ms, q);
        auth.auth(ms, ret, PrivilegeType.READ);
        return ret;
    }

    public SubNode findByIPFSPinned(MongoSession ms, String cid) {
        // #ipfs-pending-work: pending rewrite based on new Attachments array on SubNode
        throw new RuntimeException("findByIPFSPinned not available");
        // Query q = new Query();
        // /* Match the PIN to cid */
        // Criteria crit = Criteria.where(SubNode.PROPS + "." + NodeProp.IPFS_LINK.s()).is(cid);
        // /* And only consider nodes that are NOT REFs (meaning IPFS_REF prop==null) */
        // crit = crit.and(SubNode.PROPS + "." + NodeProp.IPFS_REF.s()).is(null);
        // q.addCriteria(crit);
        // SubNode ret = mongoUtil.findOne(q);
        // auth.auth(ms, ret, PrivilegeType.READ);
        // return ret;
    }

    public Iterable<SubNode> getAccountNodes(
        MongoSession ms,
        CriteriaDefinition textCriteria,
        Sort sort,
        Integer limit,
        int skip,
        boolean remote,
        boolean local
    ) {
        if (!remote && !local) {
            throw new RuntimeException("Accont query needs local and/or remote specified.");
        }
        Query q = new Query();
        Criteria crit = new Criteria();
        List<Criteria> critList = new LinkedList<>();
        if (remote) {
            critList.add(Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(NodePath.REMOTE_USERS_PATH)));
        }
        if (local) {
            critList.add(Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(NodePath.LOCAL_USERS_PATH)));
        }
        if (textCriteria != null) {
            q.addCriteria(textCriteria);
        }
        q.addCriteria(crit.orOperator(critList));
        if (limit != null && limit.intValue() > 0) {
            q.limit(limit.intValue());
        }
        if (skip > 0) {
            q.skip(skip);
        }
        if (sort != null) {
            q.with(sort);
        }
        return opsw.find(ms, q);
    }

    // (not currently used)
    public SubNode findByCID(MongoSession ms, String cid) {
        Query q = new Query();
        /* Match the PIN to cid */
        // need to add an index for this field if we ever start using it.
        Criteria crit = Criteria.where(SubNode.MCID).is(cid);
        q.addCriteria(crit);
        SubNode ret = opsw.findOne(ms, q);
        auth.auth(ms, ret, PrivilegeType.READ);
        return ret;
    }

    // This gets all nodes with a pinned attachment on IPFS.
    // (not currently used)
    public Iterable<SubNode> findAllWithIpfsLinks() {
        // #ipfs-pending-work: pending refactor with new Attachments array
        throw new RuntimeException("findAllWithIpfsLinks not available");
        // Query q = new Query();
        // Criteria crit = Criteria.where(SubNode.PROPS + "." + NodeProp.IPFS_LINK.s()).ne(null);
        // /* And only consider nodes that are NOT REFs (meaning IPFS_REF prop==null) */
        // crit = crit.and(SubNode.PROPS + "." + NodeProp.IPFS_REF.s()).is(null);
        // q.addCriteria(crit);
        // return mongoUtil.find(q);
    }

    // (not currently used)
    public Iterable<SubNode> findAllWithCids() {
        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.MCID).ne(null);
        q.addCriteria(crit);
        return opsw.find(null, q);
    }

    // If optional idMap is passed in non-null it gets loaded with a map from nodeId to TreeNode
    public TreeNode getSubGraphTree(MongoSession ms, String rootId, Criteria criteria, HashMap<String, TreeNode> idMap) {
        SubNode rootNode = getNode(ms, new ObjectId(rootId));
        if (rootNode == null) throw new RuntimeException("unable to access node: " + rootId);

        TreeNode rootTreeNode = new TreeNode(rootNode);
        if (idMap != null) {
            idMap.put(rootNode.getIdStr(), rootTreeNode);
        }

        // maps from path to node
        HashMap<String, TreeNode> nodeMap = new HashMap<>();
        nodeMap.put(rootNode.getPath(), rootTreeNode);

        // first scan to build up the nodes list and nodeMap
        for (SubNode n : getSubGraph(ms, rootNode, null, 0, false, true, criteria)) {
            nodeMap.put(n.getPath(), new TreeNode(n));
            if (nodeMap.size() > 5000) {
                throw new RuntimeException("Too much data to return. Max is 5000 nodes.");
            }
        }

        // process all nodes to add to children (as unordered children at first) to each node they go under
        nodeMap.forEach((k, n) -> {
            if (idMap != null) {
                idMap.put(n.node.getIdStr(), n);
            }
            TreeNode parent = nodeMap.get(n.node.getParentPath());

            // since root node is not in 'nodes' we know it's a failure if we find one whose parent
            // we don't know
            if (parent == null) {
                log.debug("Ignoring Orphan: " + n.node.getPath());
                // skip iteration element (warning: not returning from this function!)
                return;
            }
            if (parent.children == null) {
                parent.children = new LinkedList<>();
            }
            parent.children.add(n);
            if (n.parent == null) {
                n.parent = parent;
            }
        });

        nodeMap.forEach((k, n) -> {
            if (n.children != null) {
                n.children.sort((a, b) -> a.node.getOrdinal().compareTo(b.node.getOrdinal()));
            }
        });

        return rootTreeNode;
    }

    public List<SubNode> getFlatSubGraph(MongoSession ms, final String rootId, boolean includeComments) {
        LinkedList<SubNode> doc = new LinkedList<>();

        Criteria typeCriteria = null;
        if (!includeComments) {
            typeCriteria = Criteria.where(SubNode.TYPE).ne(NodeType.COMMENT);
        }

        TreeNode rootTreeNode = getSubGraphTree(ms, rootId, typeCriteria, null);
        traverseTree(rootTreeNode, doc);
        return doc;
    }

    void traverseTree(TreeNode tn, LinkedList<SubNode> doc) {
        doc.add(tn.node);

        if (tn.children == null) {
            return;
        }

        tn.children.sort((a, b) -> a.node.getOrdinal().compareTo(b.node.getOrdinal()));

        for (TreeNode tni : tn.children) {
            traverseTree(tni, doc);

            // help garbage collector
            tn.children = null;
        }
    }

    // Generates the logicalOrdinal of node by counting all the nodes that have a 'lower ordinal' than
    // it does. If no nodes are lower in ordinal that makes it the top one, and thus 0th ordinal, etc.
    public long generateLogicalOrdinal(MongoSession ms, SubNode node) {
        Query q = new Query();
        Criteria crit = Criteria //
            .where(SubNode.PATH)
            .regex(mongoUtil.regexDirectChildrenOfPath(node.getParentPath()))
            .and(SubNode.ORDINAL)
            .lt(node.getOrdinal());
        q.addCriteria(crit);
        return opsw.count(null, q);
    }
}
