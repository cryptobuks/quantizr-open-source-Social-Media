import Dropzone from "dropzone";
import { getAs } from "../AppContext";
import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Checkbox } from "../comp/core/Checkbox";
import { Div } from "../comp/core/Div";
import { Diva } from "../comp/core/Diva";
import { Divc } from "../comp/core/Divc";
import { HorizontalLayout } from "../comp/core/HorizontalLayout";
import { IconButton } from "../comp/core/IconButton";
import { Constants } from "../Constants";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { ConfirmDlg } from "./ConfirmDlg";
import { MediaRecorderDlg } from "./MediaRecorderDlg";

export class UploadFromFileDropzoneDlg extends DialogBase {
    hiddenInputContainer: Div;
    uploadButton: Button;
    fileList: any[] = null;
    zipQuestionAnswered: boolean = false;
    explodeZips: boolean = false;
    dropzone: any = null;
    dropzoneDiv: Div = null;
    sent: boolean = false;
    maxFiles: number = 50;

    // this varible gets set if anything is detected wrong during the upload
    uploadFailed: boolean = false;
    errorShown: boolean = false;
    numFiles: number = 0;

    /* We allow either nodeId or 'node' to be passed in here */
    constructor(private nodeId: string, private attName: string, private toIpfs: boolean, //
        private autoAddFile: File, private importMode: boolean, public allowRecording: boolean, public afterUploadFunc: Function) {
        super(importMode ? "Import File" : "Upload File");

        // if control key is down we trigger a click on the "Clipboard" button for the user.
        if (S.util.ctrlKeyCheck()) {
            setTimeout(() => {
                this.uploadFromClipboard();
            }, 700);
        }
    }

    renderDlg(): CompIntf[] {
        const ast = getAs();
        const children = [
            new Diva([
                this.importMode || !ast.config.ipfsEnabled ? null : new HorizontalLayout([
                    /* Having this checkbox and caling the setState here causes a full rerender of this dialog, and this needs work eventually
                    to have a React-compatable way of rendering a dropzone dialog that doesn't blow away the existing dropzone div
                    and create a new one any time there's a state change and rerender */
                    new Checkbox("Save to IPFS", null, {
                        setValue: (checked: boolean) => this.toIpfs = checked,
                        getValue: (): boolean => this.toIpfs
                    })
                ]),
                new Div("Click to Add Files (or Drag and Drop)"),
                this.dropzoneDiv = new Div("", { className: "dropzone" }),
                this.hiddenInputContainer = new Divc({ style: { display: "none" } }),
                this.importMode ? null : new Div("From other sources..."),
                new ButtonBar([
                    this.importMode ? null : new IconButton("fa-cloud", "Web/URL", {
                        onClick: this.uploadFromUrl,
                        title: "Upload from Web/URL"
                    }),

                    this.importMode || !ast.config.ipfsEnabled ? null : new Button("IPFS", this.uploadFromIPFS),

                    // LEAVING THIS FOR FUTURE
                    // (Currently the clipboard upload button on the editor itself is all we need so this button
                    // is not here since it just repliates that same functionality)
                    // this.importMode || !S.util.clipboardReadable() ? null : new IconButton("fa-paperclip", "Clipboard", {
                    //     onClick: this.uploadFromClipboard,
                    //     title: "Upload from Clipboard"
                    // }),

                    this.importMode || !this.allowRecording ? null : new IconButton("fa-microphone", /* "From Mic" */ null, {
                        onClick: async () => {
                            const dlg: MediaRecorderDlg = new MediaRecorderDlg(false, true);
                            await dlg.open();
                            if (dlg.uploadRequested) {
                                this.dropzone.addFile(new File([dlg.blob], "audio-recording.opus", { type: dlg.blobType }));
                                this.runButtonEnablement();
                                this.upload();
                            }
                        },
                        title: "Record Audio as Attachment"
                    }),

                    this.importMode || !this.allowRecording ? null : new IconButton("fa-video-camera", /* From WebCam */ null, {
                        onClick: async () => {
                            const dlg: MediaRecorderDlg = new MediaRecorderDlg(true, true);
                            await dlg.open();
                            if (dlg.uploadRequested) {
                                // Convert a string like: "video/webm;codecs=vp8,opus" to just the mime part.
                                dlg.blobType = S.util.chopAtLastChar(dlg.blobType, ";");
                                this.dropzone.addFile(new File([dlg.blob], "video-recording.webm", { type: dlg.blobType }));
                                this.runButtonEnablement();
                                this.upload();
                            }
                        },
                        title: "Record Video as Attachment"
                    })
                ]),
                new ButtonBar([
                    this.uploadButton = new Button(this.importMode ? "Import" : "Upload", this.upload, null, "btn-primary"),
                    new Button("Close", this.close, null, "btn-secondary float-end")
                ], "marginTop")
            ])
        ];

        this.configureDropZone();
        this.runButtonEnablement();
        return children;
    }

    uploadFromUrl = () => {
        S.attachment.openUploadFromUrlDlg(this.nodeId, null, () => {
            this.close();
            if (this.afterUploadFunc) {
                this.afterUploadFunc();
            }
        });
    }

    uploadFromIPFS = () => {
        S.attachment.openUploadFromIPFSDlg(this.nodeId, null, () => {
            this.close();
            if (this.afterUploadFunc) {
                this.afterUploadFunc();
            }
        });
    }

    // https://developer.mozilla.org/en-US/docs/Web/API/Clipboard/read
    // https://web.dev/image-support-for-async-clipboard/
    // Linux Ubuntu note: Shift + Ctrl + PrtSc -> Copy the screenshot of a specific region to the clipboard.
    // todo-2: I have a feature that pastes from clipboard to a node as text, but it needs to detect images and if image is in
    // clipboard create a node and put that image in it.
    uploadFromClipboard = async () => {
        if (!S.util.clipboardReadable()) {
            return;
        }

        (navigator as any)?.clipboard?.read().then(async (data: any) => {
            let done: boolean = false;
            for (const clipboardItem of data) {
                // this was supposed to be only images, but i'm getting all types as blob. whoops.
                for (const type of clipboardItem.types) {
                    const blob = await clipboardItem.getType(type);
                    // console.log(URL.createObjectURL(blob));

                    // DO NOT DELETE: The 'addedfile' emit may be the better way ? not sure yet. addFile() does work.
                    // this.dropzone.emit("addedfile", blob);
                    // //myDropzone.emit("thumbnail", existingFiles[i], "/image/url");
                    // this.dropzone.emit("complete", blob);

                    this.dropzone.addFile(blob);
                    this.runButtonEnablement();

                    // My personal preference is that a click on "From Clipboard" should instantly upload the image
                    // and close the upload dialog, so let's call upload() now (same as using clicking Upload button).
                    // For taking notes online this is a common scenario and saving one click goes a long way towards
                    // better usability, but this line of code is completely optional.
                    this.upload();

                    done = true;
                    break;
                }
                if (done) break;
            }
        });
    }

    upload = async (): Promise<boolean> => {
        if (this.filesAreValid()) {
            const files = this.dropzone.getAcceptedFiles();
            if (files) {
                this.numFiles = files.length;
                if (this.numFiles) {
                    S.rpcUtil.startBlockingProcess();
                    this.dropzone.processQueue();
                }
            }
        }
        return true;
    }

    configureDropZone = () => {
        /* Limit based on user quota for our user accounts */
        const maxFileSize = getAs().userPrefs.maxUploadFileSize;
        // console.log("configureDropZone: maxFileSize="+maxUploadSize);

        let action;
        if (this.importMode) {
            action = S.rpcUtil.getRpcPath() + "streamImport";
        }
        else {
            action = S.rpcUtil.getRpcPath() + "upload";
        }
        const url = action;

        // we assign 'dlg' as 'this' because the 'this' below is controlled and called by 3rd party
        // code expecting 'this' to be what the dropzoned lib needs.
        const dlg = this;

        const config: Object = {
            action,
            width: "100%",
            height: getAs().mobileMode ? "60%" : "100%",
            progressBarWidth: "100%",
            url,
            headers: {
                Bearer: S.quanta.authToken || "",
                Sig: S.crypto.userSignature || ""
            },
            // Prevents Dropzone from uploading dropped files immediately
            autoProcessQueue: false,
            paramName: "files",
            maxFilesize: maxFileSize,

            parallelUploads: 20,

            // Flag tells server to upload multiple files using multi-post requests so more than one file is allowed for each post it makes.
            uploadMultiple: true,

            maxFiles: this.maxFiles,

            addRemoveLinks: true,
            dictDefaultMessage: "Add", // we have dz-message hidden actually so this has no effect.
            hiddenInputContainer: "#" + this.hiddenInputContainer.getId(),

            // ref: https://www.dropzonejs.com/#event-list

            // WARNING: Don't try to put arrow functions in here, these functions are called by Dropzone itself and the
            // 'this' that is in scope during each call must be left as is.
            init: function () {
                this.on("addedfile", function (file: any) {
                    dlg.uploadFailed = false;
                    dlg.errorShown = false;
                    if (file.size > maxFileSize * Constants.ONE_MB) {
                        S.util.showMessage("File is too large. Max Size=" + maxFileSize + "MB", "Warning");
                        return;
                    }
                    dlg.updateFileList(this);
                    dlg.runButtonEnablement();
                });

                this.on("maxfilesexceeded", function (arg: any) {
                    S.util.showMessage("Only " + dlg.maxFiles + " file can be uploaded to a node.", "Warning");
                });

                this.on("removedfile", function () {
                    dlg.updateFileList(this);
                    dlg.runButtonEnablement();
                });

                this.on("sending", function (file: File, xhr: any, formData: any) {
                    dlg.sent = true;
                    // console.log("sending file: " + file.name);

                    formData.append("files", file);

                    // It's important to check before calling append on this formData, because when uploading multiple files
                    // when this runs for the second, third, etc file it ends up createing
                    // nodeId as a comma delimted list which is wrong.
                    if (!formData.has("nodeId")) {
                        formData.append("nodeId", dlg.nodeId);
                        formData.append("attName", dlg.attName);
                        formData.append("explodeZips", dlg.explodeZips ? "true" : "false");
                        formData.append("ipfs", dlg.toIpfs ? "true" : "false");
                    }

                    dlg.zipQuestionAnswered = false;
                });

                this.on("error", function (param1: any, param2: any, param3: any) {
                    S.rpcUtil.stopBlockingProcess();
                    if (dlg.sent) {
                        dlg.uploadFailed = true;
                        S.util.showMessage("Upload failed.", "Warning");
                    }
                });

                // not needed (this does work however)
                this.on("uploadprogress", function (file: any, progress: any) {
                    console.log("File progress" + progress);
                });

                this.on("success", function (file: File, resp: J.ResponseBase, evt: ProgressEvent) {
                    // console.log("onSuccess: dlg.numFiles=" + dlg.numFiles);
                    if (!resp.success && resp.errorType === J.ErrorType.OUT_OF_SPACE) {
                        if (!dlg.errorShown) {
                            dlg.errorShown = true;
                            dlg.uploadFailed = true;
                            S.util.showMessage("Upload failed. You're out of storage space on the server.", "Warning");
                        }
                        return;
                    }
                });

                this.on("queuecomplete", function (arg: any) {
                    S.rpcUtil.stopBlockingProcess();
                    if (dlg.sent) {
                        dlg.close();
                        if (dlg.afterUploadFunc) {
                            dlg.afterUploadFunc();
                        }
                    }
                });
            }
        };

        this.dropzoneDiv.domPreUpdateEvent = (): void => {
            // console.log("Setting up Dropzone for ID: " + this.dropzoneDiv.getId());
            this.dropzone = new Dropzone("#" + this.dropzoneDiv.getId(), config);
            const maxUploadSize = getAs().userPrefs.maxUploadFileSize;

            if (this.autoAddFile) {
                if (this.autoAddFile.size > maxUploadSize * Constants.ONE_MB) {
                    S.util.showMessage("File is too large. Max Size=" + maxUploadSize + "MB", "Warning");
                    return;
                }

                /* It took me forever to figure out that 'addFile' can do this here, I'm not sure if it's undocumented or
                could be taken away some day. Hopefully not. */
                this.dropzone.addFile(this.autoAddFile);

                // let's click the upload button too, automatically
                setTimeout(() => {
                    this.upload();
                }, 250);
            }
        }
    }

    updateFileList = async (dropzoneEvt: any) => {
        this.fileList = dropzoneEvt.getAddedFiles();
        this.fileList = this.fileList.concat(dropzoneEvt.getQueuedFiles());

        /* Detect if any ZIP files are currently selected, and ask user the question about whether they
        should be extracted automatically during the upload, and uploaded as individual nodes
        for each file */
        if (!this.importMode && !this.zipQuestionAnswered && this.hasAnyZipFiles()) {
            this.zipQuestionAnswered = true;
            const dlg = new ConfirmDlg("Do you want Zip files exploded onto the tree when uploaded?",
                "Explode Zips?");

            await dlg.open();
            this.explodeZips = dlg.yes;
        }
    }

    filesAreValid = (): boolean => {
        if (!this.fileList || this.fileList.length === 0 || this.fileList.length > this.maxFiles) {
            return false;
        }

        const maxFileSizeMb = getAs().userPrefs.maxUploadFileSize;
        for (const file of this.fileList) {
            if (file.size > maxFileSizeMb * Constants.ONE_MB) {
                return false;
            }
        }
        return true;
    }

    hasAnyZipFiles = (): boolean => {
        // todo-1: use the some() funcion here instead.
        for (const file of this.fileList) {
            if (file.name?.toLowerCase().endsWith(".zip")) {
                return true;
            }
        }
        return false;
    }

    runButtonEnablement = () => {
        const valid = this.filesAreValid();
        this.uploadButton.setEnabled(valid);
    }
}
