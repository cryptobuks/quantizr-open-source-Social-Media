import { dispatch, getAs } from "../../AppContext";
import { Diva } from "../../comp/core/Diva";
import { Selection } from "../../comp/core/Selection";
import { ConfirmDlg } from "../../dlg/ConfirmDlg";
import { EditNodeDlg, LS as EditNodeDlgState } from "../../dlg/EditNodeDlg";
import { ValueIntf } from "../../Interfaces";
import * as J from "../../JavaIntf";
import { S } from "../../Singletons";
import { Validator, ValidatorRuleName } from "../../Validator";
import { NodeCompBinary } from "../node/NodeCompBinary";
import { ButtonBar } from "./ButtonBar";
import { Checkbox } from "./Checkbox";
import { Div } from "./Div";
import { Divc } from "./Divc";
import { FlexRowLayout } from "./FlexRowLayout";
import { Icon } from "./Icon";
import { IconButton } from "./IconButton";
import { TextField } from "./TextField";

export class EditAttachmentsPanel extends Div {

    constructor(private node: J.NodeInfo, private dlg: EditNodeDlg) {
        super(null, { className: "binaryEditorSection" });
    }

    override preRender(): boolean {
        if (!this.node.attachments) return null;
        this.setChildren([]);
        let isFirst = true;

        if (this.dlg.getState<EditNodeDlgState>().selectedAttachments?.size > 0) {
            this.addChild(new ButtonBar([
                new IconButton("fa-trash fa-lg", "", {
                    onClick: () => this.dlg.utl.deleteUploads(this.dlg),
                    title: "Delete selected Attachments"
                }, "delAttachmentButton")
            ], "float-end"));
        }

        S.props.getOrderedAtts(this.node).forEach(att => {
            this.addChild(this.makeAttPanel(att, isFirst));
            isFirst = false;
        });
        return true;
    }

    makeAttPanel = (att: J.Attachment, isFirst: boolean): Div => {
        const ast = getAs();
        if (!att) return null;
        const key = (att as any).key;
        const ipfsLink = att.il;
        const mime = att.m;

        let pinCheckbox = null;
        if (ipfsLink) {
            pinCheckbox = new Checkbox("Pin", { className: "ipfsPinnedCheckbox" }, {
                setValue: (checked: boolean) => { att.ir = checked ? null : "1" },
                getValue: (): boolean => att.ir ? false : true
            });
        }

        const attCheckbox = new Checkbox(null, null, {
            setValue: (checked: boolean) => {
                const state = this.dlg.getState<EditNodeDlgState>();
                if (checked) {
                    state.selectedAttachments.add((att as any).key);
                }
                else {
                    state.selectedAttachments.delete((att as any).key);
                }
                this.dlg.mergeState<EditNodeDlgState>({});
            },
            getValue: (): boolean => this.dlg.getState<EditNodeDlgState>().selectedAttachments.has((att as any).key)
        }, "delAttCheckbox");

        const imgSizeSelection = S.props.hasImage(ast.editNode, key)
            ? this.createImgSizeSelection("Width", "widthDropDown", //
                {
                    setValue: (val: string): void => {
                        const att: J.Attachment = S.props.getAttachment(key, ast.editNode);
                        if (att) {
                            att.c = val;
                            if (isFirst) {
                                this.askMakeAllSameSize(ast.editNode, val);
                            }
                            this.dlg.binaryDirty = true;
                        }
                    },
                    getValue: (): string => {
                        const att: J.Attachment = S.props.getAttachment(key, ast.editNode);
                        return att && att.c;
                    }
                }) : null;

        const imgPositionSelection = S.props.hasImage(ast.editNode, key)
            ? this.createImgPositionSelection("Position", "positionDropDown", //
                {
                    setValue: (val: string): void => {
                        const att: J.Attachment = S.props.getAttachment(key, ast.editNode);
                        if (att) {
                            att.p = val === "auto" ? null : val;
                            this.dlg.binaryDirty = true;
                        }
                        this.dlg.mergeState({});
                    },
                    getValue: (): string => {
                        const att: J.Attachment = S.props.getAttachment(key, ast.editNode);
                        let ret = att && att.p;
                        if (!ret) ret = "auto";
                        return ret;
                    }
                }) : null;

        let fileNameFieldState: Validator = this.dlg.attFileNames.get((att as any).key);
        if (!fileNameFieldState) {
            fileNameFieldState = new Validator(att.f, [{ name: ValidatorRuleName.REQUIRED }]);
            this.dlg.attFileNames.set((att as any).key, fileNameFieldState);
        }

        const fileNameField = new TextField({
            labelClass: "txtFieldLabelShort",
            outterClass: "fileNameField",
            label: "File Name",
            val: fileNameFieldState
        });

        const list: J.Attachment[] = S.props.getOrderedAtts(ast.editNode);
        const firstAttachment = list[0].o === att.o;
        const lastAttachment = list[list.length - 1].o === att.o;

        const topBinRow = new FlexRowLayout([
            attCheckbox,
            new NodeCompBinary(ast.editNode, key, true, false, true),

            new Diva([
                ipfsLink ? new Div("IPFS", {
                    className: "smallHeading"
                }) : null
            ]),
            imgSizeSelection,
            imgPositionSelection,
            fileNameField,
            pinCheckbox,
            new Diva([
                !firstAttachment ? new Icon({
                    className: "fa fa-lg fa-arrow-up clickable marginLeft",
                    title: "Move Attachment Up",
                    onClick: () => this.moveAttUp(att, ast.editNode)
                }) : null,
                !lastAttachment ? new Icon({
                    className: "fa fa-lg fa-arrow-down clickable marginLeft",
                    title: "Move Attachment Down",
                    onClick: () => this.moveAttDown(att, ast.editNode)
                }) : null
            ])

            // todo-2: this is not doing what I want but is unimportant so removing it for now.
            // ipfsLink ? new Button("IPFS Link", () => S.render.showNodeUrl(state.node, this.ast), { title: "Show the IPFS URL for the attached file." }) : null
        ]);

        let bottomBinRow = null;
        if (ipfsLink) {
            bottomBinRow = new Divc({ className: "marginLeft marginBottom" }, [
                ipfsLink ? new Div(`CID: ${ipfsLink}`, {
                    className: "clickable",
                    title: "Click -> Copy to clipboard",
                    onClick: () => {
                        S.util.copyToClipboard(`ipfs://${ipfsLink}`);
                        S.util.flashMessage("Copied IPFS link to Clipboard", "Clipboard", true);
                    }
                }) : null,
                ipfsLink ? new Div(`Type: ${mime}`) : null
            ]);
        }

        let fileNameTagTip = null;
        if (att.p === "ft") {
            const content = this.dlg?.contentEditor?.getValue() || ast.editNode.content;
            const fileName = fileNameFieldState.getValue();

            // if fileName tag not already in the content give the user help inserting it.
            console.log("content check: " + content);
            if (content.indexOf(`{{${fileName}}}`) === -1) {
                fileNameTagTip = new Div(`Insert {{${fileName}}} in text`, {
                    title: "Click to insert File Tag",
                    className: "clickable smallMarginTop",
                    onClick: () => {
                        this.dlg?.contentEditor?.insertTextAtCursor(`{{${fileName}}}`);
                    }
                });
            }
        }

        return new Divc({ className: "binaryEditorItem" }, [
            topBinRow, fileNameTagTip, bottomBinRow
        ]);
    }

    askMakeAllSameSize = async (node: J.NodeInfo, val: string): Promise<void> => {
        setTimeout(async () => {
            const attachments = S.props.getOrderedAtts(node);
            if (attachments?.length > 1) {
                const dlg = new ConfirmDlg("Display all images at " + (val === "0" ? "their actual" : val) + " width?", "All Images?",
                    "btn-info", "alert alert-info");
                await dlg.open();
                if (dlg.yes) {
                    if (!this.node.attachments) return null;
                    attachments.forEach(att => { att.c = val; });
                    // trick to force screen render
                    this.dlg.mergeState({});
                }
            }
            return null;
        }, 250);
    }

    moveAttDown = (att: J.Attachment, node: J.NodeInfo) => {
        const list: J.Attachment[] = S.props.getOrderedAtts(node);
        let idx: number = 0;
        let setNext: number = -1;
        for (const a of list) {
            const aObj: J.Attachment = node.attachments[(a as any).key];
            if (setNext !== -1) {
                aObj.o = setNext;
                setNext = -1;
            }
            else if (a.o === att.o) {
                aObj.o = idx + 1;
                setNext = idx;
            }
            else {
                aObj.o = idx;
            }
            idx++;
        }

        this.dlg.binaryDirty = true;
        dispatch("attachmentMoveUp", s => { });
    }

    moveAttUp = (att: J.Attachment, node: J.NodeInfo) => {
        const list: J.Attachment[] = S.props.getOrderedAtts(node);
        let idx: number = 0;
        let lastA = null;
        for (const a of list) {
            const aObj: J.Attachment = node.attachments[(a as any).key];
            if (a.o === att.o) {
                aObj.o = idx - 1;
                if (lastA) {
                    lastA.o = idx;
                }
            }
            else {
                aObj.o = idx;
            }
            idx++;
            lastA = a;
        }

        this.dlg.binaryDirty = true;
        dispatch("attachmentMoveUp", s => { });
    }

    createImgSizeSelection = (label: string, extraClasses: string, valueIntf: ValueIntf): Selection => {
        const options = [
            { key: "0", val: "Actual" },
            { key: "20%", val: "20%" },
            { key: "25%", val: "25%" },
            { key: "33%", val: "33%" },
            { key: "50%", val: "50%" },
            { key: "75%", val: "75%" },
            { key: "100%", val: "100%" },
            { key: "50px", val: "50px" },
            { key: "100px", val: "100px" },
            { key: "200px", val: "200px" },
            { key: "400px", val: "400px" },
            { key: "800px", val: "800px" },
            { key: "1000px", val: "1000px" }
        ];

        return new Selection(null, label, options, null, extraClasses, valueIntf);
    }

    createImgPositionSelection = (label: string, extraClasses: string, valueIntf: ValueIntf): Selection => {
        const options = [
            { key: "auto", val: "Auto" },
            { key: "c", val: "Center" },
            { key: "ul", val: "Top Left" },
            { key: "ur", val: "Top Right" },
            { key: "ft", val: "File Tag" }
        ];

        return new Selection(null, label, options, null, extraClasses, valueIntf);
    }
}
