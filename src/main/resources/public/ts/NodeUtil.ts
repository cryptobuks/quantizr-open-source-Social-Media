import { nip19 } from "nostr-tools";
import { dispatch, getAs } from "./AppContext";
import { AppState } from "./AppState";
import { Comp } from "./comp/base/Comp";
import { Icon } from "./comp/core/Icon";
import { Span } from "./comp/core/Span";
import { Constants as C } from "./Constants";
import { LoadNodeFromIpfsDlg } from "./dlg/LoadNodeFromIpfsDlg";
import { UserProfileDlg } from "./dlg/UserProfileDlg";
import * as J from "./JavaIntf";
import { S } from "./Singletons";
import { MainTab } from "./tabs/data/MainTab";

export class NodeUtil {
    getSelNodeIdsArray = (): string[] => {
        const sels: string[] = [];
        getAs().selectedNodes.forEach(id => sels.push(id));
        return sels;
    }

    getDisplayName = (node: J.NodeInfo): string => {
        const isNostr = S.nostr.isNostrUserName(node.owner);
        let name = "";
        if (isNostr) {
            name = S.props.getPropStr(J.NodeProp.DISPLAY_NAME, node) ||
                S.props.getPropStr(J.NodeProp.NOSTR_NAME, node) ||
                S.props.getPropStr(J.NodeProp.NOSTR_USER_NAME, node);

            if (!name) {
                const pubKey = node.owner.substring(1);
                name = nip19.npubEncode(pubKey);
                if (name) {
                    name = name.substring(0, 13) + "...";
                }
            }
        }
        else {
            name = node.owner;
        }
        return name;
    }

    clearSelNodes = () => {
        dispatch("ClearSelections", s => s.selectedNodes.clear());
    }

    selectAllNodes = (nodeIds: string[]) => {
        // DO NOT DELETE (feature work in progress)
        // //todo-2: large numbers of selected nodes isn't going to scale well in this design
        // // but i am not letting perfection be the enemy of good here (yet)
        // this.selectedNodes.clear();
        // nodeIds.forEach( (nodeId, index) => {
        //     this.selectedNodes.add(nodeId);
        // });
    }

    getHighlightedNode = (): J.NodeInfo => {
        const ast = getAs();
        if (!ast.node) return null;
        const id: string = S.quanta.parentIdToFocusNodeMap.get(ast.node.id);
        if (id) {
            return MainTab.inst?.findNode(id);
        }
        return null;
    }

    /* Returns true if successful */
    highlightRowById = (ast: AppState, id: string, scroll: boolean): void => {
        let node = MainTab.inst?.findNode(id, ast);

        if (node) {
            this.highlightNode(node, scroll, ast);
        } else {
            // This code path should really never happen, but we're looking out for future cases where
            // the 'ast' passed into here might not yet have a findable node 'in findNode' below and we
            // try again on a delay.
            setTimeout(() => {
                ast = getAs();
                node = MainTab.inst?.findNode(id, ast);

                /* If node not known, resort to taking the best, previous node we had */
                if (!node) {
                    node = this.getHighlightedNode();
                }

                if (node) {
                    this.highlightNode(node, scroll, ast);
                }
                else {
                    // if we can't find that node, best behvior is at least to scroll to top.
                    if (scroll) {
                        S.view.scrollToTop();
                    }
                }
            }, 750);
        }
    }

    highlightNode = (node: J.NodeInfo, scroll: boolean, ust: AppState) => {
        ust = ust || getAs();
        if (!node || !ust.node) {
            return;
        }

        if (!ust.isAnonUser) {
            S.localDB.setVal(C.LOCALDB_LAST_PARENT_NODEID, ust.node.id);
            S.localDB.setVal(C.LOCALDB_LAST_CHILD_NODEID, node.id);
            S.util.updateHistory(node);
        }

        // this highlightNodeId is only really used to ensure state change happens, but really everything always
        // keys off parentIdToFocusNodeMap actually reading the value.
        dispatch("highlightNode", s => s.highlightNodeId = node.id);

        S.quanta.parentIdToFocusNodeMap.set(ust.node.id, node.id);

        if (scroll) {
            S.view.scrollToNode(node);
        }
    }

    /* Find node by looking everywhere we possibly can on local storage for it */
    findNode = (nodeId: string): J.NodeInfo => {
        const ast = getAs();
        for (const data of ast.tabData) {
            const node = data.findNode(nodeId);
            if (node) return node;
        }
        return null;
    }

    clearLastNodeIds = () => {
        S.localDB.setVal(C.LOCALDB_LAST_PARENT_NODEID, null);
        S.localDB.setVal(C.LOCALDB_LAST_CHILD_NODEID, null);
    }

    /* Returns the node if it's currently displaying on the page. For now we don't have ability */
    displayingOnTree = (nodeId: string): J.NodeInfo => {
        const ast = getAs();
        if (!ast.node) return null;
        if (ast.node.id === nodeId) return ast.node;
        if (!ast.node.children) return null;
        return ast.node.children.find(node => node?.id === nodeId);
    }

    getNodeByName = (node: J.NodeInfo, name: string, ust: AppState): J.NodeInfo => {
        if (!node) return null;
        if (node.name === name) return node;

        if (node.children) {
            return ust.node.children.find(node => node?.name === name);
        }
        return null;
    }

    getPathPartForNamedNode = (node: J.NodeInfo): string => {
        if (!node || !node.name) return null;

        if (node.owner === J.PrincipalName.ADMIN) {
            return "/n/" + node.name;
        }
        else {
            return "/u/" + node.owner + "/" + node.name;
        }
    }

    getPathPartForNamedNodeAttachment = (node: J.NodeInfo): string => {
        if (!node || !node.name) return null;

        if (node.owner === J.PrincipalName.ADMIN) {
            return "/f/" + node.name;
        }
        else {
            return "/f/" + node.owner + "/" + node.name;
        }
    }

    getShortContent = (node: J.NodeInfo): string => {
        let content = node.content;
        if (!content) {
            if (node.name) {
                return "Name:" + node.name;
            }
            else {
                return "ID:" + node.id;
            }
        }

        content = S.util.removeHtmlTags(content);
        content = content.trim();

        // if this is a node starting with hashtags or usernames then chop them all
        while (content.startsWith("@") || content.startsWith("#")) {
            let spaceIdx = content.indexOf(" ");
            if (spaceIdx === -1) {
                spaceIdx = content.indexOf("\n");
            }
            if (spaceIdx > 0) {
                content = content.substring(spaceIdx + 1);
            }
            else break;
        }
        content = content.trim();

        // if content was empty or we weren't able to stip off all the mentions at the front
        // of it and end up with some other text than just render this as the node id.
        if (!content || content.startsWith("@")) {
            return "ID:" + node.id;
        }

        const idx = content.indexOf("\n");
        if (idx !== -1) {
            content = content.substring(0, idx);
        }

        if (content.length > 140) content = content.substring(0, 140) + "...";

        if (S.props.isEncrypted(node)) {
            content = "[Encrypted]";
        }

        return content.trim();
    }

    // returns true if all children are same owner as parent
    allChildrenAreSameOwner = (node: J.NodeInfo): boolean => {
        if (!node || !node.children) return true;

        for (const child of node.children) {
            if (node.ownerId !== child.ownerId) {
                return false;
            }
        }
        return true;
    }

    publishNodeToIpfs = async (node: J.NodeInfo) => {
        const res = await S.rpcUtil.rpc<J.PublishNodeToIpfsRequest, J.PublishNodeToIpfsResponse>("publishNodeToIpfs", {
            nodeId: node.id
        });
        S.util.showMessage(res.message, "Server Reply", true);
    }

    loadNodeFromIpfs = (node: J.NodeInfo): any => {
        new LoadNodeFromIpfsDlg().open();
    }

    removePublicShare = async (node: J.NodeInfo, editorDlg: Comp) => {
        await S.rpcUtil.rpc<J.RemovePrivilegeRequest, J.RemovePrivilegeResponse>("removePrivilege", {
            nodeId: node.id,
            principalNodeId: J.PrincipalName.PUBLIC,
            privilege: "*"
        });
        this.removePrivilegeResponse(node, editorDlg);
    }

    removePrivilegeResponse = async (node: J.NodeInfo, editorDlg: Comp) => {
        const res = await S.rpcUtil.rpc<J.GetNodePrivilegesRequest, J.GetNodePrivilegesResponse>("getNodePrivileges", {
            nodeId: node.id
        });

        if (editorDlg) {
            const ast = getAs();
            ast.editNode.ac = res.aclEntries;
            S.edit.updateNode(ast.editNode);
        }
    }

    getSharingNames = (node: J.NodeInfo, editorDlg: Comp): Comp[] => {
        if (!node?.ac) return null;

        const ret: Comp[] = [];
        if (S.props.isPublic(node)) {
            ret.push(
                new Span(null, {
                    title: "Shared to Public (Everyone)\n\nClick to remove Public Sharing",
                    className: "sharingNamePublic marginLeftIfNotFirst",
                    onClick: () => this.removePublicShare(node, editorDlg)
                }, [
                    new Icon({
                        className: "fa fa-globe fa-lg sharingIcon microMarginRight",
                        title: "Node is Public"
                    }),
                    new Span("Public" + this.getPublicPrivilegsSuffix(J.PrincipalName.PUBLIC, node))
                ])
            );
        }

        let showMore = "";
        let moreCount: number = 0;
        let numShares = 0;
        for (const ac of node.ac) {
            const suffix = this.getPublicPrivilegsSuffix(ac.principalName, node);
            // todo-1: will come back to this. I think this happens when there's a share to a user whose
            // account no longer exists?
            // if (!ac.principalName) {
            //     console.log("missing principalName on acl: " + S.util.prettyPrint(node));
            // }

            // Skip public here we processed that above.
            if (ac.principalName && ac.principalName !== J.PrincipalName.PUBLIC) {
                let props = null;
                let title = "";
                if (ac.displayName) {
                    title = ac.displayName;
                }
                title += (title ? "\n\n" : "") + S.util.getFriendlyPrincipalName(ac);

                // If we have a local node for this user we will have the principleNodeId here and show a link
                // to open the user.
                if (ac.principalNodeId) {
                    props = {
                        onClick: (event: any) => {
                            event.stopPropagation();
                            event.preventDefault();
                            new UserProfileDlg(ac.principalNodeId).open();
                        },
                        className: "sharingName clickable",
                        title
                    }
                }
                // todo-1: Else this is a foreign user, so is there a way to set a link
                // here to a foreign account, or should we basically run the equivalent
                // of a user search here, probably best done with a modifiction to the
                // UserPreferences server call and dialog
                else {
                    props = {
                        className: "sharingName",
                        title
                    }
                }

                if (numShares > 5) {
                    moreCount++;
                    showMore += S.util.getFriendlyPrincipalName(ac);
                    if (ac.displayName) {
                        showMore += " (" + ac.displayName + ")"
                    }
                    showMore += suffix;
                    showMore += "\n";
                }
                else {
                    let nameInSpan = S.util.getFriendlyPrincipalName(ac);

                    // If nameInSpan contains '@' (a foreign user) then chop off the server name to make the
                    // display shorter
                    const atIdx = nameInSpan.indexOf("@");
                    if (atIdx !== -1) {
                        nameInSpan = nameInSpan.substring(0, atIdx);
                    }

                    ret.push(new Span(nameInSpan + suffix, props));
                }
                numShares++;
            }
        }

        if (showMore) {
            ret.push(new Span(`${moreCount} more...`, { className: "sharingName", title: "Also shared to...\n" + showMore }));
        }

        return ret;
    }

    getPublicPrivilegsSuffix = (principalName: string, node: J.NodeInfo): string => {
        if (!node || !node.ac) return "";
        let val = "";
        for (const ac of node.ac) {
            if (ac.principalName === principalName) {
                // Note: I'm leaving this loop, but really all this will generate in 'val' is either nothing
                // at all or "(+R)" (for now)
                for (const p of ac.privileges) {
                    if (val) {
                        val += ",";
                    }

                    // +R = replies
                    if (p.privilegeName.indexOf(J.PrivilegeType.WRITE) !== -1) {
                        val += " +R";
                    }
                    // val += p.privilegeName;
                }
                break;
            }
        }
        return val;
    }
}
