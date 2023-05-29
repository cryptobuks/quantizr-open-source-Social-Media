package quanta.service.nostr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.cxf.common.util.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import quanta.actpub.APConst;
import quanta.config.NodeName;
import quanta.config.ServiceBase;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.model.client.NostrEvent;
import quanta.model.client.NostrEventWrapper;
import quanta.model.client.NostrMetadata;
import quanta.model.client.NostrUserInfo;
import quanta.model.client.PrincipalName;
import quanta.model.client.PrivilegeType;
import quanta.mongo.CreateNodeLocation;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.SaveNostrEventRequest;
import quanta.request.SaveNostrSettingsRequest;
import quanta.response.NewNostrUsersPushInfo;
import quanta.response.SaveNostrEventResponse;
import quanta.response.SaveNostrSettingsResponse;
import quanta.util.ThreadLocals;
import quanta.util.Util;
import quanta.util.XString;
import quanta.util.val.IntVal;
import quanta.util.val.Val;

@Component
@Slf4j
public class NostrService extends ServiceBase {
	// cache is cleared every 3mins so it can pick up user changes
	public final ConcurrentHashMap<String, SubNode> nostrUserNodesByPubKey = new ConcurrentHashMap<>();
	public final ConcurrentHashMap<ObjectId, NostrEventWrapper> eventsPendingVerify = new ConcurrentHashMap<>();

	private static final RestTemplate restTemplate = new RestTemplate(Util.getClientHttpRequestFactory(10000));
	public static final ObjectMapper mapper = new ObjectMapper();
	{
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	// todo-1: put in enum
	static final int KIND_Metadata = 0;
	static final int KIND_Text = 1;
	static final int KIND_EncryptedDirectMessage = 4;

	// every 3 min
	@Scheduled(fixedDelay = 3 * 60 * 1000)
	public void userRefresh() {
		nostrUserNodesByPubKey.clear();
	}

	// every 10 seconds
	@Scheduled(fixedDelay = 10 * 1000)
	public void verifyEvents() {
		if (eventsPendingVerify.isEmpty())
			return;

		ConcurrentHashMap<ObjectId, NostrEventWrapper> workingMap = new ConcurrentHashMap<>(eventsPendingVerify);
		eventsPendingVerify.clear();

		List<NostrEventWrapper> events = new LinkedList<>(workingMap.values());
		arun.run(as -> {
			List<String> failedIds = nostr.nostrVerify(as, events);
			if (failedIds != null) {
				for (String id : failedIds) {
					delete.adminDelete(new ObjectId(id));
				}
			}
			return null;
		});
	}

	public SaveNostrSettingsResponse saveNostrSettings(SaveNostrSettingsRequest req) {
		SaveNostrSettingsResponse res = new SaveNostrSettingsResponse();
		String userName = ThreadLocals.getSC().getUserName();

		arun.run(as -> {
			SubNode userNode = read.getUserNodeByUserName(as, userName);
			if (userNode != null) {
				userNode.set(NodeProp.NOSTR_RELAYS, nostr.removeDuplicateRelays(req.getRelays()));
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

		arun.run(as -> {
			for (NostrEventWrapper event : req.getEvents()) {
				saveEvent(as, event, accountNodeIds, eventNodeIds, saveCount);
			}
			return null;
		});
		res.setAccntNodeIds(new LinkedList<String>(accountNodeIds));
		res.setEventNodeIds(eventNodeIds);
		res.setSaveCount(saveCount.getVal());
		return res;
	}

	public void saveEvent(MongoSession as, NostrEventWrapper event, HashSet<String> accountNodeIds, List<String> eventNodeIds,
			IntVal saveCount) {
		switch (event.getEvent().getKind()) {
			case KIND_Metadata:
				saveNostrMetadataEvent(as, event, accountNodeIds, saveCount);
				break;
			case KIND_EncryptedDirectMessage:
			case KIND_Text:
				saveNostrTextEvent(as, event, accountNodeIds, eventNodeIds, saveCount);
				break;
			default:
				// todo-1: for now we treat all unknown nodes as text, but we need to do something in the DB to
				// indicate this is NOT a known type.
				saveNostrTextEvent(as, event, accountNodeIds, eventNodeIds, saveCount);
				log.debug("UNHANDLED NOSTR KIND: " + XString.prettyPrint(event));
				break;
		}
	}

	/*
	 * returns a list of all bad nodeIds (failed verifies) or empty if all events pass signature check
	 */
	public List<String> nostrVerify(MongoSession as, List<NostrEventWrapper> events) {
		HashMap<String, Object> message = new HashMap<>();
		message.put("events", events);

		// tserver-tag (put TSERVER_API_KEY in secrets file)
		message.put("apiKey", prop.getTServerApiKey());
		String body = XString.prettyPrint(message);

		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(APConst.MTYPE_JSON));
		headers.setContentType(APConst.MTYPE_JSON);

		HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);
		String url = "http://tserver-host:" + prop.getTServerPort() + "/nostr-verify";

		ResponseEntity<List<String>> response =
				restTemplate.exchange(url, HttpMethod.POST, requestEntity, new ParameterizedTypeReference<List<String>>() {});

		// we get back a list of all IDs that failed to verify
		List<String> failedIds = response.getBody();
		// log.debug("FailedIDs: " + XString.prettyPrint(failedIds));

		return failedIds;
	}

	private void saveNostrMetadataEvent(MongoSession as, NostrEventWrapper event, HashSet<String> accountNodeIds,
			IntVal saveCount) {
		// log.debug("SaveNostr METADATA:" + XString.prettyPrint(event));

		/*
		 * Note: we can't do this verify async (in worker thread) like we do with events, because if the
		 * event is invalid we would have no way to rollback to prior definition of this users info
		 */
		List<String> failedIds = nostr.nostrVerify(as, Arrays.asList(event));
		if (failedIds != null && failedIds.size() > 0) {
			log.debug("NostrEvent SIG FAIL: " + XString.prettyPrint(event));
			return;
		}

		try {
			NostrEvent nevent = event.getEvent();
			SubNode nostrAccnt = getLocalUserByNostrPubKey(as, nevent.getPubkey());
			if (nostrAccnt != null) {
				accountNodeIds.add(nostrAccnt.getIdStr());
				// if the npub is owned by a local user we're done, and no need to create the foreign holder account
				return;
			}

			nostrAccnt = getOrCreateNostrAccount(as, nevent.getPubkey(), event.getRelays(), null, saveCount);
			if (nostrAccnt == null)
				return;

			Date timestamp = new Date(nevent.getCreatedAt() * 1000);

			try {
				NostrMetadata metadata = mapper.readValue(nevent.getContent(), NostrMetadata.class);
				// log.debug("Nostr METADATA OBJ: " + XString.prettyPrint(metadata));

				// Note: We can safely call all these setters and the 'dirty node' handling is smart enough to only
				// do a DB Write if something has changed.
				nostrAccnt.set(NodeProp.DISPLAY_NAME, metadata.getDisplayName());
				nostrAccnt.set(NodeProp.NOSTR_NAME, metadata.getName());
				nostrAccnt.set(NodeProp.NOSTR_USER_NAME, metadata.getUsername());
				nostrAccnt.set(NodeProp.NOSTR_NIP05, metadata.getNip05());
				nostrAccnt.set(NodeProp.USER_ICON_URL, metadata.getPicture());
				nostrAccnt.set(NodeProp.USER_BANNER_URL, metadata.getBanner());
				nostrAccnt.set(NodeProp.USER_BIO, metadata.getAbout());
				nostrAccnt.set(NodeProp.NOSTR_USER_WEBSITE, metadata.getWebsite());
			} catch (Exception e) {
				// ignore failed json objects for now.
				log.debug("Unable to parse content json for nostr event: " + XString.prettyPrint(event));
			}

			nostrAccnt.set(NodeProp.NOSTR_USER_TIMESTAMP, nevent.getCreatedAt());

			/*
			 * WARNING: It's tempting to think this pubkey needs to be here but for foreign nodes their username
			 * is basically ".${pubkey}" (a dot followed by the hex of their pubkey), so we don't store it on
			 * the node in a property by itself.
			 * 
			 * nostrAccnt.set(NodeProp.NOSTR_USER_PUBKEY, event.getPk());
			 */
			nostrAccnt.set(NodeProp.NOSTR_USER_NPUB, event.getNpub());

			// IMPORTANT: WE don't save a NOSTR_USER_PUBKEY on these foreign nodes because the
			// username itself is the pubkey with a '.' prefix.

			nostrAccnt.setCreateTime(timestamp);
			nostrAccnt.setModifyTime(timestamp);

			// We send back account nodes EVEN if this is not a new node, because client needs the info.
			accountNodeIds.add(nostrAccnt.getIdStr());

			String relays = event.getRelays();
			if (!StringUtils.isEmpty(relays)) {
				nostrAccnt.set(NodeProp.NOSTR_RELAYS, nostr.removeDuplicateRelays(relays));
			}

			// this should be updating thru the call to apCache.saveNotify(node) in
			// MongoListener, but I need to retest to be sure.
			apCache.acctNodesById.put(nostrAccnt.getIdStr(), nostrAccnt);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public String removeDuplicateRelays(String relays) {
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

	private SubNode getLocalUserByNostrPubKey(MongoSession as, String pubKey) {
		// try to get from cache first
		SubNode nostrAccnt = nostrUserNodesByPubKey.get(pubKey);
		if (nostrAccnt != null)
			return nostrAccnt;

		// else we need to query and then cache
		nostrAccnt = read.getLocalUserNodeByProp(as, NodeProp.NOSTR_USER_PUBKEY.s(), pubKey, false);
		if (nostrAccnt != null) {
			nostrUserNodesByPubKey.put(pubKey, nostrAccnt);
		}
		return nostrAccnt;
	}

	private void saveNostrTextEvent(MongoSession as, NostrEventWrapper event, HashSet<String> accountNodeIds,
			List<String> eventNodeIds, IntVal saveCount) {
		NostrEvent nevent = event.getEvent();
		SubNode nostrAccnt = getLocalUserByNostrPubKey(as, nevent.getPubkey());
		if (nostrAccnt != null) {
			log.debug("saveNostrTextEvent blocking attempt to save LOCAL data:" + XString.prettyPrint(event)
					+ " \n: proof: nostrAccnt=" + XString.prettyPrint(nostrAccnt));
			// if the npub is owned by a local user we're done, and no need to create the foreign holder account
			return;
		}

		SubNode nostrNode = getNodeByNostrId(as, nevent.getId(), false);
		if (nostrNode != null) {
			eventNodeIds.add(nostrNode.getIdStr());
			// log.debug("Nostr ID Existed: " + event.getId());
			return;
		}

		Val<SubNode> postsNode = new Val<>();
		nostrAccnt = getOrCreateNostrAccount(as, nevent.getPubkey(), null, postsNode, saveCount);
		if (nostrAccnt == null) {
			log.debug("Unable to get account: " + nevent.getPubkey());
			return;
		}

		accountNodeIds.add(nostrAccnt.getIdStr());

		if (postsNode.getVal() == null) {
			throw new RuntimeException("Unable to get Posts node.");
		}

		String newType;
		switch (nevent.getKind()) {
			case KIND_EncryptedDirectMessage:
				newType = NodeType.NOSTR_ENC_DM.s();
				break;
			default:
				newType = NodeType.NONE.s();
				break;
		}

		SubNode newNode = create.createNode(as, postsNode.getVal(), null, //
				newType, 0L, CreateNodeLocation.LAST, null, //
				nostrAccnt.getOwner(), true, true);

		if (nevent.getKind() != KIND_EncryptedDirectMessage) {
			acl.setKeylessPriv(as, newNode, PrincipalName.PUBLIC.s(), APConst.RDWR);
		}

		newNode.setContent(nevent.getContent());
		newNode.set(NodeProp.OBJECT_ID, "." + nevent.getId());

		if (nevent.getTags() != null) {
			newNode.set(NodeProp.NOSTR_TAGS, nevent.getTags());
			auth.shareToAllNostrUsers(nevent.getTags(), newNode);
		}

		Date timestamp = new Date(nevent.getCreatedAt() * 1000);
		newNode.setCreateTime(timestamp);
		newNode.setModifyTime(timestamp);

		update.save(as, newNode, false);
		eventNodeIds.add(newNode.getIdStr());
		saveCount.inc();

		eventsPendingVerify.put(newNode.getId(), event);
	}

	public SubNode getAccountByNostrPubKey(MongoSession as, String pubKey) {
		SubNode accntNode = getLocalUserByNostrPubKey(as, pubKey);

		// if account wasn't found as a local user's public key try a foreign one.
		if (accntNode == null) {
			accntNode = nostr.getOrCreateNostrAccount(as, pubKey, null, null, null);
		}

		return accntNode;
	}

	public void pushNostrInfoToClient() {
		if (ThreadLocals.getNewNostrUsers().size() > 0) {
			// WARNING: make call to get users BEFORE 'exec.run()' or else we won't be on the request thread.
			List<NostrUserInfo> users = new LinkedList<NostrUserInfo>(ThreadLocals.getNewNostrUsers().values());
			log.debug("Server Push Nostr Users" + XString.prettyPrint(users));

			if (users.size() > 0) {
				exec.run(() -> {
					push.sendServerPushInfo(ThreadLocals.getSC(), new NewNostrUsersPushInfo(users));
					ThreadLocals.getNewNostrUsers().clear();
				});
			}
		}
	}

	/* Gets the Quanta NostrAccount node for this userKey, and creates one if necessary */
	public SubNode getOrCreateNostrAccount(MongoSession as, String userKey, String relays, Val<SubNode> postsNode,
			IntVal saveCount) {
		SubNode nostrAccnt = read.getUserNodeByUserName(as, "." + userKey);
		if (nostrAccnt == null) {
			// log.debug("New Nostr User: " + userKey + " newCount=" + ThreadLocals.getNewNostrUsers().size());
			ThreadLocals.getNewNostrUsers().put(userKey, new NostrUserInfo(userKey, null, relays));

			nostrAccnt = mongoUtil.createUser(as, "." + userKey, "", "", true, postsNode, true);
			if (nostrAccnt == null) {
				throw new RuntimeException("Unable to create nostr user for PubKey:" + userKey);
			}
			if (saveCount != null) {
				saveCount.inc();
			}
		} else {
			// any time we get a node, and see it's metadata has never been queried for we cache that up to
			// happen asap
			if (nostrAccnt.getInt(NodeProp.NOSTR_USER_TIMESTAMP) == 0L) {
				ThreadLocals.getNewNostrUsers().put(userKey, new NostrUserInfo(userKey, null, relays));
			}
		}

		if (postsNode != null) {
			SubNode postsNodeFound = read.getUserNodeByType(as, null, nostrAccnt, "### Posts", NodeType.POSTS.s(),
					Arrays.asList(PrivilegeType.READ.s()), NodeName.POSTS);
			postsNode.setVal(postsNodeFound);
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
