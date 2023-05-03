package quanta.mongo;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.instrument.PerfMon;
import quanta.model.UserStats;
import quanta.model.client.Attachment;
import quanta.mongo.model.SubNode;
import quanta.util.Cast;
import quanta.util.ExUtil;
import quanta.util.ThreadLocals;
import quanta.util.XString;
import quanta.util.val.Val;

/**
 * Performs update (as in CRUD) operations for MongoDB
 */
@Component
@Slf4j 
public class MongoUpdate extends ServiceBase {
	public void saveObj(Object obj) {
		ops.save(obj);
	}

	public void saveIfDirty(MongoSession ms, SubNode node) {
		if (node == null || !ThreadLocals.hasDirtyNode(node.getId())) return;
		save(ms, node, true);
	}

	public void save(MongoSession ms, SubNode node) {
		save(ms, node, true);
	}

	public void setParentHasChildren(SubNode node) {
		if (node == null) return;
		arun.run(as -> {
			SubNode parent = read.findNodeByPath(node.getParentPath());
			if (parent != null) {
				parent.setHasChildren(true);
			}
			return null;
		});
	}

	@PerfMon(category = "update")
	public void save(MongoSession ms, SubNode node, boolean allowAuth) {
		if (allowAuth) {
			auth.ownerAuth(ms, node);
		}
		// log.debug("MongoApi.save: DATA: " + XString.prettyPrint(node));

		// if not doing allowAuth, we need to be sure the thread has admin session
		// because the MongoEventListener looks in threadlocals for auth
		if (!allowAuth) {
			arun.run(as -> {
				ops.save(node);
				return null;
			});
		}
		// otherwise leave same/current threadlocals as is and MongoEventListener will auth based on this
		// thread.
		else {
			ops.save(node);
		}
		ThreadLocals.clean(node);
	}

	public void saveSession(MongoSession ms) {
		update.saveSession(ms, false);
	}

	@PerfMon(category = "update")
	public void saveSession(MongoSession ms, boolean asAdmin) {
		if (ms == null || ThreadLocals.getSaving().booleanValue() || !ThreadLocals.hasDirtyNodes())
			return;

		try {
			// we check the saving flag to ensure we don't go into circular recursion here.
			ThreadLocals.setSaving(true);

			synchronized (ms) {
				ThreadLocals.getDirtyNodes().forEach((key, value) -> {
					if (!key.toHexString().equals(value.getIdStr())) {
						throw new RuntimeException(
								"Node originally cached as ID " + key.toHexString() + " now has key" + value.getIdStr());
					}
				});

				/*
				 * We use 'nodes' list to avoid a concurrent modification exception, because calling 'save()' on a
				 * node will have the side effect of removing it from dirtyNodes, and that can't happen during the
				 * loop below because we're iterating over dirtyNodes.
				 */
				List<SubNode> nodes = new LinkedList<>();

				/*
				 * check that we are allowed to write all, before we start writing any
				 */
				for (SubNode node : ThreadLocals.getDirtyNodes().values()) {
					try {
						auth.ownerAuth(ms, node);
					} catch (Exception e) {
						log.debug("Dirty node save attempt failed: " + XString.prettyPrint(node)
								+ "\bYour mongoSession has user: " + ms.getUserName() + //
								" and your ThreadLocal session is: " + ThreadLocals.getSC().getUserName());
					}

					nodes.add(node);
				}

				for (SubNode node : nodes) {
					// log.debug("Saving Dirty nodeId=" + (node.getId()==null ? "null (new node?)" : node.getIdStr()));
					update.save(ms, node, false);
				}

				/*
				 * This theoretically should never find any dirty nodes, because we just saved them all but we
				 * definitely still want this line of code here
				 */
				ThreadLocals.clearDirtyNodes();
			}
		} //
		catch (Exception e) {
			// don't rethrow any exceptions from in here.
			ExUtil.error(log, "exception in call processor", e);
		} //
		finally {
			ThreadLocals.setSaving(false);
		}
	}

	/*
	 * Unpins any IPFS data that is not currently referenced by MongoDb. Cleans up orphans.
	 */
	public String releaseOrphanIPFSPins(HashMap<ObjectId, UserStats> statsMap) {
		Val<String> ret = new Val<>("failed");

		// run as admin
		arun.run(as -> {
			int pinCount = 0, orphanCount = 0;
			LinkedHashMap<String, Object> pins = Cast.toLinkedHashMap(ipfsPin.getPins());
			if (pins != null) {
				/*
				 * For each CID that is pinned we do a lookup to see if there's a Node that is using that PIN, and
				 * if not we remove the pin
				 */
				for (String pin : pins.keySet()) {
					log.debug("Check PIN: " + pin);
					boolean attachment = false;

					SubNode ipfsNode = read.findByIPFSPinned(as, pin);
					Attachment att = ipfsNode.getFirstAttachment();

					// if there was no IPFS_LINK using this pin, then check to see if any node has the SubNode.CID
					if (ipfsNode == null) {
						// turns out MFS stuff will never be Garbage Collected, no matter what, so we don't need
						// to pin it ever, so for now I'm leaving this code here, but we don't need it, and the CIDs that
						// are
						// 'backing' the MFS file storage don't even appear in the pinning system.
						// ipfsNode = read.findByCID(as, pin);
					} else {
						attachment = true;
					}

					if (ipfsNode != null) {
						pinCount++;
						log.debug("Found CID" + (attachment ? "(att)" : "") + " nodeId=" + ipfsNode.getIdStr());

						if (attachment && statsMap != null) {
							Long binSize = att != null ? att.getSize() : null;
							if (binSize == null) {
								// Note: If binTotal is ever zero here we SHOULD do what's in the comment above
								// an call objectStat to put correct amount in.
								binSize = 0L;
							}

							/*
							 * Make sure storage space for this IPFS node pin is built into user quota. NOTE: We could be more
							 * aggressive about 'correctness' here and actually call ipfs.objectStat on each CID, to get a more
							 * bullet proof total bytes amount, but we are safe enough trusting what the node info holds,
							 * because it should be correct.
							 */
							UserStats stats = statsMap.get(ipfsNode.getOwner());
							if (stats == null) {
								stats = new UserStats();
								stats.binUsage = binSize;
								statsMap.put(ipfsNode.getOwner(), stats);
							} else {
								stats.binUsage = stats.binUsage.longValue() + binSize;
							}
						}
					} else {
						// log.debug("Removing Orphan IPFS CID=" + pin);
						orphanCount++;
						ipfsPin.remove(pin);
					}
				}
			}
			ret.setVal("Pins in use: " + pinCount + "\nOrphan Pins removed: " + orphanCount + "\n");
			log.debug(ret.getVal());
			return null;
		});
		return ret.getVal();
	}

	public void runRepairs() {
		Query query = new Query();
		Update update = new Update();
		update.set(SubNode.HAS_CHILDREN, null);
		ops.findAndModify(query, update, SubNode.class);
	}

	// returns a new BulkOps if one not yet existing
	public BulkOperations bulkOpSetPropVal(BulkOperations bops, ObjectId id, String prop, Object val) {
		if (bops == null) {
			bops = ops.bulkOps(BulkMode.UNORDERED, SubNode.class);
		}
		Query query = new Query().addCriteria(new Criteria("id").is(id));
		Update update = new Update().set(prop, val);
		bops.updateOne(query, update);
		return bops;
	}
}
