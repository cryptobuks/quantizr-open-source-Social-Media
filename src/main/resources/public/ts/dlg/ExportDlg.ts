import { dispatch, getAs } from "../AppContext";
import { CompIntf } from "../comp/base/CompIntf";
import { Anchor } from "../comp/core/Anchor";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Checkbox } from "../comp/core/Checkbox";
import { Div } from "../comp/core/Div";
import { Diva } from "../comp/core/Diva";
import { Divc } from "../comp/core/Divc";
import { Heading } from "../comp/core/Heading";
import { RadioButton } from "../comp/core/RadioButton";
import { RadioButtonGroup } from "../comp/core/RadioButtonGroup";
import { Span } from "../comp/core/Span";
import { TextField } from "../comp/core/TextField";
import { VerticalLayout } from "../comp/core/VerticalLayout";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { NodeInfo } from "../JavaIntf";
import { S } from "../Singletons";
import { Validator } from "../Validator";
import { Value } from "../Value";
import { MessageDlg } from "./MessageDlg";

export class ExportDlg extends DialogBase {

    fileNameState: Validator = new Validator();
    saveToIpfsState: Value<boolean> = new Value<boolean>(this, "toIpfs");

    constructor(private node: NodeInfo) {
        super("Export Node");
        this.fileNameState.setValue(node.name);
    }

    renderDlg(): CompIntf[] {
        const ast = getAs();
        const exportType = ast.exportSettings.exportType
        return [
            new TextField({ label: "Export File Name (without extension)", val: this.fileNameState }),
            new Heading(5, "Type of File to Export", { className: "bigMarginTop" }),
            new RadioButtonGroup([
                this.radioButton("ZIP", "zip"),
                this.radioButton("TAR", "tar"),
                this.radioButton("TAR.GZ", "tar.gz"),
                this.radioButton("PDF", "pdf")
            ], "radioButtonsBar marginTop"),

            exportType === "pdf" || ast.exportSettings.includeMD ? new Checkbox("Table of Contents", null, {
                setValue: (checked: boolean) => dispatch("exportSetting", s => s.exportSettings.includeToc = checked),
                getValue: (): boolean => getAs().exportSettings.includeToc
            }) : null,

            exportType === "zip" || exportType === "tar" || exportType === "tar.gz" ? this.makeArchiveOptions() : null,
            ast.config.ipfsEnabled ? new Diva([
                new Checkbox("Save to IPFS", null, this.saveToIpfsState)
            ]) : null,
            new ButtonBar([
                new Button("Export", this.exportNodes, null, "btn-primary"),
                new Button("Close", this.close, null, "btn-secondary float-end")
            ], "marginTop")
        ];
    }

    makeArchiveOptions = (): Div => {
        const ast = getAs();
        return new Divc({ className: "bigMarginLeft bigMarginBottom" }, [

            new Heading(5, "Files to Include", { className: "bigMarginTop" }),
            new Checkbox("HTML", null, {
                setValue: (checked: boolean) => dispatch("exportSetting", s => { s.exportSettings.includeHTML = checked; }),
                getValue: (): boolean => getAs().exportSettings.includeHTML
            }),
            new Checkbox("Markdown", null, {
                setValue: (checked: boolean) => dispatch("exportSetting", s => { s.exportSettings.includeMD = checked; }),
                getValue: (): boolean => getAs().exportSettings.includeMD
            }),
            new Checkbox("Jupyter", null, {
                setValue: (checked: boolean) => dispatch("exportSetting", s => { s.exportSettings.includeJypyter = checked; }),
                getValue: (): boolean => getAs().exportSettings.includeJypyter
            }),
            new Checkbox("JSON", null, {
                setValue: (checked: boolean) => dispatch("exportSetting", s => {
                    s.exportSettings.includeJSON = checked;

                    // our importer doesn't currently support having attachments in the 'attachments' folder so
                    // if we're exporting a file that can be reimported turn off the attOneFolder option
                    s.exportSettings.attOneFolder = false;
                }),
                getValue: (): boolean => getAs().exportSettings.includeJSON
            }),

            new Heading(5, "Other Options", { className: "bigMarginTop" }),

            !ast.exportSettings.includeJSON ? new Checkbox("Attachments Folder", null, {
                setValue: (checked: boolean) => dispatch("exportSetting", s => { s.exportSettings.attOneFolder = checked; }),
                getValue: (): boolean => getAs().exportSettings.attOneFolder
            }) : null,
            ast.exportSettings.includeHTML ? new Checkbox("IDs", null, {
                setValue: (checked: boolean) => dispatch("exportSetting", s => { s.exportSettings.includeIDs = checked; }),
                getValue: (): boolean => getAs().exportSettings.includeIDs
            }) : null,
            ast.exportSettings.includeHTML ? new Checkbox("Divider Line", null, {
                setValue: (checked: boolean) => dispatch("exportSetting", s => { s.exportSettings.dividerLine = checked; }),
                getValue: (): boolean => getAs().exportSettings.dividerLine
            }) : null,
            new Checkbox("Set Headings", null, {
                setValue: (checked: boolean) => dispatch("exportSetting", s => { s.exportSettings.updateHeadings = checked; }),
                getValue: (): boolean => getAs().exportSettings.updateHeadings
            })
        ]);
    }

    radioButton = (name: string, exportType: string) => {
        return new Span(null, null, [
            new RadioButton(name, false, "exportTypeGroup", null, {
                setValue: (checked: boolean) => {
                    if (checked) {
                        dispatch("exportSetting", s => s.exportSettings.exportType = exportType);
                    }
                },
                getValue: (): boolean => getAs().exportSettings.exportType === exportType
            }, "form-check-inline marginRight")
        ]);
    }

    exportNodes = async () => {
        const ast = getAs();
        const res = await S.rpcUtil.rpc<J.ExportRequest, J.ExportResponse>("export", {
            nodeId: this.node.id,
            exportExt: ast.exportSettings.exportType,
            fileName: this.fileNameState.getValue(),
            toIpfs: ast.exportSettings.toIpfs,
            includeToc: ast.exportSettings.includeToc,
            includeJypyter: ast.exportSettings.includeJypyter,
            attOneFolder: ast.exportSettings.attOneFolder,
            includeJSON: ast.exportSettings.includeJSON,
            includeMD: ast.exportSettings.includeMD,
            includeHTML: ast.exportSettings.includeHTML,
            includeIDs: ast.exportSettings.includeIDs,
            dividerLine: ast.exportSettings.dividerLine,
            updateHeadings: ast.exportSettings.updateHeadings
        });
        this.exportResponse(res);
        this.close();
    }

    exportResponse = (res: J.ExportResponse) => {
        const hostAndPort: string = S.util.getHostAndPort();
        /* the 'v' arg is for cachebusting. Browser won't download same file once cached, but eventually
        the plan is to have the export return the actual md5 of the export for use here */

        // disp=inline (is the other)
        const downloadLink = hostAndPort + "/file/" + res.fileName + "?disp=attachment&v=" + (new Date().getTime()) + "&token=" + S.quanta.authToken;

        // todo-3: Currently only PDF exports are saveable to IPFS MFS, and there is an inconsistency here, because we DO want ALL types exports to
        // be able to go to MFS, and it would be pretty easy to do what the PDFs are doing (recarding save to MFS) for all other types of exports.
        const ipfsMessage = (res.ipfsCid && res.ipfsCid.endsWith(".pdf")) ? " You can also use the `IPFS Explorer` to view the IPFS copy of the file." : "";

        if (S.util.checkSuccess("Export", res)) {
            new MessageDlg(
                "Export successful.<p>Use the download link below now, to get the file." + ipfsMessage,
                "Export",
                null,
                new VerticalLayout([
                    res.ipfsCid ? new Div("IPFS Location: " + res.ipfsCid, {
                        className: "ipfsCidText",
                        title: "Click -> Copy to clipboard",
                        onClick: () => {
                            S.util.copyToClipboard(res.ipfsCid);
                            S.util.flashMessage("Copied to Clipboard: " + res.ipfsCid, "Clipboard", true);
                        }
                    }) : null,
                    new Anchor(downloadLink, "Download", { target: "_blank" }),
                    res.ipfsMime ? new Div("mime type: " + res.ipfsMime) : null
                ]), false, 0, null
            ).open();

            S.view.scrollToNode();
        }
    }
}
