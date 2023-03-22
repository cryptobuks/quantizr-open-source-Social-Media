package quanta.model;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import quanta.model.client.Attachment;
import quanta.model.client.NodeLink;
import quanta.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.annotation.Transient;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Primary object passed back to client to represent a 'node'. Client sees the JSON version of this,
 * in javascript.
 */
@Data
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class NodeInfo {
	private static final Logger log = LoggerFactory.getLogger(NodeInfo.class);

	private String id;
	private String path;
	private String name;
	private String content;

	// This is the markdown to RENDER and MAY be different from 'content'
	private String renderContent;

	private String tags;

	private Date lastModified;
	private String timeAgo;

	// This is the 0-offset position (index) of the node within the resultset that
	// queried it, and is relative not to a specific page
	// but the entire resultset.
	private Long logicalOrdinal;

	private Long ordinal;
	private String type;
	private List<PropertyInfo> properties;
	private HashMap<String, Attachment> attachments;
	private HashMap<String, NodeLink> links;

	/*
	 * Holds information that the server needs to send back to the client to support client features,
	 * but that are not actually stored properties on the actual node
	 */
	private List<PropertyInfo> clientProps;

	private List<AccessControlInfo> ac;
	private boolean hasChildren;

	/*
	 * For nodes that are encrypted but shared to the current user, we send back the ciperKey (an
	 * encrypted sym key) for this node which is a key that can only be decrypted by the private key on
	 * the user's browser, but decrypted by them on their browser it gives the symmetric key to the
	 * encrypted data so they can access the encrypted node content with it
	 */
	private String cipherKey;

	// NOTE: Just a hint for gui enablement (for moveUp, moveDown, etc) in the browser,
	private boolean lastChild;

	/*
	 * This is only populated when generating user "feeds", because we want the feed to be able to show
	 * the context for the reply of a post, which entails showing the parent of the reply above the
	 * reply
	 */
	private NodeInfo parent;

	private List<NodeInfo> children;

	// This is optional, and will be non-empty whenever we're wanting not just the children of this node
	// but all the parents up to a certain number of parents, up towards the root, however many levels
	// up.
	private LinkedList<NodeInfo> parents;
	private LinkedList<NodeInfo> linkedNodes;

	private List<String> likes;

	private String imgId;
	private String displayName;
	private String owner;
	private String ownerId;
	private String transferFromId;

	private String avatarVer;
	private String apAvatar;
	private String apImage;

	// if this node is a boost we put in the target node (node being boosted here)
	private NodeInfo boostedNode;

	public NodeInfo(String id, String path, String name, String content, String renderContent, String tags, String displayName,
			String owner, String ownerId, String transferFromId, Long ordinal, Date lastModified, List<PropertyInfo> properties,
			HashMap<String, Attachment> attachments, HashMap<String, NodeLink> links, List<AccessControlInfo> ac,
			List<String> likes, boolean hasChildren, String type, long logicalOrdinal, boolean lastChild, String cipherKey,
			String avatarVer, String apAvatar, String apImage) {
		this.id = id;
		this.path = path;
		this.name = name;
		this.content = content;
		this.renderContent = renderContent;
		this.tags = tags;
		this.lastModified = lastModified;
		if (lastModified != null) {
			this.timeAgo = DateUtil.formatDurationMillis(System.currentTimeMillis() - lastModified.getTime(), false);
		}
		this.displayName = displayName;
		this.owner = owner;
		this.ownerId = ownerId;
		this.transferFromId = transferFromId;
		this.ordinal = ordinal;
		this.logicalOrdinal = logicalOrdinal;
		this.properties = properties;
		this.attachments = attachments;
		this.links = links;
		this.ac = ac;
		this.likes = likes;
		this.hasChildren = hasChildren;
		this.lastChild = lastChild;
		this.type = type;
		this.logicalOrdinal = logicalOrdinal;
		this.cipherKey = cipherKey;
		this.avatarVer = avatarVer;
		this.apAvatar = apAvatar;
		this.apImage = apImage;
	}

	@Transient
	@JsonIgnore
	public Object getPropVal(String propName) {
		if (properties == null)
			return null;

		for (PropertyInfo prop : properties) {
			if (prop.getName().equals(propName)) {
				return prop.getValue();
			}
		}
		return null;
	}

	@Transient
	@JsonIgnore
	public void setPropVal(String propName, Object val) {
		if (properties == null) {
			safeGetProperties().add(new PropertyInfo(propName, val));
			return;
		}

		/* Set property to new value if it exists already */
		for (PropertyInfo prop : properties) {
			if (prop.getName().equals(propName)) {
				prop.setValue(val);
				return;
			}
		}

		safeGetProperties().add(new PropertyInfo(propName, val));
	}

	public List<NodeInfo> safeGetChildren() {
		if (children != null)
			return children;
		return children = new LinkedList<>();
	}

	public List<PropertyInfo> safeGetProperties() {
		if (properties != null)
			return properties;
		return properties = new LinkedList<>();
	}

	public List<PropertyInfo> safeGetClientProps() {
		if (clientProps != null)
			return clientProps;
		return clientProps = new LinkedList<>();
	}
}
