import { dispatch, getAppState } from "../AppContext";
import { AppState } from "../AppState";
import { CompIntf } from "../comp/base/CompIntf";
import { Div } from "../comp/core/Div";
import { Span } from "../comp/core/Span";
import { SymKeyDataPackage } from "../Crypto";
import { DialogMode } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { Validator } from "../Validator";
import { ConfirmDlg } from "./ConfirmDlg";
import { EditNodeDlg } from "./EditNodeDlg";
import { LS } from "./EditNodeDlgState";
import { EditPropertyDlg } from "./EditPropertyDlg";
import { EmojiPickerDlg } from "./EmojiPickerDlg";
import { FriendsDlg } from "./FriendsDlg";
import { FriendsDlgState } from "./FriendsDlgState";
import { SplitNodeDlg } from "./SplitNodeDlg";
import { UploadFromFileDropzoneDlg } from "./UploadFromFileDropzoneDlg";

export class EditNodeDlgUtil {
    public countPropsShowing = (dlg: EditNodeDlg): number => {
        const ast = getAppState();
        const type = S.plugin.getType(ast.editNode.type);
        if (type) {
            type.ensureDefaultProperties(ast.editNode);
            dlg.editorHelp = type.getEditorHelp();
        }

        let numPropsShowing: number = 0;

        // This loop creates all the editor input fields for all the properties
        ast.editNode.properties?.forEach(prop => {
            if (!dlg.allowEditAllProps && !S.render.allowPropertyEdit(ast.editNode, prop.name, getAppState())) {
                return;
            }

            if (dlg.allowEditAllProps || (
                !S.render.isReadOnlyProperty(prop.name) || S.edit.showReadOnlyProperties)) {

                if (!S.props.isGuiControlBasedProp(prop)) {
                    numPropsShowing++;
                }
            }
        });
        return numPropsShowing;
    }

    public saveNode = async (dlg: EditNodeDlg): Promise<boolean> => {
        const ast = getAppState();
        const editNode = ast.editNode;

        // save these two values, becasue the S.quanta copy can get overwritten before we use them here.
        const newNodeTargetId = S.quanta.newNodeTargetId;
        const newNodeTargetOffset = S.quanta.newNodeTargetOffset;

        let content: string;
        if (dlg.contentEditor) {
            content = dlg.contentEditor.getValue();

            if (S.crypto.avail) {
                const cipherKey = S.props.getCryptoKey(editNode, getAppState());
                if (cipherKey) {
                    content = await S.crypto.symEncryptStringWithCipherKey(cipherKey, content);
                    content = J.Constant.ENC_TAG + content;
                }
            }
        }
        if (content) {
            content = content.trim();
        }

        editNode.content = content;
        editNode.name = dlg.nameState.getValue();
        editNode.tags = dlg.tagsState.getValue();

        const askToSplit = editNode.content && (editNode.content.indexOf("{split}") !== -1 ||
            editNode.content.indexOf("\n\n\n") !== -1);

        this.savePropsToNode(editNode, dlg);
        this.saveAttFileNamesToNode(editNode, dlg);

        /*
        Note: if this is an encrypted node we will be signing the cipher text (encrypted string), because content has already
        been encrypted just above.
        todo-1: Note: We only sign if admin for now, by design */

        if (dlg.getState().signCheckboxVal) {
            if (S.crypto.avail) {
                // Note: this needs to come AFTER the 'savePropsToNode' call above because we're overriding what was
                // possibly in there.
                await S.crypto.signNode(editNode);
            }
        }
        else {
            S.props.setPropVal(J.NodeProp.CRYPTO_SIG, editNode, "[null]");
        }

        const res = await S.rpcUtil.rpc<J.SaveNodeRequest, J.SaveNodeResponse>("saveNode", {
            node: editNode
        });

        if (!res?.success) {
            return false;
        }
        dlg.resetAutoSaver();

        /* IMPORTANT: If there's an after edit action function specified on the dialog then that will be the ONLY
         action performed after the saveNode, so if we ever need any of the below logic to be run, in the case with
         afterEditAction we'd have to call that logic inside the afterEditAction function.

         todo-0: this afterEditAction seems kind of ugly. Check if this can be done better now.
         */
        if (dlg.afterEditAction) return true;

        S.render.fadeInId = editNode.id;
        S.edit.saveNodeResponse(editNode, res, true, newNodeTargetId, newNodeTargetOffset, getAppState());

        // Only ask to split for ordinary nodes (non special)
        const type = S.plugin.getType(editNode.type);
        if (askToSplit && !(type && type.isSpecialAccountNode())) {
            new SplitNodeDlg(editNode).open();
        }

        S.util.notifyNodeUpdated(editNode.id, editNode.type);
        return true;
    }

    // Takes all the propStates values and converts them into node properties on the node
    savePropsToNode = (editNode: J.NodeInfo, dlg: EditNodeDlg) => {
        editNode.properties?.forEach(prop => {
            const propState = dlg.propStates.get(prop.name);
            if (propState) {
                // hack to store dates as numeric prop (todo-2: need a systematic way to assign JSON types to properties)
                if (prop.name === J.NodeProp.DATE && (typeof propState.getValue() === "string")) {
                    try {
                        prop.value = parseInt(propState.getValue());
                    }
                    catch (e) {
                        console.error("failed to parse date number: " + propState.getValue());
                    }
                }
                else {
                    prop.value = propState.getValue();
                }
            }
        });
    }

    saveAttFileNamesToNode = (editNode: J.NodeInfo, dlg: EditNodeDlg) => {
        const list: J.Attachment[] = S.props.getOrderedAttachments(editNode);
        for (const att of list) {
            const propState = dlg.attFileNames.get((att as any).key);
            if (propState) {
                att.f = propState.getValue();
            }
        }
    }

    addProperty = async (dlg: EditNodeDlg): Promise<void> => {
        const ast = getAppState();
        const state: LS = dlg.getState<LS>();
        const propDlg = new EditPropertyDlg(ast.editNode);
        await propDlg.open();

        if (propDlg.nameState.getValue()) {
            ast.editNode.properties = ast.editNode.properties || [];
            const newProp: J.PropertyInfo = {
                name: propDlg.nameState.getValue(),
                value: ""
            }
            ast.editNode.properties.push(newProp);

            // this forces a rerender, even though it looks like we're doing nothing to state.
            dlg.mergeState<LS>(state);
            this.initPropState(dlg, ast.editNode, newProp);
        }
        // we don't need to return an actual promise here
        return null;
    }

    addDateProperty = (dlg: EditNodeDlg) => {
        const state = dlg.getState<LS>();
        const ast = getAppState();
        ast.editNode.properties = ast.editNode.properties || [];

        if (S.props.getProp(J.NodeProp.DATE, ast.editNode)) {
            return;
        }

        ast.editNode.properties.push({
            name: J.NodeProp.DATE,
            value: new Date().getTime()
        }, {
            name: J.NodeProp.DURATION,
            value: "01:00"
        });

        dlg.mergeState<LS>(state);
    }

    share = async (dlg: EditNodeDlg) => {
        const ast = getAppState();
        await S.edit.editNodeSharing(getAppState(), dlg, ast.editNode);
        S.edit.updateNode(ast.editNode);
    }

    uploadFromClipboard = async (ast: AppState, dlg: EditNodeDlg) => {
        const blob = await S.util.readClipboardFile();
        if (blob) {
            dlg.immediateUploadFiles(ast, [blob]);
        }
        else {
            S.util.showMessage("Unable to get Clipboard content.", "Warning");
        }
    }

    upload = async (file: File, dlg: EditNodeDlg) => {
        const state = dlg.getState<LS>();
        const ast = getAppState();

        const uploadDlg = new UploadFromFileDropzoneDlg(ast.editNode.id, "", state.toIpfs, file, false, true, async () => {
            await this.refreshFromServer(ast.editNode);
            S.edit.updateNode(ast.editNode);
            dlg.binaryDirty = true;
        });
        await uploadDlg.open();
    }

    setNodeType = (newType: string) => {
        const ast = getAppState();
        ast.editNode.type = newType;
        S.edit.updateNode(ast.editNode);
    }

    deleteProperties = async (dlg: EditNodeDlg, propNames: string[]) => {
        const ast = getAppState();
        const res = await S.rpcUtil.rpc<J.DeletePropertyRequest, J.DeletePropertyResponse>("deleteProperties", {
            nodeId: ast.editNode.id,
            propNames
        });

        if (S.util.checkSuccess("Delete property", res)) {
            const state = dlg.getState<LS>();
            propNames.forEach(propName => S.props.deleteProp(ast.editNode, propName));
            dlg.mergeState<LS>(state);
        }
    }

    deletePropertiesButtonClick = async (dlg: EditNodeDlg) => {
        const confirmDlg = new ConfirmDlg("Delete the selected properties?", "Confirm Delete",
            "btn-danger", "alert alert-danger");
        await confirmDlg.open();
        if (confirmDlg.yes) {
            this.deleteSelectedProperties(dlg);
        }
    }

    deleteSelectedProperties = (dlg: EditNodeDlg) => {
        const keys: string[] = [];
        dlg.getState<LS>().selectedProps.forEach(prop => keys.push(prop));
        this.deleteProperties(dlg, keys);
    }

    setEncryption = (dlg: EditNodeDlg, encrypt: boolean) => {
        dlg.mergeState({ encryptCheckboxVal: encrypt });
        const state = dlg.getState<LS>();
        const ast = getAppState();
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

                    dlg.mergeState<LS>(state);
                }
                finally {
                    dlg.pendingEncryptionChange = false;
                }
            }
        })();
    }

    deleteUploads = async (dlg: EditNodeDlg) => {
        if (dlg.getState<LS>().selectedAttachments?.size === 0) return;

        const confirmDlg = new ConfirmDlg("Delete the selected Attachments?", "Confirm Delete",
            "btn-danger", "alert alert-danger");
        await confirmDlg.open();

        if (confirmDlg.yes) {
            const ast = getAppState();

            let delAttKeys = "";
            dlg.getState<LS>().selectedAttachments?.forEach(prop => {
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
            parentCount: 0
        });

        if (res.node) {
            node.attachments = res.node.attachments;
            node.links = res.node.links;
        }
    }

    initPropState = (dlg: EditNodeDlg, node: J.NodeInfo, propEntry: J.PropertyInfo) => {
        const allowEditAllProps: boolean = getAppState().isAdminUser;
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

        const speechActive = !dlg.getState().speechActive;
        if (speechActive) {
            S.speech.startListening();
        }
        else {
            S.speech.stopListening();
        }
        dlg.mergeState<LS>({ speechActive });

        setTimeout(() => {
            if (dlg.contentEditor) {
                dlg.contentEditor.focus();
            }
        }, 250);
    }

    initStates = (dlg: EditNodeDlg) => {
        const ast = getAppState();

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
                if (!dlg.allowEditAllProps && !S.render.allowPropertyEdit(node, prop.name, getAppState())) {
                    return;
                }

                if (dlg.allowEditAllProps || (
                    !S.render.isReadOnlyProperty(prop.name) || S.edit.showReadOnlyProperties)) {

                    if (!S.props.isGuiControlBasedProp(prop)) {
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
        const emojiDlg: EmojiPickerDlg = new EmojiPickerDlg();
        await emojiDlg.open();
        if (emojiDlg.getState().selectedEmoji) {
            dlg.contentEditor.insertTextAtCursor(emojiDlg.getState().selectedEmoji, selStart);
        }
    }

    insertUserNames = async (dlg: EditNodeDlg) => {
        if (!dlg.contentEditor) return;
        // get the selStart immediately or it can be wrong, after renders.
        const selStart = dlg.contentEditor.getSelStart();
        // we have to capture the cursor position BEFORE we open a dialog, because the loss of focus will make us also
        // loose the cursor position.

        const friendsDlg: FriendsDlg = new FriendsDlg("Friends", null);
        await friendsDlg.open();

        if (friendsDlg.getState<FriendsDlgState>().selections?.size > 0) {
            const names: string[] = [];
            friendsDlg.getState<FriendsDlgState>().selections.forEach(n => names.push("@" + n));
            const namesStr = names.join(" ");
            dlg.contentEditor.insertTextAtCursor(" " + namesStr + " ", selStart);
        }
    }

    speakerClickInEditor = (ast: AppState, dlg: EditNodeDlg) => {
        if (ast.speechSpeaking) {
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
        const ast = getAppState();
        dlg.closeByUser();
        dlg.close();

        // rollback properties.
        ast.editNode.properties = dlg.initialProps;

        if (dlg.binaryDirty) {
            S.quanta.refresh(getAppState());
        }
    }

    renderLinksEditing = (ast: AppState): Div => {
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
                    onClick: () => this.removeNodeLink(ast, linkName)
                }));
            });
        }
        return hasLinks ? new Div(null, { className: "linksPanelInEditor" }, linkComps) : null;
    }

    removeNodeLink = (ast: AppState, nodeName: string): void => {
        Object.keys(ast.editNode.links).forEach(key => {
            if (ast.editNode.links[key].n === nodeName) {
                delete ast.editNode.links[key];
            }
        });
        S.edit.updateNode(ast.editNode);
    }
}
