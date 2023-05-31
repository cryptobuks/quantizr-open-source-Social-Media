import { dispatch, getAs, promiseDispatch } from "../AppContext";
import { CompIntf } from "../comp/base/CompIntf";
import { Div } from "../comp/core/Div";
import { Divc } from "../comp/core/Divc";
import { Span } from "../comp/core/Span";
import { SymKeyDataPackage } from "../Crypto";
import { DialogMode } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { Validator } from "../Validator";
import { ConfirmDlg } from "./ConfirmDlg";
import { EditNodeDlg, LS as EditNodeDlgState } from "./EditNodeDlg";
import { EditPropertyDlg, LS as EditPropertyDlgState } from "./EditPropertyDlg";
import { EmojiPickerDlg, LS as EmojiPickerDlgState } from "./EmojiPickerDlg";
import { FriendsDlg, LS as FriendsDlgState } from "./FriendsDlg";
import { UploadFromFileDropzoneDlg } from "./UploadFromFileDropzoneDlg";
import { Event } from "nostr-tools";
export class EditNodeDlgUtil {
    public countPropsShowing = (dlg: EditNodeDlg): number => {
        const ast = getAs();
        const type = S.plugin.getType(ast.editNode.type);
        if (type) {
            type.ensureDefaultProperties(ast.editNode);
        }

        let numPropsShowing: number = 0;

        // This loop creates all the editor input fields for all the properties
        ast.editNode.properties?.forEach(prop => {
            if (!dlg.allowEditAllProps && !S.render.allowPropertyEdit(ast.editNode, prop.name)) {
                return;
            }

            if (dlg.allowEditAllProps || (
                !S.render.isReadOnlyProperty(prop.name) || S.edit.showReadOnlyProperties)) {

                if (!S.props.isGuiControlBasedProp(prop) && !S.props.isHiddenProp(prop)) {
                    numPropsShowing++;
                }
            }
        });
        return numPropsShowing;
    }

    public saveNode = async (dlg: EditNodeDlg): Promise<boolean> => {
        const ast = getAs();
        const editNode = ast.editNode;

        // if trying to send non-public node over Nostr. Disallow it for now, unless a DM type (which we do allow). Until we have encryptio support.
        if (editNode.type !== J.NodeType.NOSTR_ENC_DM && ast.protocolFilter === J.Constant.NETWORK_NOSTR && //
            !S.props.isPublic(editNode) && S.props.hasNonPublicNostrShares(editNode)) {
            S.util.showMessage("WARNING: Only Nostr DMs can be sent as private.", "Nostr Warning");
            return false;
        }

        // save these two values, because the S.quanta copy can get overwritten before we use them here.
        const newNodeTargetId = S.quanta.newNodeTargetId;
        const newNodeTargetOffset = S.quanta.newNodeTargetOffset;

        let content: string;
        let clearText: string;
        if (dlg.contentEditor) {
            content = clearText = dlg.contentEditor.getValue();

            if (S.crypto.avail) {
                const cipherKey = S.props.getCryptoKey(editNode);
                if (cipherKey) {
                    content = await S.crypto.symEncryptStringWithCipherKey(cipherKey, content);
                    content = J.Constant.ENC_TAG + content;
                }
            }
        }

        if (content) {
            content = content.trim();
        }

        if (clearText) {
            clearText = clearText.trim();
        }

        editNode.content = content;
        editNode.name = dlg.nameState.getValue();
        editNode.tags = dlg.tagsState.getValue();

        this.savePropsToNode(editNode, dlg);
        this.saveAttFileNamesToNode(editNode, dlg);

        /*
        Note: if this is an encrypted node we will be signing the cipher text (encrypted string), because content has already
        been encrypted just above.
        todo-1: Note: We only sign if admin for now, by design */

        if (dlg.getState<EditNodeDlgState>().signCheckboxVal) {
            if (S.crypto.avail) {
                // Note: this needs to come AFTER the 'savePropsToNode' call above because we're overriding what was
                // possibly in there.
                await S.crypto.signNode(editNode);
            }
        }
        else {
            S.props.setPropVal(J.NodeProp.CRYPTO_SIG, editNode, "[null]");
        }

        let nostrEvent: Event = null;
        const nostrRelays: string[] = [];

        const sendToNostr = ast.protocolFilter === J.Constant.NETWORK_NOSTR && //
            (editNode.type === J.NodeType.NOSTR_ENC_DM || S.props.isPublic(editNode));

        if (sendToNostr) {
            nostrEvent = await S.nostr.prepareOutboundEvent(editNode, clearText, nostrRelays);
        }

        // console.log("saveNode(): sendToActPub=" + ast.sendToActPub);
        const res = await S.rpcUtil.rpc<J.SaveNodeRequest, J.SaveNodeResponse>("saveNode", {
            node: editNode,
            saveToActPub: ast.protocolFilter === J.Constant.NETWORK_ACTPUB,
            nostrEvent: S.nostr.makeNostrEventWrapper(nostrEvent, editNode.id)
        });

        if (!res?.success) {
            return false;
        }

        if (sendToNostr && nostrEvent) {
            S.nostr.sendMessage(nostrEvent, nostrRelays);
        }

        dlg.resetAutoSaver();

        S.render.fadeInId = editNode.id;
        S.edit.saveNodeResponse(editNode, res, newNodeTargetId, newNodeTargetOffset);
        S.util.notifyNodeUpdated(editNode.id, editNode.type);
        return true;
    }

    // Takes all the propStates values and converts them into node properties on the node
    savePropsToNode = (editNode: J.NodeInfo, dlg: EditNodeDlg) => {
        const type = S.plugin.getType(editNode.type);
        editNode.properties?.forEach(prop => {
            const propState = dlg.propStates.get(prop.name);

            if (propState) {
                // hack to store dates as numeric prop (todo-2: need a systematic way to assign JSON types to properties)
                if (prop.name === J.NodeProp.DATE && (typeof propState.getValue() === "string")) {
                    try {
                        prop.value = parseInt(propState.getValue());
                    }
                    catch (e) {
                        S.util.logErr(e, "failed to parse date number: " + propState.getValue());
                    }
                }
                else {
                    const dataType = type.getType(prop.name);
                    if (dataType === "Number") {
                        prop.value = parseFloat(propState.getValue());
                    }
                    else {
                        prop.value = propState.getValue();
                    }
                }
            }
        });
    }

    saveAttFileNamesToNode = (editNode: J.NodeInfo, dlg: EditNodeDlg) => {
        const list: J.Attachment[] = S.props.getOrderedAtts(editNode);
        for (const att of list) {
            const propState = dlg.attFileNames.get((att as any).key);
            if (propState) {
                att.f = propState.getValue();
            }
        }
    }

    addProperty = async (dlg: EditNodeDlg): Promise<void> => {
        const ast = getAs();
        const state: EditNodeDlgState = dlg.getState<EditNodeDlgState>();
        const propDlg = new EditPropertyDlg(ast.editNode);
        await propDlg.open();

        if (propDlg.nameState.getValue()) {
            ast.editNode.properties = ast.editNode.properties || [];
            const prop: J.PropertyInfo = {
                name: propDlg.nameState.getValue(),
                value: ""
            }
            ast.editNode.properties.push(prop);

            // this forces a rerender, even though it looks like we're doing nothing to state.
            // todo-1: should mergeState come AFTER initPropsState ?
            dlg.mergeState<EditNodeDlgState>(state);
            this.initPropState(dlg, ast.editNode, prop);
        }
        else {
            propDlg.getState<EditPropertyDlgState>().selections.forEach(sop => {
                ast.editNode.properties = ast.editNode.properties || [];

                // if this 'sop' is ALREADY a property we have, ignore it. Don't dupliate it.
                if (ast.editNode.properties.find(prop => prop.name === sop.label)) return;

                const prop: J.PropertyInfo = {
                    name: sop.label,
                    value: ""
                }
                ast.editNode.properties.push(prop);
                this.initPropState(dlg, ast.editNode, prop);
            });

            // this forces a rerender, even though it looks like we're doing nothing to state.
            dlg.mergeState<EditNodeDlgState>(state);
        }
        // we don't need to return an actual promise here
        return null;
    }

    addDateProperty = async (dlg: EditNodeDlg) => {
        const state = dlg.getState<EditNodeDlgState>();
        const ast = getAs();
        ast.editNode.properties = ast.editNode.properties || [];

        if (S.props.getProp(J.NodeProp.DATE, ast.editNode)) {
            return;
        }

        await promiseDispatch("setPropsPanelExpanded", s => { s.propsPanelExpanded = true; });

        ast.editNode.properties.push({
            name: J.NodeProp.DATE,
            value: new Date().getTime()
        }, {
            name: J.NodeProp.DURATION,
            value: "01:00"
        });

        dlg.mergeState<EditNodeDlgState>(state);
    }

    share = async (dlg: EditNodeDlg) => {
        const ast = getAs();
        await S.edit.editNodeSharing(dlg, ast.editNode);
        S.edit.updateNode(ast.editNode);
    }

    uploadFromClipboard = async (dlg: EditNodeDlg) => {
        const blob = await S.util.readClipboardFile();
        if (blob) {
            dlg.immediateUploadFiles([blob]);
        }
        else {
            S.util.showMessage("Unable to get Clipboard content.", "Warning");
        }
    }

    upload = async (file: File, dlg: EditNodeDlg) => {
        const state = dlg.getState<EditNodeDlgState>();
        const ast = getAs();

        const uploadDlg = new UploadFromFileDropzoneDlg(ast.editNode.id, "", state.toIpfs, file, false, true, async () => {
            await this.refreshFromServer(ast.editNode);
            S.edit.updateNode(ast.editNode);
            dlg.binaryDirty = true;
        });
        await uploadDlg.open();
    }

    deleteProperties = async (dlg: EditNodeDlg, propNames: string[]) => {
        if (!propNames) return;
        const ast = getAs();
        const type = S.plugin.getType(ast.editNode.type);
        if (type) {
            propNames = propNames.filter(v => type.allowDeleteProperty(v));
        }
        const res = await S.rpcUtil.rpc<J.DeletePropertyRequest, J.DeletePropertyResponse>("deleteProperties", {
            nodeId: ast.editNode.id,
            propNames
        });

        if (S.util.checkSuccess("Delete property", res)) {
            const state = dlg.getState<EditNodeDlgState>();
            propNames.forEach(propName => S.props.deleteProp(ast.editNode, propName));
            dlg.mergeState<EditNodeDlgState>(state);
        }
    }

    deletePropsGesture = async (dlg: EditNodeDlg) => {
        const confirmDlg = new ConfirmDlg("Delete the selected properties?", "Confirm Delete",
            "btn-danger", "alert alert-danger");
        await confirmDlg.open();
        if (confirmDlg.yes) {
            this.deleteSelectedProperties(dlg);
        }
    }

    deleteSelectedProperties = (dlg: EditNodeDlg) => {
        const keys: string[] = [];
        const delProps = dlg.getState<EditNodeDlgState>().selectedProps;

        // special case: If user is deleting a date also delete the duration, because these go together.
        if (delProps.has("date")) {
            delProps.add("duration");
        }
        delProps.forEach(prop => keys.push(prop));
        this.deleteProperties(dlg, keys);
    }

    setEncryption = (dlg: EditNodeDlg, encrypt: boolean) => {
        dlg.mergeState({ encryptCheckboxVal: encrypt });
        const state = dlg.getState<EditNodeDlgState>();
        const ast = getAs();
        if (encrypt && S.props.isPublic(ast.editNode)) {
            S.util.showMessage("Cannot encrypt a node that is shared to public. Remove public share first.", "Warning");
            return;
        }
        if (dlg.pendingEncryptionChange) return;

        (async () => {
            const encrypted: boolean = S.props.isEncrypted(ast.editNode);

            /* only if the encryption setting changed do we need to do anything here */
            if (encrypted !== encrypt) {
                dlg.pendingEncryptionChange = true;
                try {
                    /* If we're turning off encryption for the node */
                    if (!encrypt) {
                        /* Take what's in the editor and put
                        that into this.node.content, because it's the correct and only place the correct updated text is guaranteed to be
                        in the case where the user made some changes before disabling encryption. */
                        ast.editNode.content = dlg.contentEditor.getValue();
                        S.props.setPropVal(J.NodeProp.ENC_KEY, ast.editNode, null);
                    }
                    /* Else need to ensure node is encrypted */
                    else {
                        // if we need to encrypt and the content is not currently encrypted.
                        if (S.crypto.avail && !ast.editNode.content?.startsWith(J.Constant.ENC_TAG)) {
                            const content = dlg.contentEditor.getValue();
                            const skdp: SymKeyDataPackage = await S.crypto.encryptSharableString(null, content);

                            if (skdp.cipherKey && skdp.cipherKey) {
                                ast.editNode.content = J.Constant.ENC_TAG + skdp.cipherText;

                                /* Set ENC_KEY to be the encrypted key, which when decrypted can be used to decrypt
                                the content of the node. This ENC_KEY was encrypted with the public key of the owner of this node,
                                and so can only be decrypted with their private key. */
                                S.props.setPropVal(J.NodeProp.ENC_KEY, ast.editNode, skdp.cipherKey);
                            }
                        }
                    }

                    dlg.mergeState<EditNodeDlgState>(state);
                }
                finally {
                    dlg.pendingEncryptionChange = false;
                }
            }
        })();
    }

    deleteUploads = async (dlg: EditNodeDlg) => {
        if (dlg.getState<EditNodeDlgState>().selectedAttachments?.size === 0) return;

        const confirmDlg = new ConfirmDlg("Delete the selected Attachments?", "Confirm Delete",
            "btn-danger", "alert alert-danger");
        await confirmDlg.open();

        if (confirmDlg.yes) {
            const ast = getAs();

            let delAttKeys = "";
            dlg.getState<EditNodeDlgState>().selectedAttachments?.forEach(prop => {
                delete ast.editNode.attachments[prop];

                if (delAttKeys) {
                    delAttKeys += ",";
                }
                delAttKeys = prop;
            });

            if (delAttKeys) {
                await S.rpcUtil.rpc<J.DeleteAttachmentRequest, J.DeleteAttachmentResponse>("deleteAttachment", {
                    nodeId: ast.editNode.id,
                    attName: delAttKeys
                });
            }

            if (S.util.getPropertyCount(ast.editNode.attachments) === 0) {
                ast.editNode.attachments = null;
            }
            S.edit.updateNode(ast.editNode);

            if (dlg.mode === DialogMode.EMBED) {
                dispatch("uploadDeleted", s => {
                    s.editNode = ast.editNode;
                });
            }
            dlg.binaryDirty = true;
        }
    }

    /* WARNING: despite the name this only refreshes attachments and links */
    refreshFromServer = async (node: J.NodeInfo) => {
        const res = await S.rpcUtil.rpc<J.RenderNodeRequest, J.RenderNodeResponse>("renderNode", {
            nodeId: node.id,
            upLevel: false,
            siblingOffset: 0,
            renderParentIfLeaf: false,
            forceRenderParent: false,
            offset: 0,
            goToLastPage: false,
            forceIPFSRefresh: false,
            singleNode: true,
            parentCount: 0,
            jumpToRss: false
        });

        if (res.node) {
            node.attachments = res.node.attachments;
            node.links = res.node.links;
        }
    }

    initPropState = (dlg: EditNodeDlg, node: J.NodeInfo, propEntry: J.PropertyInfo) => {
        const allowEditAllProps: boolean = getAs().isAdminUser;
        const isReadOnly = S.render.isReadOnlyProperty(propEntry.name);
        const propVal = propEntry.value;
        const propValStr = propVal || "";
        // console.log("making single prop editor: prop[" + propEntry.property.name + "] val[" + propEntry.property.value
        //     + "] fieldId=" + propEntry.id);

        let propState: Validator = dlg.propStates.get(propEntry.name);
        if (!propState) {
            propState = new Validator(propVal);
            dlg.propStates.set(propEntry.name, propState);
        }

        if (!allowEditAllProps && isReadOnly) {
            propState.setValue(propValStr);
        }
        else {
            const val = S.props.getPropStr(propEntry.name, node);
            propState.setValue(val);

            /* todo-2: eventually we will have data types, but for now we use a hack
            to detect to treat a string as a date based on its property name. */
            if (propEntry.name === J.NodeProp.DATE) {
                // Ensure we have set the default time if none is yet set.
                if (!propState.getValue()) {
                    propState.setValue("" + new Date().getTime());
                }
            }
        }
    }

    toggleRecognition = (dlg: EditNodeDlg) => {
        S.speech.setListenerCallback((transcript: string) => {
            if (dlg.contentEditor && transcript) {
                dlg.contentEditor.insertTextAtCursor(transcript + ". ");
            }
        });

        const speechActive = !dlg.getState<EditNodeDlgState>().speechActive;
        if (speechActive) {
            S.speech.startListening();
        }
        else {
            S.speech.stopListening();
        }
        dlg.mergeState<EditNodeDlgState>({ speechActive });

        setTimeout(() => {
            if (dlg.contentEditor) {
                dlg.contentEditor.focus();
            }
        }, 250);
    }

    initStates = (dlg: EditNodeDlg) => {
        const ast = getAs();

        dlg.onMount((elm: HTMLElement) => {
            dlg.initContent();
        });

        /* Initialize node name state */
        dlg.nameState.setValue(ast.editNode.name);
        dlg.tagsState.setValue(ast.editNode.tags);
        this.initPropStates(dlg, ast.editNode);
    }

    /* Initializes the propStates for every property in 'node', and optionally if 'onlyBinaries==true' then we process ONLY
the properties on node that are in 'S.props.allBinaryProps' list, which is how we have to update the propStates after
an upload has been added or removed.
*/
    initPropStates = (dlg: EditNodeDlg, node: J.NodeInfo): any => {
        const type = S.plugin.getType(node.type);
        if (type) {
            type.ensureDefaultProperties(node);
        }

        if (node.properties) {
            node.properties.forEach(prop => {
                if (!dlg.allowEditAllProps && !S.render.allowPropertyEdit(node, prop.name)) {
                    return;
                }

                if (dlg.allowEditAllProps || (
                    !S.render.isReadOnlyProperty(prop.name) || S.edit.showReadOnlyProperties)) {

                    if (!S.props.isGuiControlBasedProp(prop) && !S.props.isHiddenProp(prop)) {
                        this.initPropState(dlg, node, prop);
                    }
                }
            });
        }
    }

    insertTime = (dlg: EditNodeDlg) => {
        dlg.contentEditor?.insertTextAtCursor("[" + S.util.formatDateTime(new Date()) + "]");
    }

    insertEmoji = async (dlg: EditNodeDlg) => {
        if (!dlg.contentEditor) return;
        // we have to capture the cursor position BEFORE we open a dialog, because the loss of focus will make us also
        // loose the cursor position.
        const selStart = dlg.contentEditor.getSelStart();
        const eDlg: EmojiPickerDlg = new EmojiPickerDlg();
        await eDlg.open();
        if (eDlg.getState<EmojiPickerDlgState>().selectedEmoji) {
            dlg.contentEditor.insertTextAtCursor(eDlg.getState<EmojiPickerDlgState>().selectedEmoji, selStart);
        }
    }

    insertUserNames = async (dlg: EditNodeDlg) => {
        if (!dlg.contentEditor) return;
        // get the selStart immediately or it can be wrong, after renders.
        const selStart = dlg.contentEditor.getSelStart();
        // we have to capture the cursor position BEFORE we open a dialog, because the loss of focus will make us also
        // loose the cursor position.

        const friendsDlg: FriendsDlg = new FriendsDlg("Friends", null, false);
        await friendsDlg.open();

        if (friendsDlg.getState<FriendsDlgState>().selections?.size > 0) {
            const names: string[] = [];
            friendsDlg.getState<FriendsDlgState>().selections.forEach(n => names.push("@" + n));
            const namesStr = names.join(" ");
            dlg.contentEditor.insertTextAtCursor(" " + namesStr + " ", selStart);
        }
    }

    speakerClickInEditor = (dlg: EditNodeDlg) => {
        if (getAs().speechSpeaking) {
            S.speech.stopSpeaking();
        }
        else {
            const content = S.quanta.selectedForTts ? S.quanta.selectedForTts : dlg.contentEditorState.getValue();
            if (content) {
                S.speech.speakText(content, false);
            }
        }
    }

    cancelEdit = (dlg: EditNodeDlg) => {
        const ast = getAs();
        dlg.closeByUser();
        dlg.close();

        // rollback properties.
        ast.editNode.properties = dlg.initialProps;

        if (dlg.binaryDirty) {
            S.quanta.refresh();
        }
    }

    renderLinksEditing = (): Div => {
        const ast = getAs();
        if (!ast.editNode.links) return null;

        let hasLinks = false;
        const linkComps: CompIntf[] = [];
        if (ast.editNode.links) {
            linkComps.push(new Span("Links: ", { className: "linksPrompt" }));
            Object.keys(ast.editNode.links).forEach(key => {
                hasLinks = true;
                const linkName = ast.editNode.links[key].n;
                linkComps.push(new Span(linkName, {
                    className: "nodeLink",
                    title: "Click to Remove Link",
                    onClick: () => this.removeNodeLink(linkName)
                }));
            });
        }
        return hasLinks ? new Divc({ className: "linksPanelInEditor" }, linkComps) : null;
    }

    removeNodeLink = (nodeName: string): void => {
        const ast = getAs();
        Object.keys(ast.editNode.links).forEach(key => {
            if (ast.editNode.links[key].n === nodeName) {
                delete ast.editNode.links[key];
            }
        });
        S.edit.updateNode(ast.editNode);
    }
}
