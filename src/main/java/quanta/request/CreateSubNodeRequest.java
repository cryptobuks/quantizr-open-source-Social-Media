package quanta.request;

import java.util.List;
import javax.annotation.Nullable;
import quanta.model.PropertyInfo;
import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
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
}
