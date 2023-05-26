package quanta.mongo;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.CriteriaDefinition;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Component;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.result.DeleteResult;
import lombok.extern.slf4j.Slf4j;
import quanta.config.NodePath;
import quanta.config.ServiceBase;
import quanta.exception.base.RuntimeEx;
import quanta.model.client.Attachment;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.mongo.model.SubNode;
import quanta.request.DeleteNodesRequest;
import quanta.response.DeleteNodesResponse;
import quanta.util.Const;
import quanta.util.ThreadLocals;
import quanta.util.XString;
import quanta.util.val.IntVal;
import quanta.util.val.LongVal;
import quanta.util.val.Val;

/**
 * Performs the 'deletes' (as in CRUD) operations for deleting nodes in MongoDB
 */
@Component
@Slf4j
public class MongoDelete extends ServiceBase {
	public void deleteNode(MongoSession ms, SubNode node, boolean childrenOnly, boolean deleteAttachments) {
		auth.ownerAuth(ms, node);
		if (!childrenOnly && deleteAttachments) {
			if (node.getAttachments() != null) {
				node.getAttachments().forEach((String key, Attachment att) -> {
					attach.deleteBinary(ms, key, node, null, true);
				});
			}
		}

		delete(ms, node, childrenOnly);
	}

	/*
	 * When a user is creating a new node we leave FIELD_MODIFY_TIME null until their first save of it
	 * and during the time it's null no other users can see the node. However the user can also abandon
	 * the browser or cancel the editing and orphan the node that way, and this method which we call
	 * only at startup, cleans up any and all of the orphans
	 */
	public void removeAbandonedNodes(MongoSession ms) {
		Query q = new Query();
		q.addCriteria(Criteria.where(SubNode.MODIFY_TIME).is(null));

		DeleteResult res = ops.remove(q, SubNode.class);
		log.debug("Num abandoned nodes deleted: " + res.getDeletedCount());
	}

	/*
	 * DO NOT DELETE
	 *
	 * (this is referenced elsewhere, and is not currently used but we DO WANT TO KEEP IT.) This assumes
	 * the constraint violation is not caused by an orphan node being the duplicate, and actually
	 * running this code is therefore only SAFE after running a full and complete orphan delete process.
	 */
	public void removeFriendConstraintViolations(MongoSession ms) {
		Query q = new Query();

		// query for all FRIEND nodes (will represent both blocks and friends)
		Criteria crit = Criteria.where(SubNode.TYPE).is(NodeType.FRIEND.s());
		q.addCriteria(crit);

		HashSet<String> keys = new HashSet<>();

		Iterable<SubNode> nodes = mongoUtil.find(q);
		for (SubNode node : nodes) {
			if (node.getOwner() == null)
				continue;

			String key = node.getOwner().toHexString() + "-" + node.getStr(NodeProp.USER_NODE_ID);
			if (keys.contains(key)) {
				delete(ms, node);
			} else {
				keys.add(key);
			}
		}

		update.saveSession(ms);
	}

	/*
	 * note: Running this will technically invalidate the 'node.hasChildren' on all parent nodes of the
	 * deleted nodes, but this is ok, because what we deleted was just cached nodes from foreign servers
	 * and if there are still any posts left in the foreign user account then actually the
	 * 'node.hasChildren' will remain correct. So there's no reason to worry about damaging the
	 * 'correctness' of 'hasChildren' for this scenario
	 */
	public long deleteOldActPubPosts(int monthsOld, MongoSession ms) {
		Query q = new Query();
		LocalDate ldt = LocalDate.now().minusDays(30 * monthsOld);
		Date date = Date.from(ldt.atStartOfDay(ZoneId.systemDefault()).toInstant());

		Criteria crit = Criteria.where(SubNode.PROPS + "." + NodeProp.ACT_PUB_OBJ_TYPE).ne(null) //
				.and(SubNode.MODIFY_TIME).lt(date);

		q.addCriteria(crit);
		DeleteResult res = ops.remove(q, SubNode.class);
		return res.getDeletedCount();
	}

	public long deleteOldNostrPosts(int monthsOld, MongoSession ms) {
		Query q = new Query();
		LocalDate ldt = LocalDate.now().minusDays(30 * monthsOld);
		Date date = Date.from(ldt.atStartOfDay(ZoneId.systemDefault()).toInstant());

		Criteria crit = Criteria.where(SubNode.PROPS + "." + NodeProp.OBJECT_ID).regex("^\\.") //
				.and(SubNode.MODIFY_TIME).lt(date);

		q.addCriteria(crit);
		DeleteResult res = ops.remove(q, SubNode.class);
		return res.getDeletedCount();
	}

	/*
	 * This is a way to cleanup old records, but it's not needed yet.
	 * 
	 * BEWARE: the updating of 'node.hasChildren' will need to be done like we do in other places in
	 * theis file using a batch update. For now this method is not used.
	 */
	public void cleanupOldTempNodesForUser(MongoSession ms, SubNode userNode) {
		Query q = new Query();

		LocalDate ldt = LocalDate.now().minusDays(5);
		Date date = Date.from(ldt.atStartOfDay(ZoneId.systemDefault()).toInstant());

		Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexRecursiveChildrenOfPath(userNode.getPath())) //
				.and(SubNode.MODIFY_TIME).lt(date); //

		q.addCriteria(crit);

		// set all the parents of all nodes in 'q' to null child status
		bulkSetPropValOnParents(ms, q, SubNode.HAS_CHILDREN, null);

		// Example: Time Range check:
		// query.addCriteria(Criteria.where("startDate").gte(startDate).lt(endDate));

		DeleteResult res = ops.remove(q, SubNode.class);
		if (res.getDeletedCount() > 0) {
			log.debug("Temp Records Deleted (Under User: " + userNode.getIdStr() + "): " + res.getDeletedCount());
		}
	}

	/**
	 * This method assumes security check is already done.
	 */
	public long deleteUnderPath(MongoSession ms, String path) {
		Query q = new Query();
		q.addCriteria(Criteria.where(SubNode.PATH).regex(mongoUtil.regexRecursiveChildrenOfPath(path)));

		Criteria crit = auth.addSecurityCriteria(ms, null);
		if (crit != null) {
			q.addCriteria(crit);
		}

		SubNode parent = read.getNode(ms, path);
		if (parent != null) {
			parent.setHasChildren(false);
		}

		DeleteResult res = ops.remove(q, SubNode.class);
		return res.getDeletedCount();
	}

	// deletes without checking any security
	public void adminDelete(ObjectId id) {
		ops.remove(new Query().addCriteria(new Criteria("id").is(id)), SubNode.class);
	}

	/**
	 * Currently cleaning up GridFS orphans is done in gridMaintenanceScan() only, so when we delete one
	 * ore more nodes, potentially orphaning other nodes or GRID nodes (binary files), those orphans
	 * will get cleaned up later on, but not synchronously or in this method.
	 * 
	 * todo-2: However, we could also have the 'path' stored in the GridFS metadata so we can use a
	 * 'regex' query to delete all the binaries (recursively under any node, using that path prefix as
	 * the criteria) which is exacly like the one below for deleting the nodes themselves.
	 * 
	 * UPDATE: In here we call "regexRecursiveChildrenOfPath" which does the most aggressive possible
	 * delete of the entire subgraph, but it would be more performant to use a non-recursive delete ONLY
	 * of the direct children under 'node' (using "regexDirectChildrenOfPath" instead) in this
	 * synchronous call and let the orphan delete do the rest of the cleanup later on asynchronously.
	 * However we do NOT do that, only because our orphan cleanup algo is already memory intensive and
	 * so we are decreasing the memory load of the orphan cleanup routing by doing the most aggressive
	 * tree delete possible here, synchronously, at he cost of a little performance.
	 * 
	 * Said more simply: Replace 'regexRecursiveChildrenOfPath' with 'regexDirectChildrenOfPath' in this
	 * method if you want to increase performance of deletes at the cost of some additional memory.
	 */
	public long delete(MongoSession ms, SubNode node, boolean childrenOnly) {
		auth.ownerAuth(ms, node);
		log.debug("Deleting under path: " + node.getPath());

		update.saveSession(ms);

		/*
		 * First delete all the children of the node by using the path, knowing all their paths 'start with'
		 * (as substring) this path. Note how efficient it is that we can delete an entire subgraph in one
		 * single operation!
		 */
		Query q = new Query();
		q.addCriteria(Criteria.where(SubNode.PATH).regex(mongoUtil.regexRecursiveChildrenOfPath(node.getPath())));

		DeleteResult res = ops.remove(q, SubNode.class);
		log.debug("Num of SubGraph deleted: " + res.getDeletedCount());
		long totalDelCount = res.getDeletedCount();

		/*
		 * Yes we DO have to remove the node itself separate from the remove of all it's subgraph, because
		 * in order to be perfectly safe the recursive subgraph regex MUST designate the slash AFTER the
		 * root path to be sure we get the correct node, other wise deleting /ab would also delete /abc for
		 * example. so we must have our recursive delete identify deleting "/ab" as starting with "/ab/"
		 */
		if (!childrenOnly) {
			DeleteResult ret = delete(ms, node);
			totalDelCount += ret.getDeletedCount();
		} else {
			// if we're deleting children only we just update the node's hasChildren to false. We know there are
			// none
			node.setHasChildren(false);
		}
		return totalDelCount;
	}

	/*
	 * Note: This method doesn't remove orphans of the node.
	 */
	public DeleteResult delete(MongoSession ms, SubNode node) {
		SubNode parent = read.getParent(ms, node, false);
		if (parent != null) {
			parent.setHasChildren(null);
		}
		return ops.remove(node);
	}

	public void directDelete(SubNode node) {
		ops.remove(node);
	}

	public void deleteByPropVal(MongoSession ms, String prop, String val) {
		// log.debug("Deleting by prop=" + prop + " val=" + val);
		Query q = new Query();
		Criteria crit = Criteria.where(SubNode.PROPS + "." + prop).is(val);
		crit = auth.addSecurityCriteria(ms, crit);
		q.addCriteria(crit);

		// since we're deleting all nodes matching the query 'q' we set the parents of all those nodes do
		// unknown children state
		bulkSetPropValOnParents(ms, q, SubNode.HAS_CHILDREN, null);

		// look for all calls to 'ops.remove' just to doublecheck none of them need the above
		// 'bulkSetPropValOnParents'
		DeleteResult res = ops.remove(q, SubNode.class);
		log.debug("Nodes deleted: " + res.getDeletedCount());
	}

	// iterates over 'q' setting prop=val on ever PARENT node of all those nodes.
	public void bulkSetPropValOnParents(MongoSession ms, Query q, String prop, Object val) {
		Val<BulkOperations> bops = new Val<>(null);
		IntVal batchSize = new IntVal();

		// this hash set just makes sure we only submit each val set once! No replicated work.
		HashSet<ObjectId> parentIds = new HashSet<>();
		long threadId = Thread.currentThread().getId();

		ops.stream(q, SubNode.class).forEachRemaining(node -> {
			// lazy careate bops
			if (!bops.hasVal()) {
				bops.setVal(ops.bulkOps(BulkMode.UNORDERED, SubNode.class));
			}

			// since I'm new to ops.stream, I don't trust it to be threadsafe yet.
			if (threadId != Thread.currentThread().getId()) {
				throw new RuntimeException("ops.stream unexpected concurrency");
			}

			SubNode parent = read.getParent(ms, node, false);
			if (parent != null && parentIds.add(parent.getId())) {
				// we have a known 'bops' in this one and don't lazy create so we don't care about the
				// return value of this call
				update.bulkOpSetPropVal(bops.getVal(), parent.getId(), prop, val);
				batchSize.inc();
			}

			if (batchSize.getVal() > Const.MAX_BULK_OPS) {
				bops.getVal().execute();
				batchSize.setVal(0);
				bops.setVal(null);
			}
		});

		if (bops.hasVal()) {
			bops.getVal().execute();
		}
	}

	public void bulkSetPropsByIdObjs(Collection<ObjectId> ids, String prop, Object val) {
		BulkOperations bops = null;
		int batchSize = 0;

		for (ObjectId id : ids) {
			bops = update.bulkOpSetPropVal(bops, id, prop, null);
			if (++batchSize > Const.MAX_BULK_OPS) {
				bops.execute();
				batchSize = 0;
				bops = null;
			}
		}

		if (bops != null) {
			bops.execute();
		}
	}

	public void bulkSetPropsByIdStr(Collection<String> ids, String prop, Object val) {
		BulkOperations bops = null;
		int batchSize = 0;

		for (String id : ids) {
			bops = update.bulkOpSetPropVal(bops, new ObjectId(id), prop, null);
			if (++batchSize > Const.MAX_BULK_OPS) {
				bops.execute();
				batchSize = 0;
				bops = null;
			}
		}

		if (bops != null) {
			bops.execute();
		}
	}

	/*
	 * This version of deleteNodeOrphans will run slower than the one below, but uses essentially no
	 * memory
	 */
	public void deleteNodeOrphans() {
		Val<BulkOperations> bops = new Val<>(null);

		LongVal totalDeleted = new LongVal();
		LongVal opsPending = new LongVal();
		LongVal deletesInPass = new LongVal();
		int passes = 0;

		// run up to 5 passes over the whole DB (orphan trees deeper than 5 levels deep
		// won't all be cleaned but that's ok, they will get pruned off in future runs.
		while (passes++ < 5) {

			// starting a new pass, so zero deletes so far in this pass
			deletesInPass.setVal(0L);

			// scan the entire DB
			ops.stream(new Query(), SubNode.class).forEachRemaining(node -> {

				// if this node is root node, ignore
				if (NodePath.ROOT_PATH.equals(node.getPath()))
					return;

				// if this node's parent is root, also ignore it.
				String parentPath = node.getParentPath();
				if (NodePath.ROOT_PATH.equals(parentPath))
					return;

				// query to see if node's parent exists.
				Query q = new Query();
				q.addCriteria(Criteria.where(SubNode.PATH).is(parentPath));
				SubNode parent = mongoUtil.findOne(q);

				// if parent node doesn't exist, this is an orphan we can delete.
				if (parent == null) {
					// log.debug("orphan[" + node.getPath() + "]: " + node.getContent());

					// lazy create our bulk ops here.
					if (bops.getVal() == null) {
						bops.setVal(ops.bulkOps(BulkMode.UNORDERED, SubNode.class));
					}

					// add bulk ops command to delete this orphan
					bops.getVal().remove(new Query().addCriteria(new Criteria("id").is(node.getId())));

					// update counters
					opsPending.inc();
					deletesInPass.inc();

					if (opsPending.getVal() > Const.MAX_BULK_OPS) {
						BulkWriteResult results = bops.getVal().execute();
						totalDeleted.add(results.getDeletedCount());
						log.debug("DEL TOTAL: " + totalDeleted.getVal());
						bops.setVal(null);
						opsPending.setVal(0L);
					}
				}
			});

			// after the DB scan is done delete the remainder left in ops pending
			if (opsPending.getVal() > 0) {
				BulkWriteResult results = bops.getVal().execute();
				totalDeleted.add(results.getDeletedCount());
				log.debug("remainders. DEL TOTAL: " + totalDeleted.getVal());
				bops.setVal(null);
				opsPending.setVal(0L);
			}

			if (deletesInPass.getVal() == 0L) {
				log.debug("no orphans found in last pass. done.");
				break;
			}
		}

		// todo-2: broadcast this back to server as a message push
		log.debug("TOTAL ORPHANS DELETED=" + totalDeleted.getVal());
	}

	/*
	 * Deletes Orphan Nodes.
	 * 
	 * Orphan nodes are ones that have a path whose parent path doesn't exist. This version of an
	 * implementation requires ALL paths of all nodes to fit into RAM but there's an even simpler
	 * approach that would be just to scan all nodes and for each one do the 'exist' check on it's
	 * parent and delete those who don't have an existing parent.
	 * ------------------------------------------------------------------------
	 * 
	 * #optimization: Can add Verify & Repair HAS_CHILDREN in this method.
	 * 
	 * Since every node looks for it's parent in this process we could theoretically use this to also
	 * perfectly verify and/or repair every HAS_CHILDREN in the system. We'd just keep a list if which
	 * nodes claim they DO have children in a HashSet of those ObjectIds, and then as we encounter each
	 * one as a parent we "find" in this as we go along remove from the HashSet, as "correct" so that
	 * whatever's left in said HashSet when the entire process is completed will be nodes that are known
	 * to claim to have children and don't. Then do we do the 'inverse of that' to fix the nodes that
	 * claim NOT to have children but DO have children...which would be a separate hash map doing the
	 * same kind of logic.
	 */
	public void deleteNodeOrphans_fast() {
		log.debug("deleteNodeOrphans()");

		Val<BulkOperations> bops = new Val<>(null);
		IntVal nodesProcessed = new IntVal();
		LongVal opsPending = new LongVal();
		LongVal totalDeleted = new LongVal();

		// map every path to it's ObjectId
		HashMap<String, ObjectId> allNodes = new HashMap<>();

		// first all we do is build up the 'allNodes' hashMap.
		ops.stream(new Query(), SubNode.class).forEachRemaining(node -> {
			// print progress every 1000th node
			nodesProcessed.inc();
			if (nodesProcessed.getVal() % 1000 == 0) {
				log.debug("SCAN: " + nodesProcessed.getVal());
			}

			allNodes.put(node.getPath(), node.getId());
		});

		nodesProcessed.setVal(0);

		int passes = 0;
		while (passes++ < 20) {
			log.debug("Running Orphan Pass: " + passes);
			LongVal deletesInPass = new LongVal();
			HashMap<String, ObjectId> orphans = new HashMap<>();

			// scan every node we still have and any whose parent is not also in the map is a known orphan
			allNodes.entrySet().stream().forEach(entry -> {
				nodesProcessed.inc();
				if (nodesProcessed.getVal() % 1000 == 0) {
					log.debug("FINDER SCAN: " + nodesProcessed.getVal());
				}

				// log.debug("STRM: " + entry.getKey());
				if (entry.getKey().equals(NodePath.ROOT_PATH)) {
					// log.debug("ROOT NODE: " + entry.getValue().toHexString());
					return;
				}

				String parent = XString.truncAfterLast(entry.getKey(), "/");
				if (parent.equalsIgnoreCase(NodePath.ROOT_PATH)) {
					// log.debug("ROOT CHILD: " + entry.getValue().toHexString());
					return;
				}

				if (!allNodes.containsKey(parent)) {
					log.debug("ORPH: " + entry.getValue().toHexString());

					// put the stuff to delete in a separate map to avoid a concurrent modification exception
					orphans.put(entry.getKey(), entry.getValue());
				}
			});

			// found no orphans at all then we're done.
			if (orphans.size() == 0) {
				log.debug("No more orphans found. Done!");

				// breaks out of while loop of 'passes'
				break;
			}

			// delete all orphans identified in this pass
			orphans.entrySet().stream().forEach(entry -> {
				// lazy create bops
				if (bops.getVal() == null) {
					bops.setVal(ops.bulkOps(BulkMode.UNORDERED, SubNode.class));
				}

				allNodes.remove(entry.getKey());
				bops.getVal().remove(new Query().addCriteria(new Criteria("id").is(entry.getValue())));

				opsPending.inc();
				deletesInPass.inc();

				if (opsPending.getVal() > Const.MAX_BULK_OPS) {
					BulkWriteResult results = bops.getVal().execute();
					totalDeleted.add(results.getDeletedCount());
					log.debug("DEL TOTAL: " + totalDeleted.getVal());
					bops.setVal(null);
					opsPending.setVal(0L);
				}
			});

			// since we delete in blocks of 100 at a time, we might have some left pending here so
			// finish deleting those
			if (opsPending.getVal() > 0) {
				BulkWriteResult results = bops.getVal().execute();
				totalDeleted.add(results.getDeletedCount());
				log.debug("remainders. DEL TOTAL: " + totalDeleted.getVal());
				bops.setVal(null);
				opsPending.setVal(0L);
			}

			log.debug("Deletes in Pass: " + deletesInPass.getVal());
		}

		log.debug("TOTAL ORPHANS DELETED=" + totalDeleted.getVal());
	}

	/*
	 * Deletes the set of nodes specified in the request
	 */
	public DeleteNodesResponse deleteNodes(MongoSession ms, DeleteNodesRequest req) {
		DeleteNodesResponse res = new DeleteNodesResponse();

		SubNode userNode = read.getUserNodeByUserName(null, null);
		if (userNode == null) {
			throw new RuntimeEx("User not found.");
		}

		BulkOperations bops = null;
		List<SubNode> nodes = new LinkedList<>();
		HashSet<ObjectId> parentIds = new HashSet<>();
		int batchSize = 0;

		for (String nodeId : req.getNodeIds()) {
			// lookup the node we're going to delete, we call with allowAuth, becasuse it would be redundant
			// since the next thing we do is an 'ownerAuth', which is even more restrictive
			SubNode node = read.getNode(ms, nodeId, false, null);
			if (node == null)
				continue;
			auth.ownerAuth(ms, node);

			// get the parent of the node and add it's id to parentIds
			SubNode parent = read.getParent(ms, node, false);
			if (parent == null) {
				/*
				 * if node has no parent it's an orphan, so we should act like it doesn't even exist, and it's
				 * essentially already deleted. todo-1: Could we have a background queue mow thru "Known Orphans"
				 * like this one, because any subnodes of it can be blown away once you identify an orphan by
				 * whatever means it was.
				 */
				continue;
			}

			// back out the number of bytes it was using
			if (!ms.isAdmin()) {
				/*
				 * NOTE: There is no equivalent to this on the IPFS code path for deleting ipfs becuase since we
				 * don't do reference counting we let the garbage collecion cleanup be the only way user quotas are
				 * deducted from.
				 * 
				 * todo-1: Also this is incorrect for now. If the user deletes a deep subgraph of nodes we don't
				 * grant them back the space, so this would rob users of some space. Need to fix that.
				 */
				long totalBytes = user.getTotalAttachmentBytes(ms, node);
				user.addBytesToUserNodeBytes(ms, -totalBytes, userNode);
			}

			// really need a 'hasForeignShares' here to ignore if there aren't any (todo-2)
			if (node.getAc() != null) {
				nodes.add(node);
			}

			/*
			 * if 'add' returns true that means this IS the first encounter and so we add to the operations the
			 * call to set it's hasChildren to null. Note setting HAS_CHILDREN to null doesn't indicate we think
			 * there's no chilren it indicates we don't KNOW if it still has children or not.
			 */
			if (parentIds.add(parent.getId())) {
				bops = update.bulkOpSetPropVal(bops, parent.getId(), SubNode.HAS_CHILDREN, null);
			}

			/*
			 * we remove the actual specified nodes synchronously here (instead of including root in
			 * deleteSubGraphChildren below), so that the client can refresh as soon as it wants and get back
			 * correct results.
			 */
			bops = bulkOpRemoveNode(bops, node.getId());
			if (++batchSize > Const.MAX_BULK_OPS) {
				bops.execute();
				batchSize = 0;
				bops = null;
			}
		}

		// in async thread send out all the deletes to the foreign servers.
		exec.run(() -> {
			nodes.forEach(n -> {
				apub.sendNodeDelete(ms, snUtil.getIdBasedUrl(n), snUtil.cloneAcl(n));
				deleteSubGraphChildren(ms, n, false);
			});
		});

		if (bops != null) {
			bops.execute();
		}

		update.saveSession(ms);
		res.setSuccess(true);
		return res;
	}

	/*
	 * Note: this method assumes that the user associated with 'ms' already has been checked for
	 * authorization to delete 'node' (owns the node)
	 */
	public void deleteSubGraphChildren(MongoSession ms, SubNode node, boolean includeRoot) {
		BulkOperations bops = null;

		// if deleting root do it first because the getSubGraph query doesn't include root.
		if (includeRoot) {
			// if deleting children AND root then the parent of this root is the one we need to update the
			// hasChildren for
			SubNode parent = read.getParent(ms, node, false);
			if (parent != null) {
				bops = update.bulkOpSetPropVal(bops, parent.getId(), SubNode.HAS_CHILDREN, null);
			}
			bops = bulkOpRemoveNode(bops, node.getId());
		}
		// if deleting all children and NOT root we know we can just update the node hasChildren to false
		else {
			bops = update.bulkOpSetPropVal(bops, node.getId(), SubNode.HAS_CHILDREN, null);
		}

		int opCount = 0;
		/*
		 * Now Query the entire subgraph of this deleted 'node'
		 * 
		 * todo-1: Actually we can do even better here, and just run a single command 'delete' op on the
		 * underlying query that this getSubGraph ends up using, and not even need a bulk op.
		 */
		for (SubNode child : read.getSubGraph(ms, node, null, 0, false, false, false)) {
			/*
			 * NOTE: Disabling this ability to recursively delete from foreign servers because I'm not sure they
			 * won't interpret that as a DDOS attack if this happens to be a large delete underway. This will
			 * take some thought, to engineer where perhaps we limit to just 10, and do them over a period of 10
			 * minutes even, and that kind of thing, but for now we can just get by without this capability
			 */
			// apub.sendActPubForNodeDelete(ms, snUtil.getIdBasedUrl(child), snUtil.cloneAcl(child));
			bops = bulkOpRemoveNode(bops, child.getId());

			if (++opCount > Const.MAX_BULK_OPS) {
				bops.execute();
				bops = null;
				opCount = 0;
			}
		}

		// deletes all nodes in this subgraph branch
		if (bops != null) {
			bops.execute();
		}
	}

	// returns a new BulkOps if one not yet existing
	public BulkOperations bulkOpRemoveNode(BulkOperations bops, ObjectId id) {
		if (bops == null) {
			bops = ops.bulkOps(BulkMode.UNORDERED, SubNode.class);
		}
		Query query = new Query().addCriteria(new Criteria("id").is(id));
		bops.remove(query);
		return bops;
	}

	/*
	 * Deletes the set of nodes specified in the request
	 */
	public DeleteNodesResponse bulkDeleteNodes(MongoSession ms) {
		DeleteNodesResponse res = new DeleteNodesResponse();

		SubNode userNode = read.getUserNodeByUserName(null, null);
		if (userNode == null) {
			throw new RuntimeEx("User not found.");
		}

		Query q = new Query();

		// criteria finds all nodes where we are the owner but they're not decendants under our own tree
		// root.
		Criteria crit = Criteria.where(SubNode.OWNER).is(userNode.getOwner()).and(SubNode.PATH).not()
				.regex(mongoUtil.regexRecursiveChildrenOfPathIncludeRoot(userNode.getPath()));
		q.addCriteria(crit);

		// we'll be deleting every node in 'q' so we need to set the parents of all those to
		// hasChildren=null (unknown)
		bulkSetPropValOnParents(ms, q, SubNode.HAS_CHILDREN, null);

		/*
		 * This will potentially leave orphans and this is fine. We don't bother cleaning orphans now
		 * because there's no need.
		 */
		DeleteResult delRes = ops.remove(q, SubNode.class);
		String msg = "Nodes deleted: " + delRes.getDeletedCount();
		log.debug("Bulk Delete: " + msg);
		res.setMessage(msg);

		res.setSuccess(true);
		return res;
	}

	// Deletes all matches to this search criteria. Very dangerous! Only admin can run.
	public void deleteMatches(MongoSession ms, SubNode node, String prop, String text, boolean fuzzy, boolean caseSensitive,
			String timeRangeType, boolean recursive, boolean requirePriority) {
		ThreadLocals.requireAdmin();
		List<CriteriaDefinition> criterias = new LinkedList<>();

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

		// Only allow deleting of TEXT-ish type node!!!
		// Bad things can happen if we delete other nodes, even that contain
		// "bad" content (N-word racial slur, etc.), because when people put those same words in their
		// BLOCKED WORDS
		// list in their accounts we don't want to mistake that for actual USE of the word, or for places
		// where
		// people might have blocked someone with an offensive username, we don't want to DELETE those
		// blocked user
		// nodes either, so for now we just delete if the type is a comment
		crit = Criteria.where(SubNode.TYPE).in(NodeType.COMMENT.s(), NodeType.NOSTR_ENC_DM.s(), NodeType.NONE.s(), NodeType.PLAIN_TEXT.s());

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
				 * This reurns ONLY nodes containing BOTH (not any) #tag1 and #tag2 so this is sure seems like a
				 * MongoDb bug. (or a Lucene bug possibly to be exact), so I've confirmed it's basically impossible
				 * to do an OR search on strings containing special characters, without the special characters
				 * basically being ignored.
				 */
				// textCriteria.matchingAny("\"#tag1\"", "\"#tag2\"");

				textCriteria.matching(text);
				textCriteria.caseSensitive(caseSensitive);
				criterias.add(textCriteria);
			}
		}

		if (requirePriority) {
			criterias.add(Criteria.where(SubNode.PROPS + ".priority").gt("0"));
		}

		Query q = new Query();

		for (CriteriaDefinition c : criterias) {
			q.addCriteria(c);
		}

		ops.remove(q, SubNode.class);
	}
}
