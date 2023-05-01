package quanta.service.nostr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import org.apache.cxf.common.util.StringUtils;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import quanta.actpub.APConst;
import quanta.config.NodeName;
import quanta.config.ServiceBase;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.model.client.NostrEvent;
import quanta.model.client.NostrMetadata;
import quanta.model.client.NostrUserInfo;
import quanta.model.client.PrincipalName;
import quanta.model.client.PrivilegeType;
import quanta.mongo.CreateNodeLocation;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.SaveNostrEventRequest;
import quanta.request.SaveNostrSettingsRequest;
import quanta.response.SaveNostrEventResponse;
import quanta.response.SaveNostrSettingsResponse;
import quanta.util.ThreadLocals;
import quanta.util.XString;
import quanta.util.val.IntVal;
import quanta.util.val.Val;

@Component
@Slf4j
public class NostrService extends ServiceBase {

	public static final ObjectMapper mapper = new ObjectMapper();
	{
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	// todo-1: put in enum
	static final int KIND_Metadata = 0;
	static final int KIND_Text = 1;

	public SaveNostrSettingsResponse saveNostrSettings(SaveNostrSettingsRequest req) {
		SaveNostrSettingsResponse res = new SaveNostrSettingsResponse();
		String userName = ThreadLocals.getSC().getUserName();

		arun.run(as -> {
			SubNode userNode = read.getUserNodeByUserName(as, userName);
			if (userNode != null) {
				userNode.set(NodeProp.NOSTR_RELAYS, req.getRelays());
				update.save(as, userNode);
				res.setSuccess(true);
			}
			return null;
		});
		return res;
	}

	public SaveNostrEventResponse saveNostrEvents(SaveNostrEventRequest req) {
		SaveNostrEventResponse res = new SaveNostrEventResponse();
		IntVal saveCount = new IntVal(0);
		if (req.getEvents() == null)
			return res;

		HashSet<String> accountNodeIds = new HashSet<>();
		List<String> eventNodeIds = new ArrayList<>();

		// build userInfoMap for efficient lookups
		HashMap<String, NostrUserInfo> userInfoMap = new HashMap<>();
		if (req.getUserInfo() != null) {
			req.getUserInfo().forEach(ui -> {
				userInfoMap.put(ui.getPk(), ui);
			});
		}

		arun.run(as -> {
			for (NostrEvent event : req.getEvents()) {

				if (!NostrCrypto.verifyEvent(event)) {
					log.debug("NostrEvent SIG FAIL: " + XString.prettyPrint(event));
					continue;
				}

				// log.debug("NostrEvent: " + XString.prettyPrint(event));
				saveEvent(as, event, accountNodeIds, eventNodeIds, saveCount, userInfoMap);
			}
			return null;
		});
		res.setAccntNodeIds(new LinkedList<String>(accountNodeIds));
		res.setEventNodeIds(eventNodeIds);
		res.setSaveCount(saveCount.getVal());
		return res;
	}

	private void saveEvent(MongoSession as, NostrEvent event, HashSet<String> accountNodeIds, List<String> eventNodeIds,
			IntVal saveCount, HashMap<String, NostrUserInfo> userInfoMap) {
		switch (event.getKind()) {
			case KIND_Metadata:
				saveNostrMetadataEvent(as, event, accountNodeIds, saveCount);
				break;
			case KIND_Text:
				saveNostrTextEvent(as, event, accountNodeIds, eventNodeIds, saveCount, userInfoMap);
				break;
			default:
				log.debug("UNHANDLED NOSTR KIND: " + XString.prettyPrint(event));
				break;
		}
	}

	private void saveNostrMetadataEvent(MongoSession as, NostrEvent event, HashSet<String> accountNodeIds, IntVal saveCount) {
		// log.debug("SaveNostr METADATA:" + XString.prettyPrint(event));
		try {
			SubNode nostrAccnt = read.getLocalUserNodeByProp(as, NodeProp.NOSTR_USER_PUBKEY.s(), event.getPk(), false);
			if (nostrAccnt != null) {
				accountNodeIds.add(nostrAccnt.getIdStr());
				// if the npub is owned by a local user we're done, and no need to create the foreign holder account
				return;
			}

			int beforeSaveCount = saveCount.getVal();
			nostrAccnt = getOrCreateNostrAccount(as, event.getPk(), null, saveCount);
			if (nostrAccnt == null)
				return;
			boolean isNew = saveCount.getVal() > beforeSaveCount;

			Date timestamp = new Date(event.getTimestamp() * 1000);

			// if this nostr object is a brand new one or newer data than our current info
			if (isNew || timestamp.getTime() > nostrAccnt.getModifyTime().getTime()) {
				NostrMetadata metadata = mapper.readValue(event.getContent(), NostrMetadata.class);
				// log.debug("Nostr METADATA OBJ: " + XString.prettyPrint(metadata));

				nostrAccnt.set(NodeProp.DISPLAY_NAME, metadata.getDisplayName());
				nostrAccnt.set(NodeProp.NOSTR_NAME, metadata.getName());
				nostrAccnt.set(NodeProp.NOSTR_USER_NAME, metadata.getUsername());
				nostrAccnt.set(NodeProp.NOSTR_NIP05, metadata.getNip05());
				nostrAccnt.set(NodeProp.USER_ICON_URL, metadata.getPicture());
				nostrAccnt.set(NodeProp.USER_BANNER_URL, metadata.getBanner());
				nostrAccnt.set(NodeProp.USER_BIO, metadata.getAbout());

				// note: we always need to be able to generate KEY so don't ever let the client upload
				// an nip05 web url to save to this. Always send up the key.
				nostrAccnt.set(NodeProp.NOSTR_USER_NPUB, event.getNpub());

				// IMPORTANT: WE don't save a NOSTR_USER_PUBKEY on these foreign nodes because the
				// username itself is the pubkey with a '.' prefix.

				nostrAccnt.set(NodeProp.NOSTR_USER_WEBSITE, metadata.getWebsite());

				nostrAccnt.setCreateTime(timestamp);
				nostrAccnt.setModifyTime(timestamp);
			}

			// We send back account nodes EVEN if this is not a new node, because client needs the info.
			accountNodeIds.add(nostrAccnt.getIdStr());

			String relays = event.getRelays();
			if (!StringUtils.isEmpty(relays)) {
				String existingRelays = nostrAccnt.getStr(NodeProp.NOSTR_RELAYS);
				if (!StringUtils.isEmpty(existingRelays)) {
					relays += "\n" + existingRelays;
				}
				relays = removeDuplicateRelays(relays);
				nostrAccnt.set(NodeProp.NOSTR_RELAYS, relays);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// parses the new-line delimited string of relays, and returns a string with the same ones
	// but guarantees no duplicates.
	private String removeDuplicateRelays(String relays) {
		StringBuilder sb = new StringBuilder();
		StringTokenizer t = new StringTokenizer(relays, "\n\r\t ", false);
		HashSet<String> relaySet = new HashSet<>();
		while (t.hasMoreTokens()) {
			String tok = t.nextToken();
			if (relaySet.add(tok)) {
				if (sb.length() > 0) {
					sb.append("\n");
				}
				sb.append(tok);
			}
		}
		return sb.toString();
	}

	private void saveNostrTextEvent(MongoSession as, NostrEvent event, HashSet<String> accountNodeIds, List<String> eventNodeIds,
			IntVal saveCount, HashMap<String, NostrUserInfo> userInfoMap) {

		SubNode nostrAccnt = read.getLocalUserNodeByProp(as, NodeProp.NOSTR_USER_PUBKEY.s(), event.getPk(), false);
		if (nostrAccnt != null) {
			log.debug("saveNostrTextEvent blocking attempt to save LOCAL data:" + XString.prettyPrint(event)
					+ " \n: proof: nostrAccnt=" + XString.prettyPrint(nostrAccnt));
			// if the npub is owned by a local user we're done, and no need to create the foreign holder account
			return;
		}

		SubNode nostrNode = getNodeByNostrId(as, event.getId(), false);
		if (nostrNode != null) {
			eventNodeIds.add(nostrNode.getIdStr());
			// log.debug("Nostr ID Existed: " + event.getId());
			return;
		}

		Val<SubNode> postsNode = new Val<>();
		nostrAccnt = getOrCreateNostrAccount(as, event.getPk(), postsNode, saveCount);
		if (nostrAccnt == null) {
			log.debug("Unable to get account: " + event.getPk());
			return;
		}

		accountNodeIds.add(nostrAccnt.getIdStr());

		if (postsNode.getVal() == null) {
			throw new RuntimeException("Unable to get Posts node.");
		}

		SubNode newNode = create.createNode(as, postsNode.getVal(), null, //
				NodeType.NONE.s(), 0L, CreateNodeLocation.LAST, null, //
				nostrAccnt.getOwner(), true, true);

		acl.setKeylessPriv(as, newNode, PrincipalName.PUBLIC.s(), APConst.RDWR);
		newNode.setContent(event.getContent());
		newNode.set(NodeProp.OBJECT_ID, "." + event.getId());

		if (event.getTags() != null) {
			newNode.set(NodeProp.NOSTR_TAGS, event.getTags());
			auth.shareToAllNostrUsers(event.getTags(), newNode);
		}

		Date timestamp = new Date(event.getTimestamp() * 1000);
		newNode.setCreateTime(timestamp);
		newNode.setModifyTime(timestamp);

		update.save(as, newNode, false);
		eventNodeIds.add(newNode.getIdStr());
		saveCount.inc();
	}

	public SubNode getAccountByNostrPubKey(MongoSession as, String pubKey) {
		SubNode accntNode = read.getLocalUserNodeByProp(as, NodeProp.NOSTR_USER_PUBKEY.s(), pubKey, false);

		// if account wasn't found as a local user's public key try a foreign one.
		if (accntNode == null) {
			accntNode = nostr.getOrCreateNostrAccount(as, pubKey, null, null);
		}

		return accntNode;
	}

	/* Gets the Quanta NostrAccount node for this userKey, and creates one if necessary */
	public SubNode getOrCreateNostrAccount(MongoSession as, String userKey, Val<SubNode> postsNode, IntVal saveCount) {
		SubNode nostrAccnt = read.getUserNodeByUserName(as, "." + userKey);
		if (nostrAccnt == null) {
			nostrAccnt = mongoUtil.createUser(as, "." + userKey, "", "", true, postsNode, true);
			if (nostrAccnt == null) {
				throw new RuntimeException("Unable to create nostr user for PubKey:" + userKey);
			}
			if (saveCount != null) {
				saveCount.inc();
			}
		} else {
			if (postsNode != null) {
				SubNode postsNodeFound = read.getUserNodeByType(as, null, nostrAccnt, "### Posts", NodeType.POSTS.s(),
						Arrays.asList(PrivilegeType.READ.s()), NodeName.POSTS);
				postsNode.setVal(postsNodeFound);
			}
		}
		return nostrAccnt;
	}

	// nodeMissing sends back 'true' if we did attemp to find a NostrNode and failed to find it in the
	// DB
	public SubNode getNodeBeingRepliedTo(MongoSession ms, SubNode node, Val<Boolean> nodeMissing) {
		if (!isNostrNode(node))
			return null;

		Val<String> eventRepliedTo = new Val<String>();
		Val<String> relayRepliedTo = new Val<String>();
		getReplyInfo(node, eventRepliedTo, relayRepliedTo);

		if (eventRepliedTo.getVal() != null) {
			SubNode nodeFound = getNodeByNostrId(ms, eventRepliedTo.getVal(), true);
			if (nodeFound == null) {
				nodeMissing.setVal(true);
			}
			return nodeFound;
		}
		return null;
	}

	// get info about node this node is a reply to
	public void getReplyInfo(SubNode node, Val<String> event, Val<String> relay) {

		ArrayList<ArrayList<String>> tags = (ArrayList) node.getObj(NodeProp.NOSTR_TAGS.s(), ArrayList.class);
		ArrayList<String> any = null;
		ArrayList<String> reply = null;
		ArrayList<String> root = null;

		for (ArrayList<String> itm : tags) {
			if ("e".equals(itm.get(0))) {
				// deprecated positional array (["e", <event-id>, <relay-url>] as per NIP-01.)
				if (itm.size() < 4) {
					any = itm;
				}
				// Preferred non-deprecated way (["e", <event-id>, <relay-url>, <marker>])
				else if ("reply".equals(itm.get(3))) {
					reply = itm;
				} else if ("root".equals(itm.get(3))) {
					root = itm;
				}
			}
		}

		ArrayList<String> accept = null;
		if (reply != null) {
			accept = reply;
		} else if (root != null) {
			accept = root;
		} else {
			accept = any;
		}

		if (accept != null) {
			if (accept.size() > 1) {
				event.setVal(accept.get(1));
				relay.setVal("");
			}

			if (accept.size() > 2) {
				relay.setVal(accept.get(2));
			}
		}
	}

	// NOTE: All OBJECT_IDs that are Nostr ones start with "."
	public SubNode getNodeByNostrId(MongoSession ms, String id, boolean allowAuth) {
		if (!id.startsWith(".")) {
			id = "." + id;
		}
		// log.debug("Looking up OBJECT_ID: " + id);

		// Otherwise for ordinary users root is based off their username
		Query q = new Query();
		Criteria crit = Criteria.where(SubNode.PROPS + "." + NodeProp.OBJECT_ID).is(id);
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

	public boolean isNostrNode(SubNode node) {
		String objId = node.getStr(NodeProp.OBJECT_ID);
		return objId != null && objId.startsWith(".");
	}

	/*
	 * Our username is a string that is the sha256 hash of the user's PublicKey hex string prefixed by a
	 * ".". We use the dot to make sure no users can squat on it, by simply having the rule that local
	 * Quanta users are not allwed to use a dot in their username.
	 */
	public boolean isNostrUserName(String userName) {
		if (userName == null)
			return false;
		return userName.startsWith(".") && !userName.contains("@");
	}
}
