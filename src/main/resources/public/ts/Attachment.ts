import { appState } from "./AppRedux";
import { AppState } from "./AppState";
import { ConfirmDlg } from "./dlg/ConfirmDlg";
import { UploadFromFileDropzoneDlg } from "./dlg/UploadFromFileDropzoneDlg";
import { UploadFromIPFSDlg } from "./dlg/UploadFromIPFSDlg";
import { UploadFromUrlDlg } from "./dlg/UploadFromUrlDlg";
import * as J from "./JavaIntf";
import { S } from "./Singletons";

export class Attachment {
    openUploadFromFileDlg = (toIpfs: boolean, node: J.NodeInfo, autoAddFile: File, state: AppState) => {
        state = appState(state);
        if (!node) {
            node = S.nodeUtil.getHighlightedNode(state);
        }
        if (!node) {
            S.util.showMessage("No node is selected.", "Warning");
            return;
        }

        new UploadFromFileDropzoneDlg(node.id, "", toIpfs, autoAddFile, false, true, state, () => {
            S.view.jumpToId(node.id);
            // S.quanta.refresh(state);
        }).open();

        /* Note: To run legacy uploader just put this version of the dialog here, and
        nothing else is required. Server side processing is still in place for it
        (new UploadFromFileDlg()).open();
        */
    };

    openUploadFromUrlDlg = (nodeId: string, defaultUrl: string, onUploadFunc: Function, state: AppState) => {
        if (!nodeId) {
            let node = S.nodeUtil.getHighlightedNode(state);
            if (!node) {
                S.util.showMessage("No node is selected.", "Warning");
                return;
            }
            nodeId = node.id;
        }

        new UploadFromUrlDlg(nodeId, defaultUrl, onUploadFunc, state).open();
    };

    openUploadFromIPFSDlg = (nodeId: string, defaultCid: string, onUploadFunc: Function, state: AppState) => {
        if (!nodeId) {
            let node = S.nodeUtil.getHighlightedNode(state);
            if (!node) {
                S.util.showMessage("No node is selected.", "Warning");
                return;
            }
            nodeId = node.id;
        }

        new UploadFromIPFSDlg(nodeId, defaultCid, onUploadFunc, state).open();
    };

    deleteAttachment = async (node: J.NodeInfo, state: AppState): Promise<boolean> => {
        node = node || S.nodeUtil.getHighlightedNode(state);

        if (node) {
            let dlg = new ConfirmDlg("Delete the Attachment on the Node?", "Confirm", "btn-danger", "alert alert-danger", state);
            await dlg.open();
            if (dlg.yes) {
                await S.util.ajax<J.DeleteAttachmentRequest, J.DeleteAttachmentResponse>("deleteAttachment", {
                    nodeId: node.id
                });
            }
            return dlg.yes;
        }
        return false;
    };

    removeBinaryProperties = (node: J.NodeInfo) => {
        if (node) {
            S.props.allBinaryProps.forEach(s => {
                S.props.deleteProp(node, s);
            });
        }
    };
}
