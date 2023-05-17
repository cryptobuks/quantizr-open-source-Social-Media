import { getAs } from "../../AppContext";
import { Clearfix } from "../../comp/core/Clearfix";
import { Div } from "../../comp/core/Div";
import { Icon } from "../../comp/core/Icon";
import { Img } from "../../comp/core/Img";
import { Span } from "../../comp/core/Span";
import { Constants as C } from "../../Constants";
import { UserProfileDlg } from "../../dlg/UserProfileDlg";
import { TabIntf } from "../../intf/TabIntf";
import { NodeActionType } from "../../intf/TypeIntf";
import * as J from "../../JavaIntf";
import { NodeType } from "../../JavaIntf";
import { S } from "../../Singletons";
import { Button } from "../core/Button";
import { Divc } from "../core/Divc";

export class NodeCompRowHeader extends Div {

    // NOTE: If boostingNode is non-null it's the node that is boosting 'node'. In other words the rendered page will show
    // node boostingNode as the top/outter level and the 'node' will be the actual node that got boosted by 'boostingNode'
    constructor(private boostingNode: J.NodeInfo, private node: J.NodeInfo, private allowAvatars: boolean, private isMainTree: boolean,
        public tabData: TabIntf<any>, private jumpButton: boolean, private showThreadButton: boolean,
        private isBoost: boolean, private allowDelete: boolean) {
        super(null);

        const ast = getAs();
        this.attribs.className = (tabData.id === C.TAB_MAIN && ast.userPrefs.editMode && S.util.showMetaData(ast, this.node)) ? "rowHeaderEdit" : "row-header";
    }

    override preRender(): boolean {
        const ast = getAs();

        let displayName = null;
        const allowWideViewIcons = !ast.mobileMode || S.quanta.isLandscapeOrientation();
        const isNostr = S.nostr.isNostrNode(this.node);
        // const isActPub = S.nostr.isActPubNode(this.node);

        // if user has set their displayName
        if (this.node.displayName) {
            displayName = S.util.insertActPubTags(this.node.displayName, this.node);
        }

        // Warning: after running insertActPubTags above that may put us back at an empty displayName,
        // so we DO need to check for displayName here rather than putting this in an else block.
        if (!displayName) {
            displayName = this.node.owner;
            const atIdx = displayName.indexOf("@");
            if (atIdx !== -1) {
                displayName = displayName.substring(0, atIdx);
            }
        }
        const isMine = S.props.isMine(this.node);
        const children = [];
        let avatarImg: Img = null;
        const type = S.plugin.getType(this.node.type);

        // we always enable showInfo even on Tree Tab, so that we can have anonymous users comming to a shared tree link
        // and they can see whose node it is rather than seeing just content on the page that's confusing who it came from or what it is.
        const showInfo = S.util.showMetaData(ast, this.node) || this.tabData.id === C.TAB_FEED || this.tabData.id === C.TAB_THREAD || this.tabData.id === C.TAB_REPLIES;

        if (showInfo && this.allowAvatars && this.node.owner !== J.PrincipalName.ADMIN) {
            avatarImg = S.render.makeHeaderAvatar(this.node);
            if (avatarImg) {
                children.push(avatarImg);
            }
        }

        const priorityVal = S.props.getPropStr(J.NodeProp.PRIORITY, this.node);
        const priority = (priorityVal && priorityVal !== "0") ? "P" + priorityVal : "";

        // now that we have this stuff visible by default on all nodes, we don't want users to need to
        // see 'admin' on all admin nodes. too noisy
        if (showInfo && this.node.owner && this.node.owner !== "?" && this.node.owner !== J.PrincipalName.ADMIN) {
            if (this.node.transferFromId) {
                displayName = "PENDING XFER -> " + displayName;
            }

            // if display name is a nostr one...
            if (displayName.startsWith(".")) {
                displayName = S.nodeUtil.getDisplayName(this.node);
            }

            children.push(new Span(displayName, {
                className: (this.node.transferFromId ? "transferPending" : (isMine ? "createdByMe" : "createdByOther")),
                title: "Show Profile:\n\n" + this.node.owner,
                onClick: () => {
                    new UserProfileDlg(this.node.ownerId).open();
                }
            }, null, true));

            // do not delete...yet
            // if (isNostr) {
            //     children.push(new Span("Nostr", { className: "smallMarginRight" }));
            // }
            // else if (isActPub) {
            //     children.push(new Span("AP", { className: "smallMarginRight" }));
            // }
        }

        const signed = S.props.getPropStr(J.NodeProp.CRYPTO_SIG, this.node);
        if (signed) {
            children.push(new Icon({
                title: "Crypto Signature Verified",
                className: "fa fa-certificate fa-lg signatureIcon mediumMarginRight"
            }));
        }

        if (S.props.isEncrypted(this.node)) {
            children.push(new Icon({
                className: "fa fa-lock fa-lg lockIcon mediumMarginRight",
                title: "Node is Encrypted."
            }));
        }

        const editInsertAllowed = S.props.isWritableByMe(this.node);
        const actPubId = S.props.getPropStr(J.NodeProp.OBJECT_ID, this.node);

        // always show a reply if activity pub, or else not public non-repliable (all person to person shares ARE replyable)
        // Also we don't allow admin user to do any replies
        // WARNING: Mastodon can't cope with the concept of replying to the actual booster node but only the booseted node,
        // do don't allow replying to boosts.
        if (!this.node.boostedNode && !ast.isAdminUser && showInfo && (editInsertAllowed || actPubId)) {
            children.push(new Icon({
                title: "Reply to this Post",
                className: "fa fa-reply fa-lg rowHeaderIcon",
                onClick: () => {
                    if (ast.isAnonUser) {
                        S.util.showMessage("Login to create content and reply to nodes.", "Login!");
                    }
                    else {
                        // when replying to a boost, we want to be able to additionally add to the sharing the person
                        // that DID the boost, so the reply is shared with both the 'booster' and the 'boostee'

                        // reply to a DM with another DM, else make it a COMMENT type.
                        const replyType = this.node.type === NodeType.NOSTR_ENC_DM ? NodeType.NOSTR_ENC_DM : NodeType.COMMENT;

                        S.edit.addNode(this.boostingNode?.ownerId, this.node.id, replyType, true, null, null, this.node.id, null, true);
                    }
                }
            }));
        }

        if (showInfo) {
            // Don't allow boosting a node that is itself a boost. This would confuse Mastodon.
            if (!ast.isAdminUser && !ast.isAnonUser && !this.node.boostedNode && !isNostr) {
                children.push(new Icon({
                    title: "Boost this Node",
                    className: "fa fa-retweet fa-lg rowHeaderIcon",
                    onClick: () => {
                        if (ast.isAnonUser) {
                            S.util.showMessage("Login to boost nodes.", "Login!");
                        }
                        else {
                            S.edit.addNode(null, null, NodeType.COMMENT, false, null, null, null, this.node.id, false)
                        }
                    }
                }));
            }

            const hasNonPublicShares = S.props.hasNonPublicShares(this.node);
            const hasMentions = S.props.hasMentions(this.node);

            let youLiked: boolean = false;
            let likeDisplay: string = null;
            if (this.node.likes) {
                youLiked = !!this.node.likes.find(u => u === ast.userName);
                likeDisplay = "Liked by " + this.node.likes.length;
                if (youLiked) {
                    likeDisplay += " (including You)";
                }
            }

            // NOTE: Don't allow liking of boosting nodes. Mastodon doesn't know how to handle that.
            if (!this.node.boostedNode && !ast.isAdminUser && !ast.isAnonUser && !isNostr) {
                children.push(new Icon({
                    title: likeDisplay ? likeDisplay : "Like this Node",
                    className: "fa fa-star fa-lg rowHeaderIcon " + (youLiked ? "likedByMeIcon" : ""),
                    onClick: () => {
                        if (ast.isAdminUser) {
                            S.util.showMessage("Admin user can't do Likes.", "Admin");
                            return;
                        }

                        if (ast.isAnonUser) {
                            S.util.showMessage("Login to like and create content.", "Login!");
                        }
                        else {
                            S.edit.likeNode(this.node, !youLiked);
                        }
                    }
                }, this.node.likes?.length > 0 ? this.node.likes.length.toString() : ""));
            }

            /* only allow this for logged in users, because it might try to access over ActivityPub potentially
            and we need to have a user identity for all the HTTP sigs for that. */
            if (!ast.isAnonUser && (hasNonPublicShares || hasMentions || this.node.likes?.length > 0)) {
                children.push(new Icon({
                    title: "People mentioned or shared related to this node",
                    className: "fa fa-users fa-lg rowHeaderIcon",
                    onClick: () => S.user.showUsersList(this.node)
                }));
            }

            if (showInfo && allowWideViewIcons) {
                children.push(new Icon({
                    className: "fa fa-link fa-lg rowHeaderIcon",
                    title: "Show URLs for this node",
                    onClick: () => S.render.showNodeUrl(this.node)
                }));
            }

            // Allow bookmarking any kind of node other than bookmark nodes.
            if (showInfo && !ast.isAnonUser && this.node.type !== J.NodeType.BOOKMARK && this.node.type !== J.NodeType.BOOKMARK_LIST) {
                children.push(new Icon({
                    className: "fa fa-bookmark fa-lg rowHeaderIcon",
                    title: "Bookmark this Node",
                    onClick: () => S.edit.addBookmark(this.node)
                }));
            }

            // Because the POSTS node will be at a depth like this: "/r/usr/L/b/q", we require
            // the path to be at least deeper than that to show the history button.
            // L = Local Users, then: [UserNode]/[PostsNode]/[ActualNode]
            // Also if we have 'inReplyTo' that will also enable the button.
            const inReplyTo = S.props.getPropStr(J.NodeProp.INREPLYTO, this.node);
            const inReplToNostr: string[] = S.nostr.getRepliedToItemOfNode(this.node);
            const slashCount = S.util.countChars(this.node.path, "/");

            if (showInfo && this.showThreadButton && (slashCount > 6 || !!inReplyTo || Array.isArray(inReplToNostr))) {
                children.push(new Icon({
                    className: "fa fa-th-list fa-lg rowHeaderIcon",
                    title: "Show Thread History",
                    onClick: () => S.srch.showThread(this.node)
                }));
            }

            const repliesProp: string = S.props.getPropStr(J.NodeProp.ACT_PUB_REPLIES, this.node);
            if (showInfo && ast.allowedFeatures?.indexOf("ap:replies") !== -1 && repliesProp) {
                children.push(new Icon({
                    className: "fa fa-commenting fa-lg rowHeaderIcon",
                    title: "Show Replies",
                    onClick: () => S.srch.showReplies(this.node)
                }));
            }

            // Don't try to read Foreign server content (by checking actPubId to detect remote)
            // because the content is likely to be loaded with HTML
            // and won't read well by TTS, whereas local posts will be JSON and should read ok.
            if (!actPubId && !ast.isAnonUser && allowWideViewIcons) {
                children.push(new Icon({
                    className: "fa fa-lg fa-volume-up rowHeaderIcon",
                    onMouseOver: () => { S.quanta.selectedForTts = window.getSelection().toString(); },
                    onMouseOut: () => { S.quanta.selectedForTts = null; },
                    onClick: async () => {
                        if (getAs().speechSpeaking) {
                            await S.speech.stopSpeaking();
                        }
                        else if (this.node.content) {
                            S.speech.speakText(this.node.content, false);
                        }
                    },
                    title: "Text-to-Speech: Read this Node"
                }));
            }
        }

        if (showInfo && priority) {
            children.push(new Span(priority, {
                className: "mediumMarginRight priorityTag" + priorityVal
            }));
        }

        const floatUpperRightDiv: Div = new Divc({
            className: "float-end floatRightHeaderDiv"
        });

        if (showInfo && this.node.timeAgo) {
            floatUpperRightDiv.addChild(new Span(this.node.timeAgo, {
                className: "lastModifiedTime",
                title: "Last Modified: " + S.util.formatDateTime(new Date(this.node.lastModified))
            }));
        }

        // for node type of NONE, don't show the type icon
        if (type && type.getTypeName() !== NodeType.NONE) {
            const iconClass = type.getIconClass();
            if (showInfo && iconClass) {
                floatUpperRightDiv.addChild(new Icon({
                    className: iconClass + (type.schemaOrg ? " microMarginRight" : " marginRight"),
                    title: "Node Type: " + type.getName()
                }));

                if (type.schemaOrg) {
                    floatUpperRightDiv.addChild(new Span(type.getName(), { className: "marginRight" }));
                }
            }
        }

        if (showInfo && this.node.name) {
            const byNameUrl = window.location.origin + S.nodeUtil.getPathPartForNamedNode(this.node);
            floatUpperRightDiv.addChild(new Span(this.node.name, {
                className: "nodeNameDisp",
                title: "Node name (Click to copy link to clipboard)",
                onClick: () => {
                    S.util.copyToClipboard(byNameUrl);
                    S.util.flashMessage("Copied link to Clipboard", "Clipboard", true);
                }
            }));
        }

        const unpublished = S.props.getPropStr(J.NodeProp.UNPUBLISHED, this.node);
        const unpublishedIcon = unpublished ? new Icon({
            className: "fa fa-eye-slash fa-lg sharingIcon marginRight",
            title: "Node is Unpublished\n\nWill not appear in feed"
        }) : null;

        if (showInfo) {
            // If node is shared to public we just show the globe icon and not the rest of the shares that may be present.
            if (S.props.isPublic(this.node)) {
                const appendNode = S.props.isPublicWritable(this.node) ? "Anyone can reply" : "No Replies Allowed";
                floatUpperRightDiv.addChildren([
                    new Icon({
                        className: "fa fa-globe fa-lg sharingIcon marginRight",
                        title: "Node is Public\n(" + appendNode + ")"
                    }),
                    unpublishedIcon
                ]);
            }
            // Show all the share names
            else if (S.props.isShared(this.node)) {
                const shareComps = S.nodeUtil.getSharingNames(this.node, null);
                floatUpperRightDiv.addChildren([
                    new Span(null, {
                        className: "rowHeaderSharingNames"
                    }, [
                        new Icon({
                            className: "fa fa-envelope fa-lg sharingIcon"
                        }),
                        ...shareComps
                    ]),
                    unpublishedIcon
                ]);
            }
        }

        let editingAllowed = S.edit.isEditAllowed(this.node);
        let deleteAllowed = false;
        let editableNode = true;

        if (ast.isAdminUser) {
            editingAllowed = true;
            editableNode = true;
            deleteAllowed = true;
        }
        else if (type) {
            if (editingAllowed) {
                editingAllowed = type.allowAction(NodeActionType.editNode, this.node);
                editableNode = type.allowAction(NodeActionType.editNode, this.node);
                deleteAllowed = type.allowAction(NodeActionType.delete, this.node);
            }
        }

        if (!this.allowDelete) {
            deleteAllowed = false;
        }

        let editButton: Icon = null;
        let jumpButton: Icon = null;
        let pasteButton: Button = null;

        /* Note: if this is on the main tree then we don't show the edit button here because it'll be
        showing up in a different place. We show here only for timeline, or search results views */
        if (!this.isBoost && !this.isMainTree && ast.userPrefs.editMode && !isNostr) {
            if (editingAllowed && editableNode) {
                editButton = new Icon({
                    className: "fa fa-edit fa-lg buttonBarIcon",
                    onClick: S.edit.runEditNodeByClick,
                    title: "Edit Node",
                    [C.NODE_ID_ATTR]: this.node.id
                });
            }

            if (deleteAllowed && this.node.id !== ast.userProfile?.userNodeId) {
                floatUpperRightDiv.addChild(new Icon({
                    className: "fa fa-trash fa-lg buttonBarIcon",
                    title: "Delete node(s)",
                    [C.NODE_ID_ATTR]: this.node.id,
                    onClick: S.edit.deleteSelNodes
                }));
            }
        }

        const userCanPaste = S.props.isMine(this.node) || ast.isAdminUser || this.node.id === ast.userProfile?.userNodeId;
        if (!!ast.nodesToMove && userCanPaste) {
            pasteButton = new Button("Paste Inside",
                S.edit.pasteSelNodesInside, { [C.NODE_ID_ATTR]: this.node.id }, "btn-secondary pasteButton")
        }

        let jumpButtonAdded = false;
        /* If we're not on a search result display (or timeline) and there's a TARGET_ID on the node
        then we need to show the jump button point to it.
        */
        if (this.isMainTree) {
            const targetId = S.props.getPropStr(J.NodeProp.TARGET_ID, this.node);
            if (targetId) {
                jumpButtonAdded = true;
                jumpButton = new Icon({
                    className: "fa fa-arrow-right fa-lg buttonBarIcon",
                    onClick: () => S.view.jumpToId(targetId),
                    title: "Jump to Tree"
                });
            }
        }

        /* Only need this Jump button if admin. Would work fine for ordinary users, but isn't really needed. */
        if (this.jumpButton && !jumpButtonAdded) {
            jumpButton = new Icon({
                className: "fa fa-arrow-right fa-lg buttonBarIcon",
                onClick: () => S.srch.clickSearchNode(this.node.id),
                title: "Jump to Tree"
            });
        }

        if (editButton || jumpButton) {
            floatUpperRightDiv.addChildren([pasteButton, editButton, jumpButton]);
        }

        // for mobile, we don't show this float right component unless in wide-screen orientation.
        if ((!ast.mobileMode || S.quanta.isLandscapeOrientation()) && floatUpperRightDiv.hasChildren()) {
            children.push(floatUpperRightDiv);
            children.push(new Clearfix());
        }

        this.setChildren(children);
        return true;
    }
}
