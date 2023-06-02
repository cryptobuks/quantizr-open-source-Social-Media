
package quanta.request;

import java.util.List;
import javax.annotation.Nullable;
import quanta.model.PropertyInfo;
import quanta.request.base.RequestBase;

public class CreateSubNodeRequest extends RequestBase {
	private String nodeId;
	private String boostTarget;
	private boolean pendingEdit;
	private String content; // optional, default content
	private String newNodeName;
	private String typeName;
	private boolean createAtTop;
	/* Adds TYPE_LOCK property which prevents user from being able to change the type on the node */
	private boolean typeLock;
	// default properties to add, or null if none
	private List<PropertyInfo> properties;
	// for a DM this can be optionally provided to share the node with this person immediately
	private String shareToUserId;
	// If this node is a reply to a boosted node, then we will recieve the booster id here so the node
	// can
	// also be shared with that person as well.
	private String boosterUserId;
	// send out over Fediverse only if this is true. Will generally be either something created by a
	// "Post" button or a "Reply" button only
	private boolean fediSend;
	/* special purpose values for when creating special types of nodes */
	@Nullable
	private String payloadType;
	private boolean reply;

	
	public String getNodeId() {
		return this.nodeId;
	}

	
	public String getBoostTarget() {
		return this.boostTarget;
	}

	
	public boolean isPendingEdit() {
		return this.pendingEdit;
	}

	
	public String getContent() {
		return this.content;
	}

	
	public String getNewNodeName() {
		return this.newNodeName;
	}

	
	public String getTypeName() {
		return this.typeName;
	}

	
	public boolean isCreateAtTop() {
		return this.createAtTop;
	}

	
	public boolean isTypeLock() {
		return this.typeLock;
	}

	
	public List<PropertyInfo> getProperties() {
		return this.properties;
	}

	
	public String getShareToUserId() {
		return this.shareToUserId;
	}

	
	public String getBoosterUserId() {
		return this.boosterUserId;
	}

	
	public boolean isFediSend() {
		return this.fediSend;
	}

	@Nullable
	
	public String getPayloadType() {
		return this.payloadType;
	}

	
	public boolean isReply() {
		return this.reply;
	}

	
	public void setNodeId(final String nodeId) {
		this.nodeId = nodeId;
	}

	
	public void setBoostTarget(final String boostTarget) {
		this.boostTarget = boostTarget;
	}

	
	public void setPendingEdit(final boolean pendingEdit) {
		this.pendingEdit = pendingEdit;
	}

	
	public void setContent(final String content) {
		this.content = content;
	}

	
	public void setNewNodeName(final String newNodeName) {
		this.newNodeName = newNodeName;
	}

	
	public void setTypeName(final String typeName) {
		this.typeName = typeName;
	}

	
	public void setCreateAtTop(final boolean createAtTop) {
		this.createAtTop = createAtTop;
	}

	
	public void setTypeLock(final boolean typeLock) {
		this.typeLock = typeLock;
	}

	
	public void setProperties(final List<PropertyInfo> properties) {
		this.properties = properties;
	}

	
	public void setShareToUserId(final String shareToUserId) {
		this.shareToUserId = shareToUserId;
	}

	
	public void setBoosterUserId(final String boosterUserId) {
		this.boosterUserId = boosterUserId;
	}

	
	public void setFediSend(final boolean fediSend) {
		this.fediSend = fediSend;
	}

	
	public void setPayloadType(@Nullable final String payloadType) {
		this.payloadType = payloadType;
	}

	
	public void setReply(final boolean reply) {
		this.reply = reply;
	}

	public CreateSubNodeRequest() {
	}
}
