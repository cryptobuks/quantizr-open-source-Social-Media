package quanta.mongo;

import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.instrument.PerfMon;
import quanta.model.PropertyInfo;
import quanta.model.client.NodeType;
import quanta.model.client.PrivilegeType;
import quanta.mongo.model.SubNode;
import quanta.util.Const;

/**
 * Performs the 'create' (as in CRUD) operations for creating new nodes in MongoDB
 */
@Component
@Slf4j 
public class MongoCreate extends ServiceBase {
	// this large top reserve size means the "insert at top" will always be done with out multiple node
	// updates
	// except for once every thousand times.
	private static long RESERVE_BLOCK_SIZE = 1000;

	public SubNode createNode(MongoSession ms, SubNode parent, String type, Long ordinal, CreateNodeLocation location,
			boolean updateParentOrdinals) {
		return createNode(ms, parent, null, type, ordinal, location, null, null, updateParentOrdinals, true);
	}

	public SubNode createNode(MongoSession ms, String path) {
		SubNode node = new SubNode(ms.getUserNodeId(), path, NodeType.NONE.s(), null);
		update.setParentHasChildren(node);
		return node;
	}

	public SubNode createNode(MongoSession ms, String path, String type) {
		if (type == null) {
			type = NodeType.NONE.s();
		}
		SubNode node = new SubNode(ms.getUserNodeId(), path, type, null);
		update.setParentHasChildren(node);
		return node;
	}

	/*
	 * Creates a node, but does NOT persist it. If parent==null it assumes it's adding a root node. This
	 * is required, because all the nodes at the root level have no parent. That is, there is no ROOT
	 * node. Only nodes considered to be on the root.
	 * 
	 * relPath can be null if no path is known
	 */
	@PerfMon(category = "create")
	public SubNode createNode(MongoSession ms, SubNode parent, String relPath, String type, Long ordinal,
			CreateNodeLocation location, List<PropertyInfo> properties, ObjectId ownerId, boolean updateOrdinals,
			boolean updateParent) {
		if (relPath == null) {
			/*
			 * Adding a node ending in '?' will trigger for the system to generate a leaf node automatically.
			 */
			relPath = "?";
		}

		if (type == null) {
			type = NodeType.NONE.s();
		}

		String path = (parent == null ? "" : parent.getPath()) + "/" + relPath;

		if (ownerId == null) {
			ownerId = ms.getUserNodeId();
		}

		// for now not worried about ordinals for root nodes.
		if (parent == null) {
			ordinal = 0L;
		} else {
			if (updateOrdinals) {
				if (ordinal == null) {
					ordinal = 0L;
				}

				Long _ordinal = ordinal;
				ordinal = (Long) arun.run(as -> {
					return create.prepOrdinalForLocation(as, location, parent, _ordinal);
				});
			}
		}

		SubNode node = new SubNode(ownerId, path, type, ordinal);

		if (updateParent) {
			parent.setHasChildren(true);
		}

		if (properties != null) {
			for (PropertyInfo propInfo : properties) {
				node.set(propInfo.getName(), propInfo.getValue());
			}
		}

		return node;
	}

	@PerfMon(category = "create")
	private Long prepOrdinalForLocation(MongoSession ms, CreateNodeLocation location, SubNode parent, Long ordinal) {
		switch (location) {
			case FIRST:
				ordinal = create.insertOrdinal(ms, parent, 0L, 1L);
				break;
			case LAST:
				ordinal = read.getMaxChildOrdinal(ms, parent) + 1;
				break;
			case ORDINAL:
				ordinal = create.insertOrdinal(ms, parent, ordinal, 1L);
				break;
			default:
				throw new RuntimeException("Unknown ordinal");
		}

		update.saveSession(ms);
		return ordinal;
	}

	/*
	 * Shifts all child ordinals down (increments them by rangeSize), that are >= 'ordinal' to make a
	 * slot for the new ordinal positions for some new nodes to be inserted into this newly available
	 * range of unused sequential ordinal values (range of 'ordinal+1' thru 'ordinal+1+rangeSize')
	 * 
	 * Example: Inserting at top will normally send the ordinal in that's the same as the current TOP
	 * ordinal, so the new node will occupy that slot and everythnig else shifts down.
	 * 
	 * Returns the first ordinal in the range we actually ended up freeing up for use.
	 */
	@PerfMon(category = "create")
	public long insertOrdinal(MongoSession ms, SubNode node, long ordinal, long rangeSize) {
		long minOrdinal = read.getMinChildOrdinal(ms, node);

		// default new ordinal to ordinal
		long newOrdinal = ordinal;
		/*
		 * We detect the special case where we're attempting to insert at 'top' ordinals and if we find room
		 * to grab an ordinal at minOrdinal-1 then we do so. Whenever Quanta renumbers nodes it tries to
		 * leave RESERVE_BLOCK_SIZE at the head so that inserts "at top" will alway some in as 999, 998,
		 * 997, etc, until it's forced to renumber, when the top node happens to have zero ordinal and we
		 * end up trying to insert above it.
		 */

		// if we're inserting a single node
		if (rangeSize == 1) {
			// if the target ordinal is at or below the current minimum
			if (ordinal <= minOrdinal) {
				// if we have space below the current minimum we can just use it
				if (minOrdinal > 0) {
					long ret = minOrdinal - 1;

					// always grab the index at halfway to zero so we can leave room for for future inserts to
					// get lucky and have a place to land without cusing a multi record node renumbering.
					if (ret > 0) {
						ret = ret / 2;
					}
					return ret;
				}
				// else minOrdinal is already at zero so we insert a new block, and then let
				// "INSERT_BLOCK_SIZE - 1" be the topmost ordinal now
				else {
					rangeSize = RESERVE_BLOCK_SIZE;
					newOrdinal = RESERVE_BLOCK_SIZE - 1;
				}
			}
		}

		auth.auth(ms, node, PrivilegeType.READ);

		// save all if there's any to save.
		update.saveSession(ms);

		Criteria crit = Criteria.where(SubNode.ORDINAL).gte(ordinal);

		BulkOperations bops = null;
		int batchSize = 0;

		for (SubNode child : read.getChildren(ms, node, Sort.by(Sort.Direction.ASC, SubNode.ORDINAL), null, 0, crit)) {

			// lazy create bulkOps
			if (bops == null) {
				bops = ops.bulkOps(BulkMode.UNORDERED, SubNode.class);
			}

			Query query = new Query().addCriteria(new Criteria("id").is(child.getId()));
			Update update = new Update().set(SubNode.ORDINAL, child.getOrdinal() + rangeSize);
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

		return newOrdinal;
	}
}
