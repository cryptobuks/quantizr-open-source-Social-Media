package quanta.mongo.model;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceConstructor;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.model.client.Attachment;
import quanta.model.client.Constant;
import quanta.model.client.NodeLink;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.util.ExUtil;
import quanta.util.ThreadLocals;
import quanta.util.Util;
import quanta.util.XString;

/**
 * The primary element of storage for the entire Quanta DB.
 * 
 * todo-p0: should all @JsonIgnores in here also be @Transient ?
 */
@Document(collection = "nodes")
@TypeAlias("n1")
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({SubNode.PATH, SubNode.CONTENT, SubNode.NAME, SubNode.ID, SubNode.ORDINAL, SubNode.OWNER, SubNode.XFR,
		SubNode.CREATE_TIME, SubNode.MODIFY_TIME, SubNode.AC, SubNode.PROPS, SubNode.ATTACHMENTS})
@Slf4j
public class SubNode {
	public static final ObjectMapper mapper = new ObjectMapper();

	// NOTE: This didn't allow unknown properties as expected but putting the
	// following in the JSON classes did:
	// @JsonIgnoreProperties(ignoreUnknown = true)
	{
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}
	// This optimization is optional and we have this flag if we need to turn it off.
	public static final boolean USE_HAS_CHILDREN = true;

	/*
	 * This tells all parts of the code that any changes being made on this node can be accepted without
	 * further auth checks. important for when a user thread is doing something but that causes us to
	 * need to modify some node that user is not expected to also own
	 */
	@Transient
	@JsonIgnore
	public boolean adminUpdate = false;

	// note: All bulk ops using "id" when the value here is "_id". I can't remember the
	// reason for "_id"
	public static final String ID = "_id";
	@Id
	@Field(ID)
	private ObjectId id;

	public static final String ORDINAL = "ord";
	@Field(ORDINAL)
	private Long ordinal;

	// Holds null if children status unknown. Not yet generated.
	// NOTE: We have no index on this field because we never query on it.
	public static final String HAS_CHILDREN = "hch";
	@Field(HAS_CHILDREN)
	private Boolean hch;

	public static final String PATH = "pth";
	@Field(PATH)
	private String path;

	public static final String TYPE = "typ";
	@Field(TYPE)
	private String type;

	public static final String CONTENT = "cont";
	@Field(CONTENT)
	private String content;

	public static final String TAGS = "tag";
	@Field(TAGS)
	private String tags;

	public static final String NAME = "name";
	@Field(NAME)
	private String name;

	public static final String OWNER = "own";
	@Field(OWNER)
	private ObjectId owner;

	// OwnerId of person who transfered this node to "owner"
	public static final String XFR = "xfr";
	@Field(XFR)
	private ObjectId transferFrom;

	public static final String CREATE_TIME = "ctm";
	@Field(CREATE_TIME)
	private Date createTime;

	public static final String MODIFY_TIME = "mtm";
	@Field(MODIFY_TIME)
	private Date modifyTime;

	// Also defined in NodeProp.SUBNODE_PROPS
	public static final String PROPS = "p";
	@Field(PROPS)
	private HashMap<String, Object> props;

	@Transient
	@JsonIgnore
	private Object propLock = new Object();

	public static final String ATTACHMENTS = "a";
	@Field(ATTACHMENTS)
	// for now the key string can be "p" (primary) or "h" (header). Header is the user's account header
	// image.
	private HashMap<String, Attachment> attachments;

	@Transient
	@JsonIgnore
	private Object linksLock = new Object();

	public static final String LINKS = "lnk";
	@Field(LINKS)
	private HashMap<String, NodeLink> links;

	@Transient
	@JsonIgnore
	private Object attLock = new Object();

	public static final String LIKES = "like";
	@Field(LIKES)
	private HashSet<String> likes;

	@Transient
	@JsonIgnore
	private Object likesLock = new Object();

	// these are public on purpose. (the M means this CID is from MFS, and no need to pin or unpin ever)
	public static final String MCID = "mcid";
	@Field(MCID)
	public String mcid;

	// (the M means this CID is from MFS, and no need to pin or unpin ever)
	public static final String PREV_MCID = "prevMcid";
	@Field(PREV_MCID)
	public String prevMcid;

	/*
	 * ACL=Access Control List
	 * 
	 * Keys are userNodeIds, and values is a comma delimited list of any of PrivilegeType.java values.
	 * However in addition to userNodeIds identifying users the additional key of "public" is allowed as
	 * a key which indicates privileges granted to everyone (the entire public)
	 */
	public static final String AC = "ac";
	@Field(AC)
	private HashMap<String, AccessControl> ac;

	@Transient
	@JsonIgnore
	private Object acLock = new Object();

	public static final String[] ALL_FIELDS = { //
			SubNode.PATH, //
			SubNode.TYPE, //
			SubNode.CONTENT, //
			SubNode.TAGS, //
			SubNode.NAME, //
			SubNode.ID, //
			SubNode.ORDINAL, //
			SubNode.HAS_CHILDREN, //
			SubNode.OWNER, //
			SubNode.XFR, //
			SubNode.CREATE_TIME, //
			SubNode.MODIFY_TIME, //
			SubNode.AC, //
			SubNode.PROPS, //
			SubNode.ATTACHMENTS, //
			SubNode.LINKS, //
			SubNode.LIKES};

	@Transient
	@JsonIgnore
	private int contentLength;

	@PersistenceConstructor
	public SubNode() {
		/*
		 * WARNING: Do NOT initialize times (mod time or create time) in here. This constructor gets called
		 * any time the persistence engine loads a node!!!!
		 */
	}

	public SubNode(ObjectId owner, String path, String type, Long ordinal) {
		this.owner = owner;
		this.type = type;
		this.ordinal = ordinal;
		setPath(path);

		Date now = Calendar.getInstance().getTime();
		setModifyTime(now);
		setCreateTime(now);
	}

	// we don't annotate this because we have a custom getter.
	// @JsonProperty(FIELD_ID)
	public ObjectId getId() {
		return id;
	}

	@Transient
	@JsonIgnore
	public String getIdStr() {
		return getId() != null ? getId().toHexString() : null;
	}

	/* Auth: Anyone can write the id as there's no pre-existing id */
	@JsonProperty(ID)
	public void setId(ObjectId id) {
		// IDs are allowed to be set to null and ImportArchiveBase does this to force nodes to get saved
		// as a new document when they're being imported.
		if (id != null && this.id != null && !this.id.equals(id)) {
			throw new RuntimeException("Node IDs are immutable.");
		}
		this.id = id;
	}

	@JsonGetter(ID)
	public String jsonId() {
		return id != null ? id.toHexString() : null;
	}

	@JsonProperty(PATH)
	public String getPath() {
		return path;
	}

	@Transient
	@JsonIgnore
	public String getParentPath() {
		if (getPath() == null)
			return null;
		return XString.truncAfterLast(getPath(), "/");
	}

	@Transient
	@JsonIgnore
	public String getLastPathPart() {
		if (getPath() == null)
			return null;
		return XString.parseAfterLast(getPath(), "/");
	}

	/*
	 * Auth: As long as the current user owns this node they can set it's path to any path, but only
	 * when the save is done is the final validation done
	 */
	@JsonProperty(PATH)
	public void setPath(String path) {
		if (Util.equalObjs(path, this.path))
			return;

		ServiceBase.auth.ownerAuth(this);
		this.verifyParentPath = true;
		ThreadLocals.dirty(this);
		this.path = path;
	}

	/*
	 * Initialized to false in MongoListener if the node has a known path, then all calls to setPath
	 * trigger it as dirty so that upon final save of a node the MondoListener can check to be sure it
	 * has a parent, so orphans can never be written. It's just extra precaution for DB integrity and
	 * theoretically doesn't need to exist.
	 */

	@Transient
	@JsonIgnore
	public boolean verifyParentPath;

	@Transient
	@JsonIgnore
	public void directSetPath(String path) {
		this.path = path;
	}

	@JsonProperty(ORDINAL)
	public Long getOrdinal() {
		return ordinal;
	}

	@JsonProperty(ORDINAL)
	public void setOrdinal(Long ordinal) {
		if (Util.equalObjs(ordinal, this.ordinal))
			return;
		ThreadLocals.dirty(this);
		this.ordinal = ordinal;
	}

	@JsonProperty(HAS_CHILDREN)
	public Boolean getHasChildren() {
		return hch;
	}

	@JsonProperty(HAS_CHILDREN)
	public void setHasChildren(Boolean hch) {
		if (Util.equalObjs(hch, this.hch))
			return;
		ThreadLocals.dirty(this);
		this.hch = hch;
	}

	// we don't annotate this because we have a custom getter
	// @JsonProperty(FIELD_OWNER)
	public ObjectId getOwner() {
		return owner;
	}

	@JsonProperty(OWNER)
	public void setOwner(ObjectId owner) {
		if (Util.equalObjs(owner, this.owner))
			return;
		ThreadLocals.dirty(this);
		this.owner = owner;
	}

	@JsonGetter(OWNER)
	public String jsonOwner() {
		return owner != null ? owner.toHexString() : null;
	}

	public ObjectId getTransferFrom() {
		return transferFrom;
	}

	@JsonProperty(XFR)
	public void setTransferFrom(ObjectId transferFrom) {
		if (Util.equalObjs(transferFrom, this.transferFrom))
			return;
		ThreadLocals.dirty(this);
		this.transferFrom = transferFrom;
	}

	@JsonGetter(XFR)
	public String jsonTransferFrom() {
		return transferFrom != null ? transferFrom.toHexString() : null;
	}

	@JsonProperty(CREATE_TIME)
	public Date getCreateTime() {
		return createTime;
	}

	@JsonProperty(CREATE_TIME)
	public void setCreateTime(Date createTime) {
		ThreadLocals.dirty(this);
		this.createTime = createTime;
	}

	@JsonProperty(MODIFY_TIME)
	public Date getModifyTime() {
		return modifyTime;
	}

	@JsonProperty(MODIFY_TIME)
	public void setModifyTime(Date modifyTime) {
		ThreadLocals.dirty(this);
		this.modifyTime = modifyTime;
	}

	public void touch() {
		setModifyTime(Calendar.getInstance().getTime());
	}

	@JsonProperty(AC)
	public HashMap<String, AccessControl> getAc() {
		synchronized (acLock) {
			return ac;
		}
	}

	@Transient
	@JsonIgnore
	public HashMap<String, AccessControl> safeGetAc() {
		synchronized (acLock) {
			if (ac == null) {
				ac = new HashMap<>();
				ThreadLocals.dirty(this);
			}
			return ac;
		}
	}

	// Write an access control value. Does nothing if same value is already existing.
	@Transient
	@JsonIgnore
	public void putAc(String key, AccessControl ac) {
		// don't allow adding this node to it's own sharing.
		if (getOwner() != null && getOwner().toHexString().equals(key))
			return;

		synchronized (acLock) {
			// look up any ac already existing for this key
			AccessControl thisAc = safeGetAc().get(key);

			// only put the new ac key in the map if the existing was not found or if it's not the same value we
			// already have
			if (thisAc == null || !thisAc.eq(ac)) {
				safeGetAc().put(key, ac);
				ThreadLocals.dirty(this);
			}
		}
	}

	@JsonProperty(AC)
	public void setAc(HashMap<String, AccessControl> ac) {
		if (ac == null && this.ac == null)
			return;
		ThreadLocals.dirty(this);
		synchronized (acLock) {
			// sanity check do disallow this this node sharing to it's owner
			if (ac != null && getOwner() != null) {
				ac.remove(getOwner().toHexString());
			}
			this.ac = ac;
		}
	}

	public void clearSecretProperties() {
		synchronized (propLock) {
			if (props != null) {
				props.remove(NodeProp.CRYPTO_KEY_PRIVATE.s());
				props.remove(NodeProp.EMAIL.s());
				props.remove(NodeProp.CODE.s());
				props.remove(NodeProp.ENC_KEY.s());
				props.remove(NodeProp.PWD_HASH.s());
				props.remove(NodeProp.VOTE.s());
			}
		}
	}

	@JsonProperty(PROPS)
	public HashMap<String, Object> getProps() {
		synchronized (propLock) {
			return props;
		}
	}

	@JsonProperty(PROPS)
	public void setProps(HashMap<String, Object> props) {
		if (props == null && this.props == null)
			return;
		ThreadLocals.dirty(this);
		synchronized (propLock) {
			this.props = props;
		}
	}

	@JsonProperty(ATTACHMENTS)
	public HashMap<String, Attachment> getAttachments() {
		synchronized (attLock) {
			return attachments;
		}
	}

	@JsonProperty(ATTACHMENTS)
	public void setAttachments(HashMap<String, Attachment> attachments) {
		if (attachments == null && this.attachments == null)
			return;
		ThreadLocals.dirty(this);
		synchronized (attLock) {
			this.attachments = attachments;
		}
	}

	@JsonProperty(LINKS)
	public HashMap<String, NodeLink> getLinks() {
		synchronized (linksLock) {
			return links;
		}
	}

	@JsonProperty(LINKS)
	public void setLinks(HashMap<String, NodeLink> links) {
		if (links == null && this.links == null)
			return;
		ThreadLocals.dirty(this);
		synchronized (linksLock) {
			this.links = links;
		}
	}

	@Transient
	@JsonIgnore
	public void addLink(String key, NodeLink link) {
		synchronized (linksLock) {
			if (links == null) {
				links = new HashMap<>();
			}
			if (key == null) {
				key = "k" + links.size();
			}
			links.put(key, link);
			ThreadLocals.dirty(this);
		}
	}

	@Transient
	@JsonIgnore
	public Attachment getFirstAttachment() {
		Attachment att = null;
		synchronized (attLock) {
			if (attachments != null) {
				List<Attachment> atts = getOrderedAttachments();
				if (atts != null && atts.size() > 0) {
					att = atts.get(0);
				}
			}
		}
		return att;
	}

	@Transient
	@JsonIgnore
	public void addAttachment(Attachment att) {
		synchronized (attLock) {
			if (attachments == null) {
				attachments = new HashMap<>();
			}
			attachments.put(att.getKey(), att);
			ThreadLocals.dirty(this);
		}
	}

	@Transient
	@JsonIgnore
	public List<Attachment> getOrderedAttachments() {
		List<Attachment> list = new LinkedList<>();
		synchronized (attLock) {
			if (attachments != null) {
				attachments.forEach((String key, Attachment att) -> {
					att.setKey(key);
					list.add(att);
				});
			}
		}
		list.sort((a1, a2) -> {
			int a1Idx = a1.getOrdinal() != null ? a1.getOrdinal().intValue() : 0;
			int a2Idx = a2.getOrdinal() != null ? a2.getOrdinal().intValue() : 0;
			return a1Idx - a2Idx;
		});
		return list.size() > 0 ? list : null;
	}

	// get the named attachment if there is one, and if there isn't and create is true then create it
	// and return it.
	@Transient
	@JsonIgnore
	public Attachment getAttachment(String name, boolean create, boolean forceNew) {
		synchronized (attLock) {
			if (StringUtils.isEmpty(name)) {
				name = Constant.ATTACHMENT_PRIMARY.s();
			}

			Attachment ret = null;
			if (attachments != null) {
				if (forceNew) {
					ret = new Attachment(this);
					attachments.put(name, ret);
					ThreadLocals.dirty(this);
				} else {
					ret = attachments.get(name);
					if (ret != null) {
						return ret;
					}
					if (create) {
						ret = new Attachment(this);
						attachments.put(name, ret);
						ThreadLocals.dirty(this);
					}
				}
			} else if (create || forceNew) {
				ret = new Attachment(this);
				attachments = new HashMap<>();
				attachments.put(name, ret);
				ThreadLocals.dirty(this);
			}
			return ret;
		}
	}

	public void fixAttachments() {
		if (getAttachments() != null) {
			if (getAttachments().size() == 0) {
				setAttachments(null);
			} else {
				getAttachments().forEach((String key, Attachment att) -> {
					att.setOwnerNode(this);
					if ("blob".equals(att.getFileName())) {
						att.setFileName("file-" + key);
					}
				});
			}
		}
	}

	@JsonProperty(LIKES)
	public HashSet<String> getLikes() {
		synchronized (likesLock) {
			return likes;
		}
	}

	@JsonProperty(LIKES)
	public void setLikes(HashSet<String> likes) {
		if (likes == null && this.likes == null)
			return;
		ThreadLocals.dirty(this);
		synchronized (likesLock) {
			this.likes = likes;
		}
	}

	@Transient
	@JsonIgnore
	public void addLike(String actor) {
		if (getLikes() == null) {
			setLikes(new HashSet<>());
		}

		if (getLikes().add(actor)) {
			// set node to dirty only if it just changed.
			ThreadLocals.dirty(this);
		}
	}

	@Transient
	@JsonIgnore
	public void removeLike(String actor) {
		if (getLikes() == null)
			return;

		if (getLikes().remove(actor)) {
			// set node to dirty only if it just changed.
			ThreadLocals.dirty(this);

			// if likes set is now empty make it null.
			if (getLikes().size() == 0) {
				setLikes(null);
			}
		}
	}

	@Transient
	@JsonIgnore
	public boolean set(NodeProp nt, Object val) {
		return set(nt.s(), val);
	}

	@Transient
	@JsonIgnore
	public void setIfNotExist(NodeProp nt, Object val) {
		if (!props().containsKey(nt.s())) {
			set(nt.s(), val);
		}
	}

	@Transient
	@JsonIgnore
	public boolean set(String key, Object val) {
		synchronized (propLock) {
			if (props == null) {
				// if there are no props currently, and the val is null we do nothing, because
				// the way we set a null prop anyway is by REMOVING it from the props.
				if (val == null)
					return false;
				props = props();
			}

			boolean changed = false;
			if (val == null) {
				changed = props.remove(key) != null;
			} else {
				Object curVal = props.get(key);
				changed = curVal == null || !val.equals(curVal);
				props.put(key, val);
			}

			if (changed) {
				ThreadLocals.dirty(this);
			}
			return changed;
		}
	}

	@Transient
	@JsonIgnore
	public void delete(NodeProp prop) {
		delete(prop.s());
	}

	@Transient
	@JsonIgnore
	public void delete(String key) {
		if (props == null)
			return;

		synchronized (propLock) {
			if (props().remove(key) != null) {
				ThreadLocals.dirty(this);
			}
		}
	}

	@Transient
	@JsonIgnore
	public String getStr(NodeProp prop) {
		return getStr(prop.s());
	}

	@Transient
	@JsonIgnore
	public String getStr(String key) {
		if (props == null)
			return null;
		try {
			synchronized (propLock) {
				Object v = props().get(key);
				if (v == null)
					return null;

				return v.toString();
			}
		} catch (Exception e) {
			ExUtil.error(log, "failed to get String from key: " + key, e);
			return null;
		}
	}

	@Transient
	@JsonIgnore
	public Long getInt(NodeProp prop) {
		return getInt(prop.s());
	}

	@Transient
	@JsonIgnore
	public Long getInt(String key) {
		if (props == null)
			return 0L;
		try {
			synchronized (propLock) {
				Object v = props().get(key);
				if (v == null)
					return 0L;

				if (v instanceof Integer) {
					return Long.valueOf((Integer) v);
				}

				// todo-2: When saving from client the values are always sent as strings, and
				// this is a workaround until that changes.
				if (v instanceof String) {
					if (((String) v).length() == 0) {
						return 0L;
					}
					return Long.parseLong((String) v);
				}
				return (Long) v;
			}
		} catch (Exception e) {
			ExUtil.error(log, "failed to get Long from key: " + key, e);
			return null;
		}
	}

	@Transient
	@JsonIgnore
	public Date getDate(NodeProp prop) {
		return getDate(prop.s());
	}

	@Transient
	@JsonIgnore
	public Date getDate(String key) {
		if (props == null)
			return null;
		try {
			synchronized (propLock) {
				Object v = props().get(key);
				if (v == null)
					return null;
				return (Date) v;
			}
		} catch (Exception e) {
			return null;
		}
	}

	@Transient
	@JsonIgnore
	public <T> T getObj(String key, Class<T> classType) {
		if (props == null)
			return null;

		synchronized (propLock) {
			try {
				return (T) props().get(key);
			} catch (Exception e) {
				log.debug("Failed to read prop " + key + " as a type " + classType.getName());
				return null;
			}
		}
	}

	@Transient
	@JsonIgnore
	public <T> T getTypedObj(String key, TypeReference ref) {
		if (props == null)
			return null;

		synchronized (propLock) {
			try {
				return (T) mapper.convertValue(props().get(key), ref);
			} catch (Exception e) {
				log.debug("Failed to read prop " + key + " as a type " + ref.toString());
				return null;
			}
		}
	}

	@Transient
	@JsonIgnore
	public Double getFloat(NodeProp prop) {
		return getFloat(prop.s());
	}

	@Transient
	@JsonIgnore
	public Double getFloat(String key) {
		if (props == null)
			return 0.0;
		try {
			synchronized (propLock) {
				Object v = props().get(key);
				if (v == null)
					return 0.0;
				return (Double) v;
			}
		} catch (Exception e) {
			ExUtil.error(log, "failed to get Float from key: " + key, e);
			return null;
		}
	}

	@Transient
	@JsonIgnore
	public Boolean getBool(NodeProp prop) {
		return getBool(prop.s());
	}

	@Transient
	@JsonIgnore
	public Boolean getBool(String key) {
		if (props == null)
			return false;
		try {
			synchronized (propLock) {
				Object v = props().get(key);
				if (v == null)
					return false;

				/*
				 * Our current property editor only knows how to save strings, so we just cope with that here, but
				 * eventually we will have type-safety and types even in the editor.
				 */
				if (v instanceof String) {
					String s = ((String) v).toLowerCase();
					// detect true or 1.
					return s.contains("t") || s.contains("1");
				}
				return (Boolean) v;
			}
		} catch (Exception e) {
			ExUtil.error(log, "failed to get Boolean from key: " + key, e);
			return null;
		}
	}

	@Transient
	@JsonIgnore
	private HashMap<String, Object> props() {
		synchronized (propLock) {
			if (props == null) {
				props = new HashMap<String, Object>();
			}
			return props;
		}
	}

	@Transient
	@JsonIgnore
	public boolean isType(NodeType type) {
		return type.s().equals(this.type);
	}

	@JsonProperty(TYPE)
	public String getType() {
		return type;
	}

	@JsonProperty(TYPE)
	public void setType(String type) {
		if (Util.equalObjs(type, this.type))
			return;
		ThreadLocals.dirty(this);
		this.type = type;
	}

	@JsonProperty(NAME)
	public String getName() {
		return name;
	}

	@JsonProperty(NAME)
	public void setName(String name) {
		if (Util.equalObjs(name, this.name))
			return;
		ThreadLocals.dirty(this);
		this.name = name;
	}

	@JsonProperty(CONTENT)
	public String getContent() {
		return content;
	}

	@JsonProperty(CONTENT)
	public void setContent(String content) {
		if (Util.equalObjs(content, this.content))
			return;
		ThreadLocals.dirty(this);
		this.content = content;
	}

	@JsonProperty(TAGS)
	public String getTags() {
		return tags;
	}

	@JsonProperty(TAGS)
	public void setTags(String tags) {
		if (Util.equalObjs(tags, this.tags))
			return;
		// temporary hack (I saw empty tags prop in some JSON)
		if ("".equals(tags))
			tags = null;
		ThreadLocals.dirty(this);
		this.tags = tags;
	}

	public void addProps(HashMap<String, Object> props) {
		ThreadLocals.dirty(this);
		synchronized (propLock) {
			props().putAll(props);
		}
	}

	public int getContentLength() {
		return contentLength;
	}

	public void setContentLength(int contentLength) {
		this.contentLength = contentLength;
	}

	public String getMcid() {
		return mcid;
	}

	public void setMcid(String mcid) {
		if (Util.equalObjs(mcid, this.mcid))
			return;
		ThreadLocals.dirty(this);
		this.mcid = mcid;
	}

	public String getPrevMcid() {
		return prevMcid;
	}

	public void setPrevMcid(String prevMcid) {
		if (Util.equalObjs(prevMcid, this.prevMcid))
			return;
		ThreadLocals.dirty(this);
		this.prevMcid = prevMcid;
	}
}
