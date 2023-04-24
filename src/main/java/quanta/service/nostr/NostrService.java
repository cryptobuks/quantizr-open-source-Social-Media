package quanta.service.nostr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
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
			boolean failed = false;
			SubNode userNode = read.getUserNodeByUserName(as, userName);

			if (!failed) {
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

		arun.run(as -> {
			for (NostrEvent event : req.getEvents()) {
				/*
				 * NOTE: todo-0: For performance, I'm removing this server-side verification for now (for anything
				 * other than user metadata), but in the future we can do this in an async worker thread, such that
				 * it will retro-actively delete the node from the DB if it does happen to be a bad signature. Since
				 * we do veify on client, this server-side verify is basically just protection against hackers
				 * (hostile actors)
				 */
				if (event.getKind() != KIND_Metadata) {
					if (!NostrCrypto.verifyEvent(event)) {
						log.debug("NostrEvent SIG FAIL: " + XString.prettyPrint(event));
						continue;
					}
				}

				// log.debug("NostrEvent: " + XString.prettyPrint(event));
				saveEvent(as, event, accountNodeIds, eventNodeIds, req.getRelays(), saveCount);
			}
			return null;
		});
		res.setAccntNodeIds(new LinkedList<String>(accountNodeIds));
		res.setEventNodeIds(eventNodeIds);
		res.setSaveCount(saveCount.getVal());
		return res;
	}

	private void saveEvent(MongoSession as, NostrEvent event, HashSet<String> accountNodeIds, List<String> eventNodeIds,
			String relays, IntVal saveCount) {
		switch (event.getKind()) {
			case KIND_Metadata:
				saveNostrMetadataEvent(as, event, accountNodeIds, relays, saveCount);
				break;
			case KIND_Text:
				saveNostrTextEvent(as, event, accountNodeIds, eventNodeIds, saveCount);
				break;
			default:
				log.debug("UNHANDLED NOSTR KIND: " + XString.prettyPrint(event));
				break;
		}
	}

	private void saveNostrMetadataEvent(MongoSession as, NostrEvent event, HashSet<String> accountNodeIds, String relays,
			IntVal saveCount) {
		// log.debug("SaveNostr METADATA:" + XString.prettyPrint(event));
		try {
			NostrMetadata metadata = mapper.readValue(event.getContent(), NostrMetadata.class);
			// log.debug("Nostr METADATA OBJ: " + XString.prettyPrint(metadata));

			int beforeSaveCount = saveCount.getVal();
			SubNode nostrAccnt = getNostrAccount(as, event.getPk(), null, saveCount);
			if (nostrAccnt == null)
				return;
			boolean isNew = saveCount.getVal() > beforeSaveCount;

			Date timestamp = new Date(event.getTimestamp() * 1000);

			// if this nostr object is a brand new one or newer data than our current info
			if (isNew || timestamp.getTime() > nostrAccnt.getModifyTime().getTime()) {
				nostrAccnt.set(NodeProp.DISPLAY_NAME, metadata.getDisplayName());
				nostrAccnt.set(NodeProp.NOSTR_NAME, metadata.getName());
				nostrAccnt.set(NodeProp.NOSTR_USER_NAME, metadata.getUsername());
				nostrAccnt.set(NodeProp.NOSTR_NIP05, metadata.getNip05());
				nostrAccnt.set(NodeProp.USER_ICON_URL, metadata.getPicture());
				nostrAccnt.set(NodeProp.USER_BANNER_URL, metadata.getBanner());
				nostrAccnt.set(NodeProp.USER_BIO, metadata.getAbout());
				nostrAccnt.set(NodeProp.NOSTR_USER_NPUB, event.getNpub());
				nostrAccnt.set(NodeProp.NOSTR_USER_WEBSITE, metadata.getWebsite());

				nostrAccnt.setCreateTime(timestamp);
				nostrAccnt.setModifyTime(timestamp);
			}

			// We send back account nodes EVEN if this is not a new node, because client needs the info.
			accountNodeIds.add(nostrAccnt.getIdStr());

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
			IntVal saveCount) {
		SubNode nostrNode = getNodeByNostrId(as, event.getId(), false);
		if (nostrNode != null) {
			eventNodeIds.add(nostrNode.getIdStr());
			log.debug("Nostr ID Existed: " + event.getId());
			return;
		}

		Val<SubNode> postsNode = new Val<>();
		SubNode nostrAccnt = getNostrAccount(as, event.getPk(), postsNode, saveCount);
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
		}

		Date timestamp = new Date(event.getTimestamp() * 1000);
		newNode.setCreateTime(timestamp);
		newNode.setModifyTime(timestamp);

		update.save(as, newNode, false);
		eventNodeIds.add(newNode.getIdStr());
		saveCount.inc();
	}

	/* Gets the Quanta NostrAccount node for this userKey, and creates one if necessary */
	private SubNode getNostrAccount(MongoSession as, String userKey, Val<SubNode> postsNode, IntVal saveCount) {
		SubNode nostrAccnt = read.getUserNodeByUserName(as, "." + userKey);
		if (nostrAccnt == null) {
			nostrAccnt = mongoUtil.createUser(as, "." + userKey, "", "", true, postsNode, true);
			if (nostrAccnt == null) {
				throw new RuntimeException("Unable to create nostr user for PubKey:" + userKey);
			}
			saveCount.inc();
		} else {
			if (postsNode != null) {
				SubNode postsNodeFound = read.getUserNodeByType(as, null, nostrAccnt, "### Posts", NodeType.POSTS.s(),
						Arrays.asList(PrivilegeType.READ.s()), NodeName.POSTS);
				postsNode.setVal(postsNodeFound);
			}
		}
		return nostrAccnt;
	}

	// nodeMissing sends back 'true' if we did attemp to find a NostrNode and failed to find it in the DB
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
		for (ArrayList<String> subList : tags) {
			int len = subList.size();

			if ("e".equals(subList.get(0))) {
				boolean accept = false;

				// deprecated positional array (["e", <event-id>, <relay-url>] as per NIP-01.)
				if (len < 4) {
					accept = true;
				}
				// Preferred non-deprecated way (["e", <event-id>, <relay-url>, <marker>])
				else if ("reply".equals(subList.get(3))) {
					accept = true;
				}

				if (accept) {
					if (len > 1) {
						event.setVal(subList.get(1));
						relay.setVal("");
					}

					if (len > 2) {
						relay.setVal(subList.get(2));
					}
				}
			}
		}
	}

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
