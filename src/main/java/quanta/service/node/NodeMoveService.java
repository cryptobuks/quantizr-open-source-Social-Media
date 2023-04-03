package quanta.service.node;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.exception.base.RuntimeEx;
import quanta.model.client.NodeProp;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.JoinNodesRequest;
import quanta.request.MoveNodesRequest;
import quanta.request.SelectAllNodesRequest;
import quanta.request.SetNodePositionRequest;
import quanta.response.JoinNodesResponse;
import quanta.response.MoveNodesResponse;
import quanta.response.SelectAllNodesResponse;
import quanta.response.SetNodePositionResponse;
import quanta.util.Const;
import quanta.util.ThreadLocals;

/**
 * Service for controlling the positions (ordinals) of nodes relative to their parents and/or moving
 * nodes to locate them under a different parent. This is similar type of functionality to
 * cut-and-paste in file systems. Currently there is no way to 'clone' or copy nodes, but user can
 * move any existing nodes they have to any new location they want, subject to security constraints
 * of course.
 */
@Component
@Slf4j 
public class NodeMoveService extends ServiceBase {
	/*
	 * Moves the the node to a new ordinal/position location (relative to parent)
	 *
	 * We allow the special case of req.siblingId="[topNode]" and that indicates move the node to be the
	 * first node under its parent.
	 */
	public SetNodePositionResponse setNodePosition(MongoSession ms, SetNodePositionRequest req) {
		SetNodePositionResponse res = new SetNodePositionResponse();

		String nodeId = req.getNodeId();
		SubNode node = read.getNode(ms, nodeId);
		auth.ownerAuth(ms, node);
		if (node == null) {
			throw new RuntimeEx("Node not found: " + nodeId);
		}

		if ("up".equals(req.getTargetName())) {
			moveNodeUp(ms, node);
		} else if ("down".equals(req.getTargetName())) {
			moveNodeDown(ms, node);
		} else if ("top".equals(req.getTargetName())) {
			moveNodeToTop(ms, node);
		} else if ("bottom".equals(req.getTargetName())) {
			moveNodeToBottom(ms, node);
		} else {
			throw new RuntimeEx("Invalid target type: " + req.getTargetName());
		}

		res.setSuccess(true);
		return res;
	}

	public void moveNodeUp(MongoSession ms, SubNode node) {
		SubNode nodeAbove = read.getSiblingAbove(ms, node, null);
		if (nodeAbove != null) {
			Long saveOrdinal = nodeAbove.getOrdinal();
			nodeAbove.setOrdinal(node.getOrdinal());
			node.setOrdinal(saveOrdinal);
		}
		update.saveSession(ms);
	}

	public void moveNodeDown(MongoSession ms, SubNode node) {
		SubNode nodeBelow = read.getSiblingBelow(ms, node, null);
		if (nodeBelow != null) {
			Long saveOrdinal = nodeBelow.getOrdinal();
			nodeBelow.setOrdinal(node.getOrdinal());
			node.setOrdinal(saveOrdinal);
		}
		update.saveSession(ms);
	}

	public void moveNodeToTop(MongoSession ms, SubNode node) {
		SubNode parentNode = read.getParent(ms, node);
		if (parentNode == null) {
			return;
		}
		create.insertOrdinal(ms, parentNode, 0L, 1L);

		/*
		 * todo-2: there is a slight ineffieiency here in that 'node' does end up getting saved both as part
		 * of the insertOrdinal, and also then with the setting of it to zero. Will be easy to fix when I
		 * get to it.
		 */
		update.saveSession(ms);
		node.setOrdinal(0L);
		update.saveSession(ms);
	}

	public void moveNodeToBottom(MongoSession ms, SubNode node) {
		SubNode parentNode = read.getParent(ms, node);
		if (parentNode == null) {
			return;
		}
		long ordinal = read.getMaxChildOrdinal(ms, parentNode) + 1L;
		node.setOrdinal(ordinal);
		update.saveSession(ms);
	}

	/*
	 * Note: Browser can send nodes in any order, in the request, and always the lowest ordinal is the
	 * one we keep and join to.
	 */
	public JoinNodesResponse joinNodes(MongoSession ms, JoinNodesRequest req) {
		JoinNodesResponse res = new JoinNodesResponse();

		// add to list because we will sort
		ArrayList<SubNode> nodes = new ArrayList<SubNode>();
		String parentPath = null;

		// scan all nodes to verify we own them all, and they're all under same parent, and load all into
		// 'nodes'
		for (String nodeId : req.getNodeIds()) {
			SubNode node = read.getNode(ms, nodeId);

			if (parentPath == null) {
				parentPath = node.getParentPath();
			} else if (!parentPath.equals(node.getParentPath())) {
				res.setMessage("Failed: All nodes must be under the same parent node.");
				res.setSuccess(false);
				return res;
			}

			auth.ownerAuth(ms, node);

			if (read.hasChildren(ms, node, false, false)) {
				res.setMessage("Failed. Nodes to be joined cannot have any children/subnodes");
				res.setSuccess(false);
				return res;
			}

			nodes.add(node);
		}

		nodes.sort((s1, s2) -> (int) (s1.getOrdinal() - s2.getOrdinal()));

		StringBuilder sb = new StringBuilder();
		SubNode firstNode = null;
		int counter = 0;

		for (SubNode n : nodes) {
			if (firstNode == null) {
				firstNode = n;
			}
			if (counter > 0) {
				sb.append("\n");
			}

			if (!StringUtils.isEmpty(n.getContent())) {
				// trim and add ONE new line, for consistency.
				sb.append(n.getContent().trim());
				sb.append("\n");
			}

			// counter > 0 means we have a firstNode but are NOT now processing first node.
			if (counter > 0) {
				attach.mergeAttachments(n, firstNode);
				delete.deleteNode(ms, n, false, false);
			}
			counter++;
		}

		firstNode.setContent(sb.toString());
		firstNode.touch();
		update.saveSession(ms);
		res.setSuccess(true);
		return res;
	}

	/*
	 * Moves a set of nodes to a new location, underneath (i.e. children of) the target node specified.
	 */
	public MoveNodesResponse moveNodes(MongoSession ms, MoveNodesRequest req) {
		MoveNodesResponse res = new MoveNodesResponse();
		ms = ThreadLocals.ensure(ms);

		moveNodesInternal(ms, req.getLocation(), req.getTargetNodeId(), req.getNodeIds(), res);
		res.setSuccess(true);
		return res;
	}

	/*
	 * If req.location==inside then the targetId is the parent node we will be inserting children into,
	 * but if req.location==inline the targetId represents the child who will become a sibling of what
	 * we are inserting, and the inserted nodes will be pasted in directly below that ordinal (i.e. new
	 * siblings posted in below it)
	 */
	private void moveNodesInternal(MongoSession ms, String location, String targetId, List<String> nodeIds,
			MoveNodesResponse res) {
		// log.debug("moveNodesInternal: targetId=" + targetId + " location=" + location);

		// get targetNode which is node we're pasting at or into.
		SubNode targetNode = read.getNode(ms, targetId);
		SubNode parentToPasteInto = location.equalsIgnoreCase("inside") ? targetNode : read.getParent(ms, targetNode);

		auth.ownerAuth(ms, parentToPasteInto);
		String parentPath = parentToPasteInto.getPath();
		// log.debug("targetPath (pasting into this): " + parentPath);
		Long curTargetOrdinal = null;

		// location==inside
		if (location.equalsIgnoreCase("inside")) {
			curTargetOrdinal = read.getMaxChildOrdinal(ms, targetNode) + 1;
		}
		// location==inline (todo-2: rename this to inline-below -- or better yet, do an
		// enum)
		else if (location.equalsIgnoreCase("inline")) {
			curTargetOrdinal = targetNode.getOrdinal() + 1;
			create.insertOrdinal(ms, parentToPasteInto, curTargetOrdinal, nodeIds.size());
		}
		// location==inline-above
		else if (location.equalsIgnoreCase("inline-above")) {
			curTargetOrdinal = targetNode.getOrdinal();
			create.insertOrdinal(ms, parentToPasteInto, curTargetOrdinal, nodeIds.size());
		}

		String sourceParentPath = null;
		List<SubNode> nodesToMove = new ArrayList<SubNode>();
		SubNode nodeParent = null;

		for (String nodeId : nodeIds) {
			SubNode node = read.getNode(ms, nodeId);
			auth.ownerAuth(ms, node);

			// log.debug("Will be Moving ID (and children of): " + nodeId + " path: " + node.getPath());
			nodesToMove.add(node);

			/*
			 * Verify all nodes being pasted are siblings
			 */
			if (sourceParentPath != null && !sourceParentPath.equals(node.getParentPath())) {
				throw new RuntimeException("Nodes to move must be all from the same parent.");
			}
			sourceParentPath = node.getParentPath();

			// get the nodeParent if we don't have it already.
			if (nodeParent == null) {
				nodeParent = read.getParent(ms, node);
			}
		}

		// make sure nodes to move are in ordinal order.
		nodesToMove.sort((n1, n2) -> (int) (n1.getOrdinal() - n2.getOrdinal()));

		// process all nodes being moved.
		for (SubNode node : nodesToMove) {
			// log.debug("MovingID (and it's children): " + node.getIdStr() + "[" + node.getContent() + "] path: " + node.getPath());
			Long _targetOrdinal = curTargetOrdinal;
			SubNode _nodeParent = nodeParent;
			arun.run(as -> {
				/*
				 * If this 'node' will be changing parents (moving to new parent) we need to update its subgraph, of
				 * all children and also update its own path, otherwise it's staying under same parent and only it's
				 * ordinal will change.
				 */
				if (!_nodeParent.getPath().equals(parentToPasteInto.getPath())) {
					// log.debug("pasting going into DIFFERENT parent");
					/*
					 * if a parent node is attempting to be pasted into one of it's children that's an impossible move
					 * so we reject the attempt.
					 */
					if (parentToPasteInto.getPath().startsWith(node.getPath())) {
						throw new RuntimeException("Impossible node move requested.");
					}

					// find any new Path available under the paste target location 'parentPath'
					String newPath = mongoUtil.findAvailablePath(parentPath + "/"); // + node.getLastPathPart());
					// log.debug("New Available Path Found: " + newPath);
					changePathOfSubGraph(as, node, node.getPath(), newPath, res, false);

					node.setPath(newPath);

					// crypto sig uses path as part of it, so we just invalidated the signature.
					if (node.getStr(NodeProp.CRYPTO_SIG) != null) {
						node.delete(NodeProp.CRYPTO_SIG);
						if (res != null) {
							res.setSignaturesRemoved(true);
						}
					}
					// log.debug("Final AvailPath on MovingID node: " + newPath);

					// verifyParentPath=false signals to MongoListener to not waste cycles checking the path on this
					// to verify the parent exists upon saving, because we know the path is fine.
					node.verifyParentPath = false;

					// we know this tareget node has chilren now.
					parentToPasteInto.setHasChildren(true);
					// only if we get here do we know the original parent (moved FROM) now has an indeterminate
					// hasChildren status
					_nodeParent.setHasChildren(null);
				}

				// do processing for when ordinal has changed.
				if (!node.getOrdinal().equals(_targetOrdinal)) {
					node.setOrdinal(_targetOrdinal);

					// we know this tareget node has chilren now.
					parentToPasteInto.setHasChildren(true);
				}
				return null;
			});
			curTargetOrdinal++;
		}
	}

	/*
	 * WARNING: This does NOT affect the path of 'graphRoot' itself, but only changes the location of
	 * all the children under it
	 */
	public void changePathOfSubGraph(MongoSession ms, SubNode graphRoot, String oldPathPrefix, String newPathPrefix, //
			MoveNodesResponse res, boolean fast) {
		String originalPath = graphRoot.getPath();

		// log.debug("changePathOfSubGraph. Original graphRoot.path: " + originalPath + " oldPathPrefix=" + oldPathPrefix
		// 		+ " newPathPrefix" + newPathPrefix);
		boolean removeOrphans = !fast;
		BulkOperations bops = null;
		int batchSize = 0;

		for (SubNode node : read.getSubGraph(ms, graphRoot, null, 0, removeOrphans, false, false)) {
			if (!node.getPath().startsWith(originalPath)) {
				throw new RuntimeEx("Algorighm failure: path " + node.getPath() + " should have started with " + originalPath);
			}
			// log.debug(" PROCESSING oldPath: " + node.getPath());

			String newPath = node.getPath().replace(oldPathPrefix, newPathPrefix);
			// log.debug("      SETTING new path:" + newPath);

			if (bops == null) {
				bops = ops.bulkOps(BulkMode.UNORDERED, SubNode.class);
			}

			Query query = new Query().addCriteria(new Criteria("id").is(node.getId()));
			Update update = new Update().set(SubNode.PATH, newPath);

			if (node.getStr(NodeProp.CRYPTO_SIG) != null) {
				// crypto sig uses path as part of it, so we just invalidated the signature.
				node.getProps().remove(NodeProp.CRYPTO_SIG.s());
				update.set(SubNode.PROPS, node.getProps());
				res.setSignaturesRemoved(true);
			}

			bops.updateOne(query, update);
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

	public SelectAllNodesResponse selectAllNodes(MongoSession ms, SelectAllNodesRequest req) {
		SelectAllNodesResponse res = new SelectAllNodesResponse();
		ms = ThreadLocals.ensure(ms);

		String nodeId = req.getParentNodeId();
		SubNode node = read.getNode(ms, nodeId);
		List<String> nodeIds = read.getChildrenIds(ms, node, false, null);
		if (nodeIds != null) {
			res.setNodeIds(nodeIds);
		}
		res.setSuccess(true);
		return res;
	}
}
