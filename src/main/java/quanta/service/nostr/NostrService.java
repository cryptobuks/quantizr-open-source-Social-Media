package quanta.service.nostr;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import org.apache.commons.lang3.StringUtils;
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
import quanta.response.SaveNostrEventResponse;
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

	public SaveNostrEventResponse saveNostrEvent(SaveNostrEventRequest req) {
		SaveNostrEventResponse res = new SaveNostrEventResponse();
		IntVal saveCount = new IntVal(0);
		if (req.getEvents() == null)
			return res;
		HashSet<String> accountNodeIds = new HashSet<>();
		arun.run(as -> {
			for (NostrEvent event : req.getEvents()) {
				if (!NostrCrypto.verifyEvent(event)) {
					log.debug("NostrEvent SIG FAIL: " + XString.prettyPrint(event));
					continue;
				}

				// log.debug("NostrEvent: " + XString.prettyPrint(event));
				saveEvent(as, event, accountNodeIds, req.getRelays(), saveCount);
			}
			return null;
		});
		res.setAccntNodeIds(new LinkedList<String>(accountNodeIds));
		res.setSaveCount(saveCount.getVal());
		return res;
	}

	private void saveEvent(MongoSession as, NostrEvent event, HashSet<String> accountNodeIds, String relays, IntVal saveCount) {
		switch (event.getKind()) {
			case KIND_Metadata:
				saveNostrMetadataEvent(as, event, accountNodeIds, relays, saveCount);
				break;
			case KIND_Text:
				saveNostrTextEvent(as, event, accountNodeIds, saveCount);
				break;
			default:
				log.debug("UNHANDLED NOSTR KIND: " + XString.prettyPrint(event));
				break;
		}
	}

	private void saveNostrMetadataEvent(MongoSession as, NostrEvent event, HashSet<String> accountNodeIds, String relays,
			IntVal saveCount) {
		// log.debug("METADATA:" + XString.prettyPrint(event));
		try {
			NostrMetadata metadata = mapper.readValue(event.getContent(), NostrMetadata.class);
			// log.debug("METADATA OBJ: " + XString.prettyPrint(metadata));

			SubNode nostrAccnt = getNostrAccount(as, event.getPk(), null, saveCount);
			if (nostrAccnt == null)
				return;

			accountNodeIds.add(nostrAccnt.getIdStr());

			// get the best display name we can find.
			String displayName = getBestDisplayName(metadata);
			nostrAccnt.set(NodeProp.DISPLAY_NAME, displayName);
			nostrAccnt.set(NodeProp.USER_ICON_URL, metadata.getPicture());
			nostrAccnt.set(NodeProp.USER_BIO, metadata.getAbout());
			nostrAccnt.set(NodeProp.NOSTR_RELAYS, relays);

			Date timestamp = new Date(event.getTimestamp() * 1000);
			nostrAccnt.setCreateTime(timestamp);
			nostrAccnt.setModifyTime(timestamp);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private String getBestDisplayName(NostrMetadata metadata) {
		String displayName = metadata.getDisplayName();
		if (StringUtils.isEmpty(displayName)) {
			displayName = metadata.getName();
		}
		if (StringUtils.isEmpty(displayName)) {
			displayName = metadata.getUsername();
		}
		return displayName;
	}

	private void saveNostrTextEvent(MongoSession as, NostrEvent event, HashSet<String> accountNodeIds, IntVal saveCount) {
		SubNode nostrNode = getNodeByNostrId(as, event.getId(), false);
		if (nostrNode != null) {
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

		Date timestamp = new Date(event.getTimestamp() * 1000);
		newNode.setCreateTime(timestamp);
		newNode.setModifyTime(timestamp);

		update.save(as, newNode, false);
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

	public SubNode getNodeByNostrId(MongoSession ms, String id, boolean allowAuth) {
		if (!id.startsWith(".")) {
			id = "." + id;
		}
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
