package quanta.mongo;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.CriteriaDefinition;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.NodeName;
import quanta.config.NodePath;
import quanta.config.ServiceBase;
import quanta.exception.base.RuntimeEx;
import quanta.instrument.PerfMon;
import quanta.instrument.PerfMonEvent;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.model.client.PrincipalName;
import quanta.model.client.PrivilegeType;
import quanta.mongo.model.SubNode;
import quanta.util.ThreadLocals;
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
@Slf4j
public class MongoRead extends ServiceBase {
    int MAX_DOC_DEPTH = 7;
    int MAX_DOC_ITEMS_PER_CALL = 40;

    private static final Object dbRootsLock = new Object();
    private SubNode dbRoot;

    // we call this during app init so we don't need to have thread safety here the rest of the time.
    public SubNode getDbRoot() {
        synchronized (dbRootsLock) {
            if (dbRoot == null) {
                dbRoot = findNodeByPath(NodePath.ROOT_PATH);
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
    @PerfMon(category = "read")
    public String getNodeOwner(MongoSession ms, SubNode node) {
        if (node.getOwner() == null) {
            throw new RuntimeEx("Node has null owner: " + XString.prettyPrint(node));
        }
        SubNode userNode = read.getNode(ms, node.getOwner());
        return userNode.getStr(NodeProp.USER);
    }

    // Used this for troubleshooting a constraint violation on one of the partial indexes
    public void dumpByPropertyMatch(String prop, String val) {
        log.debug("Dump for: prop " + prop + "=" + val);
        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PROPS + "." + prop).is(val);
        q.addCriteria(crit);
        Iterable<SubNode> iter = mongoUtil.find(q);
        for (SubNode node : iter) {
            log.debug("NODE: " + XString.prettyPrint(node));
        }
    }

    public String getParentPath(SubNode node) {
        return XString.truncAfterLast(node.getPath(), "/");
    }

    @PerfMon(category = "read")
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

    @PerfMon(category = "read")
    public long getChildCount(MongoSession ms, String path) {
        // statistically I think it pays off to always try the faster way and then assume worst case is that
        // we might have warmed up the MongoDb for what the following query will need.
        if (noChildren(ms, path))
            return 0;

        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(path));
        crit = auth.addSecurityCriteria(ms, crit);
        q.addCriteria(crit);
        return ops.count(q, SubNode.class);
    }

    /*
     * we only update the hasChildren if doAuth is false, because doAuth would be a person-specific
     * exact query, unlike what the hasChildren represents which is a global
     * "any children visible to any user" thing.
     */
    @PerfMon(category = "read(m,n)")
    public boolean hasChildren(MongoSession ms, SubNode node, boolean doAuth, boolean allowUpdate) {

        // cannot both be true
        if (doAuth && allowUpdate) {
            throw new RuntimeException("doAuth and allowUpdate are mutually exclusive.");
        }

        // if the node knows it's children status (non-null value) return that.
        if (SubNode.USE_HAS_CHILDREN && !doAuth && node.getHasChildren() != null) {
            // calling booleanValue for clarity
            return node.getHasChildren().booleanValue();
        }
        boolean ret = hasChildrenByQuery(ms, node.getPath(), doAuth);
        if (!doAuth && allowUpdate) {
            // log.debug("Update HAS_CHILDREN: "+node.getIdStr());
            node.setHasChildren(ret);
        }
        return ret;
    }

    @PerfMon(category = "read(m,pth)")
    public boolean hasChildrenByQuery(MongoSession ms, String path, boolean doAuth) {
        // WARNING: Leave this as a note to NOT call this optimization here. It is definitely
        // counter-productive.
        // if (noChildren(ms, path)) return false;

        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(path));
        if (doAuth) {
            crit = auth.addSecurityCriteria(ms, crit);
        }
        q.addCriteria(crit);
        return ops.exists(q, SubNode.class);
    }

    @PerfMon(category = "read")
    public long getNodeCount() {
        Query q = new Query();
        Criteria crit = arun.run(as -> auth.addSecurityCriteria(as, null));
        if (crit != null) {
            q.addCriteria(crit);
        }
        return ops.count(q, SubNode.class);
    }

    /* If this 'path' is known to exist and never needs to be validated, return true */
    public boolean knownPath(String path) {
        // if this is a path we KNOW exists, return false
        if (path == null || path.length() == 0 || !path.contains("/") || path.equals(NodePath.PENDING_PATH)
                || path.equals(NodePath.ROOT_PATH) || path.equals(NodePath.USERS_PATH) || path.equals(NodePath.LOCAL_USERS_PATH)
                || path.equals(NodePath.REMOTE_USERS_PATH)) {
            return true;
        }
        return false;
    }

    /* Checks ALL parent paths to make sure they all exist. Returns true of some parent doesn't exist */
    public boolean isOrphan(String path) {
        int sanityCheck = 0;

        // if this is a path we KNOW exists, return false
        if (knownPath(path))
            return false;

        while (sanityCheck++ < 1000) {
            // get parent path
            path = XString.truncAfterLast(path, "/");

            if (knownPath(path))
                return false;

            // if the parent path does not exist, we're done. Path passed in is an orphan.
            if (!read.pathExists(path)) {
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
        if (isRootPath)
            return;

        String parentPath = XString.truncAfterLast(path, "/");
        if (parentPath == null || parentPath.equals("") || parentPath.equals("/") || parentPath.equals(NodePath.ROOT_PATH)
                || parentPath.equals(NodePath.PENDING_PATH) || parentPath.equals(NodePath.PENDING_PATH + "/"))
            return;

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

        if (!read.pathExists(parentPath)) {
            throw new RuntimeEx("Attempted to add a node before its parent exists:" + parentPath);
        }
    }

    @PerfMon(category = "read")
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
    @PerfMon(category = "read")
    public SubNode getNodeByName(MongoSession ms, String name, boolean allowAuth, Val<SubNode> accntNode) {
        Query q = new Query();

        if (name == null)
            return null;
        // we tolerate a prefix at the FRONT of either format 1, or 2, and ignore it.
        name = XString.stripIfStartsWith(name, ":");
        // log.debug("getNodeByName: " + name);

        ObjectId nodeOwnerId;
        int colonIdx = -1;
        SubNode userNode = null;

        /*
         * if 'name' doesn't contain a colon it's known to be just an admin-owned global named node without
         * a user prefix
         */
        if ((colonIdx = name.indexOf(":")) == -1) {
            nodeOwnerId = getDbRoot().getOwner();
            // log.debug("no leading colon, so this is expected to have admin owner=" +
            // nodeOwnerId.toHexString());
        }
        /*
         * If there is a colon in the name then it's of the format 'userName:nodeName'
         */
        else {
            String userName = name.substring(0, colonIdx);

            /*
             * pass a null session here to cause adminSession to be used which is required to get a user node,
             * but it always safe to get this node this way here.
             */
            userNode = getUserNodeByUserName(null, userName);
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

        q.addCriteria(Criteria.where(SubNode.NAME).is(name)//
                .and(SubNode.OWNER).is(nodeOwnerId));

        SubNode ret = mongoUtil.findOne(q);
        if (ret != null) {
            // log.debug("Node found: id=" + ret.getIdStr());
        }

        if (allowAuth) {
            auth.auth(ms, ret, PrivilegeType.READ);
        }

        return ret;
    }

    @PerfMon(category = "read(m,i)")
    public SubNode getNode(MongoSession ms, String identifier) {
        // calling thru proxy for AOP here.
        return read.getNode(ms, identifier, true, null);
    }

    @PerfMon(category = "read(m,i,a)")
    public SubNode getNode(MongoSession ms, String identifier, boolean allowAuth) {
        // calling thru proxy for AOP here.
        return read.getNode(ms, identifier, allowAuth, null);
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
    @PerfMon(category = "read(m,i,a)")
    public SubNode getNode(MongoSession ms, String identifier, boolean allowAuth, Val<SubNode> accntNode) {
        if (identifier == null)
            return null;

        if (identifier.equals("/")) {
            throw new RuntimeEx(
                    "SubNode doesn't implement the root node. Root is implicit and never needs an actual node to represent it.");
        }
        // log.debug("getNode identifier=" + identifier);
        SubNode ret = null;
        boolean authPending = true;

        if (identifier.startsWith(".")) {
            ret = nostr.getNodeByNostrId(ms, identifier, allowAuth);
        } else if (identifier.startsWith("~")) {
            String typeName = identifier.substring(1);
            if (!typeName.startsWith("sn:")) {
                typeName = "sn:" + typeName;
            }
            ret = read.getUserNodeByType(ms, ms.getUserName(), null, null, typeName, null, null);
        }
        // Node name lookups are done by prefixing the search with a colon (:)
        else if (identifier.startsWith(":")) {
            ret = read.getNodeByName(ms, identifier.substring(1), allowAuth, accntNode);
            authPending = false;
        }
        // If search doesn't start with a slash then it's a nodeId and not a path
        else if (!identifier.startsWith("/")) {
            ret = read.getNode(ms, new ObjectId(identifier), allowAuth);
            authPending = false;
        }
        // otherwise this is a path lookup
        else {
            ret = read.findNodeByPath(identifier);
        }

        if (authPending && allowAuth) {
            auth.auth(ms, ret, PrivilegeType.READ);
        }
        return ret;
    }

    @PerfMon(category = "read")
    public SubNode findNodeByPath(String path) {
        path = XString.stripIfEndsWith(path, "/");
        Query q = new Query();
        q.addCriteria(Criteria.where(SubNode.PATH).is(path));
        return mongoUtil.findOne(q);
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

    @PerfMon(category = "read(m,o)")
    public SubNode getNode(MongoSession ms, ObjectId objId) {
        return read.getNode(ms, objId, true);
    }

    @PerfMon(category = "read")
    public SubNode getOwner(MongoSession ms, SubNode node, boolean allowAuth) {
        return read.getNode(ms, node.getOwner(), allowAuth);
    }

    @PerfMon(category = "read(m,o,a)")
    public SubNode getNode(MongoSession ms, ObjectId objId, boolean allowAuth) {
        SubNode ret = mongoUtil.findById(objId);
        if (ret != null && allowAuth) {
            auth.auth(ms, ret, PrivilegeType.READ);
        }
        return ret;
    }

    /*
     * todo-2: Need to implement a save hook/callback capability in the MongoListener so we can get
     * notifications sent to any threads that are waiting to lookup a node once it exists, but we will
     * STILL probably need to DO the lookup so we don't have concurrent access threading bug.
     */
    @PerfMon(category = "read(m,o,a,r)")
    public SubNode getNode(MongoSession ms, ObjectId objId, boolean allowAuth, int retries) {
        SubNode ret = read.getNode(ms, objId, allowAuth);
        while (ret == null && retries-- > 0) {
            Util.sleep(3000);
            ret = read.getNode(ms, objId, allowAuth);
        }
        return ret;
    }

    public SubNode getParent(MongoSession ms, SubNode node) {
        return getParent(ms, node, true);
    }

    public SubNode getParent(MongoSession ms, SubNode node, boolean allowAuth) {
        return getParentByPath(ms, node.getPath(), allowAuth);
    }

    /*
     * WARNING: This always converts a 'pending' path to a non-pending one (/r/p/ v.s. /r/)
     */
    @PerfMon(category = "read")
    public SubNode getParentByPath(MongoSession ms, String path, boolean allowAuth) {
        if ("/".equals(path)) {
            return null;
        }
        String parentPath = XString.truncAfterLast(path, "/");
        if (StringUtils.isEmpty(parentPath))
            return null;

        String pendingPath = NodePath.PENDING_PATH + "/";
        String rootPath = NodePath.ROOT_PATH + "/";

        /*
         * If node is in pending area take the pending part out of the path to get the real parent
         */
        parentPath = parentPath.replace(pendingPath, rootPath);

        SubNode ret = read.getNode(ms, parentPath, allowAuth, null);
        if (ret != null && allowAuth) {
            auth.auth(ms, ret, PrivilegeType.READ);
        }

        return ret;
    }

    @PerfMon(category = "read")
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
        Criteria crit =
                Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(node == null ? "" : node.getPath()));
        crit = auth.addSecurityCriteria(ms, crit);

        if (ordered) {
            q.with(Sort.by(Sort.Direction.ASC, SubNode.ORDINAL));
        }
        q.addCriteria(crit);

        Iterable<SubNode> iter = mongoUtil.find(q);
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
    @PerfMon(category = "read(pth)")
    public Iterable<SubNode> getChildren(MongoSession ms, String path, Sort sort, Integer limit, int skip,
            TextCriteria textCriteria, Criteria moreCriteria, boolean preCheck) {
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

        crit = auth.addSecurityCriteria(ms, crit);
        q.addCriteria(crit);
        return mongoUtil.find(q);
    }

    public Iterable<SubNode> getChildren(MongoSession ms, SubNode node, Sort sort, Integer limit, int skip) {
        return getChildren(ms, node, sort, limit, skip, null);
    }

    /*
     * If node is null it's path is considered empty string, and it represents the 'root' of the tree.
     * There is no actual NODE that is root node
     */
    @PerfMon(category = "read")
    public Iterable<SubNode> getChildren(MongoSession ms, SubNode node, Sort sort, Integer limit, int skip,
            Criteria moreCriteria) {
        if (noChildren(node)) {
            return Collections.<SubNode>emptyList();
        }
        auth.auth(ms, node, PrivilegeType.READ);
        return read.getChildren(ms, node.getPath(), sort, limit, skip, null, moreCriteria, false);
    }

    public Iterable<SubNode> getChildren(MongoSession ms, SubNode node) {
        return read.getChildren(ms, node, null, null, 0);
    }

    /*
     * All we need to do here is query for children an do a "max(ordinal)" operation on that, but
     * digging the information off the web for how to do this appears to be something that may take a
     * few hours so i'm skipping it for now and just doing an inverse sort on ORDER and pulling off the
     * top one and using that for my MAX operation. AFAIK this might even be the most efficient
     * approach. Who knows.
     */
    @PerfMon(category = "read")
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

        if (noChildren(node))
            return 0L;

        auth.auth(ms, node, PrivilegeType.READ);

        // todo-2: research if there's a way to query for just one, rather than simply
        // calling findOne at the end? What's best practice here?
        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(node.getPath()));
        q.with(Sort.by(Sort.Direction.DESC, SubNode.ORDINAL));
        q.addCriteria(crit);
        q.limit(1);

        SubNode nodeFound = mongoUtil.findOne(q);
        if (nodeFound == null) {
            return 0L;
        }
        return nodeFound.getOrdinal();
    }

    // this is NOT returning the first node with the least ordinal!!!
    @PerfMon(category = "read")
    public Long getMinChildOrdinal(MongoSession ms, SubNode node) {
        if (noChildren(node))
            return 0L;

        auth.auth(ms, node, PrivilegeType.READ);

        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(node.getPath()));
        q.with(Sort.by(Sort.Direction.ASC, SubNode.ORDINAL));
        q.addCriteria(crit);
        q.limit(1);

        SubNode nodeFound = mongoUtil.findOne(q);
        if (nodeFound == null) {
            return 0L;
        }
        return nodeFound.getOrdinal();
    }

    // if 'parent' of 'node' is known it should be passed in, or else null passed in, and parent will be
    // looked up instead
    @PerfMon(category = "read")
    public SubNode getSiblingAbove(MongoSession ms, SubNode node, SubNode parent) {
        if (parent == null) {
            parent = read.getParent(ms, node);
        }
        if (parent == null || noChildren(parent))
            return null;

        auth.auth(ms, node, PrivilegeType.READ);

        if (node.getOrdinal() == null) {
            node.setOrdinal(0L);
        }

        // todo-2: research if there's a way to query for just one, rather than simply
        // calling findOne at the end? What's best practice here?
        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(node.getParentPath()));
        q.with(Sort.by(Sort.Direction.DESC, SubNode.ORDINAL));
        crit = auth.addSecurityCriteria(ms, crit);
        q.addCriteria(crit);

        // leave this example. you can do a RANGE like this.
        // query.addCriteria(Criteria.where(SubNode.FIELD_ORDINAL).lt(50).gt(20));
        q.addCriteria(Criteria.where(SubNode.ORDINAL).lt(node.getOrdinal()));
        q.limit(1);

        return mongoUtil.findOne(q);
    }

    // if 'parent' of 'node' is known it should be passed in, or else null passed in, and parent will be
    // looked up instead
    public SubNode getSiblingBelow(MongoSession ms, SubNode node, SubNode parent) {
        if (parent == null) {
            parent = read.getParent(ms, node);
        }
        if (parent == null || noChildren(parent))
            return null;

        auth.auth(ms, node, PrivilegeType.READ);
        if (node.getOrdinal() == null) {
            node.setOrdinal(0L);
        }

        // todo-2: research if there's a way to query for just one, rather than simply
        // calling findOne at the end? What's best practice here?
        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(node.getParentPath()));
        q.with(Sort.by(Sort.Direction.ASC, SubNode.ORDINAL));
        crit = auth.addSecurityCriteria(ms, crit);
        q.addCriteria(crit);

        // leave this example. you can do a RANGE like this.
        // query.addCriteria(Criteria.where(SubNode.FIELD_ORDINAL).lt(50).gt(20));
        q.addCriteria(Criteria.where(SubNode.ORDINAL).gt(node.getOrdinal()));
        q.limit(1);

        return mongoUtil.findOne(q);
    }

    /*
     * Gets (recursively) all nodes under 'node', by using all paths starting with the path of that node
     */
    public Iterable<SubNode> getSubGraph(MongoSession ms, SubNode node, Sort sort, int limit, boolean removeOrphans,
            boolean publicOnly, boolean doAuth) {
        if (noChildren(node)) {
            return Collections.<SubNode>emptyList();
        }
        /*
         * The removeOrphans algo REQUIRES all nodes to be returned (no 'limit')
         */
        // DO NOT REMOVE THIS CHECK!!!
        if (removeOrphans && limit > 0) {
            throw new RuntimeException("getSubGraph: invalid parameters");
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

        if (doAuth) {
            crit = auth.addSecurityCriteria(ms, crit);
        }
        q.addCriteria(crit);

        if (sort != null) {
            q.with(sort);
        }

        if (limit > 0) {
            q.limit(limit);
        }

        Iterable<SubNode> iter = mongoUtil.find(q);
        return removeOrphans ? mongoUtil.filterOutOrphans(ms, node, iter) : iter;
    }

    /**
     * prop is optional and if non-null means we should search only that one field.
     * 
     * timeRangeType: futureOnly, pastOnly, all
     */
    @PerfMon(category = "read")
    public Iterable<SubNode> searchSubGraph(MongoSession ms, SubNode node, String prop, String text, String sortField,
            String sortDir, int limit, int skip, boolean fuzzy, boolean caseSensitive, String timeRangeType, boolean recursive,
            boolean requirePriority, boolean requireAttachment) {
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

        crit = auth.addSecurityCriteria(ms, crit);
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
                else if ("pastOnly".equals(timeRangeType)) {
                    criterias.add(Criteria.where(sortField).lt(new Date().getTime()));
                }
                // if showing all dates the condition here is that there at least IS a 'date'
                // prop on the node
                else if ("all".equals(timeRangeType)) {
                    criterias.add(Criteria.where(sortField).ne(null));
                }
            }

            if (!StringUtils.isEmpty(sortField)) {
                if ("contentLength".equals(sortField)) {
                    sortDir = "ASC";
                }
                sort = Sort.by((sortDir != null && sortDir.equalsIgnoreCase("DESC")) ? Sort.Direction.DESC : Sort.Direction.ASC,
                        sortField);
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
            aggOps.add(Aggregation.project().andInclude(SubNode.ALL_FIELDS)//
                    .andExpression("strLenCP(cont)").as("contentLength"));

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
        }
        // Otherwise a standard Query.
        else {
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

            return mongoUtil.find(q);
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
        crit = auth.addSecurityCriteria(ms, crit);
        q.addCriteria(crit);
        q.addCriteria(Criteria.where(SubNode.PROPS + "." + NodeProp.DATE).ne(null));

        return mongoUtil.find(q);
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
        crit = auth.addSecurityCriteria(ms, crit);
        q.addCriteria(crit);

        return mongoUtil.find(q);
    }

    /*
     * Accepts either the 'userName' or the 'userNode' for the user. It's best to pass userNode if you
     * have it, to avoid a DB query.
     */
    @PerfMon(category = "read")
    public SubNode getUserNodeByType(MongoSession ms, String userName, SubNode userNode, String content, String type,
            List<String> publicPrivs, String defaultName) {
        if (userNode == null) {
            if (userName == null) {
                userName = ThreadLocals.getSC().getUserName();
            }
            userNode = read.getUserNodeByUserName(ms, userName);
        }

        if (userNode == null) {
            log.warn("userNode not found for user name: " + userName);
            return null;
        }

        SubNode node = read.findSubNodeByType(ms, userNode, type);

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
        if (atIdx == -1)
            return userName;
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
        Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(NodePath.LOCAL_USERS_PATH)) //
                .and(SubNode.PROPS + "." + propName).is(propVal) //
                .and(SubNode.TYPE).is(NodeType.ACCOUNT.s());

        q.addCriteria(crit);

        SubNode ret = mongoUtil.findOne(q);
        if (allowAuth) {
            SubNode _ret = ret;
            // we run with 'ms' if it's non-null, or with admin if ms is null
            arun.run(ms, as -> {
                auth.auth(as, _ret, PrivilegeType.READ);
                return null;
            });
        }
        return ret;
    }

    @PerfMon(category = "read")
    public SubNode getUserNodeByUserName(MongoSession ms, String user, boolean allowAuth) {
        if (user == null) {
            user = ThreadLocals.getSC().getUserName();
        }
        user = user.trim();

        // if user name ends with "@quanta.wiki" for example, truncate it after the '@'
        // character, so that ONLY foreign names will have any '@' in the string.
        user = convertIfLocalName(user);

        String pathToQuery =
                user.contains("@") || nostr.isNostrUserName(user) ? NodePath.REMOTE_USERS_PATH : NodePath.LOCAL_USERS_PATH;

        // For the ADMIN user their root node is considered to be the entire root of the
        // whole DB
        if (PrincipalName.ADMIN.s().equalsIgnoreCase(user)) {
            return getDbRoot();
        }

        // Otherwise for ordinary users root is based off their username
        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(pathToQuery)) //
                // case-insensitive lookup of username:
                .and(SubNode.PROPS + "." + NodeProp.USER).regex("^" + user + "$") //
                .and(SubNode.TYPE).is(NodeType.ACCOUNT.s());

        q.addCriteria(crit);

        SubNode ret = mongoUtil.findOne(q);
        if (allowAuth) {
            SubNode _ret = ret;
            // we run with 'ms' if it's non-null, or with admin if ms is null
            arun.run(ms, as -> {
                auth.auth(as, _ret, PrivilegeType.READ);
                return null;
            });
        }
        return ret;
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
            userNode = read.getUserNodeByUserName(ms, userName, false);
            if (userNode == null) {
                return null;
            }
        }

        // Otherwise for ordinary users root is based off their username
        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.OWNER).is(ownerId)//
                .and(SubNode.TYPE).is(NodeType.FRIEND.s()) //
                .and(SubNode.PROPS + "." + NodeProp.USER_NODE_ID.s()).is(userNode.getIdStr());

        crit = auth.addSecurityCriteria(ms, crit);
        q.addCriteria(crit);
        SubNode ret = mongoUtil.findOne(q);
        return ret;
    }

    /*
     * Finds and returns the first node matching userName and type as direct child of 'node', or null if
     * not existing. Caller can pass userNode if its available, or else userName will be used to look it
     * up
     */
    @PerfMon(category = "read")
    public SubNode findNodeByUserAndType(MongoSession ms, SubNode node, SubNode userNode, String userName, String type) {
        // It's slighty more resiliant when, in this special case we don't trust the 'hasChildren'
        // flag
        // if (noChildren(node)) {
        // return null;
        // }

        if (userNode == null) {
            userNode = read.getUserNodeByUserName(ms, userName, false);
            if (userNode == null) {
                return null;
            }
        }

        // Otherwise for ordinary users root is based off their username
        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PATH)//
                .regex(mongoUtil.regexDirectChildrenOfPath(node.getPath()))//
                .and(SubNode.TYPE).is(type) //
                .and(SubNode.PROPS + "." + NodeProp.USER_NODE_ID.s()).is(userNode.getIdStr());

        crit = auth.addSecurityCriteria(ms, crit);
        q.addCriteria(crit);
        SubNode ret = mongoUtil.findOne(q);
        return ret;
    }

    /*
     * Finds the first node matching 'type' under 'path' (non-recursively, direct children only)
     */
    @PerfMon(category = "read")
    public SubNode findSubNodeByType(MongoSession ms, SubNode node, String type) {
        if (noChildren(node)) {
            return null;
        }
        // Other wise for ordinary users root is based off their username
        Query q = new Query();
        Criteria crit = Criteria.where(//
                SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(node.getPath()))//
                .and(SubNode.TYPE).is(type);

        q.addCriteria(crit);
        SubNode ret = mongoUtil.findOne(q);

        auth.auth(ms, ret, PrivilegeType.READ);
        return ret;
    }

    // ========================================================================
    // Typed Node Under Path
    // ========================================================================

    /*
     * Finds nodes matching 'type' under 'path' (recursively)
     */
    public Iterable<SubNode> findSubNodesByType(MongoSession ms, SubNode node, String type, boolean recursive, Sort sort,
            Integer limit) {
        if (noChildren(node)) {
            return Collections.<SubNode>emptyList();
        }
        Query q = typedNodesUnderPath_query(ms, node, type, recursive, sort, limit);
        return mongoUtil.find(q);
    }

    /*
     * Counts nodes matching 'type' under 'path' (recursively)
     */
    public long countTypedNodesUnderPath(MongoSession ms, SubNode node, String type, Sort sort, Integer limit) {
        if (noChildren(node)) {
            return 0L;
        }
        Query q = typedNodesUnderPath_query(ms, node, type, true, sort, limit);
        return ops.count(q, SubNode.class);
    }

    public Query typedNodesUnderPath_query(MongoSession ms, SubNode node, String type, boolean recursive, Sort sort,
            Integer limit) {
        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PATH)
                .regex(recursive ? mongoUtil.regexRecursiveChildrenOfPath(node.getPath())
                        : mongoUtil.regexDirectChildrenOfPath(node.getPath()))//
                .and(SubNode.TYPE).is(type);

        if (sort != null) {
            q.with(sort);
        }

        if (limit != null) {
            q.limit(limit);
        }

        crit = auth.addSecurityCriteria(ms, crit);
        q.addCriteria(crit);
        return q;
    }

    // ========================================================================

    /*
     * Returns one (or first) node contained directly under path (non-recursively) that has a matching
     * propName and propVal
     */
    @PerfMon(category = "read")
    public SubNode findNodeByProp(MongoSession ms, SubNode node, String propName, String propVal) {
        if (noChildren(node)) {
            return null;
        }

        Query q = new Query();
        Criteria crit = Criteria.where(//
                SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(node.getPath()))//
                .and(SubNode.PROPS + "." + propName).is(propVal);
        q.addCriteria(crit);
        SubNode ret = mongoUtil.findOne(q);
        auth.auth(ms, ret, PrivilegeType.READ);
        return ret;
    }

    /*
     * Same as findSubNodeByProp but returns multiples. Finda ALL nodes contained directly under path
     * (non-recursively) that has a matching propName and propVal
     */
    @PerfMon(category = "read")
    public Iterable<SubNode> findNodesByProp(MongoSession ms, String path, String propName, String propVal) {
        if (noChildren(ms, path)) {
            return Collections.<SubNode>emptyList();
        }

        Query q = new Query();
        Criteria crit = Criteria.where(//
                SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(path))//
                .and(SubNode.PROPS + "." + propName).is(propVal);

        crit = auth.addSecurityCriteria(ms, crit);
        q.addCriteria(crit);
        return mongoUtil.find(q);
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
        SubNode ret = mongoUtil.findOne(q);
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

    public Iterable<SubNode> getAccountNodes(MongoSession ms, CriteriaDefinition textCriteria, Sort sort, Integer limit, int skip,
            boolean remote, boolean local) {
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

        return mongoUtil.find(q);
    }

    // (not currently used)
    public SubNode findByCID(MongoSession ms, String cid) {
        Query q = new Query();

        /* Match the PIN to cid */
        // need to add an index for this field if we ever start using it.
        Criteria crit = Criteria.where(SubNode.MCID).is(cid);
        q.addCriteria(crit);
        SubNode ret = mongoUtil.findOne(q);
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
        return mongoUtil.find(q);
    }

    /*
     * Generates a Document-like View of a Subgraph. Nodes in "document order", as if reading like a
     * book, or conventional word processing monolilthic document view. Very powerful because of
     * 'nodeId' which is the place to start loading from. With 'nodeId' it's like we can start our
     * recursion from anywhere, so as we're browsing down a document scrolling in new content each time
     * this method is called to load more records, the nodeId passed in will be whatever was at the
     * bottom of the document which is being appended to by using the results of calling this method.
     */
    public List<SubNode> genDocList(MongoSession ms, final String rootId, final String nodeId, boolean includeComments,
            HashSet<String> truncates) {
        LinkedList<SubNode> doc = new LinkedList<>();
        PerfMonEvent perf = new PerfMonEvent(0, null, ms.getUserName());
        SubNode rootNode = read.getNode(ms, new ObjectId(rootId));
        perf.chain("getDocList:gotRoot");

        // get the node we're querying starting at.
        SubNode node = read.getNode(ms, new ObjectId(nodeId));
        if (!(node != null))
            return doc;
        perf.chain("getDocList:gotScanStart");

        int rootSlashCount = StringUtils.countMatches(rootNode.getPath(), "/");

        // entry point into recusion
        if (!recurseDocList(doc, ms, node, rootSlashCount, includeComments, truncates)) {
            // log.debug("Started from root. Found enough");
            return doc;
        }
        perf.chain("getDocList:initialRecurse");

        if (node.getIdStr().equals(rootId)) {
            // log.debug("Started from root. Nothing more to scan");
            return doc;
        }

        // if we jave enough we're done.
        while (doc.size() < MAX_DOC_ITEMS_PER_CALL) {
            perf = new PerfMonEvent(0, null, ms.getUserName());
            /*
             * General Algorighm
             * 
             * if the subgraph under 'doc' didn't lead to capturing enough doc items when we simply process all
             * the siblings below 'node', to gather more, and complete the level of the tree where 'node' was a
             * child, but if we STILL didn't get enough doc items we go up a level on the tree and repeat the
             * process of building doc items, until we reach a node with rootId, and then we know that's the end
             * of the subgraph under rootId
             */

            // log.debug("siblingsBelow " + node.getContent() + " ordinal=" + node.getOrdinal());
            // IMPORTANT: build this critera BEFORE we set 'node' to the parent.
            Criteria siblingsBelow = Criteria.where(SubNode.ORDINAL).gt(node.getOrdinal());

            if (!includeComments) {
                siblingsBelow = siblingsBelow.and(SubNode.TYPE).ne(NodeType.COMMENT);
            }

            node = read.getParent(ms, node);
            perf.chain("docListGotParent");
            if (node == null) {
                log.warn("oops, no parent. This should never happen!");
                break;
            }

            // log.debug("Processing remaining children under: " + node.getContent());

            Iterable<SubNode> nodeIter = read.getChildren(ms, node, Sort.by(Sort.Direction.ASC, SubNode.ORDINAL),
                    MAX_DOC_ITEMS_PER_CALL, 0, siblingsBelow);
            Iterator<SubNode> iterator = nodeIter.iterator();

            perf.chain("docListGotChildren");

            // iterates here for all siblings of 'node' that are below it in ordinal order.
            while (iterator.hasNext()) {
                SubNode sibling = iterator.next();
                perf.chain("docListGotNextChild");
                // log.debug("sibling[" + count + "] " + sibling.getContent() + " ordinal=" + sibling.getOrdinal());
                if (!recurseDocList(doc, ms, sibling, rootSlashCount, includeComments, truncates)) {
                    break;
                }
            }

            // if we just processed the rest of the children of the root, we're done.
            if (node.getIdStr().equals(rootId)) {
                // log.debug("out of children under root. done.");
                break;
            }
        }

        // always throw away the first node which will nodeId
        SubNode first = doc.removeFirst();
        if (!first.getIdStr().equals(nodeId)) {
            log.error("Algorithm failed. Document scan list had wrong first item, not nodeId " + nodeId + " as expected, but "
                    + first.getIdStr());
        }

        // log.debug("Returning: " + XString.prettyPrint(doc));
        return doc;
    }

    // Adds 'node' and it's entire subgraph (up to limits) into 'doc'
    // returns false to terminate then we have enough doc items
    public boolean recurseDocList(LinkedList<SubNode> doc, MongoSession ms, SubNode node, int rootSlashCount,
            boolean includeComments, HashSet<String> truncates) {
        if (!includeComments && node.isType(NodeType.COMMENT)) {
            return true;
        }
        doc.add(node);

        int thisSlashCount = StringUtils.countMatches(node.getPath(), "/");
        int depth = thisSlashCount - rootSlashCount;
        if (depth < 0) {
            throw new RuntimeException("oops depth is negative.");
        }
        // log.debug("RECURSE: " + " ".repeat(depth) + node.getContent() + " ordinal=" + node.getOrdinal());

        if (depth >= MAX_DOC_DEPTH) {
            // log.debug("MAX DEPTH (ignoring). " + node.getContent());
            if (truncates != null && hasChildren(ms, node, true, false)) {
                truncates.add(node.getIdStr());
            }
            // return true to keep iterating, although we're ignoring these 'too deep' ones.
            return true;
        }

        // if we reach iteration limits return false to unwind, we're done
        if (doc.size() >= MAX_DOC_ITEMS_PER_CALL) {
            // log.debug("MAX ITEMS.");
            return false;
        }

        // The rest of the below is about processing children, so we can try to optimize by getting the
        // parent of this node, and if it has no children we can bail out here.
        if (SubNode.USE_HAS_CHILDREN && noChildren(node)) {
            return true;
        }

        Criteria typeCriteria = !includeComments ? Criteria.where(SubNode.TYPE).ne(NodeType.COMMENT) : null;

        PerfMonEvent perf = new PerfMonEvent(0, null, ms.getUserName());

        Iterable<SubNode> iter =
                read.getChildren(ms, node, Sort.by(Sort.Direction.ASC, SubNode.ORDINAL), MAX_DOC_ITEMS_PER_CALL, 0, typeCriteria);
        perf.chain("recurseDocList:query");

        for (SubNode n : iter) {
            perf.chain("recurseDocList:queryIter");
            if (!recurseDocList(doc, ms, n, rootSlashCount, includeComments, truncates)) {
                return false;
            }
        }
        return true;
    }

    // Generates the logicalOrdinal of node by counting all the nodes that have a 'lower ordinal' than
    // it does. If no nodes are lower in ordinal that makes it the top one, and thus 0th ordinal, etc.
    public long generateLogicalOrdinal(MongoSession ms, SubNode node) {
        Query q = new Query();
        Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(node.getParentPath())) //
                .and(SubNode.ORDINAL).lt(node.getOrdinal());
        q.addCriteria(crit);
        return ops.count(q, SubNode.class);
    }
}
