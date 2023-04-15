package quanta.service;

import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import quanta.actpub.APConst;
import quanta.config.NodeName;
import quanta.config.NodePath;
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
import quanta.util.ThreadLocals;
import quanta.util.XString;
import quanta.util.val.Val;

@Component
@Slf4j
public class NostrService extends ServiceBase {

	public static final ObjectMapper mapper = new ObjectMapper();
	{
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

	// todo-0: put in enum
	static final int KIND_Metadata = 0;
	static final int KIND_Text = 1;

	public SaveNostrEventResponse saveNostrEvent(SaveNostrEventRequest req) {
		ThreadLocals.requireAdmin();

		SaveNostrEventResponse res = new SaveNostrEventResponse();
		if (req.getEvents() == null)
			return res;
		arun.run(as -> {
			// todo-0: we need to check the signature on the event before saving into DB.
			for (NostrEvent event : req.getEvents()) {
				// log.debug("NostrEvent: " + XString.prettyPrint(event));
				saveEvent(as, event);
			}
			return null;
		});
		return res;
	}

	private void saveEvent(MongoSession as, NostrEvent event) {
		// todo-0: need to check signatures on all.
		switch (event.getKind()) {
			case KIND_Metadata:
				saveNostrMetadataEvent(as, event);
				break;
			case KIND_Text:
				saveNostrTextEvent(as, event);
				break;
			default:
				log.debug("UNHANDLED NOSTR KIND: " + XString.prettyPrint(event));
				break;
		}
	}

	private void saveNostrMetadataEvent(MongoSession as, NostrEvent event) {
		log.debug("METADATA:" + XString.prettyPrint(event));
		try {
			// HashMap<String, Object> metadata =
			// mapper.readValue(event.getContent(), new TypeReference<HashMap<String, Object>>() {});
			NostrMetadata metadata = mapper.readValue(event.getContent(), NostrMetadata.class);
			log.debug("METADATA OBJ: " + XString.prettyPrint(metadata));

			SubNode nostrAccnt = getNostrAccount(as, event.getPk(), null);
			if (nostrAccnt == null)
				return;

			// get the best display name we can find.
			String displayName = getBestDisplayName(metadata);
			nostrAccnt.set(NodeProp.DISPLAY_NAME, displayName);
			nostrAccnt.set(NodeProp.USER_IMG_URL, metadata.getPicture());
			nostrAccnt.set(NodeProp.USER_BIO, metadata.getAbout());
			
			// todo-0: we should be able to remove this line. Will only save if dirty, then.
			update.save(as, nostrAccnt, false);

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

	private void saveNostrTextEvent(MongoSession as, NostrEvent event) {
		// todo-0: replace this with an "exists()" call that doesn't get the actual object
		SubNode nostrNode = getNodeByNostrId(as, event.getId(), false);
		if (nostrNode != null) {
			// log.debug("Node Existed: " + XString.prettyPrint(nostrNode));
			return;
		}

		Val<SubNode> postsNode = new Val<>();
		SubNode nostrAccnt = getNostrAccount(as, event.getPk(), postsNode);

		if (postsNode.getVal() == null) {
			throw new RuntimeException("Unable to get Posts node.");
		}

		SubNode newNode = create.createNode(as, postsNode.getVal(), null, //
				NodeType.NONE.s(), 0L, CreateNodeLocation.LAST, null, //
				nostrAccnt.getOwner(), true, true);

		acl.setKeylessPriv(as, newNode, PrincipalName.PUBLIC.s(), APConst.RDWR);
		newNode.setContent(event.getContent());
		newNode.set(NodeProp.NOSTR_ID, event.getId());
		newNode.touch();
		update.save(as, newNode, false);
	}

	/* Gets the Quanta NostrAccount node for this userKey, and creates one if necessary */
	private SubNode getNostrAccount(MongoSession as, String userKey, Val<SubNode> postsNode) {
		SubNode nostrAccnt;
		nostrAccnt = getUserNodeByNostrId(as, userKey, false);
		if (nostrAccnt == null) {
			nostrAccnt = mongoUtil.createUser(as, "nostr-" + System.currentTimeMillis(), "", "", true, postsNode, true);
			if (nostrAccnt == null) {
				throw new RuntimeException("Unable to create nostr user for PubKey:" + userKey);
			}
			nostrAccnt.set(NodeProp.NOSTR_ID, userKey);
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
		// Otherwise for ordinary users root is based off their username
		Query q = new Query();
		Criteria crit = Criteria.where(SubNode.PROPS + "." + NodeProp.NOSTR_ID).is(id);
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

	public SubNode getUserNodeByNostrId(MongoSession ms, String pubKey, boolean allowAuth) {
		// Otherwise for ordinary users root is based off their username
		Query q = new Query();
		Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(NodePath.REMOTE_USERS_PATH)) //
				// case-insensitive lookup of username:
				.and(SubNode.PROPS + "." + NodeProp.NOSTR_ID).is(pubKey) //
				.and(SubNode.TYPE).is(NodeType.ACCOUNT.s());

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

}
