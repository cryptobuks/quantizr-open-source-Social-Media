
package quanta.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import quanta.actpub.APConst;
import quanta.config.ServiceBase;
import quanta.exception.NodeAuthFailedException;
import quanta.exception.base.RuntimeEx;
import quanta.model.client.NodeProp;
import quanta.model.client.PrincipalName;
import quanta.mongo.MongoSession;
import quanta.mongo.model.AccessControl;
import quanta.mongo.model.MongoPrincipal;
import quanta.mongo.model.SubNode;
import quanta.request.AddPrivilegeRequest;
import quanta.request.CopySharingRequest;
import quanta.request.GetNodePrivilegesRequest;
import quanta.request.RemovePrivilegeRequest;
import quanta.request.SetCipherKeyRequest;
import quanta.request.SetUnpublishedRequest;
import quanta.response.AddPrivilegeResponse;
import quanta.response.CopySharingResponse;
import quanta.response.GetNodePrivilegesResponse;
import quanta.response.RemovePrivilegeResponse;
import quanta.response.SetCipherKeyResponse;
import quanta.response.SetUnpublishedResponse;
import quanta.util.Const;
import quanta.util.XString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service methods for (ACL): processing security, privileges, and Access Control List information
 * on nodes.
 */
@Component
public class AclService extends ServiceBase {
	
	private static Logger log = LoggerFactory.getLogger(AclService.class);

	/**
	 * Returns the privileges that exist on the node identified in the request.
	 */
	public GetNodePrivilegesResponse getNodePrivileges(MongoSession ms, GetNodePrivilegesRequest req) {
		GetNodePrivilegesResponse res = new GetNodePrivilegesResponse();
		String nodeId = req.getNodeId();
		SubNode node = read.getNode(ms, nodeId);
		res.setAclEntries(auth.getAclEntries(ms, node));
		res.setSuccess(true);
		return res;
	}

	public CopySharingResponse copySharing(MongoSession ms, CopySharingRequest req) {
		CopySharingResponse res = new CopySharingResponse();
		SubNode node = read.getNode(ms, req.getNodeId());
		BulkOperations bops = null;
		/*
		 * todo-2: It seems like maybe batching can't update a collection property? so for now I'm disabling
		 * batch mode which makes this code work.
		 */
		boolean batchMode = false;
		Boolean unpublished = node.getBool(NodeProp.UNPUBLISHED);
		int batchSize = 0;
		for (SubNode n : read.getSubGraph(ms, node, null, 0, true,  //
		false, true)) {
			if (batchMode) {
				// lazy instantiate
				if (bops == null) {
					bops = ops.bulkOps(BulkMode.UNORDERED, SubNode.class);
				}
				try {
					auth.ownerAuth(ms, n);
					n.set(NodeProp.UNPUBLISHED, unpublished ? unpublished : null);
					Query query = new Query().addCriteria(new Criteria("id").is(n.getId()));
					// log.debug("Setting [" + n.getIdStr() + "] AC to " + XString.prettyPrint(node.getAc()));
					Update update = new Update().set(SubNode.AC, node.getAc()).set(SubNode.PROPS, n.getProps());
					bops.updateOne(query, update);
					if (++batchSize > Const.MAX_BULK_OPS) {
						bops.execute();
						batchSize = 0;
						bops = null;
					}
				} catch (Exception e) {
				}
			} else 
			// not an error, we just can't properties on nodes we don't own, so we skip them
			{
				auth.ownerAuth(ms, n);
				n.set(NodeProp.UNPUBLISHED, unpublished ? unpublished : null);
				log.debug("Set Unpublished on node " + n.getIdStr() + " to " + unpublished);
				n.setAc(node.getAc());
			}
		}
		if (batchMode && bops != null) {
			bops.execute();
		} else {
			update.saveSession(ms);
		}
		res.setSuccess(true);
		return res;
	}

	/*
	 * Adds or updates a new privilege to a node
	 */
	public AddPrivilegeResponse addPrivilege(MongoSession ms, AddPrivilegeRequest req) {
		AddPrivilegeResponse res = new AddPrivilegeResponse();
		String nodeId = req.getNodeId();
		SubNode node = read.getNode(ms, nodeId);
		auth.ownerAuth(ms, node);
		boolean success = true;
		for (String principal : req.getPrincipals()) {
			principal = XString.stripIfStartsWith(principal, "@");
			if (!addPrivilege(ms, null, node, principal, null, req.getPrivileges(), res)) {
				success = false;
			}
		}
		res.setSuccess(success);
		return res;
	}

	/*
	 * Adds or updates a new privilege to a node
	 */
	public SetUnpublishedResponse setUnpublished(MongoSession ms, SetUnpublishedRequest req) {
		SetUnpublishedResponse res = new SetUnpublishedResponse();
		String nodeId = req.getNodeId();
		SubNode node = read.getNode(ms, nodeId);
		auth.ownerAuth(ms, node);
		node.set(NodeProp.UNPUBLISHED, req.isUnpublished() ? true : null);
		res.setSuccess(true);
		return res;
	}

	/*
	 * Adds or updates a new encryption key to a node
	 */
	public SetCipherKeyResponse setCipherKey(MongoSession ms, SetCipherKeyRequest req) {
		SetCipherKeyResponse res = new SetCipherKeyResponse();
		String nodeId = req.getNodeId();
		SubNode node = read.getNode(ms, nodeId);
		auth.ownerAuth(ms, node);
		String cipherKey = node.getStr(NodeProp.ENC_KEY);
		if (cipherKey == null) {
			throw new RuntimeEx("Attempted to alter keys on a non-encrypted node.");
		}
		boolean success = setCipherKey(ms, node, req.getPrincipalNodeId(), req.getCipherKey(), res);
		res.setSuccess(success);
		return res;
	}

	public boolean setCipherKey(MongoSession ms, SubNode node, String principalNodeId, String cipherKey, SetCipherKeyResponse res) {
		boolean ret = false;
		HashMap<String, AccessControl> acl = node.getAc();
		AccessControl ac = acl.get(principalNodeId);
		if (ac != null) {
			ac.setKey(cipherKey);
			node.setAc(acl);
			update.save(ms, node);
			ret = true;
		}
		return ret;
	}

	/**
	 * Adds the privileges to the node sharing this node to principal, which will be either a userName
	 * or 'public' (when the node is being shared to public)
	 * 
	 * If BulkOperations is non-null we use it instead of a non-bulk operation.
	 */
	public boolean addPrivilege(MongoSession ms, BulkOperations bops, SubNode node, String principal, SubNode principalNode, List<String> privileges, AddPrivilegeResponse res) {
		if ((principal == null && principalNode == null) || node == null) return false;
		if (principal != null) {
			principal = principal.trim();
		}
		String cipherKey = node.getStr(NodeProp.ENC_KEY);
		String mapKey = null;
		/* If we are sharing to public, then that's the map key */
		if (PrincipalName.PUBLIC.s().equalsIgnoreCase(principal)) {
			if (cipherKey != null) {
				throw new RuntimeEx("Cannot make an encrypted node public.");
			}
			mapKey = PrincipalName.PUBLIC.s();
		} else 
		/*
		 * otherwise we're sharing to a person so we now get their userNodeId to use as map key
		 */
		{
			// if no principal node passed in, then look it up
			if (principalNode == null) {
				String _principal = principal;
				principalNode = arun.run(as -> read.getUserNodeByUserName(as, _principal));
				if (principalNode == null) {
					if (res != null) {
						res.setMessage("Unknown user name: " + principal);
						res.setSuccess(false);
					}
					return false;
				}
			} else {
				principal = principalNode.getStr(NodeProp.USER);
			}
			mapKey = principalNode.getIdStr();
			/*
			 * If this node is encrypted we get the public key of the user being shared with to send back to the
			 * client, which will then use it to encrypt the symmetric key to the data, and then send back up to
			 * the server to store in this sharing entry
			 */
			if (cipherKey != null) {
				String principalPubKey = principalNode.getStr(NodeProp.USER_PREF_PUBLIC_KEY);
				if (principalPubKey == null) {
					if (res != null) {
						res.setMessage("User doesn\'t have a PublicKey available: " + principal);
						res.setSuccess(false);
						return false;
					}
				}
				if (res != null) {
					res.setPrincipalPublicKey(principalPubKey);
					res.setPrincipalNodeId(mapKey);
				}
			}
		}
		HashMap<String, AccessControl> acl = node.getAc();
		/* initialize acl to a map if it's null, or if we're sharing to public */
		if (acl == null) {
			acl = new HashMap<>();
		}
		/*
		 * Get access control entry from map, but if one is not found, we can just create one.
		 */
		AccessControl ac = acl.get(mapKey);
		if (ac == null) {
			ac = new AccessControl();
		}
		String prvs = "";
		boolean authAdded = false;
		/* Scan all the privileges to be added to this principal (rd, rw, etc) */
		for (String priv : privileges) {
			/* If this privilege is not already on ac.prvs string then append it */
			if (prvs.indexOf(priv) == -1) {
				authAdded = true;
				if (prvs.length() > 0) {
					prvs += ",";
				}
				prvs += priv;
			}
		}
		if (authAdded) {
			ac.setPrvs(prvs);
			acl.put(mapKey, ac);
			// Bulk ops is not currently being used and if/when we add it back it needs to be consistent
			// with the immediateSave, which may be tricky.
			// if bulk operation
			// if (ok(bops)) {
			// /*
			// * todo-1: this needs testing because the other place I'm doing similar code elsewhere refuses to
			// * work somehow. Seems like updating collections might not work in batching. Currently there are
			// no
			// * places we call this method with bops passed in, so this bops branch is not currently being used
			// * for that reason.
			// */
			// Query query = new Query().addCriteria(new Criteria("id").is(node.getId()));
			// Update update = new Update().set(SubNode.AC, acl);
			// bops.updateOne(query, update);
			// }
			// else non-bulk
			// else {
			node.setAc(acl);
		}
		// }
		// if (!principal.equalsIgnoreCase(PrincipalName.PUBLIC.s())) {
		// SubNode fromUserNode = read.getNode(session, node.getOwner());
		// String fromUserName = fromUserNode.getStrProp(NodeProp.USER);
		// SubNode toOwnerNode = read.getUserNodeByUserName(auth.getAdminSession(), principal);
		// /*
		// * todo-2: Although I am disabling these for now both of these lines of code do work perfectly: we
		// * can send an email notification here about node edits (first line), and the line below that
		// works
		// * fine and adds a node to the user's inbox that links to this newly shared node.
		// *
		// * I just want to think more about when exactly to trigger these notifictions. For example I may
		// * make these two buttons on the editor users must click called "Email Notification to Shares",
		// and
		// * "Send to Inboxes of Shares"
		// */
		// // outboxMgr.sendEmailNotification(auth.getAdminSession(), fromUserName, toOwnerNode, node);
		// // outboxMgr.addInboxNotification(principal, toOwnerNode, node, "New node shared to you.");
		// }
		return true;
	}

	public void removeAclEntry(MongoSession ms, SubNode node, String principalNodeId, String privToRemove) {
		/* special syntax is we remove all if asterisk specified */
		if (principalNodeId.equals("*")) {
			node.setAc(null);
			update.save(ms, node);
			return;
		}
		HashMap<String, AccessControl> acl = node.getAc();
		if (acl == null) return;
		String newPrivs = "";
		boolean removed = false;
		AccessControl ac = null;
		// if removing all privileges
		if ("*".equals(privToRemove)) {
			removed = true;
		} else 
		// else removing just some specific privileges
		{
			ac = acl.get(principalNodeId);
			if (ac == null) {
				log.debug("ac not found for " + principalNodeId + "\nACL DUMP: " + XString.prettyPrint(acl));
				return;
			}
			String privs = ac.getPrvs();
			if (privs == null) {
				log.debug("privs not found for " + principalNodeId + "\nACL DUMP: " + XString.prettyPrint(acl));
				return;
			}
			HashSet<String> setToRemove = XString.tokenizeToSet(privToRemove, ",", true);
			StringTokenizer t = new StringTokenizer(privs, ",", false);
			/*
			 * build the new comma-delimited privs list by adding all that aren't in the setToRemove
			 */
			while (t.hasMoreTokens()) {
				String tok = t.nextToken().trim();
				if (setToRemove.contains(tok)) {
					removed = true;
					continue;
				}
				if (newPrivs.length() > 0) {
					newPrivs += ",";
				}
				newPrivs += tok;
			}
		}
		if (removed) {
			/*
			 * If there are no privileges left for this principal, then remove the principal entry completely
			 * from the ACL. We don't store empty ones.
			 */
			if (newPrivs.equals("")) {
				acl.remove(principalNodeId);
			} else {
				ac.setPrvs(newPrivs);
				acl.put(principalNodeId, ac);
			}
			/*
			 * if there are now no acls at all left set the ACL to null, so it is completely removed from the
			 * node
			 */
			if (acl.isEmpty()) {
				node.setAc(null);
			} else {
				node.setAc(acl);
			}
			update.save(ms, node);
		}
	}

	/*
	 * Removes the privilege specified in the request from the node specified in the request
	 */
	public RemovePrivilegeResponse removePrivilege(MongoSession ms, RemovePrivilegeRequest req) {
		RemovePrivilegeResponse res = new RemovePrivilegeResponse();
		String nodeId = req.getNodeId();
		SubNode node = read.getNode(ms, nodeId);
		auth.ownerAuth(ms, node);
		removeAclEntry(ms, node, req.getPrincipalNodeId(), req.getPrivilege());
		// if there are no privileges left remove the "unpublished" flag, because there's no need for it.
		if (node.getAc() == null || node.getAc().size() == 0) {
			node.set(NodeProp.UNPUBLISHED, null);
		}
		res.setSuccess(true);
		return res;
	}

	public List<String> getOwnerNames(MongoSession ms, SubNode node) {
		Set<String> ownerSet = new HashSet<>();
		/*
		 * We walk up the tree util we get to the root, or find ownership on node, or any of it's parents
		 */
		int sanityCheck = 0;
		while (++sanityCheck < 100) {
			List<MongoPrincipal> principals = getNodePrincipals(ms, node);
			for (MongoPrincipal p : principals) {
				/*
				 * todo-3: this is a spot that can be optimized. We should be able to send just the userNodeId back
				 * to client, and the client should be able to deal with that (i think). depends on how much
				 * ownership info we need to show user. ownerSet.add(p.getUserNodeId());
				 */
				SubNode userNode = read.getNode(ms, p.getUserNodeId());
				String userName = userNode.getStr(NodeProp.USER);
				ownerSet.add(userName);
			}
			if (principals.size() == 0) {
				node = read.getParent(ms, node);
				if (node == null) break;
			} else {
				break;
			}
		}
		List<String> ownerList = new LinkedList<>(ownerSet);
		Collections.sort(ownerList);
		return ownerList;
	}

	public static List<MongoPrincipal> getNodePrincipals(MongoSession ms, SubNode node) {
		List<MongoPrincipal> principals = new LinkedList<>();
		MongoPrincipal principal = new MongoPrincipal();
		principal.setUserNodeId(node.getId());
		principal.setAccessLevel("w");
		principals.add(principal);
		return principals;
	}

	public static boolean isPublic(MongoSession ms, SubNode node) {
		return node != null && node.getAc() != null && node.getAc().containsKey(PrincipalName.PUBLIC.s());
	}

	public void makePublicAppendable(MongoSession ms, SubNode node) {
		setKeylessPriv(ms, node, PrincipalName.PUBLIC.s(), APConst.RDWR);
	}

	// The effeciency of using this function is it won't set the node to dirty of nothing changed.
	public void setKeylessPriv(MongoSession ms, SubNode node, String key, String prvs) {
		// if no privileges exist at all just add the one we need to add
		if (node.getAc() == null) {
			node.putAc(key, new AccessControl(null, prvs));
		} else 
		// otherwise first check to see if it's already added
		{
			AccessControl ac = node.getAc().get(key);
			if (ac != null && ac.getPrvs().equals(prvs)) {
			} else 
			// already had the correct ac, nothing to do here
			// else need to add it.
			{
				node.putAc(key, new AccessControl(null, prvs));
			}
		}
	}

	public void failIfAdminOwned(SubNode node) {
		if (isAdminOwned(node)) {
			throw new NodeAuthFailedException();
		}
	}

	public boolean isAdminOwned(SubNode node) {
		if (node == null) return false;
		return node.getOwner().equals(auth.getAdminSession().getUserNodeId());
	}
}
