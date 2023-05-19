import { dispatch, getAs } from "./AppContext";
import * as J from "./JavaIntf";
import { S } from "./Singletons";

export class Props {
    readOnlyPropertyList: Set<string> = new Set<string>();

    /* Holds the list of properties that are edited using something like a checkbox, or dropdown menu, or whatever, such
    that it would never make sense to display an edit field for editing their value in the editor */
    controlBasedPropertyList: Set<string> = new Set<string>();

    hiddenPropertyList: Set<string> = new Set<string>();

    moveNodePosition = (props: J.PropertyInfo[], idx: number, typeName: string): number => {
        const tagIdx: number = S.util.arrayIndexOfItemByProp(props, "name", typeName);
        if (tagIdx !== -1) {
            S.util.arrayMoveItem(props, tagIdx, idx++);
        }
        return idx;
    }

    deleteProp = (node: J.NodeInfo, propertyName: string) => {
        if (node.properties) {
            node.properties = node.properties.filter(p => {
                return p.name !== propertyName;
            });
        }
    }

    getClientProp = (propName: string, node: J.NodeInfo): J.PropertyInfo => {
        if (!node?.clientProps) {
            return null;
        }

        return node.clientProps.find(p => p.name === propName);
    }

    /* Gets the crypto key from this node that will allow user to decrypt the node. If the user is the owner of the
    node this simply returns the ENC_KEY property but if not we look up in the ACL on the node a copy of the encrypted
    key that goes with the current user (us, logged in user), which should decrypt using our private key.
    */
    getCryptoKey = (node: J.NodeInfo) => {
        if (!node) return null;
        let key = null;

        /* if we own this node then this cipherKey for it will be ENC_KEY for us */
        if (getAs().userName === node.owner) {
            key = this.getPropStr(J.NodeProp.ENC_KEY, node);
        }
        /* else if the server has provided the cipher key to us from the ACL (AccessControl) then use it. */
        else {
            key = node.cipherKey;
        }
        return key;
    }

    isShared = (node: J.NodeInfo): boolean => {
        return !!node.ac;
    }

    hasNonPublicShares = (node: J.NodeInfo): boolean => {
        return node && node.ac && !!node.ac.find(ace => ace.principalNodeId !== J.PrincipalName.PUBLIC);
    }

    hasNonPublicNostrShares = (node: J.NodeInfo): boolean => {
        return node && node.ac && !!node.ac.find(ace => ace.principalNodeId?.startsWith("."));
    }

    hasMentions = (node: J.NodeInfo): boolean => {
        const tags: any = S.props.getPropObj(J.NodeProp.ACT_PUB_TAG, node);
        let ret = false;
        if (tags?.forEach) {
            tags.forEach((t: any) => {
                if (t.type === "Mention") {
                    ret = true;
                }
            })
        }
        return ret;
    }

    isPublic = (node: J.NodeInfo): boolean => {
        return node && node.ac && !!node.ac.find(ace => ace.principalNodeId === J.PrincipalName.PUBLIC);
    }

    isPublicWritable = (node: J.NodeInfo): boolean => {
        return node && node.ac && !!node.ac.find(ace => ace.principalNodeId === J.PrincipalName.PUBLIC && this.hasPrivilege(ace, J.PrivilegeType.WRITE));
    }

    isWritableByMe = (node: J.NodeInfo): boolean => {
        if (!node) return false;
        const ast = getAs();

        // anonymous can never write
        if (ast.isAnonUser) return false;

        // if we own the node
        if (ast.userName === node.owner) return true;

        // if we are admin
        if (ast.isAdminUser) return true;

        if (ast.userProfile?.userNodeId === node.id) {
            return true;
        }

        // writeable by us if there's any kind of share to us or a writable public share.
        return node && node.ac && !!node.ac.find(ace =>
            (ace.principalNodeId === J.PrincipalName.PUBLIC && this.hasPrivilege(ace, J.PrivilegeType.WRITE)) ||
            ace.principalNodeId === ast.userProfile?.userNodeId);
    }

    isPublicReadOnly = (node: J.NodeInfo): boolean => {
        return node && node.ac && !!node.ac.find(ace => ace.principalNodeId === J.PrincipalName.PUBLIC && !this.hasPrivilege(ace, J.PrivilegeType.WRITE));
    }

    getAcCount = (node: J.NodeInfo): number => {
        return node && node.ac ? node.ac.length : 0;
    }

    hasPrivilege = (ace: J.AccessControlInfo, priv: string): boolean => {
        if (!ace.privileges) return false;
        return !!ace.privileges.find(p => p.privilegeName.indexOf(priv) !== -1);
    }

    isMine = (node: J.NodeInfo): boolean => {
        const ast = getAs();
        if (!node || !ast.userName || ast.userName === J.PrincipalName.ANON) return false;
        return ast.userName === node.owner;
    }

    isEncrypted = (node: J.NodeInfo): boolean => {
        return node?.content?.indexOf(J.Constant.ENC_TAG) === 0;
    }

    getOrderedAtts = (node: J.NodeInfo): J.Attachment[] => {
        const list: J.Attachment[] = [];

        // put all attachments in 'list', random order
        let defaultOrdinal = 0;
        if (node.attachments) {
            Object.keys(node.attachments).forEach(key => {
                // this is bizarre looking yes, but we need each object returned to know what it's key is
                (node.attachments[key] as any).key = key;

                // fixing ordinal with defaultOrdinal is just to be resilient against bad/duplicate ordinals.
                if (!node.attachments[key].o) {
                    node.attachments[key].o = defaultOrdinal++;
                }
                list.push(node.attachments[key]);
            });
        }

        // now sort and return the list
        list.sort((a, b) => a.o - b.o);
        return list;
    }

    hasBinary = (node: J.NodeInfo): boolean => {
        return !!node.attachments;
    }

    hasImage = (node: J.NodeInfo, attName: string): boolean => {
        const att = this.getAttachment(attName, node);
        const target = att ? att.m : null;
        return (target && target.startsWith("image/"));
    }

    hasAudio = (node: J.NodeInfo, attName: string): boolean => {
        const att = this.getAttachment(attName, node);
        const target = att ? att.m : null;
        return (target && target.startsWith("audio/"));
    }

    hasVideo = (node: J.NodeInfo, attName: string): boolean => {
        const att = this.getAttachment(attName, node);
        const target = att ? att.m : null;
        return (target && target.startsWith("video/"));
    }

    /*
     * brute force searches on node (NodeInfo.java) object properties list, and returns the first property
     * (PropertyInfo.java) with name matching propertyName, else null.
     */
    getProp = (propName: string, node: J.NodeInfo): J.PropertyInfo => {
        if (!node || !node.properties) {
            return null;
        }

        return node.properties.find(p => p.name === propName);
    }

    getPropStr = (propertyName: string, node: J.NodeInfo): string => {
        const prop = this.getProp(propertyName, node);
        return prop ? prop.value : null;
    }

    getPropObj = (propertyName: string, node: J.NodeInfo): any => {
        const prop = this.getProp(propertyName, node);
        return prop ? prop.value : null;
    }

    getClientPropStr = (propertyName: string, node: J.NodeInfo): string => {
        const prop = this.getClientProp(propertyName, node);
        return prop ? prop.value : null;
    }

    getAttachment = (name: string, node: J.NodeInfo): J.Attachment => {
        if (!name) name = J.Constant.ATTACHMENT_PRIMARY;
        return node && node.attachments ? node.attachments[name] : null;
    }

    setPropVal = (propertyName: string, node: J.NodeInfo, val: any) => {
        let prop = this.getProp(propertyName, node);

        /* If we found a property by propertyName, then set it's value */
        if (prop) {
            prop.value = val;
        }
        /* Else this is a new property we must add (ret remains true here) */
        else {
            prop = {
                name: propertyName,
                value: val
            };
            node.properties = node.properties || [];
            node.properties.push(prop);
        }
    }

    setProp = (node: J.NodeInfo, newProp: J.PropertyInfo) => {
        if (!newProp) return;
        const prop = this.getProp(newProp.name, node);

        /* If we found a property by propertyName, then set it's value */
        if (prop) {
            prop.value = newProp.value;
        }
        /* Else this is a new property we must add (ret remains true here) */
        else {
            node.properties = node.properties || [];
            node.properties.push(newProp);
        }
    }

    // here's the simple mode property hider!
    initConstants = () => {
        S.util.addAllToSet(this.readOnlyPropertyList, [ //
            J.NodeProp.JSON_HASH, //
            J.NodeProp.ENC_KEY, //
            J.NodeProp.TYPE_LOCK, //
            J.NodeProp.UNPUBLISHED
        ]);

        /* These props are the ones we don't create a TextField for during editing, because the editor will have
        some other more specialized way of managing the property */
        S.util.addAllToSet(this.controlBasedPropertyList, [ //
            J.NodeProp.INLINE_CHILDREN, //
            J.NodeProp.NOWRAP, //
            J.NodeProp.SAVE_TO_IPFS, //
            J.NodeProp.LAYOUT, //
            J.NodeProp.PRIORITY, //
            J.NodeProp.UNPUBLISHED, //
            J.NodeProp.CRYPTO_SIG, //
            J.NodeProp.ACT_PUB_OBJ_URLS, //
            J.NodeProp.ACT_PUB_OBJ_ICONS, //
            J.NodeProp.ACT_PUB_TAG
        ]);

        S.util.addAllToSet(this.hiddenPropertyList, [ //
            J.NodeProp.TYPE_LOCK, //
            J.NodeProp.BOOST,
            J.NodeProp.NO_EXPORT
        ]);
    }

    isGuiControlBasedProp = (prop: J.PropertyInfo): boolean => {
        return !!S.props.controlBasedPropertyList.has(prop.name);
    }

    isHiddenProp = (prop: J.PropertyInfo): boolean => {
        // let admin user see all props.
        if (getAs().isAdminUser) return false;

        if (this.isSystemProp(prop.name)) {
            return true;
        }
        return !!S.props.hiddenPropertyList.has(prop.name);
    }

    isSystemProp = (prop: string): boolean => {
        switch (prop) {
            case J.NodeProp.RSS_FEED_SRC:
                return false;

            case J.NodeProp.OBJECT_ID:
            case J.NodeProp.INLINE_CHILDREN:
            case J.NodeProp.PRIORITY:
            case J.NodeProp.LAYOUT:
            case J.NodeProp.ORDER_BY:
            case J.NodeProp.NO_EXPORT:
            case J.NodeProp.UNPUBLISHED:
            case J.NodeProp.BOOST:
            case J.NodeProp.IN_PENDING_PATH:
            case J.NodeProp.TRUNCATED:
                return true;

            default:
                return prop.indexOf(":") !== -1;
        }
    }

    /* This is kind of a hard-coded hack for the one particular type name
    where we are using it, but needs to work for all properties */
    getInputClassForType = (typeName: string): string => {
        if (typeName === J.NodeProp.DURATION) {
            return "durationTypeInput";
        }
        return null;
    }

    getParentPath = (node: J.NodeInfo): string => {
        const slashIdx = node.path.lastIndexOf("/");
        if (slashIdx === -1) return null;
        return node.path.substring(0, slashIdx);
    }

    addRecentType = (type: string): void => {
        const ast = getAs();
        const recentTypes = ast.userProfile.recentTypes;
        let typesArray: string[];
        if (recentTypes) {
            typesArray = recentTypes.split(",");
            if (typesArray) {
                typesArray = typesArray.filter(t => t !== type);
            }
        }
        else {
            typesArray = [];
        }

        // make sure list is short
        if (typesArray.length >= 9) {
            typesArray.pop();
        }
        // at this type to front of list (Note this ordering is not what controls GUI display ordering,
        // but is still significant for keeping 'Most Recently Used 10' algo functional for this list)
        typesArray.unshift(type);
        ast.userProfile.recentTypes = typesArray.join(",");

        dispatch("SetUserProfile", s => {
            s.userProfile = ast.userProfile;
        });

        S.rpcUtil.rpc<J.SaveUserProfileRequest, J.SaveUserProfileResponse>("saveUserProfile", {
            userName: null,
            userTags: ast.userProfile.userTags,
            blockedWords: ast.userProfile.blockedWords,
            userBio: ast.userProfile.userBio,
            displayName: ast.userProfile.displayName,
            publish: false,
            mfsEnable: ast.userProfile.mfsEnable,
            recentTypes: ast.userProfile.recentTypes
        });
    }

    hasDisplayableProps = (node: J.NodeInfo) => {
        if (node.properties) {
            for (const prop of node.properties) {
                if (!S.props.isGuiControlBasedProp(prop) && !S.props.isHiddenProp(prop)) return true;
            }
        }
        return false;
    }
}
