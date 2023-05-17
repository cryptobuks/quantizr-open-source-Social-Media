package quanta.service.node;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import lombok.extern.slf4j.Slf4j;
import quanta.config.NodeName;
import quanta.config.ServiceBase;
import quanta.config.SessionContext;
import quanta.exception.NodeAuthFailedException;
import quanta.exception.base.RuntimeEx;
import quanta.instrument.PerfMon;
import quanta.model.BreadcrumbInfo;
import quanta.model.CalendarItem;
import quanta.model.NodeInfo;
import quanta.model.NodeMetaInfo;
import quanta.model.client.Constant;
import quanta.model.client.ConstantInt;
import quanta.model.client.ErrorType;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.InitNodeEditRequest;
import quanta.request.RenderCalendarRequest;
import quanta.request.RenderNodeRequest;
import quanta.response.InitNodeEditResponse;
import quanta.response.RenderCalendarResponse;
import quanta.response.RenderNodeResponse;
import quanta.util.Const;
import quanta.util.Convert;
import quanta.util.DateUtil;
import quanta.util.ThreadLocals;
import quanta.util.XString;

/**
 * Service for rendering the content of a page. The actual page is not rendered on the server side.
 * What we are really doing here is generating a list of POJOS that get converted to JSON and sent
 * to the client. But regardless of format this is the primary service for pulling content up for
 * rendering the pages on the client as the user browses around on the tree.
 */
@Component
@Slf4j
public class NodeRenderService extends ServiceBase {
	/*
	 * This is the call that gets all the data to show on a page. Whenever user is browsing to a new
	 * page, this method gets called once per page and retrieves all the data for that page.
	 */
	@PerfMon(category = "render")
	public RenderNodeResponse renderNode(MongoSession ms, RenderNodeRequest req) {
		RenderNodeResponse res = new RenderNodeResponse();

		// by default we do showReplies
		boolean showReplies = true;
		boolean adminOnly = false;

		SessionContext sc = ThreadLocals.getSC();

		// this is not anon user, we set the flag based on their preferences
		if (sc != null && !sc.isAnonUser()) {
			showReplies = sc.getUserPreferences().isShowReplies();

			// log.debug("rendering with user prefs: [hashCode=" + sc.getUserPreferences().hashCode() + "] " +
			// XString.prettyPrint(sc.getUserPreferences()));
		}

		String targetId = req.getNodeId();
		boolean isActualUplevelRequest = req.isUpLevel();

		// log.debug("renderNode: \nreq=" + XString.prettyPrint(req));
		SubNode node = null;
		try {
			node = read.getNode(ms, targetId);
			if (node == null && !sc.isAnonUser()) {
				node = read.getNode(ms, sc.getRootId());
			}
			adminOnly = acl.isAdminOwned(node);
		} catch (NodeAuthFailedException e) {
			res.setSuccess(false);
			res.setMessage("Unauthorized.");
			res.setErrorType(ErrorType.AUTH.s());
			log.error("error", e);
			return res;
		}

		// If this request is indicates the server would rather display RSS than jump to an RSS node itself,
		// then here we indicate to the caller this happened and return immediately.
		if (req.isJumpToRss() && node != null && NodeType.RSS_FEED.s().equals(node.getType())) {
			res.setSuccess(true);
			res.setRssNode(true);

			NodeInfo nodeInfo = convert.convertToNodeInfo(adminOnly, ThreadLocals.getSC(), ms, node, false,
					Convert.LOGICAL_ORDINAL_IGNORE, false, false, true, //
					false, true, true, null, false);
			res.setNode(nodeInfo);

			return res;
		}

		if (node == null) {
			log.debug("nodeId not found: " + targetId + " sending user to :public instead");
			node = read.getNode(ms, prop.getUserLandingPageNode());
		}

		if (node == null) {
			res.setNoDataResponse("Node not found.");
			return res;
		}

		// NOTE: This code was for loading MFS defined content live as it's rendered, but for now we don't
		// do this, and only have a kind of import/export to/from
		// a node and MFS as a menu option that must be explicitly run.
		// if (ok(node.getStr(NodeProp.IPFS_SCID))) {
		// SyncFromMFSService svc = (SyncFromMFSService) context.getBean(SyncFromMFSService.class);
		// svc.loadNode(ms, node);
		// }

		/* If only the single node was requested return that */
		if (req.isSingleNode()) {
			// that loads these all asynchronously.
			NodeInfo nodeInfo = convert.convertToNodeInfo(adminOnly, ThreadLocals.getSC(), ms, node, false,
					Convert.LOGICAL_ORDINAL_GENERATE, false, false, true, false, true, true, null, false);
			res.setNode(nodeInfo);
			res.setSuccess(true);
			return res;
		}

		/*
		 * If scanToNode is non-null it means we are trying to get a subset of the children that contains
		 * scanToNode as one child, because that's the child we want to highlight and scroll to on the front
		 * end when the query returns, and the page root node will of course be the parent of scanToNode
		 */
		SubNode scanToNode = null;

		// we pass doAuth=true because right here we DO care that the hasChildren is considering only based
		// on what WE can access.
		if (req.isForceRenderParent() || (req.isRenderParentIfLeaf() && !read.hasChildren(ms, node, true, false))) {
			req.setUpLevel(true);
		}

		/*
		 * the 'siblingOffset' is for jumping forward or backward thru at the same level of the tree without
		 * having to first 'uplevel' and then click on the prev or next node.
		 */
		if (req.getSiblingOffset() != 0) {
			SubNode parent = read.getParent(ms, node);
			if (req.getSiblingOffset() < 0) {
				SubNode nodeAbove = read.getSiblingAbove(ms, node, parent);
				if (nodeAbove != null) {
					node = nodeAbove;
				} else {
					node = parent != null ? parent : node;
				}
			} else if (req.getSiblingOffset() > 0) {
				SubNode nodeBelow = read.getSiblingBelow(ms, node, parent);
				if (nodeBelow != null) {
					node = nodeBelow;
				} else {
					node = parent != null ? parent : node;
				}
			} else {
				node = parent != null ? parent : node;
			}
		} else {
			if (req.isUpLevel()) {
				try {
					SubNode parent = read.getParent(ms, node);
					if (parent != null) {
						scanToNode = node;
						node = parent;
					}
				} catch (Exception e) {
					/*
					 * failing to get parent is only an "auth" problem if this was an ACTUAL uplevel request, and not
					 * something we decided to to inside this method based on trying not to render a page with no
					 * children showing.
					 */
					if (isActualUplevelRequest) {
						res.setErrorType(ErrorType.AUTH.s());
						res.setSuccess(true);
						return res;
					}
				}
			}
		}

		int limit = ConstantInt.ROWS_PER_PAGE.val();

		// Collect all the parents we need to based on parentCount
		LinkedList<NodeInfo> parentNodes = new LinkedList<>();
		SubNode highestUpParent = node;
		int parentCount = req.getParentCount();
		boolean done = false;
		while (!done && parentCount-- > 0) {
			try {
				highestUpParent = read.getParent(ms, highestUpParent);
				if (highestUpParent != null) {
					NodeInfo nodeInfo = convert.convertToNodeInfo(adminOnly, ThreadLocals.getSC(), ms, highestUpParent, false,
							Convert.LOGICAL_ORDINAL_IGNORE, false, false, false, false, true, true, null, false);

					if (nodeInfo != null) {
						// each parent up goes on top of list for correct rendering order on client.
						parentNodes.addFirst(nodeInfo);
					}
				}
			} catch (Exception e) {
				done = true;
				// if we run into any errors collecting children we can ignore them.
			}
		}

		LinkedList<BreadcrumbInfo> breadcrumbs = new LinkedList<>();
		res.setBreadcrumbs(breadcrumbs);
		render.getBreadcrumbs(ms, highestUpParent, breadcrumbs);

		NodeInfo nodeInfo = render.processRenderNode(adminOnly, ms, req, res, node, scanToNode, -1, 0, limit, showReplies);
		if (nodeInfo != null) {
			nodeInfo.setParents(parentNodes);
			res.setNode(nodeInfo);
			res.setSuccess(true);
		} else {
			res.setSuccess(false);
		}

		// log.debug("renderNode Full Return: " + XString.prettyPrint(res));
		return res;
	}

	@PerfMon(category = "render")
	public NodeInfo processRenderNode(boolean adminOnly, MongoSession ms, RenderNodeRequest req, RenderNodeResponse res,
			SubNode node, SubNode scanToNode, long logicalOrdinal, int level, int limit, boolean showReplies) {
		NodeInfo nodeInfo = convert.convertToNodeInfo(adminOnly, ThreadLocals.getSC(), ms, node, false, logicalOrdinal, level > 0,
				false, true, false, true, true, null, false);

		if (nodeInfo == null) {
			return null;
		}

		if (level > 0) {
			return nodeInfo;
		}

		nodeInfo.setChildren(new LinkedList<>());

		/*
		 * If we are scanning to a node we know we need to start from zero offset, or else we use the offset
		 * passed in. Offset is the number of nodes to IGNORE before we start collecting nodes.
		 */
		int offset = scanToNode != null ? 0 : req.getOffset();
		if (offset < 0) {
			offset = 0;
		}

		/*
		 * todo-2: need optimization to work well with large numbers of child nodes: If scanToNode is in
		 * use, we should instead look up the node itself, and then get it's ordinal, and use that as a '>='
		 * in the query to pull up the list when the node ordering is ordinal. Note, if sort order is by a
		 * timestamp we'd need a ">=" on the timestamp itself instead. We request ROWS_PER_PAGE+1, because
		 * that is enough to trigger 'endReached' logic to be set correctly
		 */
		int queryLimit = scanToNode != null ? -1 : limit + 1;
		// log.debug("query: offset=" + offset + " limit=" + queryLimit + " scanToNode=" + scanToNode);

		String orderBy = node.getStr(NodeProp.ORDER_BY);
		Sort sort = null;

		if (!StringUtils.isEmpty(orderBy)) {
			sort = parseOrderBy(orderBy);
		}
		// if this is a user's POSTS node show in revchron always.
		else {
			if (NodeName.POSTS.equals(node.getName())) {
				sort = Sort.by(Sort.Direction.DESC, SubNode.MODIFY_TIME);
			}
		}

		boolean isOrdinalOrder = false;
		if (sort == null) {
			// log.debug("processRenderNode querying by ordinal.");
			sort = Sort.by(Sort.Direction.ASC, SubNode.ORDINAL);
			isOrdinalOrder = true;
		}

		Criteria moreCriteria = null;
		/*
		 * #optional-show-replies: disabling this for now. Needs more thought regarding how to keep this
		 * from accidentally hiding nodes from users in a way where they don't realize nodes are being
		 * hidden simply because of being comment types. especially with the 'Show Comments' being hidden
		 * away in the settings menu instead of like at the top of the tree view like document view does.
		 */
		// if (!showReplies) {
		// moreCriteria = Criteria.where(SubNode.TYPE).ne(NodeType.COMMENT.s());
		// }

		Iterable<SubNode> nodeIter = read.getChildren(ms, node, sort, queryLimit, offset, moreCriteria);
		Iterator<SubNode> iterator = nodeIter.iterator();
		int idx = offset;

		// this should only get set to true if we run out of records, because we reached
		// the true end of records and not related to a queryLimit
		boolean endReached = false;

		if (req.isGoToLastPage()) {
			// todo-2: fix
			throw new RuntimeEx("No ability to go to last page yet in new mongo api.");
			// offset = (int) nodeIter.getSize() - ROWS_PER_PAGE;
			// if (offset < 0) {
			// offset = 0;
			// }
		}

		// if (offset > 0) {
		// // log.debug("Skipping the first " + offset + " records in the resultset.");
		// idx = read.skip(iterator, offset);
		// }

		List<SubNode> slidingWindow = null;
		NodeInfo ninfo = null;

		// -1 means "no last ordinal known" (i.e. first iteration)
		long lastOrdinal = -1;
		BulkOperations bops = null;
		int batchSize = 0;

		/*
		 * Main loop to keep reading nodes from the database until we have enough to render the page
		 */
		while (true) {
			if (!iterator.hasNext()) {
				// log.debug("End reached.");
				endReached = true;
				break;
			}
			SubNode n = iterator.next();

			/*
			 * Side Effect: Fixing Duplicate Ordinals
			 * 
			 * we do the side effect of repairing ordinals here just because it's really only an issue if it's
			 * rendered and here's where we're rendering. It would be 'possible' but less performant to just
			 * detect when a node's children have dupliate ordinals, and fix the entire list of children
			 */
			if (isOrdinalOrder) {
				if (lastOrdinal != -1 && lastOrdinal == n.getOrdinal()) {
					lastOrdinal++;
					// add to bulk ops this: n.ordinal = lastOrdinal + 1;
					if (bops == null) {
						bops = ops.bulkOps(BulkMode.UNORDERED, SubNode.class);
					}
					Query query = new Query().addCriteria(new Criteria("id").is(n.getId()));
					Update update = new Update().set(SubNode.ORDINAL, lastOrdinal);
					bops.updateOne(query, update);
					if (++batchSize > Const.MAX_BULK_OPS) {
						bops.execute();
						batchSize = 0;
						bops = null;
					}
				} else {
					lastOrdinal = n.getOrdinal();
				}
			}

			idx++;
			// log.debug("Iterate [" + idx + "]: nodeId" + n.getIdStr() + "scanToNode=" +
			// scanToNode);
			// log.debug(" DATA: " + XString.prettyPrint(n));

			/* are we still just scanning for our target node */
			if (scanToNode != null) {
				/*
				 * If this is the node we are scanning for turn off scan mode, and add up to ROWS_PER_PAGE-1 of any
				 * sliding window nodes above it.
				 */
				if (n.getPath().equals(scanToNode.getPath())) {
					scanToNode = null;

					if (slidingWindow != null) {
						int count = slidingWindow.size();
						if (count > 0) {
							int relativeIdx = idx - 1;
							for (int i = count - 1; i >= 0; i--) {
								SubNode sn = slidingWindow.get(i);
								relativeIdx--;
								ninfo = render.processRenderNode(adminOnly, ms, req, res, sn, null, relativeIdx, level + 1, limit,
										showReplies);
								nodeInfo.getChildren().add(0, ninfo);

								/*
								 * If we have enough records we're done. Note having ">= ROWS_PER_PAGE/2" for example would also
								 * work and would bring back the target node as close to the center of the results sent back to
								 * the brower as possible, but what we do instead is just set to ROWS_PER_PAGE which maximizes
								 * performance by iterating the smallese number of results in order to get a page that contains
								 * what we need (namely the target node as indiated by scanToNode item)
								 */
								if (nodeInfo.getChildren().size() >= limit - 1) {
									break;
								}
							}
						}

						// We won't need sliding window again, we now just accumulate up to
						// ROWS_PER_PAGE max and we're done.
						slidingWindow = null;
					}
				}
				/*
				 * else, we can continue while loop after we incremented 'idx'. Nothing else to do on this
				 * iteration/node
				 */
				else {
					/* lazily create sliding window */
					if (slidingWindow == null) {
						slidingWindow = new LinkedList<>();
					}

					/* update sliding window */
					slidingWindow.add(n);
					if (slidingWindow.size() > limit) {
						slidingWindow.remove(0);
					}

					continue;
				}
			}

			/* if we get here we're accumulating rows */
			ninfo = render.processRenderNode(adminOnly, ms, req, res, n, null, idx - 1L, level + 1, limit, showReplies);
			nodeInfo.getChildren().add(ninfo);

			if (!iterator.hasNext()) {
				// since we query for 'limit+1', we will end up here if we're at the true end of the records.
				endReached = true;
				break;
			}

			if (nodeInfo.getChildren().size() >= limit) {
				/* break out of while loop, we have enough children to send back */
				// log.debug("Full page is ready. Exiting loop.");
				break;
			}
		}

		/*
		 * if we accumulated less than ROWS_PER_PAGE, then try to scan back up the sliding window to build
		 * up the ROW_PER_PAGE by looking at nodes that we encountered before we reached the end.
		 */
		if (slidingWindow != null && nodeInfo.getChildren().size() < limit) {
			int count = slidingWindow.size();
			if (count > 0) {
				int relativeIdx = idx - 1;
				for (int i = count - 1; i >= 0; i--) {
					SubNode sn = slidingWindow.get(i);
					relativeIdx--;

					ninfo = render.processRenderNode(adminOnly, ms, req, res, sn, null, (long) relativeIdx, level + 1, limit,
							showReplies);
					nodeInfo.getChildren().add(0, ninfo);

					// If we have enough records we're done
					if (nodeInfo.getChildren().size() >= limit) {
						break;
					}
				}
			}
		}

		if (idx == 0) {
			log.trace("no child nodes found.");
		}

		if (endReached && ninfo != null && nodeInfo.getChildren().size() > 0) {
			// set 'lastChild' on the last child
			nodeInfo.getChildren().get(nodeInfo.getChildren().size() - 1).setLastChild(true);
		}

		// log.debug("Setting endReached="+endReached);
		res.setEndReached(endReached);

		if (bops != null) {
			bops.execute();
		}

		return nodeInfo;
	}

	/*
	 * Nodes can have a propety like orderBy="priority asc", and that allow the children to be displayed
	 * in that order.
	 *
	 * parses something like "priority asc" into a Sort object, assuming the field is in the property
	 * array of the node, rather than the name of an actual SubNode object member property.
	 */
	private Sort parseOrderBy(String orderBy) {
		Sort sort = null;
		int spaceIdx = orderBy.indexOf(" ");
		String dir = "asc"; // asc or desc
		if (spaceIdx != -1) {
			String orderByProp = orderBy.substring(0, spaceIdx);
			dir = orderBy.substring(spaceIdx + 1);
			sort = Sort.by(dir.equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, SubNode.PROPS + "." + orderByProp);

			/*
			 * when sorting by priority always do second level REV-CHRON sort, so newest un-prioritized nodes
			 * appear at top. todo-2: probably would be better to to just make this orderBy parser handle
			 * comma-delimited sort list which is not a difficult change
			 */
			if (orderByProp.equals(NodeProp.PRIORITY.s())) {
				sort = sort.and(Sort.by(Sort.Direction.DESC, SubNode.MODIFY_TIME));
			}
		}
		return sort;
	}

	public InitNodeEditResponse initNodeEdit(MongoSession ms, InitNodeEditRequest req) {
		InitNodeEditResponse res = new InitNodeEditResponse();
		String nodeId = req.getNodeId();

		/*
		 * IF EDITING A FRIEND NODE: If 'nodeId' is the Admin-Owned user account node, and this user it
		 * wanting to edit his Friend node representing this user.
		 */
		if (req.getEditMyFriendNode()) {
			String _nodeId = nodeId;
			nodeId = arun.run(as -> {
				Criteria crit = Criteria.where(SubNode.PROPS + "." + NodeProp.USER_NODE_ID.s()).is(_nodeId);
				// we query as a list, but there should only be ONE result.
				List<SubNode> friendNodes = user.getSpecialNodesList(as, null, NodeType.FRIEND_LIST.s(), null, false, crit);
				if (friendNodes != null) {
					for (SubNode friendNode : friendNodes) {
						return friendNode.getIdStr();
					}
				}
				return null;
			});
		}

		SubNode node = read.getNode(ms, nodeId);
		auth.ownerAuth(ms, node);

		if (node == null) {
			res.setMessage("Node not found.");
			res.setSuccess(false);
			return res;
		}

		NodeInfo nodeInfo = convert.convertToNodeInfo(false, ThreadLocals.getSC(), ms, node, true, //
				Convert.LOGICAL_ORDINAL_IGNORE, false, false, //
				true, false, false, false, null, false);
		res.setNodeInfo(nodeInfo);
		res.setSuccess(true);
		return res;
	}

	/*
	 * There is a system defined way for admins to specify what node should be displayed in the browser
	 * when a non-logged in user (i.e. anonymouse user) is browsing the site, and this method retrieves
	 * that page data.
	 */
	public RenderNodeResponse anonPageLoad(MongoSession ms, RenderNodeRequest req) {
		ms = ThreadLocals.ensure(ms);

		if (req.getNodeId() == null) {
			String id = prop.getUserLandingPageNode();
			// log.debug("Anon Render Node ID: " + id);

			// if (ok(ThreadLocals.getSC().getUrlId())) {
			// id = ThreadLocals.getSC().getUrlId();
			// ThreadLocals.getSC().setUrlId(null);
			// }

			// log.debug("anonPageLoad id=" + id);
			req.setNodeId(id);
		}

		RenderNodeResponse res = renderNode(ms, req);
		return res;
	}

	public void populateSocialCardProps(SubNode node, Model model) {
		if (node == null)
			return;

		NodeMetaInfo metaInfo = snUtil.getNodeMetaInfo(node);
		model.addAttribute("ogTitle", metaInfo.getTitle());
		model.addAttribute("ogDescription", metaInfo.getDescription());

		String mime = metaInfo.getAttachmentMime();
		if (mime != null && mime.startsWith("image/")) {
			model.addAttribute("ogImage", metaInfo.getAttachmentUrl());
		}

		model.addAttribute("ogUrl", metaInfo.getUrl());
	}

	public RenderCalendarResponse renderCalendar(MongoSession ms, RenderCalendarRequest req) {
		RenderCalendarResponse res = new RenderCalendarResponse();

		SubNode node = read.getNode(ms, req.getNodeId());
		if (node == null) {
			return res;
		}

		LinkedList<CalendarItem> items = new LinkedList<>();
		res.setItems(items);

		for (SubNode n : read.getCalendar(ms, node)) {
			CalendarItem item = new CalendarItem();

			String content = n.getContent();
			content = render.getFirstLineAbbreviation(content, 25);

			item.setTitle(content);
			item.setId(n.getIdStr());
			item.setStart(n.getInt(NodeProp.DATE));

			String durationStr = n.getStr(NodeProp.DURATION);
			long duration = DateUtil.getMillisFromDuration(durationStr);
			if (duration == 0) {
				duration = 60 * 60 * 1000;
			}

			item.setEnd(item.getStart() + duration);
			items.add(item);
		}

		return res;
	}

	@PerfMon(category = "render")
	public void getBreadcrumbs(MongoSession ms, SubNode node, LinkedList<BreadcrumbInfo> list) {
		ms = ThreadLocals.ensure(ms);

		try {
			if (node != null) {
				node = read.getParent(ms, node);
			}

			while (node != null) {
				BreadcrumbInfo bci = new BreadcrumbInfo();
				if (list.size() >= 5) {
					// This toplevel one is shows up on the client as "..." indicating more parents
					// further up
					bci.setId("");
					list.add(0, bci);
					break;
				}

				String content = node.getContent();

				if (StringUtils.isEmpty(content)) {
					if (!StringUtils.isEmpty(node.getName())) {
						content = node.getName();
					} else {
						content = "";
					}
				} else if (node.getType() == NodeType.NOSTR_ENC_DM.s() || //
						content.startsWith(Constant.ENC_TAG.s())) {
					content = "[encrypted]";
				} else {
					content = getFirstLineAbbreviation(content, 25);
				}

				bci.setName(content);
				bci.setId(node.getIdStr());
				bci.setType(node.getType());
				list.add(0, bci);

				node = read.getParent(ms, node);
			}
		} catch (Exception e) {
			/*
			 * this is normal for users to wind up here because looking up the tree always ends at a place they
			 * can't access, and whatever paths we accumulated until this access error is what we do want to
			 * return so we just return everything as is by ignoring this exception
			 */
		}
	}

	public String stripRenderTags(String content) {
		if (content == null)
			return null;
		content = content.trim();

		while (content.startsWith("#")) {
			content = XString.stripIfStartsWith(content, "#");
		}
		content = content.trim();
		return content;
	}

	public String getFirstLineAbbreviation(String content, int maxLen) {
		if (content == null)
			return null;

		// if this is a node starting with hashtags or usernames then chop them all
		while (content.startsWith("@") || content.startsWith("#")) {
			int spaceIdx = content.indexOf(" ");
			if (spaceIdx == -1) {
				spaceIdx = content.indexOf("\n");
			}
			if (spaceIdx > 0) {
				content = content.substring(spaceIdx + 1);
			}
		}

		content = XString.truncAfterFirst(content, "\n");
		content = XString.truncAfterFirst(content, "\r");

		if (content.length() > maxLen) {
			content = content.substring(0, maxLen) + "...";
		}
		return content.trim();
	}
}
