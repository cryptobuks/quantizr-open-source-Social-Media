import { getAppState } from "../AppContext";
import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Checkbox } from "../comp/core/Checkbox";
import { Clearfix } from "../comp/core/Clearfix";
import { Div } from "../comp/core/Div";
import { HelpButton } from "../comp/core/HelpButton";
import { EditPrivsTable } from "../comp/EditPrivsTable";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { FriendsDlg } from "./FriendsDlg";

interface LS { // Local State
    nodePrivsInfo?: J.GetNodePrivilegesResponse;
    recursive?: boolean;
}

export class SharingDlg extends DialogBase {
    dirty: boolean = false;

    constructor(private node: J.NodeInfo) {
        super("Node Sharing", "app-modal-content-medium-width");
        this.mergeState<LS>({ nodePrivsInfo: null, recursive: false });
    }

    renderDlg(): CompIntf[] {
        const isPublic = S.props.isPublic(this.node);
        const state: LS = this.getState<LS>();

        return [
            new Div(null, null, [
                new Div("Note: All usernames mentioned in the content text will also be automatically added to this sharing list when you save the node, " +
                    "so you don't need to add users here if they're mentioned when you save.", { className: "marginBottom" }),
                new EditPrivsTable((allowAppends: boolean) => {
                    this.shareNodeToPublic(allowAppends);
                }, this.getState<LS>().nodePrivsInfo, this.removePrivilege),
                S.props.isShared(this.node) ? new Div("Remove All", {
                    className: "marginBottom marginRight float-end clickable",
                    onClick: this.removeAllPrivileges
                }) : null,
                new Clearfix(),
                // todo-1: There's a bug in turning this OFF and also it just needs more thought in the design, becasue it's too
                // easy to create a node and share it with someone and then have it NOT end up visible in the feeds of the people it's shared to.
                // #unpublish-disabled
                // new Checkbox("Unpublished", null, {
                //     setValue: async (checked: boolean) => {
                //         let state: LS = this.getState<LS>();
                //         this.dirty = true;
                //         state.nodePrivsInfo.unpublished = checked;
                //         await S.util.ajax<J.SetUnpublishedRequest, J.AddPrivilegeResponse>("setUnpublished", {
                //             nodeId: this.node.id,
                //             unpublished: checked
                //         });

                //         this.mergeState<LS>({ nodePrivsInfo: state.nodePrivsInfo });
                //         return null;
                //     },
                //     getValue: (): boolean => {
                //         return state.nodePrivsInfo.unpublished;
                //     }
                // }),
                new Checkbox("Apply to all children (that you own)", null, {
                    setValue: (checked: boolean) => {
                        this.dirty = true;
                        this.mergeState<LS>({ recursive: checked });
                    },
                    getValue: (): boolean => state.recursive
                }),
                new ButtonBar([
                    new Button("Add Person", async () => {
                        const friendsDlg: FriendsDlg = new FriendsDlg(this.node, true);
                        await friendsDlg.open();
                        if (friendsDlg.getState().selectedName) {
                            this.dirty = true;
                            this.shareImmediate(friendsDlg.getState().selectedName);
                        }
                    }, null, "btn-primary"),
                    isPublic ? null : new Button("Make Public", () => this.shareNodeToPublic(false), null, "btn-secondary"),
                    new Button("Done", () => {
                        this.close();
                    }, null, "btn-secondary float-end"),
                    new HelpButton(() => getAppState().config.help?.sharing?.dialog)
                ], "marginTop")
            ])
        ];
    }

    shareImmediate = async (userName: string) => {
        await S.util.ajax<J.AddPrivilegeRequest, J.AddPrivilegeResponse>("addPrivilege", {
            nodeId: this.node.id,
            principal: userName,
            privileges: [J.PrivilegeType.READ, J.PrivilegeType.WRITE]
        });
        this.reload();
    }

    async preLoad(): Promise<void> {
        await this.reload();
    }

    /*
     * Gets privileges from server and saves into state.
     */
    reload = async () => {
        const res = await S.util.ajax<J.GetNodePrivilegesRequest, J.GetNodePrivilegesResponse>("getNodePrivileges", {
            nodeId: this.node.id,
            includeAcl: true,
            includeOwners: true
        });
        this.node.ac = res.aclEntries;
        this.mergeState<LS>({ nodePrivsInfo: res });
    }

    removeAllPrivileges = async () => {
        this.dirty = true;
        await S.util.ajax<J.RemovePrivilegeRequest, J.RemovePrivilegeResponse>("removePrivilege", {
            nodeId: this.node.id,
            principalNodeId: "*",
            privilege: "*"
        });

        this.removePrivilegeResponse();
    }

    super_close = this.close;
    close = () => {
        this.super_close();
        if (this.dirty) {
            // console.log("Sharing dirty=true. Full refresh pending.");
            if (this.getState<LS>().recursive) {
                setTimeout(async () => {
                    await S.util.ajax<J.CopySharingRequest, J.CopySharingResponse>("copySharing", {
                        nodeId: this.node.id
                    });
                    S.quanta.refresh(getAppState());
                }, 100);
            }
            else {
                S.quanta.refresh(getAppState());
            }
        }
    }

    removePrivilege = async (principalNodeId: string, privilege: string) => {
        this.dirty = true;
        await S.util.ajax<J.RemovePrivilegeRequest, J.RemovePrivilegeResponse>("removePrivilege", {
            nodeId: this.node.id,
            principalNodeId,
            privilege
        });
        this.removePrivilegeResponse();
    }

    removePrivilegeResponse = async () => {
        const res = await S.util.ajax<J.GetNodePrivilegesRequest, J.GetNodePrivilegesResponse>("getNodePrivileges", {
            nodeId: this.node.id,
            includeAcl: true,
            includeOwners: true
        });

        this.node.ac = res.aclEntries;
        this.mergeState<LS>({ nodePrivsInfo: res });
    }

    shareNodeToPublic = async (allowAppends: boolean) => {
        this.dirty = true;
        const encrypted = S.props.isEncrypted(this.node);
        if (encrypted) {
            S.util.showMessage("This node is encrypted, and therefore cannot be made public.", "Warning");
            return;
        }

        /*
         * Add privilege and then reload share nodes dialog from scratch doing another callback to server
         *
         * TODO: this additional call can be avoided as an optimization
         */
        await S.util.ajax<J.AddPrivilegeRequest, J.AddPrivilegeResponse>("addPrivilege", {
            nodeId: this.node.id,
            principal: "public",
            privileges: allowAppends ? [J.PrivilegeType.READ, J.PrivilegeType.WRITE] : [J.PrivilegeType.READ]
        });

        this.reload();
    }
}
