import { dispatch } from "../AppRedux";
import { AppState } from "../AppState";
import { DialogMode } from "../enums/DialogMode";
import { TypeHandlerIntf } from "../intf/TypeHandlerIntf";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { SymKeyDataPackage } from "../SymKeyDataPackage";
import { ValidatedState } from "../ValidatedState";
import { ConfirmDlg } from "./ConfirmDlg";
import { EditNodeDlg } from "./EditNodeDlg";
import { LS } from "./EditNodeDlgState";
import { EditPropertyDlg } from "./EditPropertyDlg";
import { EmojiPickerDlg } from "./EmojiPickerDlg";
import { FriendsDlg } from "./FriendsDlg";
import { SplitNodeDlg } from "./SplitNodeDlg";
import { UploadFromFileDropzoneDlg } from "./UploadFromFileDropzoneDlg";

export class EditNodeDlgUtil {
    public countPropsShowing = (dlg: EditNodeDlg): number => {
        let state = dlg.getState<LS>();
        let typeHandler: TypeHandlerIntf = S.plugin.getTypeHandler(state.node.type);
        let customProps: string[] = null;
        if (typeHandler) {
            customProps = typeHandler.getCustomProperties();
            typeHandler.ensureDefaultProperties(state.node);
            dlg.editorHelp = typeHandler.getEditorHelp();
        }

        let numPropsShowing: number = 0;
        if (state.node.properties) {
            // This loop creates all the editor input fields for all the properties
            state.node.properties.forEach((prop: J.PropertyInfo) => {
                // console.log("prop=" + S.util.prettyPrint(prop));

                if (!dlg.allowEditAllProps && !S.render.allowPropertyEdit(state.node, prop.name, dlg.appState)) {
                    // console.log("Hiding property: " + prop.name);
                    return;
                }

                if (dlg.allowEditAllProps || (
                    !S.render.isReadOnlyProperty(prop.name) || S.edit.showReadOnlyProperties)) {

                    if (!dlg.isGuiControlBasedProp(prop)) {
                        numPropsShowing++;
                    }
                }
            });
        }
        return numPropsShowing;
    }

    public saveNode = async (dlg: EditNodeDlg): Promise<void> => {
        let state = dlg.getState<LS>();

        let content: string;
        if (dlg.contentEditor) {
            content = dlg.contentEditor.getValue();
            let cipherKey = S.props.getCryptoKey(state.node, dlg.appState);
            if (cipherKey) {
                content = await S.encryption.symEncryptStringWithCipherKey(cipherKey, content);
                content = J.Constant.ENC_TAG + content;
            }
        }
        if (content) {
            content = content.trim();
        }
        state.node.content = content;
        state.node.name = dlg.nameState.getValue();

        let askToSplit = state.node.content && ((state.node as J.NodeInfo).content.indexOf("{split}") !== -1 ||
            (state.node as J.NodeInfo).content.indexOf("\n\n\n") !== -1);

        this.savePropsToNode(dlg);
        // console.log("calling saveNode(). PostData=" + S.util.prettyPrint(state.node));

        let res: J.SaveNodeResponse = await S.util.ajax<J.SaveNodeRequest, J.SaveNodeResponse>("saveNode", {
            node: state.node
        });

        // if we're saving a bookmark but NOT viewing the bookmark list then we don't need to do any
        // page refreshing after the edit.
        if (res.node.type === J.NodeType.BOOKMARK && dlg.appState.node.type !== J.NodeType.BOOKMARK_LIST) {
            // do nothing.
        }
        else {
            S.render.fadeInId = state.node.id;
            S.edit.saveNodeResponse(state.node, res, true, dlg.appState);

            if (askToSplit) {
                new SplitNodeDlg(state.node, dlg.appState).open();
            }
        }

        // if we just saved a bookmark, reload bookmarks menu
        if ((state.node as J.NodeInfo).type === J.NodeType.BOOKMARK) {
            setTimeout(() => {
                S.util.loadBookmarks();
            }, 250);
        }
    }

    // Takes all the propStates values and converts them into node properties on the node
    savePropsToNode = (dlg: EditNodeDlg) => {
        let state = dlg.getState<LS>();
        if (state.node.properties) {
            state.node.properties.forEach((prop: J.PropertyInfo) => {
                // console.log("Save prop iterator: name=" + prop.name);
                let propState = dlg.propStates.get(prop.name);
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
                        // console.log("   val=" + prop.value);
                    }
                }
            });
        }
    }

    addProperty = async (dlg: EditNodeDlg): Promise<void> => {
        let state: LS = dlg.getState<LS>();
        let propDlg = new EditPropertyDlg(state.node, dlg.appState);
        await propDlg.open();

        if (propDlg.nameState.getValue()) {
            if (!state.node.properties) {
                state.node.properties = [];
            }
            state.node.properties.push({
                name: propDlg.nameState.getValue(),
                value: ""
            });
            dlg.mergeState<LS>(state);
        }
        // we don't need to return an actual promise here
        return null;
    }

    addDateProperty = (dlg: EditNodeDlg): void => {
        let state = dlg.getState<LS>();
        if (!state.node.properties) {
            state.node.properties = [];
        }

        if (S.props.getNodeProp(J.NodeProp.DATE, state.node)) {
            return;
        }

        state.node.properties.push({
            name: J.NodeProp.DATE,
            value: new Date().getTime()
        });

        state.node.properties.push({
            name: J.NodeProp.DURATION,
            value: "01:00"
        });

        // Ensure the have the panel expanded so we can see the new date.
        // nope, i decided I don't like this auto-expanding.
        // EditNodeDlg.morePanelExpanded = true;
        dlg.mergeState<LS>(state);
    }

    share = async (dlg: EditNodeDlg): Promise<void> => {
        let state = dlg.getState<LS>();
        await S.edit.editNodeSharing(dlg.appState, state.node);
        dlg.mergeState<LS>({ node: state.node });
        return null;
    }

    upload = async (dlg: EditNodeDlg): Promise<void> => {
        let state = dlg.getState<LS>();

        let uploadDlg = new UploadFromFileDropzoneDlg(state.node.id, "", state.toIpfs, null, false, true, dlg.appState, async () => {
            await this.refreshBinaryPropsFromServer(dlg, state.node);
            this.initPropStates(dlg, state.node, true);
            dlg.mergeState<LS>({ node: state.node });
            dlg.binaryDirty = true;
        });
        await uploadDlg.open();
    }

    setNodeType = (dlg: EditNodeDlg, newType: string): void => {
        let state = dlg.getState<LS>();
        state.node.type = newType;
        dlg.mergeState<LS>({ node: state.node });
    }

    deleteProperties = async (dlg: EditNodeDlg, propNames: string[]) => {
        let res: J.DeletePropertyResponse = await S.util.ajax<J.DeletePropertyRequest, J.DeletePropertyResponse>("deleteProperties", {
            nodeId: dlg.getState<LS>().node.id,
            propNames
        });

        if (S.util.checkSuccess("Delete property", res)) {
            let state = dlg.getState<LS>();
            propNames.forEach(propName => {
                S.props.deleteProp(state.node, propName);
            });
            dlg.mergeState<LS>(state);
        }
    }

    deletePropertiesButtonClick = async (dlg: EditNodeDlg): Promise<void> => {
        let confirmDlg: ConfirmDlg = new ConfirmDlg("Delete the selected properties?", "Confirm Delete",
            null, null, dlg.appState);
        await confirmDlg.open();
        if (confirmDlg.yes) {
            this.deleteSelectedProperties(dlg);
        }
    }

    deleteSelectedProperties = (dlg: EditNodeDlg): void => {
        let keys: string[] = [];
        dlg.getState<LS>().selectedProps.forEach(prop => keys.push(prop));
        this.deleteProperties(dlg, keys);
    }

    setEncryption = (dlg: EditNodeDlg, encrypt: boolean): void => {
        let state = dlg.getState<LS>();
        if (dlg.pendingEncryptionChange) return;

        (async () => {
            let encrypted: boolean = S.props.isEncrypted(state.node);

            if (encrypt && S.props.isPublic(state.node)) {
                S.util.showMessage("Cannot encrypt a node that is shared to public. Remove public share first.", "Warning");
                return;
            }

            /* only if the encryption setting changed do we need to do anything here */
            if (encrypted !== encrypt) {
                dlg.pendingEncryptionChange = true;
                try {
                    /* If we're turning off encryption for the node */
                    if (!encrypt) {
                        /* Take what's in the editor and put
                        that into this.node.content, because it's the correct and only place the correct updated text is guaranteed to be
                        in the case where the user made some changes before disabling encryption. */
                        state.node.content = dlg.contentEditor.getValue();
                        S.props.setNodePropVal(J.NodeProp.ENC_KEY, state.node, null);
                    }
                    /* Else need to ensure node is encrypted */
                    else {
                        // if we need to encrypt and the content is not currently encrypted.
                        if (!state.node.content?.startsWith(J.Constant.ENC_TAG)) {
                            let content = dlg.contentEditor.getValue();

                            let skdp: SymKeyDataPackage = await S.encryption.encryptSharableString(null, content);
                            state.node.content = J.Constant.ENC_TAG + skdp.cipherText;

                            /* Set ENC_KEY to be the encrypted key, which when decrypted can be used to decrypt
                            the content of the node. This ENC_KEY was encrypted with the public key of the owner of this node,
                            and so can only be decrypted with their private key. */
                            S.props.setNodePropVal(J.NodeProp.ENC_KEY, state.node, skdp.cipherKey);
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

    deleteUpload = async (dlg: EditNodeDlg): Promise<void> => {
        let state = dlg.getState<LS>();

        /* Note: This doesn't resolve until either user clicks no on confirmation dialog or else has clicked yes and the delete
        call has fully completed. */
        let deleted: boolean = await S.attachment.deleteAttachment(state.node, dlg.appState);

        if (deleted) {
            S.attachment.removeBinaryProperties(state.node);
            this.initPropStates(dlg, state.node, true);
            dlg.mergeState<LS>({ node: state.node });

            if (dlg.mode === DialogMode.EMBED) {
                dispatch("Action_editNodeUpdated", (s: AppState): AppState => {
                    s.editNode = state.node;
                    return s;
                });
            }

            dlg.binaryDirty = true;
        }
    }

    /* Queries the server for the purpose of just loading the binary properties into node, and leaving everything else intact */
    refreshBinaryPropsFromServer = async (dlg: EditNodeDlg, node: J.NodeInfo): Promise<void> => {
        let res: J.RenderNodeResponse = await S.util.ajax<J.RenderNodeRequest, J.RenderNodeResponse>("renderNode", {
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

        if (!res.node) return;
        if (res.node?.properties) {
            S.props.transferBinaryProps(res.node, node);

            if (res.node) {
                S.nodeUtil.updateNodeMap(res.node, dlg.appState);
            }
        }
    }

    initPropState = (dlg: EditNodeDlg, node: J.NodeInfo, typeHandler: TypeHandlerIntf, propEntry: J.PropertyInfo, allowCheckbox: boolean): void => {
        let allowEditAllProps: boolean = dlg.appState.isAdminUser;
        let isReadOnly = S.render.isReadOnlyProperty(propEntry.name);
        let propVal = propEntry.value;
        let propValStr = propVal || "";
        // console.log("making single prop editor: prop[" + propEntry.property.name + "] val[" + propEntry.property.value
        //     + "] fieldId=" + propEntry.id);

        let propState: ValidatedState<any> = dlg.propStates.get(propEntry.name);
        if (!propState) {
            propState = new ValidatedState<any>();
            dlg.propStates.set(propEntry.name, propState);
        }

        if (!allowEditAllProps && isReadOnly) {
            propState.setValue(propValStr);
        }
        else {
            let val = S.props.getNodePropVal(propEntry.name, node);
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

    speechRecognition = (dlg: EditNodeDlg): void => {
        S.speech.setCallback((transcript: string) => {
            if (dlg.contentEditor && transcript) {
                // Capitalize and put period at end. This may be annoying in the long run but for now i "think"
                // I will like it? Time will tell.
                if (transcript.trim().length > 0) {
                    transcript = transcript.charAt(0).toUpperCase() + transcript.slice(1);
                    dlg.contentEditor.insertTextAtCursor(transcript + ". ");
                }
                else {
                    dlg.contentEditor.insertTextAtCursor(transcript);
                }
            }
        });

        S.speech.toggleActive();
        dlg.mergeState<LS>({ speechActive: S.speech.speechActive });

        setTimeout(() => {
            if (dlg.contentEditor) {
                dlg.contentEditor.focus();
            }
        }, 250);
    }

    initStates = (dlg: EditNodeDlg): void => {
        let state = dlg.getState<LS>();

        /* Init main content text on node */
        let value = state.node.content || "";
        if (!value.startsWith(J.Constant.ENC_TAG)) {
            dlg.contentEditorState.setValue(value);
        }
        else {
            dlg.contentEditorState.setValue("");
        }

        /* Initialize node name state */
        dlg.nameState.setValue(state.node.name);
        this.initPropStates(dlg, state.node, false);
    }

        /* Initializes the propStates for every property in 'node', and optionally if 'onlyBinaries==true' then we process ONLY
    the properties on node that are in 'S.props.allBinaryProps' list, which is how we have to update the propStates after
    an upload has been added or removed. */
    initPropStates = (dlg: EditNodeDlg, node: J.NodeInfo, onlyBinaries: boolean): any => {
        let typeHandler: TypeHandlerIntf = S.plugin.getTypeHandler(node.type);
        let customProps: string[] = null;
        if (typeHandler) {
            customProps = typeHandler.getCustomProperties();
            typeHandler.ensureDefaultProperties(node);
        }

        /* If we're updating binaries from the node properties, we need to wipe all the existing ones first to account for
        props that need to be removed */
        if (onlyBinaries) {
            S.props.allBinaryProps.forEach(s => {
                if (dlg.propStates.get(s)) {
                    dlg.propStates.delete(s);
                }
            });
        }

        if (node.properties) {
            node.properties.forEach((prop: J.PropertyInfo) => {
                // console.log("prop: " + S.util.prettyPrint(prop));

                // if onlyBinaries and this is NOT a binary prop then skip it.
                if (onlyBinaries) {
                    if (S.props.allBinaryProps.has(prop.name)) {
                        this.initPropState(dlg, node, typeHandler, prop, false);
                    }
                    return;
                }

                if (!dlg.allowEditAllProps && !S.render.allowPropertyEdit(node, prop.name, dlg.appState)) {
                    // ("Hiding property: " + prop.name);
                    return;
                }

                if (dlg.allowEditAllProps || (
                    !S.render.isReadOnlyProperty(prop.name) || S.edit.showReadOnlyProperties)) {

                    if (!dlg.isGuiControlBasedProp(prop)) {
                        let allowSelection = !customProps || !customProps.find(p => p === prop.name);
                        this.initPropState(dlg, node, typeHandler, prop, allowSelection);
                    }
                }
            });
        }
    }

    insertTime = (dlg: EditNodeDlg): void => {
        if (dlg.contentEditor) {
            dlg.contentEditor.insertTextAtCursor("[" + S.util.formatDate(new Date()) + "]");
        }
    }

    insertMention = async (dlg: EditNodeDlg): Promise<void> => {
        if (dlg.contentEditor) {
            let friendDlg: FriendsDlg = new FriendsDlg(null, dlg.appState, true);
            await friendDlg.open();
            if (friendDlg.getState().selectedName) {
                dlg.contentEditor.insertTextAtCursor(" @" + friendDlg.getState().selectedName + " ");
            }
        }
    }

    insertEmoji = async (dlg: EditNodeDlg): Promise<void> => {
        if (dlg.contentEditor) {
            let emojiDlg: EmojiPickerDlg = new EmojiPickerDlg(dlg.appState);
            await emojiDlg.open();
            if (emojiDlg.getState().selectedEmoji) {
                dlg.contentEditor.insertTextAtCursor(emojiDlg.getState().selectedEmoji);
            }
        }
    }

    cancelEdit = (dlg: EditNodeDlg): void => {
        dlg.close();

        // rollback properties.
        dlg.getState<LS>().node.properties = dlg.initialProps;

        if (dlg.binaryDirty) {
            S.quanta.refresh(dlg.appState);
        }
    }
}
