package quanta.util;

import static quanta.util.Util.no;
import static quanta.util.Util.ok;
import java.net.URLConnection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import quanta.config.ServiceBase;
import quanta.config.SessionContext;
import quanta.instrument.PerfMon;
import quanta.model.AccessControlInfo;
import quanta.model.NodeInfo;
import quanta.model.PrivilegeInfo;
import quanta.model.PropertyInfo;
import quanta.model.client.NodeProp;
import quanta.model.client.PrincipalName;
import quanta.model.client.PrivilegeType;
import quanta.mongo.MongoSession;
import quanta.mongo.model.AccessControl;
import quanta.mongo.model.SubNode;
import quanta.mongo.model.SubNodePropVal;
import quanta.mongo.model.SubNodePropertyMap;
import quanta.types.TypeBase;

/**
 * Converting objects from one type to another, and formatting.
 */
@Component
public class Convert extends ServiceBase {
	private static final Logger log = LoggerFactory.getLogger(Convert.class);

	/*
	 * Generates a NodeInfo object, which is the primary data type that is also used on the
	 * browser/client to encapsulate the data for a given node which is used by the browser to render
	 * the node.
	 */
	@PerfMon(category = "convert")
	public NodeInfo convertToNodeInfo(SessionContext sc, MongoSession ms, SubNode node, boolean htmlOnly, boolean initNodeEdit,
			long ordinal, boolean allowInlineChildren, boolean lastChild, boolean childrenCheck, boolean getFollowers) {

		/* If session user shouldn't be able to see secrets on this node remove them */
		if (ms.isAnon() || (ok(ms.getUserNodeId()) && !ms.getUserNodeId().equals(node.getOwner()))) {
			if (!ms.isAdmin()) {
				node.clearSecretProperties();
			}
		}

		ImageSize imageSize = null;
		String dataUrl = null;
		String mimeType = node.getStr(NodeProp.BIN_MIME.s());
		if (ok(mimeType)) {
			boolean isImage = mongoUtil.isImageAttached(node);

			if (isImage) {
				imageSize = mongoUtil.getImageSize(node);

				String dataUrlProp = node.getStr(NodeProp.BIN_DATA_URL.s());
				if (ok(dataUrlProp)) {
					dataUrl = attach.getStringByNode(ms, node);

					// sanity check here.
					if (!dataUrl.startsWith("data:")) {
						dataUrl = null;
					}
				}
			}
		}

		// ensure we have the best mimeType we can if not set in the data.
		if (StringUtils.isEmpty(mimeType)) {
			String binUrl = node.getStr(NodeProp.BIN_URL.s());
			if (!StringUtils.isEmpty(binUrl)) {
				mimeType = URLConnection.guessContentTypeFromName(binUrl);
				if (!StringUtils.isEmpty(mimeType)) {
					node.set(NodeProp.BIN_MIME.s(), mimeType);
				}
			}
		}

		boolean hasChildren = childrenCheck ? read.hasChildren(ms, node) : false;
		// log.trace("hasNodes=" + hasChildren + " node: "+node.getIdStr());

		List<PropertyInfo> propList = buildPropertyInfoList(sc, node, htmlOnly, initNodeEdit);
		List<AccessControlInfo> acList = buildAccessControlList(sc, node);

		if (no(node.getOwner())) {
			throw new RuntimeException("node has no owner: " + node.getIdStr() + " node.path=" + node.getPath());
		}

		String ownerId = node.getOwner().toHexString();
		String avatarVer = null;
		String nameProp = null;
		String displayName = null;
		String apAvatar = null;
		String apImage = null;
		String owner = PrincipalName.ADMIN.s();

		/*
		 * todo-1: for endpoints like 'renderNode' we can optimize these calls to get owner nodes all as the
		 * LAST step and get them all (will only be up to 25 at a time) in one single MongoDb "in clause"
		 * type query! But for now this doesn't appear to be a bottleneck so we won't bother.
		 */
		SubNode userNode = read.getOwner(ms, node, false);

		if (ok(userNode)) {
			nameProp = userNode.getStr(NodeProp.USER.s());
			avatarVer = userNode.getStr(NodeProp.BIN.s());
			displayName = userNode.getStr(NodeProp.DISPLAY_NAME.s());
			apAvatar = userNode.getStr(NodeProp.ACT_PUB_USER_ICON_URL);
			apImage = userNode.getStr(NodeProp.ACT_PUB_USER_IMAGE_URL);
			owner = nameProp;

			/*
			 * todo-2: right here, get user profile off 'userNode', and put it into a map that will be sent back
			 * to client packaged in this response, so that tooltip on the browser can display it, and the
			 * browser will simply contain this same 'map' that maps userIds to profile text, for good
			 * performance.
			 */
		}

		log.trace("RENDER ID=" + node.getIdStr() + " rootId=" + ownerId + " session.rootId=" + sc.getRootId() + " node.content="
				+ node.getContent() + " owner=" + owner);

		// log.debug("RENDER nodeId: " + node.getIdStr()+" -- json:
		// "+XString.prettyPrint(node));

		/*
		 * If the node is not owned by the person doing the browsing we need to extract the key from ACL and
		 * put in cipherKey, so send back so the user can decrypt the node.
		 */
		String cipherKey = null;
		if (!ownerId.equals(sc.getRootId()) && ok(node.getAc())) {
			AccessControl ac = node.getAc().get(sc.getRootId());
			if (ok(ac)) {
				cipherKey = ac.getKey();
				if (ok(cipherKey)) {
					log.debug("Rendering Sent Back CipherKey: " + cipherKey);
				}
			}
		}

		NodeInfo nodeInfo = new NodeInfo(node.jsonId(), node.getPath(), node.getName(), node.getContent(), displayName, owner,
				ownerId, node.getOrdinal(), //
				node.getModifyTime(), propList, acList, hasChildren, //
				ok(imageSize) ? imageSize.getWidth() : 0, //
				ok(imageSize) ? imageSize.getHeight() : 0, //
				node.getType(), ordinal, lastChild, cipherKey, dataUrl, avatarVer, apAvatar, apImage);

		// if this node type has a plugin run it's converter to let it contribute
		TypeBase plugin = typePluginMgr.getPluginByType(node.getType());
		if (ok(plugin)) {
			plugin.convert(ms, nodeInfo, node, getFollowers);
		}

		if (allowInlineChildren) {
			boolean hasInlineChildren = node.getBool(NodeProp.INLINE_CHILDREN);
			if (hasInlineChildren) {
				Iterable<SubNode> nodeIter = read.getChildren(ms, node, Sort.by(Sort.Direction.ASC, SubNode.ORDINAL), 100, 0);
				Iterator<SubNode> iterator = nodeIter.iterator();
				long inlineOrdinal = 0;
				while (true) {
					if (!iterator.hasNext()) {
						break;
					}
					SubNode n = iterator.next();

					// log.debug("renderNode DUMP[count=" + count + " idx=" +
					// String.valueOf(idx) + " logicalOrdinal=" + String.valueOf(offset
					// + count) + "]: "
					// + XString.prettyPrint(node));

					// NOTE: If this is set to false it then only would allow one level of depth in
					// the 'inlineChildren' capability
					boolean multiLevel = true;

					nodeInfo.safeGetChildren().add(convertToNodeInfo(sc, ms, n, htmlOnly, initNodeEdit, inlineOrdinal++,
							multiLevel, lastChild, childrenCheck, false));
				}
			}
		}
		// log.debug("NODEINFO: " + XString.prettyPrint(nodeInfo));
		return nodeInfo;
	}

	public static ImageSize getImageSize(SubNode node) {
		ImageSize imageSize = new ImageSize();

		try {
			Long width = node.getInt(NodeProp.IMG_WIDTH);
			if (ok(width)) {
				imageSize.setWidth(width.intValue());
			}

			Long height = node.getInt(NodeProp.IMG_HEIGHT);
			if (ok(height)) {
				imageSize.setHeight(height.intValue());
			}
		} catch (Exception e) {
			imageSize.setWidth(0);
			imageSize.setHeight(0);
		}
		return imageSize;
	}

	public List<PropertyInfo> buildPropertyInfoList(SessionContext sc, SubNode node, //
			boolean htmlOnly, boolean initNodeEdit) {

		List<PropertyInfo> props = null;
		SubNodePropertyMap propMap = node.getProperties();
		Set<Entry<String,SubNodePropVal>> set = propMap.entrySet();
		if (ok(set)) {
			for (Map.Entry<String, SubNodePropVal> entry :set) {
				String propName = entry.getKey();
				SubNodePropVal p = entry.getValue();

				/* lazy create props */
				if (no(props)) {
					props = new LinkedList<>();
				}

				PropertyInfo propInfo = convertToPropertyInfo(sc, node, propName, p, htmlOnly, initNodeEdit);
				// log.debug(" PROP Name: " + propName + " val=" + p.getValue().toString());

				props.add(propInfo);
			}
		}

		if (ok(props)) {
			props.sort((a, b) -> a.getName().compareTo(b.getName()));
		}
		return props;
	}

	public List<AccessControlInfo> buildAccessControlList(SessionContext sc, SubNode node) {
		List<AccessControlInfo> ret = null;
		HashMap<String, AccessControl> ac = node.getAc();
		if (no(ac))
			return null;

		for (Map.Entry<String, AccessControl> entry : ac.entrySet()) {
			String principalId = entry.getKey();
			AccessControl acval = entry.getValue();

			/* lazy create list */
			if (no(ret)) {
				ret = new LinkedList<>();
			}

			AccessControlInfo acInfo = convertToAccessControlInfo(sc, node, principalId, acval);
			ret.add(acInfo);
		}

		return ret;
	}

	public AccessControlInfo convertToAccessControlInfo(SessionContext sc, SubNode node, String principalId, AccessControl ac) {
		AccessControlInfo acInfo = new AccessControlInfo();
		acInfo.setPrincipalNodeId(principalId);

		if (ok(ac.getPrvs()) && ac.getPrvs().contains(PrivilegeType.READ.s())) {
			acInfo.addPrivilege(new PrivilegeInfo(PrivilegeType.READ.s()));
		}

		if (ok(ac.getPrvs()) && ac.getPrvs().contains(PrivilegeType.WRITE.s())) {
			acInfo.addPrivilege(new PrivilegeInfo(PrivilegeType.WRITE.s()));
		}

		if (ok(principalId)) {
			arun.run(s -> {
				acInfo.setPrincipalName(auth.getAccountPropById(s, principalId, NodeProp.USER.s()));
				acInfo.setDisplayName(auth.getAccountPropById(s, principalId, NodeProp.DISPLAY_NAME.s()));

				// currently don't ever need this info for displaying rows, so don't waste the
				// CPU cycles to get it.
				// if (!"public".equals(principalId)) {
				// SubNode accountNode = read.getNode(s, principalId);
				// if (ok(accountNode )) {
				// acInfo.setAvatarVer(accountNode.getStrProp(NodeProp.BIN));
				// }
				// }
				return null;
			});
		}
		return acInfo;
	}

	public PropertyInfo convertToPropertyInfo(SessionContext sc, SubNode node, String propName, SubNodePropVal prop,
			boolean htmlOnly, boolean initNodeEdit) {
		try {
			Object value = null;
			switch (propName) {
				case "content":
					value = formatValue(sc, prop.getValue(), false, initNodeEdit);
					break;

				// Special processing (need to build this kind of stuff into the "Plugin" architecture for types)
				case "ap:tag": // NodeProp.ACT_PUB_TAG
					value = prop.getValue();
					break;

				default:
					value = prop.getValue().toString();
					break;
			}

			/* log.trace(String.format("prop[%s]=%s", prop.getName(), value)); */

			PropertyInfo propInfo = new PropertyInfo(propName, value);
			return propInfo;
		} catch (Exception ex) {
			throw ExUtil.wrapEx(ex);
		}
	}

	public String basicTextFormatting(String val) {
		val = val.replace("\n\r", "<p>");
		val = val.replace("\n", "<p>");
		val = val.replace("\r", "<p>");
		return val;
	}

	public String formatValue(SessionContext sc, Object value, boolean convertToHtml, boolean initNodeEdit) {
		try {
			if (value instanceof Date) {
				return DateUtil.formatTimeForUserTimezone((Date) value, sc.getTimezone(), sc.getTimeZoneAbbrev());
			} else {
				String ret = value.toString();

				/*
				 * If we are doing an initNodeEdit we don't do this, because we want the text to render to the user
				 * exactly as they had typed it and not with links converted.
				 */
				if (!initNodeEdit) {
					ret = convertLinksToMarkdown(ret);
				}

				return ret;
			}
		} catch (Exception e) {
			return "";
		}
	}

	/**
	 * Searches in 'val' anywhere there is a line that begins with http:// (or https), and replaces that
	 * with the normal way of doing a link in markdown. So we are injecting a snippet of markdown (not
	 * html)
	 */
	public static String convertLinksToMarkdown(String val) {
		while (true) {
			/* find http after newline character */
			int startOfLink = val.indexOf("\nhttp://");

			/* or else find one after return char */
			if (startOfLink == -1) {
				startOfLink = val.indexOf("\rhttp://");
			}

			/* or else find one after return char */
			if (startOfLink == -1) {
				startOfLink = val.indexOf("\nhttps://");
			}

			/* or else find one after return char */
			if (startOfLink == -1) {
				startOfLink = val.indexOf("\rhttps://");
			}

			/* nothing found we're all done here */
			if (startOfLink == -1)
				break;

			/*
			 * locate end of link via \n or \r
			 */
			int endOfLink = val.indexOf("\n", startOfLink + 1);
			if (endOfLink == -1) {
				endOfLink = val.indexOf("\r", startOfLink + 1);
			}
			if (endOfLink == -1) {
				endOfLink = val.length();
			}

			String link = val.substring(startOfLink + 1, endOfLink);

			String left = val.substring(0, startOfLink + 1);
			String right = val.substring(endOfLink);
			val = left + "[" + link + "](" + link + ")" + right;
		}
		return val;
	}
}
