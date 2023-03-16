import { dispatch, getAs } from "../AppContext";
import { Comp, ScrollPos } from "../comp/base/Comp";
import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Checkbox } from "../comp/core/Checkbox";
import { Clearfix } from "../comp/core/Clearfix";
import { CollapsiblePanel } from "../comp/core/CollapsiblePanel";
import { DateTimeField } from "../comp/core/DateTimeField";
import { Div } from "../comp/core/Div";
import { EditAttachmentsPanel } from "../comp/core/EditAttachmentsPanel";
import { Icon } from "../comp/core/Icon";
import { IconButton } from "../comp/core/IconButton";
import { Label } from "../comp/core/Label";
import { Selection } from "../comp/core/Selection";
import { Span } from "../comp/core/Span";
import { TextArea } from "../comp/core/TextArea";
import { TextContent } from "../comp/core/TextContent";
import { TextField } from "../comp/core/TextField";
import { Constants as C } from "../Constants";
import { DialogBase, DialogMode } from "../DialogBase";
import * as I from "../Interfaces";
import { EditorOptions } from "../Interfaces";
import { NodeActionType, TypeIntf } from "../intf/TypeIntf";
import * as J from "../JavaIntf";
import { PropValueHolder } from "../PropValueHolder";
import { S } from "../Singletons";
import { Validator } from "../Validator";
import { PickNodeTypeDlg } from "./PickNodeTypeDlg";
import { EditNodeDlgUtil } from "./EditNodeDlgUtil";
import { LS as SelectTagsDlgLS, SelectTagsDlg } from "./SelectTagsDlg";

export interface LS {
    selectedProps?: Set<string>;
    selectedAttachments?: Set<string>;
    toIpfs?: boolean;
    speechActive?: boolean;
    signCheckboxVal?: boolean;
    encryptCheckboxVal?: boolean;
}

/**
 * Node Editor Dialog
 */
export class EditNodeDlg extends DialogBase {
    static autoSaveTimer: any = null;
    static currentInst: EditNodeDlg = null;
    static pendingUploadFile: File = null;
    public utl: EditNodeDlgUtil = new EditNodeDlgUtil();
    static embedInstance: EditNodeDlg;
    public contentEditor: I.TextEditorIntf;
    contentEditorState: Validator = new Validator();
    decryptFailed: boolean = false;
    nameState: Validator = new Validator();
    tagsState: Validator = new Validator();
    newTagState: Validator = new Validator();

    // holds a map of states by property names.
    propStates: Map<string, Validator> = new Map<string, Validator>();

    // Holds all the filenames for attachments
    attFileNames: Map<string, Validator> = new Map<string, Validator>();

    pendingEncryptionChange: boolean = false;

    // if user uploads or deletes an upload we set this, to force refresh when dialog closes even if they don't click save.
    binaryDirty: boolean = false;

    /* Since some of our property editing (the Selection components) modify properties 'in-place' in the node we have
    this initialProps clone so we can 'rollback' properties if use clicks cancel */
    initialProps: J.PropertyInfo[];

    allowEditAllProps: boolean = false;
    contentScrollPos = new ScrollPos();
    tagTextField: TextField;

    constructor(encrypt: boolean, private showJumpButton: boolean, mode: DialogMode) {
        super("[none]", (mode === DialogMode.EMBED ? "app-embed-content" : "app-modal-content") + " " + C.TAB_MAIN, false, mode);
        const ast = getAs();

        // need a deterministic id here, that can be found across renders, for scrolling.
        this.setId("EditNodeDlg_" + ast.editNode.id);
        let signCheckboxVal = false;
        let encryptCheckboxVal = false;
        if (S.crypto.avail) {
            // set checkbox to always on if this is admin user, otherwise set based on if it's already signed or not
            signCheckboxVal = !ast.unknownPubSigKey && ast.isAdminUser ? true : !!S.props.getPropStr(J.NodeProp.CRYPTO_SIG, ast.editNode);
            encryptCheckboxVal = !ast.unknownPubEncKey && ast.editNode?.content?.indexOf(J.Constant.ENC_TAG) === 0;
        }

        // we have this inst just so we can let the autoSaveTimer be static and always reference the latest one.
        EditNodeDlg.currentInst = this;

        if (mode === DialogMode.EMBED) {
            if (EditNodeDlg.embedInstance) {
                /* we get here if user starts editing another node and abandons the one currently being edited.
                 for now we just let this happen, but we could have asked the user if they MEANT to do that.
                 */
            }
            EditNodeDlg.embedInstance = this;
        }

        this.mergeState<LS>({
            // selected props is used as a set of all 'selected' (via checkbox) property names
            selectedProps: new Set<string>(),
            selectedAttachments: new Set<string>(),
            signCheckboxVal,
            encryptCheckboxVal
        });

        this.allowEditAllProps = getAs().isAdminUser;
        this.utl.initStates(this);
        this.initialProps = S.util.arrayClone(ast.editNode.properties);

        /* This 'encrypt' will trigger this node to be encrypted whenever we're replying to
        an encrypted node. (i.e. the parent of this node is encrypted) */
        if (encrypt && !ast.unknownPubEncKey) {
            setTimeout(() => {
                this.utl.setEncryption(this, true);
            }, 500);
        }

        if (EditNodeDlg.pendingUploadFile) {
            setTimeout(async () => {
                // DO NOT DELETE. Leave this here as an FYI.
                // await this.utl.upload(EditNodeDlg.pendingUploadFile, this);

                this.immediateUploadFiles([EditNodeDlg.pendingUploadFile]);
                EditNodeDlg.pendingUploadFile = null;
            }, 250);
        }

        // create one timer one time (singleton pattern)
        if (!EditNodeDlg.autoSaveTimer) {
            // save editor state every few seconds so user can recover editing if anything goes wrong.
            // This should be CLEARED upon successful saves only, and have this static var set back to null
            EditNodeDlg.autoSaveTimer = setInterval(async () => {
                const ast = getAs();
                if (!ast || !ast.editNode) return;
                await S.localDB.setVal(C.STORE_EDITOR_DATA, {
                    nodeId: ast.editNode.id,
                    content: EditNodeDlg.currentInst.contentEditorState.getValue()
                });
            }, 5000);
        }
    }

    immediateUploadFiles = async (files: File[]) => {
        const ast = getAs();
        await S.domUtil.uploadFilesToNode(files, ast.editNode.id, false);
        await this.utl.refreshFromServer(ast.editNode);
        S.edit.updateNode(ast.editNode);
        this.binaryDirty = true;
    }

    resetAutoSaver = async () => {
        if (EditNodeDlg.autoSaveTimer) {
            clearInterval(EditNodeDlg.autoSaveTimer);
            EditNodeDlg.autoSaveTimer = null;
        }
        S.localDB.setVal(C.STORE_EDITOR_DATA, null);
    }

    createLayoutSelection = (): Selection => {
        const ast = getAs();
        const selection: Selection = new Selection(null, "Subnode Layout", [
            { key: "v", val: "1 column" },
            { key: "c2", val: "2 columns" },
            { key: "c3", val: "3 columns" },
            { key: "c4", val: "4 columns" },
            { key: "c5", val: "5 columns" },
            { key: "c6", val: "6 columns" }
        ], null, "layoutSelection", new PropValueHolder(ast.editNode, J.NodeProp.LAYOUT, "v"));
        return selection;
    }

    createPrioritySelection = (): Selection => {
        const ast = getAs();
        return new Selection(null, "Priority", [
            { key: "0", val: "none" },
            { key: "1", val: "Top (P1)" },
            { key: "2", val: "High (P2)" },
            { key: "3", val: "Medium (P3)" },
            { key: "4", val: "Low (P4)" },
            { key: "5", val: "Backlog (P5)" }
        ], null, "col-3", new PropValueHolder(ast.editNode, J.NodeProp.PRIORITY, "0"));
    }

    getTitleIconComp(): CompIntf {
        const ast = getAs();
        let span: Span = null;

        const type = S.plugin.getType(ast.editNode.type);
        if (type) {
            const iconClass = type.getIconClass();
            if (iconClass) {
                span = span || new Span();
                span.addChild(new Icon({
                    title: `Node Type: ${type.getName()}`,
                    className: iconClass + " dlgIcon clickable",
                    onClick: this.openChangeNodeTypeDlg
                }));
            }

            if (S.props.getPropStr(J.NodeProp.DATE, ast.editNode)) {
                span = span || new Span();
                span.addChild(new Icon({
                    title: "Node has a 'Date' property.",
                    className: "fa fa-calendar fa-lg dlgIcon"
                }));
            }
            span.addChild(new Span(type.getName(), {
                className: "marginRight clickable",
                onClick: this.openChangeNodeTypeDlg
            }));
        }
        else {
            span = span || new Span();
            span.addChild(new Icon({
                title: "Node Type: Unknown",
                className: "fa fa-question-circle fa-lg dlgIcon clickable",
                onClick: this.openChangeNodeTypeDlg
            }));
            span.addChild(new Span("Unknown Type", { className: "marginRight" }));
        }

        if (this.showJumpButton) {
            span = span || new Span();
            span.addChild(new Icon({
                title: "Jump to Node",
                className: "fa fa-arrow-right fa-lg jumpButton",
                onClick: () => {
                    this.utl.cancelEdit(this);
                    S.nav.closeFullScreenViewer();
                    S.view.jumpToId(ast.editNode.id);
                }
            }));
        }
        return span;
    }

    getExtraTitleBarComps(): CompIntf[] {
        let comps: CompIntf[] = null;

        if (this.getState<LS>().signCheckboxVal) {
            comps = comps || [];
            comps.push(new Icon({
                title: "Crypto Signature Verified",
                className: "fa fa-certificate fa-lg bigSignatureIcon iconMarginLeft"
            }));
            if (getAs().isAdminUser) {
                comps.push(new Span("<-Admin"));
            }
        }

        if (this.getState<LS>().encryptCheckboxVal) {
            comps = comps || [];
            comps.push(new Icon({
                title: "Node is Encrypted",
                className: "fa fa-lock fa-lg bigLockIcon iconMarginLeft"
            }));
        }
        return comps;
    }

    renderDlg(): CompIntf[] {
        const state = this.getState<LS>();
        const ast = getAs();
        const hasAttachment: boolean = S.props.hasBinary(ast.editNode);

        const type = S.plugin.getType(ast.editNode.type);
        let customProps: string[] = null;
        let editorOpts: EditorOptions = {};
        let autoExpandProps = false;
        if (type) {
            editorOpts = type.getEditorOptions();
            customProps = type.getCustomProperties();
            type.ensureDefaultProperties(ast.editNode);
            autoExpandProps = type.getAutoExpandProps();
        }

        const allowContentEdit: boolean = type ? type.getAllowContentEdit() : true;
        let propEditFieldContainer: Div = null;
        const children = [
            S.speech.speechActive ? new TextContent("Speech-to-Text active. Mic listening...", "alert alert-primary") : null,
            new Div(null, null, [
                new Div(null, {
                }, [
                    propEditFieldContainer = new Div("", {
                    })
                ])
            ])
        ];

        const flowPanel: Div = new Div(null, { className: "marginTop d-flex flex-row flex-wrap" });

        if (ast.editNode.hasChildren) {
            flowPanel.addChild(this.createLayoutSelection());
        }

        flowPanel.addChildren(this.makeCheckboxesRow(editorOpts));

        // This is the table that contains the custom editable properties inside the collapsable panel at the bottom.
        let propsTable: Comp = null;
        let mainPropsTable: Comp = null;

        const flexPropsEditPanel = !customProps;
        let propsParent: Div = null;
        console.log("customProps: " + customProps);
        // if customProps exists then the props are all added into 'editPropsTable' instead of the collapsible panel.
        // todo-0: does this make sense in the new schemaOrg system ? having this if condition and two different ways?
        if (!customProps) {
            propsParent = new Div(null, {
                className: "edit-props-table marginBottom" + (flexPropsEditPanel ? " flexPropsEditPanel" : "")
            });

            propsTable = propsParent;
            // This is the container that holds the custom properties if provided, or else the name+content textarea at the top of not
            mainPropsTable = new Div(null, {
                className: "marginBottom"
            });
        }
        else {
            propsParent = new Div(null, {
                className: "edit-props-table marginBottom " + (flexPropsEditPanel ? "flexPropsEditPanel" : "")
            });
            mainPropsTable = propsParent;
        }

        const isWordWrap = !S.props.getPropStr(J.NodeProp.NOWRAP, ast.editNode);

        let nodeNameTextField: TextField = null;
        if (editorOpts.nodeName) {
            nodeNameTextField = new TextField({ label: "Node Name", outterClass: "col-9", val: this.nameState });
        }

        let propsVisible: boolean = false;
        if (allowContentEdit) {
            const rows = editorOpts.contentEditorRows || (getAs().mobileMode ? "8" : "10");

            mainPropsTable.addChild(this.makeContentEditor(rows));
            this.contentEditor.setWordWrap(isWordWrap);
            propsVisible = true;
        }

        let propsHeaderBar: Div = null;
        if (this.buildPropsEditPanel({ propsParent, state, type, customProps, flexPropsEditPanel })) {
            propsVisible = true;

            if (type.getAllowPropertyAdd()) {
                const state = this.getState<LS>();
                propsHeaderBar = new Div(null, { className: "float-end" }, [
                    new Icon({
                        className: "fa fa-plus-circle fa-lg clickable marginRight tinyMarginBottom",
                        onClick: async () => {
                            dispatch("setPropsPanelExpanded", s => {
                                s.propsPanelExpanded = true;
                            });
                            await this.utl.addProperty(this);
                        },
                        title: "Add property"
                    }),
                    state.selectedProps.size > 0 ? new Icon({
                        className: "fa fa-trash fa-lg clickable marginRight tinyMarginBottom",
                        onClick: () => this.utl.deletePropertiesButtonClick(this),
                        title: "Delete property"
                    }) : null
                ]);
            }
        }

        if (!propsVisible) {
            mainPropsTable = null;
        }

        const binarySection = hasAttachment ? new EditAttachmentsPanel(ast.editNode, this) : null;
        const shareComps: Comp[] = S.nodeUtil.getSharingNames(ast.editNode, this);
        const isPublic = S.props.isPublic(ast.editNode);

        let sharingDiv = null;
        let sharingDivClearFix = null;
        if (shareComps) {
            const unpublished = S.props.getPropStr(J.NodeProp.UNPUBLISHED, ast.editNode);
            sharingDiv = new Div(null, {
                className: "float-end clickable marginBottom"
            }, [
                new Span("Shared to: ", {
                    title: "Edit Node Sharing",
                    onClick: () => this.utl.share(this)
                }),
                ...shareComps,
                !isPublic ? new Button("Make Public", () => { this.makePublic(true); }, { className: "marginLeft" }) : null,
                unpublished ? new Icon({
                    className: "fa fa-eye-slash fa-lg sharingIcon marginLeft microMarginRight",
                    title: "Node is Unpublished\n\nWill not appear in feed"
                }) : null
            ]);
            sharingDivClearFix = new Clearfix();
        }

        // if this props table would be empty don't display it (set to null)
        if (propsTable && !propsTable.hasChildren()) {
            propsTable = null;
        }

        let propsCollapsePanel: CollapsiblePanel = null;
        if (propsTable) {
            propsCollapsePanel = new CollapsiblePanel("Properties", "Hide Properties", null, [
                new Clearfix(),
                propsHeaderBar,
                propsTable
            ], false,
                (expanded: boolean) => {
                    if (autoExpandProps) return;
                    dispatch("setPropsPanelExpanded", s => {
                        s.propsPanelExpanded = expanded;
                    });
                }, getAs().propsPanelExpanded || autoExpandProps, "", "propsPanelExpanded", "propsPanelCollapsed float-end", "div");
        }

        const tagsEditRow = editorOpts.tags ? new Div(null, { className: "editorTagsSection" }, [
            this.tagsState.getValue() ? S.render.renderTagsStrDiv(this.tagsState.getValue(), this.removeTag, this.selectTags) : null,
            this.utl.renderLinksEditing()
        ]) : null;

        let editorSubPanel: Comp = null;
        if (type) {
            editorSubPanel = type.renderEditorSubPanel(ast.editNode);
        }

        const collapsePanel = !customProps ? new CollapsiblePanel("Advanced", "Hide Advanced", null, [
            tagsEditRow,
            new Div(null, { className: "row align-items-end" }, [
                editorOpts.nodeName ? nodeNameTextField : null,
                editorOpts.priority ? this.createPrioritySelection() : null
            ]),
            flowPanel
        ], false,
            (expanded: boolean) => {
                dispatch("setMorePanelExpanded", s => {
                    s.morePanelExpanded = expanded;
                });
            }, getAs().morePanelExpanded, "marginRight btn-primary", "", "", "div") : null;

        const morePanel = new Div(null, { className: "marginBottom" }, [
            collapsePanel
        ]);

        const propsPanel = new Div(null, null, [
            propsCollapsePanel
        ]);

        this.attribs[C.NODE_ID_ATTR] = ast.editNode.id;
        // Allows user to drag-n-drop files onto editor to upload
        S.domUtil.setDropHandler(this.attribs, async (evt: DragEvent) => {
            const files: File[] = [...evt.dataTransfer.files];
            let hasEmail = false;
            files.forEach((file: File) => {
                const name = file.name;
                const lcName = name.toLowerCase();
                if (lcName.endsWith(".eml")) {
                    hasEmail = true;
                }
            });

            if (hasEmail) {
                const ret: J.UploadResponse = await S.domUtil.parseFiles(files);
                let val = this.contentEditorState.getValue();
                ret.payloads?.forEach((payload: any) => {
                    val += "\n" + payload;
                });
                this.contentEditorState.setValue(val);
            }
            else {
                this.immediateUploadFiles(files);
            }
        });

        // -------------------------
        // DO NOT DELETE:
        // This kind of code pattern *might* be needed at some point again.
        // S.domUtil.setDropHandler(this.attribs, (evt: DragEvent) => {
        //     for (const item of evt.dataTransfer.items) {
        //         if (item.kind === "file") {
        //             const file = item.getAsFile();
        //             this.utl.upload(file, this);
        //             return;
        //         }
        //     }
        // });
        // -------------------------

        propEditFieldContainer.setChildren([editorSubPanel, mainPropsTable, sharingDiv, sharingDivClearFix, binarySection,
            propsPanel, morePanel, new Clearfix(), this.renderButtons()]);

        return children;
    }

    makePublic = async (allowAppends: boolean) => {
        const ast = getAs();
        if (this.getState<LS>().encryptCheckboxVal) {
            S.util.showMessage("This node is encrypted, and therefore cannot be made public.", "Warning");
            return;
        }

        await S.rpcUtil.rpc<J.AddPrivilegeRequest, J.AddPrivilegeResponse>("addPrivilege", {
            nodeId: ast.editNode.id,
            principals: [J.PrincipalName.PUBLIC],
            privileges: allowAppends ? [J.PrivilegeType.READ, J.PrivilegeType.WRITE] : [J.PrivilegeType.READ]
        });

        const res = await S.rpcUtil.rpc<J.GetNodePrivilegesRequest, J.GetNodePrivilegesResponse>("getNodePrivileges", {
            nodeId: ast.editNode.id
        });
        ast.editNode.ac = res.aclEntries;

        S.edit.updateNode(ast.editNode);
    }

    /* returns true if props table is not empty. This method has an "options object" arguments
    pattern I'm trying out.
    todo-1: This pattern of argument passing by name, not position is better for larg argument lists. */
    buildPropsEditPanel = (_: { propsParent: CompIntf, state: LS, type: TypeIntf, customProps: string[], flexPropsEditPanel: boolean }): boolean => {
        let ret = false;
        const ast = getAs();
        if (ast.editNode.properties) {
            const durationProp = S.props.getProp(J.NodeProp.DURATION, ast.editNode);

            // This loop creates all the editor input fields for all the properties
            ast.editNode.properties.forEach(prop => {
                // console.log("prop=" + S.util.prettyPrint(prop));
                if (prop.name === durationProp?.name) return;

                if (!this.allowEditAllProps && !S.render.allowPropertyEdit(ast.editNode, prop.name)) {
                    // console.log("Hiding property: " + prop.name);
                    return;
                }

                if (this.allowEditAllProps || (
                    !S.render.isReadOnlyProperty(prop.name) || S.edit.showReadOnlyProperties)) {

                    if (!S.props.isGuiControlBasedProp(prop)) {
                        const allowSelection = !_.customProps || _.type?.hasSelectableProp(prop.name);
                        const tableRow = this.makePropEditField(_.type, prop, durationProp, allowSelection, _.type ? _.type.getEditorRowsForProp(prop.name) : 1, _.flexPropsEditPanel);
                        _.propsParent.addChild(tableRow);
                        ret = true;
                    }
                }
            });
        }

        _.propsParent.ordinalSortChildren();
        return ret;
    }

    removeTag = (removeTag: string) => {
        let val = this.tagsState.getValue();
        val = val.trim();
        const tags: string[] = val.split(" ");
        let newTags = "";

        tags.forEach(tag => {
            if (removeTag !== tag) {
                if (newTags) newTags += " ";
                newTags += tag;
            }
        });

        this.tagsState.setValue(newTags);
        this.mergeState({});
    }

    sortTags = (tagStr: string) => {
        if (!tagStr) return tagStr;
        const tags: string[] = tagStr.split(" ");
        tags.sort();
        return tags.join(" ");
    }

    addTagsToTextField = (dlg: SelectTagsDlg) => {
        let val = this.tagsState.getValue();
        val = val.trim();
        const tags: string[] = val.split(" ");
        dlg.getState<SelectTagsDlgLS>().selectedTags.forEach(tag => {
            if (!tag.startsWith("#")) {
                tag = "#" + tag;
            }
            if (!tags.includes(tag)) {
                if (val) val += " ";
                val += tag;
            }
        });
        this.tagsState.setValue(this.sortTags(val));
        this.mergeState({});
    }

    makeCheckboxesRow = (advancedOpts: EditorOptions): Comp[] => {
        const ast = getAs();
        const encryptCheckBox = advancedOpts.encrypt ? new Checkbox("Encrypt", null, {
            setValue: (checked: boolean) => {
                if (S.crypto.encKeyOk()) {
                    this.utl.setEncryption(this, checked);
                }
            },
            getValue: (): boolean => this.getState<LS>().encryptCheckboxVal
        }) : null;

        const signCheckBox = advancedOpts.sign && S.crypto.avail ? new Checkbox("Sign", null, {
            setValue: (checked: boolean) => {
                if (checked && S.crypto.sigKeyOk()) {
                    this.mergeState({ signCheckboxVal: checked });
                }
            },
            getValue: (): boolean => this.getState<LS>().signCheckboxVal
        }) : null;

        const wordWrapCheckbox = advancedOpts.wordWrap ? new Checkbox("Word Wrap", null, {
            setValue: (checked: boolean) => {
                // this is counter-intuitive that we invert here because 'NOWRAP' is a negation of "wrap"
                S.props.setPropVal(J.NodeProp.NOWRAP, ast.editNode, checked ? null : "1");
                if (this.contentEditor) {
                    this.contentEditor.setWordWrap(checked);
                }
            },
            getValue: (): boolean => S.props.getPropStr(J.NodeProp.NOWRAP, ast.editNode) !== "1"
        }) : null;

        const inlineChildrenCheckbox = advancedOpts.inlineChildren && ast.editNode.hasChildren ? new Checkbox("Inline Subnodes", null,
            this.makeCheckboxPropValueHandler(J.NodeProp.INLINE_CHILDREN)) : null;

        return [inlineChildrenCheckbox, wordWrapCheckbox, encryptCheckBox, signCheckBox];
    }

    makeCheckboxPropValueHandler(propName: string): I.ValueIntf {
        const ast = getAs();
        return {
            setValue: (checked: boolean) => S.props.setPropVal(propName, ast.editNode, checked ? "1" : null),
            getValue: (): boolean => S.props.getPropStr(propName, ast.editNode) === "1"
        };
    }

    save = async () => {
        // it's important to call saveNode before close, because close destroys some of our state, what we need
        // to complete the updating and page refresh.
        const savedOk: boolean = await this.utl.saveNode(this);
        if (savedOk) {
            this.close();
        }
    }

    renderButtons(): CompIntf {
        const ast = getAs();
        // let hasAttachment: boolean = S.props.hasBinary(state.node);

        const type = S.plugin.getType(ast.editNode.type);
        if (type) {
            type.ensureDefaultProperties(ast.editNode);
        }

        // let allowContentEdit: boolean = type ? type.getAllowContentEdit() : true;
        // //regardless of value, if this property is present we consider the type locked
        // let typeLocked = !!S.props.getNodePropVal(J.NodeProp.TYPE_LOCK, state.node);

        const allowUpload: boolean = type ? (getAs().isAdminUser || type.allowAction(NodeActionType.upload, ast.editNode)) : true;
        const allowShare: boolean = type ? (getAs().isAdminUser || type.allowAction(NodeActionType.share, ast.editNode)) : true;

        // let typeLocked = !!S.props.getNodePropVal(J.NodeProp.TYPE_LOCK, state.node);
        const datePropExists = S.props.getProp(J.NodeProp.DATE, ast.editNode);

        const numPropsShowing = this.utl.countPropsShowing(this);
        const advancedButtons: boolean = !!this.contentEditor;
        const allowPropAdd: boolean = type ? type.getAllowPropertyAdd() : true;

        return new ButtonBar([
            new Button("Save", this.save, { title: "Save this node and close editor." }, "attentionButton"),
            new Button("Cancel", () => this.utl.cancelEdit(this), null, "btn-secondary float-end"),

            allowUpload ? new IconButton("fa-upload", "File", {
                onClick: () => this.utl.upload(null, this),
                title: "Upload file attachment"
            }) : null,

            allowUpload && S.util.clipboardReadable() ? new IconButton("fa-paperclip", "Clip", {
                onClick: () => this.utl.uploadFromClipboard(this),
                title: "Upload from Clipboard"
            }) : null,

            allowShare ? new IconButton("fa-share-alt", "Share", {
                onClick: () => this.utl.share(this),
                title: "Share Node"
            }) : null,

            allowPropAdd && numPropsShowing === 0 ? new IconButton("fa-plus-circle", null, {
                onClick: async () => {
                    dispatch("setPropsPanelExpanded", s => {
                        s.propsPanelExpanded = true;
                    });
                    await this.utl.addProperty(this);
                },
                title: "Add Property"
            }) : null,

            !this.tagsState.getValue() ? new IconButton("fa-tag fa-lg", "", {
                onClick: this.selectTags,
                title: "Select Hashtags"
            }) : null,

            // show delete button only if we're in a fullscreen viewer (like Calendar view)
            S.util.fullscreenViewerActive()
                ? new Button("Delete", () => {
                    S.edit.deleteSelNodes(null, ast.editNode.id);
                    this.close();
                }) : null,

            advancedButtons && !datePropExists ? new IconButton("fa-calendar", null, {
                title: "Add 'date' property to node\n\nMakes node a Calendar Entry)",
                onClick: () => this.utl.addDateProperty(this)
            }) : null
        ]);
    }

    selectTags = async () => {
        const dlg = new SelectTagsDlg("edit", this.tagsState.getValue(), false);
        await dlg.open();
        this.addTagsToTextField(dlg);
    }

    super_closeByUser = this.closeByUser;
    closeByUser = () => {
        this.super_closeByUser();
        this.resetAutoSaver();
    }

    super_close = this.close;
    close = () => {
        this.super_close();
        S.speech.stopListening();

        EditNodeDlg.embedInstance = null;
        dispatch("endEditing", s => {
            s.editNode = null;
            s.editNodeOnTab = null;
            s.editNodeReplyToId = null;
            S.quanta.newNodeTargetId = null;
            S.quanta.newNodeTargetOffset = -1;
            s.editShowJumpButton = false;
            s.editEncrypt = false;
        });
    }

    openChangeNodeTypeDlg = async () => {
        const ast = getAs();
        const dlg = new PickNodeTypeDlg(ast.editNode.type);
        await dlg.open();
        if (dlg.chosenType) {
            ast.editNode.type = dlg.chosenType;
            S.edit.updateNode(ast.editNode);
        }
    }

    /* Creates the editing field for a single property 'propEntry' */
    makePropEditField = (type: TypeIntf, propEntry: J.PropertyInfo, durationPropEntry: J.PropertyInfo,
        allowCheckbox: boolean, rows: number, flexPropsEditPanel: boolean): Div => {
        const ast = getAs();

        // Warning: Don't put any margins on this row because the widths to allow widths that sum to
        // precisely 100% to work correctly. Adding a margin would make it wrap prematurely.
        const rowAttribs: any = { className: "marginBottom" };
        const propConfig: any = type.getPropConfig(propEntry.name);
        const ordinal: number = propConfig?.ord || 200; // 200 is just a high enough number to fall below numered ones
        const tableRow = new Div(null, rowAttribs);
        const allowEditAllProps: boolean = getAs().isAdminUser;
        const isReadOnly = S.render.isReadOnlyProperty(propEntry.name);
        const editItems: any[] = [];
        const label = propConfig?.label || (type ? type.getEditLabelForProp(propEntry.name) : propEntry.name);
        const propType = type.getType(propEntry.name);

        if (flexPropsEditPanel) {
            const w: number = propConfig?.width || 100;
            const widthStr = "" + w + "%";
            rowAttribs.style = { width: widthStr, maxWidth: widthStr };
        }

        // console.log("making single prop editor: prop[" + propEntry.name + "] val[" + propEntry.value + "]");

        let propState: Validator = this.propStates.get(propEntry.name);
        if (!propState) {
            propState = new Validator(propEntry.value);
            this.propStates.set(propEntry.name, propState);
        }

        // WARNING: propState.setValue() calls will have been done in initStates, and should NOT be set here, because this can run during render callstacks
        // which is not a valid time to be updating states

        // todo-0: actually this is wrong to just do a Textarea always when it's readonly. It might be a non-multiline item here
        // and be better with a Textfield based editor
        if (!allowEditAllProps && isReadOnly) {
            const textarea = new TextArea(label + " (read-only)", {
                readOnly: "readOnly",
                disabled: "disabled"
            }, propState, "marginRight");

            editItems.push(textarea);
        }
        else {
            this.addPropCheckboxOrLabel(allowCheckbox, label, propEntry, editItems);
            let valEditor: CompIntf = null;
            const multiLine = rows > 1;

            // We have the one special case that a property named 'date' is assumed to be a "Date" type always
            // DATE TYPE
            if (propType === "Date" || propEntry.name === J.NodeProp.DATE) {
                let durationState: Validator = null;
                if (durationPropEntry) {
                    durationState = this.propStates.get(durationPropEntry.name);
                    if (!durationState) {
                        durationState = new Validator(durationPropEntry.value);
                        this.propStates.set(durationPropEntry.name, durationState);
                    }
                }
                valEditor = new DateTimeField(propState, durationState);
            }
            // TEXT/TEXTAREA TYPE
            else if (propType === "Text") {
                if (multiLine) {
                    // todo-0: I think displayCell class is wrong here, based on current refactoring.
                    valEditor = new TextArea(null, {
                        rows: "" + rows,
                        id: "prop_" + ast.editNode.id
                    }, propState, "textarea-min-4 displayCell marginRight");
                }
                else {
                    valEditor = new TextField({
                        outterClass: "marginRight",
                        inputClass: S.props.getInputClassForType(propEntry.name),
                        val: propState
                    });
                }
            }
            // NUMBER TYPE
            else if (propType === "Number") {
                valEditor = new TextField({
                    outterClass: "marginRight",
                    inputClass: S.props.getInputClassForType(propEntry.name),
                    val: propState
                });
            }
            else {
                console.error("Unsupported type: " + type.getType(propEntry.name));
            }

            editItems.push(valEditor as any as Comp);
        }
        tableRow.setChildren(editItems);
        tableRow.ordinal = ordinal;
        return tableRow;
    }

    private addPropCheckboxOrLabel(allowCheckbox: boolean, label: string, propEntry: J.PropertyInfo, editItems: any[]) {
        if (allowCheckbox) {
            const checkbox = new Checkbox(label, null, {
                setValue: (checked: boolean) => {
                    const state = this.getState<LS>();
                    if (checked) {
                        state.selectedProps.add(propEntry.name);
                    }
                    else {
                        state.selectedProps.delete(propEntry.name);
                    }
                    this.mergeState<LS>({ selectedProps: state.selectedProps });
                },
                getValue: (): boolean => this.getState<LS>().selectedProps.has(propEntry.name)
            });
            editItems.push(checkbox);
        }
        else {
            editItems.push(new Label(label, { className: "marginTop" }));
        }
    }

    async initContent(): Promise<void> {
        const ast = getAs();
        const value = ast.editNode.content || "";
        const encrypted = value.startsWith(J.Constant.ENC_TAG);
        if (!encrypted) {
            this.contentEditorState.setValue(value);
            this.decryptFailed = false;
        }
        else {
            if (S.crypto.avail) {
                // console.log("decrypting: " + value);
                const cipherText = value.substring(J.Constant.ENC_TAG.length);
                const cipherKey = S.props.getCryptoKey(ast.editNode);
                if (cipherKey) {
                    const clearText: string = await S.crypto.decryptSharableString(null, { cipherKey, cipherText });

                    // Warning clearText can be "" (which is a 'falsy' value and a valid decrypted string!)
                    if (clearText === null) {
                        this.contentEditorState.setError("Decryption Failed");
                        this.decryptFail();
                    }
                    else {
                        // console.log("decrypted to:" + value);
                        this.contentEditorState.setValue(clearText);
                    }
                }
                else {
                    this.contentEditorState.setError("Decryption Failed. No Key available.");
                    this.decryptFail();
                }
            }
            else {
                this.contentEditorState.setError("Decryption Failed (Crypto not available)");
                this.decryptFail();
            }
        }
    }

    decryptFail = (): void => {
        this.decryptFailed = true;
        if (this.contentEditor) {
            this.contentEditor.setEnabled(false);
        }
    }

    makeContentEditor = (rows: string): Div => {
        const ast = getAs();
        const editItems: Comp[] = [];

        // if this is the first pass thru here (not a re-render) then allow focus() to get called
        const allowFocus = !this.contentEditor;
        // console.log("making field editor for val[" + value + "]");

        this.contentEditor = new TextArea(null, {
            id: C.ID_PREFIX_EDIT + ast.editNode.id,
            rows
        }, this.contentEditorState, "font-inherit", true, this.contentScrollPos);
        if (this.decryptFailed) {
            this.contentEditor.setEnabled(false);
        }

        const wrap: boolean = S.props.getPropStr(J.NodeProp.NOWRAP, ast.editNode) !== "1";
        this.contentEditor.setWordWrap(wrap);

        if (allowFocus) {
            this.contentEditor.focus();
        }

        editItems.push(new ButtonBar([
            new Icon({
                className: (S.speech.speechActive ? "fa fa-lg fa-microphone-slash editorIcon" : "fa fa-microphone editorIcon"),
                title: "Toggle on/off Speech Recognition to input text",
                onClick: () => this.utl.toggleRecognition(this)
            }),

            new Icon({
                className: "fa fa-lg fa-clock-o editorIcon",
                title: "Insert current time at cursor",
                onClick: () => this.utl.insertTime(this)
            }),

            new Icon({
                className: "fa fa-lg fa-smile-o editorIcon",
                title: "Insert emoji at cursor",
                onClick: () => this.utl.insertEmoji(this)
            }),

            new Icon({
                className: "fa fa-lg fa-user editorIcon",
                title: "Insert Username(s) at cursor",
                onClick: () => this.utl.insertUserNames(this)
            }),

            new Icon({
                className: "fa fa-lg fa-volume-up editorIcon",
                onMouseOver: () => { S.quanta.selectedForTts = window.getSelection().toString(); },
                onMouseOut: () => { S.quanta.selectedForTts = null; },
                onClick: () => this.utl.speakerClickInEditor(this),
                title: "Text-to-Speech: Editor Text or Selection"
            })
        ], "float-end microMarginBottom bigMarginRight"));
        editItems.push(this.contentEditor as any as Comp);

        return new Div(null, { className: "contentEditor" }, editItems);
    }

    // NOTE: Be careful renaming this method. It's referenced in an "as any" way in one place.
    addSharingToContentText = () => {
        const ast = getAs();
        if (ast.editNode.ac?.length > 0) {
            let content: string = this.contentEditorState.getValue();
            let newLine = false;
            let accum = 0;
            for (const ac of ast.editNode.ac) {
                if (ac.principalName !== J.PrincipalName.PUBLIC) {
                    const insertName = "@" + ac.principalName;
                    if (content.indexOf(insertName) === -1) {
                        if (!newLine) {
                            content += "\n\n";
                            newLine = true;
                        }
                        content += insertName + " ";

                        // new line afer every 7 names.
                        if (++accum >= 7) {
                            content += "\n";
                            accum = 0;
                        }
                    }
                }
            }
            this.contentEditorState.setValue(content.trim());
        }
    }

    // returns true if all the shares on the node ARE mentioned in the text.
    areAllSharesInContent = () => {
        const content: string = this.contentEditorState.getValue();
        const ast = getAs();
        if (ast.editNode.ac?.length > 0) {
            for (const ac of ast.editNode.ac) {
                if (ac.principalName !== J.PrincipalName.PUBLIC) {
                    const insertName = "@" + ac.principalName;
                    if (!content || content.indexOf(insertName) === -1) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
