package quanta.service;

import static quanta.util.Util.no;
import static quanta.util.Util.ok;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
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
import quanta.util.ThreadLocals;

/**
 * Service for controlling the positions (ordinals) of nodes relative to their parents and/or moving
 * nodes to locate them under a different parent. This is similar type of functionality to
 * cut-and-paste in file systems. Currently there is no way to 'clone' or copy nodes, but user can
 * move any existing nodes they have to any new location they want, subject to security constraints
 * of course.
 */
@Component
public class NodeMoveService extends ServiceBase {
	private static final Logger log = LoggerFactory.getLogger(NodeMoveService.class);

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
		if (no(node)) {
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
		if (ok(nodeAbove)) {
			Long saveOrdinal = nodeAbove.getOrdinal();
			nodeAbove.setOrdinal(node.getOrdinal());
			node.setOrdinal(saveOrdinal);
		}
		update.saveSession(ms);
	}

	public void moveNodeDown(MongoSession ms, SubNode node) {
		SubNode nodeBelow = read.getSiblingBelow(ms, node, null);
		if (ok(nodeBelow)) {
			Long saveOrdinal = nodeBelow.getOrdinal();
			nodeBelow.setOrdinal(node.getOrdinal());
			node.setOrdinal(saveOrdinal);
		}
		update.saveSession(ms);
	}

	public void moveNodeToTop(MongoSession ms, SubNode node) {
		SubNode parentNode = read.getParent(ms, node);
		if (no(parentNode)) {
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
		if (no(parentNode)) {
			return;
		}
		long ordinal = read.getMaxChildOrdinal(ms, parentNode) + 1L;
		node.setOrdinal(ordinal);
		update.saveSession(ms);
	}

	/*
	 * Note: Browser can send nodes in any order, in the request, and always the lowest ordinal is the
	 * one we keep and join to.
	 * 
	 * todo-1: need to verify that none of the nodes being joined (except the one we persist) have any
	 * attachments because we can't combine mutiple attachments into a single node.
	 */
	public JoinNodesResponse joinNodes(MongoSession ms, JoinNodesRequest req) {
		JoinNodesResponse res = new JoinNodesResponse();

		// add to list becasue we will sort
		ArrayList<SubNode> nodes = new ArrayList<SubNode>();

		String parentPath = null;
		for (String nodeId : req.getNodeIds()) {
			SubNode node = read.getNode(ms, nodeId);

			if (no(parentPath)) {
				parentPath = node.getParentPath();
			} else if (!parentPath.equals(node.getParentPath())) {
				res.setMessage("Failed: All nodes must be under the same parent node.");
				res.setSuccess(false);
				return res;
			}

			auth.ownerAuth(ms, node);
			nodes.add(node);
		}

		nodes.sort((s1, s2) -> (int) (s1.getOrdinal() - s2.getOrdinal()));

		StringBuilder sb = new StringBuilder();
		SubNode firstNode = null;
		int counter = 0;

		for (SubNode n : nodes) {
			if (no(firstNode)) {
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

			if (counter > 0) {
				/* If node has an attachment we don't delete the node, but just set it's content to null 
				*/
				if (ok(n.getAttachments())) {
					n.setContent(null);
					n.touch();
					update.save(ms, n);
				}
				/* or else we delete the node */
				else {
					// pass updateParentHasChildren, because we know a 'join nodes' never affects whether the parent
					// had children. It DOES have children, and we're joining them.
					delete.deleteNode(ms, n, false);
				}
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

		moveNodesInternal(ms, req.getLocation(), req.getTargetNodeId(), req.getNodeIds());
		res.setSuccess(true);
		return res;
	}

	/*
	 * If req.location==inside then the targetId is the parent node we will be inserting children into,
	 * but if req.location==inline the targetId represents the child who will become a sibling of what
	 * we are inserting, and the inserted nodes will be pasted in directly below that ordinal (i.e. new
	 * siblings posted in below it)
	 */
	private void moveNodesInternal(MongoSession ms, String location, String targetId, List<String> nodeIds) {
		// log.debug("moveNodesInternal: targetId=" + targetId + " location=" + location);
		SubNode targetNode = read.getNode(ms, targetId);
		SubNode parentToPasteInto = location.equalsIgnoreCase("inside") ? targetNode : read.getParent(ms, targetNode);

		auth.ownerAuth(ms, parentToPasteInto);
		String parentPath = parentToPasteInto.getPath();
		// log.debug("targetPath: " + parentPath);
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
			// log.debug("Moving ID: " + nodeId);
			SubNode node = read.getNode(ms, nodeId);
			auth.ownerAuth(ms, node);
			nodesToMove.add(node);

			/*
			 * Verify all nodes being pasted are siblings
			 */
			if (ok(sourceParentPath) && !sourceParentPath.equals(node.getParentPath())) {
				throw new RuntimeException("Nodes to move must be all from the same parent.");
			}
			sourceParentPath = node.getParentPath();

			// get the nodeParent if we don't have it already.
			if (no(nodeParent)) {
				nodeParent = read.getParent(ms, node);
			}
		}

		// make sure nodes to move are in ordinal order.
		nodesToMove.sort((n1, n2) -> (int) (n1.getOrdinal() - n2.getOrdinal()));

		for (SubNode node : nodesToMove) {
			// log.debug("Moving ID: " + nodeId);
			Long _targetOrdinal = curTargetOrdinal;
			SubNode _nodeParent = nodeParent;
			arun.run(as -> {
				/*
				 * If this 'node' will be changing parents (moving to new parent) we need to update its subgraph, of
				 * all children and also update its own path, otherwise it's staying under same parent and only it's
				 * ordinal will change.
				 */
				if (_nodeParent.getId().compareTo(parentToPasteInto.getId()) != 0) {
					/*
					 * if a parent node is attempting to be pasted into one of it's children that's an impossible move
					 * so we reject the attempt.
					 */
					if (parentToPasteInto.getPath().startsWith(node.getPath())) {
						throw new RuntimeException("Impossible node move requested.");
					}
					changePathOfSubGraph(as, node, parentPath);

					String newPath = mongoUtil.findAvailablePath(parentPath + "/" + node.getLastPathPart());
					node.setPath(newPath);

					// special case here we know path is fine.
					node.pathDirty = false;

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

	private void changePathOfSubGraph(MongoSession ms, SubNode graphRoot, String newPathPrefix) {
		String originalPath = graphRoot.getPath();
		// log.debug("originalPath (graphRoot.path): " + originalPath);
		int originalParentPathLen = graphRoot.getParentPath().length();

		for (SubNode node : read.getSubGraph(ms, graphRoot, null, 0, true, false, false)) {
			if (!node.getPath().startsWith(originalPath)) {
				throw new RuntimeEx("Algorighm failure: path " + node.getPath() + " should have started with " + originalPath);
			}
			// log.debug("PROCESSING MOVE: oldPath: " + node.getPath());

			String pathSuffix = node.getPath().substring(originalParentPathLen + 1);
			String newPath = newPathPrefix + "/" + pathSuffix;
			// log.debug(" newPath: [" + newPathPrefix + "]/[" + pathSuffix + "]");
			newPath = mongoUtil.findAvailablePath(newPath);
			node.setPath(newPath);
			node.pathDirty = false;
		}
	}

	public SelectAllNodesResponse selectAllNodes(MongoSession ms, SelectAllNodesRequest req) {
		SelectAllNodesResponse res = new SelectAllNodesResponse();
		ms = ThreadLocals.ensure(ms);

		String nodeId = req.getParentNodeId();
		SubNode node = read.getNode(ms, nodeId);
		List<String> nodeIds = read.getChildrenIds(ms, node, false, null);
		if (ok(nodeIds)) {
			res.setNodeIds(nodeIds);
		}
		res.setSuccess(true);
		return res;
	}
}
