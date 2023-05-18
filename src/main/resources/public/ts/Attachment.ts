import { UploadFromFileDropzoneDlg } from "./dlg/UploadFromFileDropzoneDlg";
import { UploadFromIPFSDlg } from "./dlg/UploadFromIPFSDlg";
import { UploadFromUrlDlg } from "./dlg/UploadFromUrlDlg";
import * as J from "./JavaIntf";
import { S } from "./Singletons";

export class Attachment {
    openUploadFromFileDlg = (toIpfs: boolean, nodeId: string, autoAddFile: File) => {
        if (!nodeId) {
            const node = S.nodeUtil.getHighlightedNode();
            nodeId = node?.id;
        }

        if (!nodeId) {
            S.util.showMessage("No node is selected.", "Warning");
            return;
        }

        new UploadFromFileDropzoneDlg(nodeId, "", toIpfs, autoAddFile, false, true, () => {
            S.view.jumpToId(nodeId);
        }).open();
    };

    openUploadFromUrlDlg = (nodeId: string, defaultUrl: string, onUploadFunc: Function) => {
        if (!nodeId) {
            const node = S.nodeUtil.getHighlightedNode();
            if (!node) {
                S.util.showMessage("No node is selected.", "Warning");
                return;
            }
            nodeId = node.id;
        }

        new UploadFromUrlDlg(nodeId, onUploadFunc).open();
    };

    openUploadFromIPFSDlg = (nodeId: string, defaultCid: string, onUploadFunc: Function) => {
        if (!nodeId) {
            const node = S.nodeUtil.getHighlightedNode();
            if (!node) {
                S.util.showMessage("No node is selected.", "Warning");
                return;
            }
            nodeId = node.id;
        }

        new UploadFromIPFSDlg(nodeId, onUploadFunc).open();
    };

    getAttachmentUrl = (urlPart: string, node: J.NodeInfo, attName: string, downloadLink: boolean): string => {
        /* If this node attachment points to external URL return that url */
        const att = S.props.getAttachment(attName, node);
        if (!att) return null;
        return this.getAttUrl(urlPart, att, node.id, downloadLink);
    }

    getAttUrl = (urlPart: string, att: J.Attachment, nodeId: string, downloadLink: boolean): string => {
        if (att.u) {
            return att.u;
        }

        const ipfsLink = att.il;
        let bin = att.b;

        if (bin || ipfsLink) {
            if (ipfsLink) {
                bin = "ipfs";
            }
            // todo-1: to make nostr posts cleaner (when inspected as text) we should support a url in the
            // form of /att/${nodeId}/${bin} here, and have a version of this 'getAttUrl()' just for Nostr.
            let ret: string = S.rpcUtil.getRpcPath() + urlPart + "/" + bin + "?nodeId=" + nodeId;

            if (downloadLink) {
                ret += "&download=true";
            }
            return ret;
        }

        return null;
    }

    getUrlForNodeAttachment = (node: J.NodeInfo, attName: string, downloadLink: boolean): string => {
        return this.getAttachmentUrl("bin", node, attName, downloadLink);
    }

    getStreamUrlForNodeAttachment = (node: J.NodeInfo, attName: string): string => {
        return this.getAttachmentUrl("stream", node, attName, false);
    }
}
