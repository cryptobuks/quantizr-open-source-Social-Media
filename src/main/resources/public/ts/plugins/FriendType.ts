import { getAs } from "../AppContext";
import { Comp } from "../comp/base/Comp";
import { Heading } from "../comp/core/Heading";
import { UserProfileDlg } from "../dlg/UserProfileDlg";
import { EditorOptions } from "../Interfaces";
import { TabIntf } from "../intf/TabIntf";
import { NodeActionType } from "../intf/TypeIntf";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { TypeBase } from "./base/TypeBase";

export class FriendType extends TypeBase {
    static helpExpanded: boolean;

    constructor() {
        super(J.NodeType.FRIEND, "User", "fa-user", false);
    }

    override getEditorHelp(): string {
        const ast = getAs();
        return ast.config.help?.type?.friend?.editor;
    }

    override getAllowRowHeader(): boolean {
        return false;
    }

    override allowAction(action: NodeActionType, node: J.NodeInfo): boolean {
        switch (action) {
            case NodeActionType.delete:
            case NodeActionType.editNode:
                return true;
            default:
                return false;
        }
    }

    override getAllowPropertyAdd(): boolean {
        return false;
    }

    override getAllowContentEdit(): boolean {
        return false;
    }

    override allowPropertyEdit(propName: string): boolean {
        return false;
    }

    override ensureDefaultProperties(node: J.NodeInfo) {
        this.ensureStringPropExists(node, J.NodeProp.USER);
    }

    override renderEditorSubPanel = (node: J.NodeInfo): Comp => {
        const user: string = S.props.getPropStr(J.NodeProp.USER, node);
        return new Heading(3, user);
    }

    override render = (node: J.NodeInfo, tabData: TabIntf<any>, rowStyling: boolean, isTreeView: boolean, isLinkedNode: boolean): Comp => {
        const user: string = S.props.getPropStr(J.NodeProp.USER, node);
        const userBio: string = S.props.getClientPropStr(J.NodeProp.USER_BIO, node);
        const userNodeId: string = S.props.getPropStr(J.NodeProp.USER_NODE_ID, node);
        const actorUrl = S.props.getClientPropStr(J.NodeProp.ACT_PUB_ACTOR_URL, node);
        const displayName = S.props.getClientPropStr(J.NodeProp.DISPLAY_NAME, node);
        let imgSrc = S.props.getClientPropStr(J.NodeProp.USER_ICON_URL, node);

        /* If not ActivityPub try as local user */
        if (!imgSrc) {
            const avatarVer: string = S.props.getClientPropStr("avatarVer", node);
            if (avatarVer) {
                imgSrc = S.render.getAvatarImgUrl(userNodeId, avatarVer);
            }
        }

        // Note: we pass showMessageButton as true when isTreeView is true only.
        return S.render.renderUser(node, user, userBio, imgSrc, actorUrl,
            displayName, null, isTreeView ? "treeFriendImage" : "listFriendImage", isTreeView, () => {
                new UserProfileDlg(userNodeId).open();
            });
    }

    override getEditorOptions(): EditorOptions {
        return {
            tags: true
        };
    }
}
