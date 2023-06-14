package quanta.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import quanta.actpub.APConst;
import quanta.actpub.model.APOHashtag;
import quanta.actpub.model.APOMention;
import quanta.actpub.model.APObj;
import quanta.config.NodePath;
import quanta.config.ServiceBase;
import quanta.config.SessionContext;
import quanta.exception.NodeAuthFailedException;
import quanta.instrument.PerfMon;
import quanta.model.AccessControlInfo;
import quanta.model.NodeInfo;
import quanta.model.PrivilegeInfo;
import quanta.model.PropertyInfo;
import quanta.model.client.Attachment;
import quanta.model.client.Constant;
import quanta.model.client.NodeProp;
import quanta.model.client.NostrUserInfo;
import quanta.model.client.PrincipalName;
import quanta.model.client.PrivilegeType;
import quanta.mongo.MongoSession;
import quanta.mongo.model.AccessControl;
import quanta.mongo.model.SubNode;
import quanta.types.TypeBase;
import quanta.util.val.Val;

/**
 * Converting objects from one type to another, and formatting.
 */
@Component
public class Convert extends ServiceBase {

    private static Logger log = LoggerFactory.getLogger(Convert.class);
    // indicates we don't need to worry about sending back a good logicalOrdinal
    public static int LOGICAL_ORDINAL_IGNORE = -1;
    // indicates we need generate the correct logicalOrdinal
    public static int LOGICAL_ORDINAL_GENERATE = -2;

    /*
     * Generates a NodeInfo object, which is the primary data type that is also used on the
     * browser/client to encapsulate the data for a given node which is used by the browser to render
     * the node.
     *
     * Note: childrenCheck=true means that we DO need the correct value for hasChildren (from a global,
     * non-user specific point of view) to be set on this node. Node that we do set hasChildren to true
     * if there ARE an children REGARDLESS of whether the given user can access those children.
     *
     * todo-0: make sure any saving that's triggered in here is done in an async thread that doesn't block
     * this method from returning fast
     */
    @PerfMon(category = "convert")
    public NodeInfo convertToNodeInfo(
        boolean adminOnly,
        SessionContext sc,
        MongoSession ms,
        SubNode node,
        boolean initNodeEdit,
        long logicalOrdinal,
        boolean allowInlineChildren,
        boolean lastChild,
        boolean childrenCheck,
        boolean getFollowers,
        boolean loadLikes,
        boolean attachBoosted,
        Val<SubNode> boostedNodeVal,
        boolean attachLinkedNodes
    ) {
        String sig = node.getStr(NodeProp.CRYPTO_SIG);
        // if we have a signature, check it.
        boolean sigFail = false;
        if (sig != null && !crypto.nodeSigVerify(node, sig)) {
            sigFail = true;
        }
        // #sig: need a config setting that specifies which path(s) are required to be signed so
        // this can be enabled/disabled easily by admin
        if (prop.isRequireCrypto() && node.getPath().startsWith(NodePath.PUBLIC_PATH + "/")) {
            if ((sig == null || sigFail) && !sc.isAdmin()) {
                // todo-2: we need a special global counter for when this happens, so the server info can show it.
                /*
                 * if we're under the PUBLIC_PATH and a signature fails, don't even show the node if this is an
                 * ordinary user, because this means an 'admin' node is failing it's signature, and is an indication
                 * of a server DB being potentially hacked so we completely refuse to display this content to the
                 * user by returning null here. We only show 'signed' admin nodes to users. If we're logged in as
                 * admin we will be allowed to see even nodes that are failing their signature check, or unsigned.
                 */
                return null;
            }
        }
        // if we know we shold only be including admin node then throw an error if this is not an admin
        // node, but only if we ourselves are not admin.
        if (adminOnly && !acl.isAdminOwned(node) && !sc.isAdmin()) {
            throw new NodeAuthFailedException();
        }
        /* If session user shouldn't be able to see secrets on this node remove them */
        if (ms.isAnon() || (ms.getUserNodeId() != null && !ms.getUserNodeId().equals(node.getOwner()))) {
            if (!ms.isAdmin()) {
                node.clearSecretProperties();
            }
        }
        attach.fixAllAttachmentMimes(node);

        boolean hasChildren = read.hasChildren(ms, node, false, childrenCheck);
        List<PropertyInfo> propList = buildPropertyInfoList(sc, node, initNodeEdit, sigFail);
        List<AccessControlInfo> acList = buildAccessControlList(sc, node);
        if (node.getOwner() == null) {
            throw new RuntimeException("node has no owner: " + node.getIdStr() + " node.path=" + node.getPath());
        }
        String ownerId = node.getOwner().toHexString();
        String avatarVer = null;
        String nameProp = null;
        String displayName = null;
        String nostrPubKey = null;
        String apAvatar = null;
        String apImage = null;
        String owner = PrincipalName.ADMIN.s();
        SubNode ownerAccnt = ThreadLocals.getCachedNode(node.getOwner());
        if (ownerAccnt == null) {
            ownerAccnt = read.getOwner(ms, node, false);
            if (ownerAccnt != null) {
                ThreadLocals.cacheNode(ownerAccnt);
            }
        }
        if (ownerAccnt != null) {
            nameProp = ownerAccnt.getStr(NodeProp.USER);
            Attachment userAtt = ownerAccnt.getAttachment(Constant.ATTACHMENT_PRIMARY.s(), false, false);
            if (userAtt != null) {
                avatarVer = userAtt.getBin();
            }
            displayName = user.getFriendlyNameFromNode(ownerAccnt);
            nostrPubKey = ownerAccnt.getStr(NodeProp.NOSTR_USER_NPUB);
            apAvatar = ownerAccnt.getStr(NodeProp.USER_ICON_URL);
            apImage = ownerAccnt.getStr(NodeProp.USER_BANNER_URL);
            owner = nameProp;
            if (nostr.isNostrNode(node) && nameProp.startsWith(".")) {
                // nostrId will be the name wit the "." prefix removed
                String nostrId = nameProp.substring(1);
                String relays = ownerAccnt.getStr(NodeProp.NOSTR_RELAYS);
                if (ownerAccnt.getInt(NodeProp.NOSTR_USER_TIMESTAMP) == 0L) {
                    log.debug("Queueing client to update metadata for nostr user: " + nostrPubKey);
                    /*
                     * Queueing up these nostrIds causes them to be sent down to the browser to be queries and then the
                     * info is saved to the server once known, however that happens in the background and initially when
                     * the page renders there might be some "User Info" (username & avatar) that is missing on the page,
                     * until it gets resolved. They way they get resolved on the browser page is by the fact that
                     * nostr.ts has a metadataQueue which accumulates any 'unknown metadata users' and then querys for
                     * them, and then simply re-renders the page. Any component that renders a nostr (username+avatar)
                     * must be smart enough to notice the missing data, and then try to render it from the nostr
                     * metadataCache immediately or else queue it into metadataQueue so that the page re-renders
                     * correctly shortly thereafter.
                     */
                    ThreadLocals.getNewNostrUsers().put(nostrId, new NostrUserInfo(nostrId, null, relays));
                }
            }
        }
        /*
         * todo-2: right here, get user profile off 'userNode', and put it into a map that will be sent back
         * to client packaged in this response, so that tooltip on the browser can display it, and the
         * browser will simply contain this same 'map' that maps userIds to profile text, for good
         * performance.
         */
        // log.trace("RENDER ID=" + node.getIdStr() + " rootId=" + ownerId + " session.rootId=" +
        // sc.getRootId() + " node.content="
        // + node.getContent() + " owner=" + owner);
        /*
         * If the node is not owned by the person doing the browsing we need to extract the key from ACL and
         * put in cipherKey, so send back so the user can decrypt the node.
         */
        String cipherKey = null;
        if (!ownerId.equals(sc.getRootId()) && node.getAc() != null) {
            AccessControl ac = node.getAc().get(sc.getRootId());
            if (ac != null) {
                cipherKey = ac.getKey();
                if (cipherKey != null) {
                    log.debug("Rendering Sent Back CipherKey: " + cipherKey);
                }
            }
        }
        ArrayList<String> likes = null;
        if (node.getLikes() != null) {
            likes = new ArrayList<String>(node.getLikes());
        }
        String content = node.getContent();
        String renderContent = replaceTagsWithHtml(node, true);
        if (logicalOrdinal == LOGICAL_ORDINAL_GENERATE) {
            logicalOrdinal = read.generateLogicalOrdinal(ms, node);
        }

        NodeInfo nodeInfo = new NodeInfo(
            node.jsonId(),
            node.getPath(),
            node.getName(),
            content,
            renderContent, //
            node.getTags(),
            displayName, //
            owner,
            ownerId,
            nostrPubKey, //
            node.getTransferFrom() != null ? node.getTransferFrom().toHexString() : null, //
            node.getOrdinal(), //
            node.getModifyTime(),
            propList,
            node.getAttachments(),
            node.getLinks(),
            acList,
            likes,
            hasChildren, //
            node.getType(),
            logicalOrdinal,
            lastChild,
            cipherKey,
            avatarVer,
            apAvatar,
            apImage
        );
        // if this node type has a plugin run it's converter to let it contribute
        TypeBase plugin = typePluginMgr.getPluginByType(node.getType());
        if (plugin != null) {
            plugin.convert(ms, nodeInfo, node, ownerAccnt, getFollowers);
        }
        // allow client to know if this node is not yet saved by user
        if (node.getPath().startsWith(NodePath.PENDING_PATH + "/")) {
            nodeInfo.safeGetClientProps().add(new PropertyInfo(NodeProp.IN_PENDING_PATH.s(), "1"));
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
                    // NOTE: If this is set to false it then only would allow one level of depth in
                    // the 'inlineChildren' capability
                    boolean multiLevel = true;
                    NodeInfo info = convertToNodeInfo(
                        false,
                        sc,
                        ms,
                        n,
                        initNodeEdit,
                        inlineOrdinal++,
                        multiLevel,
                        lastChild,
                        childrenCheck,
                        false,
                        loadLikes,
                        false,
                        null,
                        false
                    );
                    if (info != null) {
                        nodeInfo.safeGetChildren().add(info);
                    }
                }
            }
        }
        // -----------------------
        // DO NOT DELETE: This code works, but for now we don't use it. However this is important and VERY
        // likely we'll be needing this, once we have some use case where we want the linked node
        // to be embedded/displayed in the node that links to it.
        // if (attachLinkedNodes) {
        // if (ok(node.getLinks())) {
        // LinkedList<NodeInfo> linkedNodes = new LinkedList<>();
        // nodeInfo.setLinkedNodes(linkedNodes);
        // node.getLinks().forEach((k, v) -> {
        // SubNode linkNode = read.getNode(ms, v.getNodeId());
        // if (ok(linkNode)) {
        // NodeInfo info = convertToNodeInfo(false, sc, ms, linkNode, false, 0, false, false, false, false,
        // false,
        // false, null, false);
        // if (ok(info)) {
        // linkedNodes.add(info);
        // }
        // }
        // });
        // }
        // }
        // -----------------------
        if (attachBoosted) {
            SubNode boostedNode = null;
            if (boostedNodeVal != null) {
                // if boosted node was passed in use it
                boostedNode = boostedNodeVal.getVal();
            } else {
                // otherwise check to see if we have a boosted node from scratch.
                String boostTargetId = node.getStr(NodeProp.BOOST);
                if (boostTargetId != null) {
                    try {
                        boostedNode = read.getNode(ms, boostTargetId);
                    } catch (Exception e) {
                        log.debug("Unable to access boosted node: " + boostTargetId);
                        return null;
                    }
                }
            }
            if (boostedNode != null) {
                NodeInfo info = convertToNodeInfo(
                    false,
                    sc,
                    ms,
                    boostedNode,
                    false,
                    Convert.LOGICAL_ORDINAL_IGNORE,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    null,
                    false
                );
                if (info != null) {
                    nodeInfo.setBoostedNode(info);
                }
            }
        }
        return nodeInfo;
    }

    /*
     * reads thru 'content' of node and if there are any "@mentions" that we can render as HTML links
     * then we insert all those links into the text and return the resultant markdown with the HTML
     * anchors in it.
     *
     * NOTE: The client knows not to render any openGraph panels for anchor tags that have classes
     * 'mention' or 'hashtag' on them
     */
    public static String replaceTagsWithHtml(SubNode node, boolean includeHashtags) {
        // don't process foreign-created nodes!
        if (node.getStr(NodeProp.OBJECT_ID) != null) {
            return null;
        }
        // todo-1: look for other places where we only call parseTags(text) or parseTags(node) but needed to
        // parse both.
        HashMap<String, APObj> tags = apub.parseTags(node.getContent(), true, false);
        HashMap<String, APObj> nodePropTags = apub.parseTags(node);
        if (nodePropTags != null) {
            tags.putAll(nodePropTags);
        }
        // sending back null for renderContent if no tags were inserted (no special HTML to send back, but
        // just markdown)
        if (tags == null || tags.size() == 0) return null;
        StringBuilder sb = new StringBuilder();
        StringTokenizer t = new StringTokenizer(node.getContent(), APConst.TAGS_TOKENIZER, true);

        while (t.hasMoreTokens()) {
            String tok = t.nextToken();
            int tokLen = tok.length();
            int atCount = 0;
            boolean formatted = false;
            // Hashtag
            if (
                includeHashtags &&
                tokLen > 1 &&
                tok.startsWith("#") &&
                StringUtils.countMatches(tok, "#") == 1 &&
                Character.isLetter(tok.charAt(1))
            ) {
                APObj tag = tags.get(tok);
                if (tag instanceof APOHashtag) {
                    String href = (String) tag.get(APObj.href);
                    if (href != null) {
                        String shortTok = XString.stripIfStartsWith(tok, "#");
                        // having class = 'mention hashtag' is NOT a typo. Mastodon used both, so we will.
                        formatted = true;
                        sb.append( //
                            "<a class=\'mention hashtag\' href=\'" +
                            href +
                            "\' target=\'_blank\'>#<span>" +
                            shortTok +
                            "</span></a>"
                        );
                    }
                }
            } //
            else if ( // Mention // "' rel='" + Const.REL_FOREIGN_LINK + "' target='_blank'>#<span>" + shortTok + "</span></a>"); // sb.append("<a class='mention hashtag' href='" + href + // //
                tokLen > 1 &&
                tok.startsWith("@") &&
                (atCount = StringUtils.countMatches(tok, "@")) <= 2 &&
                Character.isLetterOrDigit(tok.charAt(1))
            ) {
                APObj tag = tags.get(tok);
                if (tag instanceof APOMention) {
                    String href = (String) tag.get(APObj.href);
                    if (href != null) {
                        // if tok is a 'long fedi name' make it the shortened version (no domain)
                        if (atCount == 2) {
                            tok = XString.truncAfterLast(tok, "@");
                        }
                        String shortTok = XString.stripIfStartsWith(tok, "@");
                        // NOTE: h-card and u-url are part of 'microformats'
                        formatted = true;
                        sb.append( //
                            "<span class=\'h-card\'><a class=\'u-url mention\' href=\'" +
                            href +
                            "\' target=\'_blank\'>@<span>" +
                            shortTok +
                            "</span></a></span>"
                        );
                    }
                }
            }
            // sb.append("<span class='h-card'><a class='u-url mention' href='" + href + //
            // "' rel='" + Const.REL_FOREIGN_LINK + "' target='_blank'>@<span>" + shortTok
            // + "</span></a></span>");
            if (!formatted) {
                sb.append(tok);
            }
        }
        return sb.toString();
    }

    public static ImageSize getImageSize(Attachment att) {
        ImageSize imageSize = new ImageSize();
        if (att != null) {
            try {
                Integer width = att.getWidth();
                if (width != null) {
                    imageSize.setWidth(width.intValue());
                }
                Integer height = att.getHeight();
                if (height != null) {
                    imageSize.setHeight(height.intValue());
                }
            } catch (Exception e) {
                imageSize.setWidth(0);
                imageSize.setHeight(0);
            }
        }
        return imageSize;
    }

    public List<PropertyInfo> buildPropertyInfoList(
        SessionContext sc,
        SubNode node, //
        boolean initNodeEdit,
        boolean sigFail
    ) {
        List<PropertyInfo> props = null;
        HashMap<String, Object> propMap = node.getProps();
        if (propMap != null && propMap.keySet() != null) {
            for (String propName : propMap.keySet()) {
                // inticate to the client the signature is no good by not even sending the bad signature to client.
                if (sigFail && NodeProp.CRYPTO_SIG.s().equals(propName)) {
                    continue;
                }
                /* lazy create props */
                if (props == null) {
                    props = new LinkedList<>();
                }
                PropertyInfo propInfo = convertToPropertyInfo(sc, node, propName, propMap.get(propName), initNodeEdit);
                props.add(propInfo);
            }
        }
        if (props != null) {
            props.sort((a, b) -> a.getName().compareTo(b.getName()));
        }
        return props;
    }

    public List<AccessControlInfo> buildAccessControlList(SessionContext sc, SubNode node) {
        List<AccessControlInfo> ret = null;
        HashMap<String, AccessControl> ac = node.getAc();
        if (ac == null) return null;

        for (Map.Entry<String, AccessControl> entry : ac.entrySet()) {
            String principalId = entry.getKey();
            AccessControl acval = entry.getValue();
            /* lazy create list */
            if (ret == null) {
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
        if (ac.getPrvs() != null && ac.getPrvs().contains(PrivilegeType.READ.s())) {
            acInfo.addPrivilege(new PrivilegeInfo(PrivilegeType.READ.s()));
        }
        if (ac.getPrvs() != null && ac.getPrvs().contains(PrivilegeType.WRITE.s())) {
            acInfo.addPrivilege(new PrivilegeInfo(PrivilegeType.WRITE.s()));
        }
        if (principalId != null) {
            arun.run(s -> {
                // todo-2: if the actual user account has been delete we can get here and end up with null user name
                // I think. Look into it.
                if (PrincipalName.PUBLIC.s().equals(principalId)) {
                    acInfo.setPrincipalName(PrincipalName.PUBLIC.s());
                    acInfo.setDisplayName(PrincipalName.PUBLIC.s());
                } else {
                    acInfo.setPrincipalName(auth.getAccountPropById(s, principalId, NodeProp.USER.s()));
                    acInfo.setDisplayName(auth.getAccountPropById(s, principalId, NodeProp.DISPLAY_NAME.s()));
                    acInfo.setNostrNpub(auth.getAccountPropById(s, principalId, NodeProp.NOSTR_USER_NPUB.s()));
                    acInfo.setNostrRelays(auth.getAccountPropById(s, principalId, NodeProp.NOSTR_RELAYS.s()));
                }
                return null;
            });
        }
        return acInfo;
    }

    public PropertyInfo convertToPropertyInfo(
        SessionContext sc,
        SubNode node,
        String propName,
        Object prop,
        boolean initNodeEdit
    ) {
        try {
            Object value = null;
            if (prop instanceof Date) {
                value = DateUtil.formatTimeForUserTimezone((Date) prop, sc.getTimezone(), sc.getTimeZoneAbbrev());
            } //
            else if (prop instanceof Collection) {
                value = prop;
            } else {
                value = prop.toString();
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
    /**
     * Searches in 'val' anywhere there is a line that begins with http:// (or https), and replaces that
     * with the normal way of doing a link in markdown. So we are injecting a snippet of markdown (not
     * html)
     *
     * Not currently used, but I'm leaving it just in case.
     */
    // public static String convertLinksToMarkdown(String val) {
    // while (true) {
    // /* find http after newline character */
    // int startOfLink = val.indexOf("\nhttp://");
    // /* or else find one after return char */
    // if (startOfLink == -1) {
    // startOfLink = val.indexOf("\rhttp://");
    // }
    // /* or else find one after return char */
    // if (startOfLink == -1) {
    // startOfLink = val.indexOf("\nhttps://");
    // }
    // /* or else find one after return char */
    // if (startOfLink == -1) {
    // startOfLink = val.indexOf("\rhttps://");
    // }
    // /* nothing found we're all done here */
    // if (startOfLink == -1)
    // break;
    // /*
    // * locate end of link via \n or \r
    // */
    // int endOfLink = val.indexOf("\n", startOfLink + 1);
    // if (endOfLink == -1) {
    // endOfLink = val.indexOf("\r", startOfLink + 1);
    // }
    // if (endOfLink == -1) {
    // endOfLink = val.length();
    // }
    // String link = val.substring(startOfLink + 1, endOfLink);
    // String left = val.substring(0, startOfLink + 1);
    // String right = val.substring(endOfLink);
    // val = left + "[" + link + "](" + link + ")" + right;
    // }
    // return val;
    // }
}
