import { dispatch } from "../AppContext";
import { AppTab } from "../comp/AppTab";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Checkbox } from "../comp/core/Checkbox";
import { Div } from "../comp/core/Div";
import { Diva } from "../comp/core/Diva";
import { Divc } from "../comp/core/Divc";
import { Heading } from "../comp/core/Heading";
import { Icon } from "../comp/core/Icon";
import { IconButton } from "../comp/core/IconButton";
import { Span } from "../comp/core/Span";
import { Spinner } from "../comp/core/Spinner";
import { TextField } from "../comp/core/TextField";
import { FilesTable } from "../comp/FilesTable";
import { FilesTableCell } from "../comp/FilesTableCell";
import { FilesTableRow } from "../comp/FilesTableRow";
import { Constants as C } from "../Constants";
import { ConfirmDlg } from "../dlg/ConfirmDlg";
import { TabIntf } from "../intf/TabIntf";
import * as J from "../JavaIntf";
import { PubSub } from "../PubSub";
import { S } from "../Singletons";
import { IPFSFilesViewProps } from "./IPFSFilesViewProps";

export class IPFSFilesView extends AppTab<IPFSFilesViewProps, IPFSFilesView> {

    loaded: boolean = false;
    static history: string[] = [];

    constructor(data: TabIntf<IPFSFilesViewProps, IPFSFilesView>) {
        super(data);
        data.inst = this;

        PubSub.subSingleOnce(C.PUBSUB_tabChanging, (tabName: string) => {
            if (tabName === this.data.id) {
                if (this.loaded) return;
                this.loaded = true;
                this.refreshFiles();
            }
        });
    }

    override preRender(): boolean {
        const children = [];

        if (this.data.props.loading) {
            children.push(new Diva([
                new Heading(4, "Loading Web3 Files..."),
                new Divc({
                    className: "progressSpinner"
                }, [new Spinner()])
            ]));
        }
        else {
            children.push(new Heading(4, "IPFS Explorer"));

            const mfsFolder = this.data.props.cidField.getValue();
            const slashCount = S.util.countChars(mfsFolder, "/");
            const isRoot = !mfsFolder || slashCount === 1;
            const showParentButton = mfsFolder && slashCount > 1;

            children.push(new Diva([
                new TextField({
                    label: "MFS Path or CID",
                    val: this.data.props.cidField,
                    enter: () => {
                        const item = this.data.props.cidField.getValue();
                        if (item) {
                            this.openItem(item);
                        }
                    }
                })
            ]));

            children.push(new ButtonBar([
                isRoot ? null : new Button("Root", this.goToRoot, {
                    title: "Go to root folder."
                }),
                IPFSFilesView.history.length > 1 ? new Button("Back", this.goBack, {
                    title: "Previous location"
                }) : null,
                showParentButton ? new Button("Parent", this.goToParent, {
                    title: "Parent Folder"
                }) : null,
                new IconButton("fa-refresh", "Refresh", {
                    onClick: () => this.refreshFiles()
                }, "marginAll")
            ]));

            const mfsMode = mfsFolder && mfsFolder.indexOf("/") === 0;
            children.push(new Heading(5, "Listing: " + (mfsMode ? "Mutable File System" : "Hierarchy (DAG)")))

            children.push(new Diva([
                new Span(null, { className: "float-end marginBottom" }, [
                    new Checkbox("List CIDs", null, {
                        setValue: (checked: boolean) => {
                            dispatch("setListCids", s => {
                                this.data.props.listCids = checked;
                            });
                        },
                        getValue: (): boolean => this.data.props.listCids
                    })
                ])
            ]));

            // Only show the CID if it's different from what's in search field
            if (this.data.props.mfsFolderCid && this.data.props.mfsFolderCid !== mfsFolder) {
                children.push(new Div("CID: " + this.data.props.mfsFolderCid, { className: "marginButtom" }));
            }

            if (this.data.props.mfsFiles) {
                children.push(this.renderFilesTable(this.data.props.mfsFiles));
            }

            // DO NOT DELETE (this shows history, good for debugging.)
            // IPFSFilesView.history.forEach(f => {
            //     children.push(new Div(f));
            // });
        }

        this.setChildren([new Divc({ className: "mfsFileView" }, children)]);
        return true;
    }

    renderFilesTable = (mfsFiles: J.MFSDirEntry[]): FilesTable => {
        if (mfsFiles) {
            const mfsFolder = this.data.props.cidField.getValue();
            // mfsMode means this is a true MFS query on the local server and not a DAG query of CID which can be remote.
            const mfsMode = mfsFolder && mfsFolder.indexOf("/") === 0;

            const propTable = new FilesTable({
                border: "1",
                className: "files-table"
            });

            // render folders only
            this.renderItems(propTable, mfsFiles, mfsFolder, mfsMode, true);

            // render non-folders only
            this.renderItems(propTable, mfsFiles, mfsFolder, mfsMode, false);
            return propTable;
        } else {
            return null;
        }
    }

    renderItems = (propTable: FilesTable, mfsFiles: J.MFSDirEntry[], mfsFolder: any, mfsMode: boolean, foldersOnly: boolean) => {
        mfsFiles.forEach(entry => {
            let iconClass: string = null;
            let isFile: boolean = false;

            switch (entry.Type) {
                case 0:
                    iconClass = "fa-file fileIcon";
                    isFile = true;
                    break;
                case 1:
                    iconClass = "fa-folder folderIcon";
                    break;
                default:
                    iconClass = "fa-caret-right";
                    break;
            }

            // return from the forEach iterator
            if (foldersOnly && isFile) return;
            if (!foldersOnly && !isFile) return;

            const fullName = mfsMode ? (mfsFolder + "/" + entry.Name) : null;
            const locationToOpen = mfsMode ? fullName : entry.Hash;
            let sizeStr = S.util.formatMemory(entry.Size);
            if (sizeStr === "0 bytes") {
                sizeStr = "";
            }

            const propTableRow = new FilesTableRow({
                className: "filesTableRow"
            }, [
                // TYPE ICON
                new FilesTableCell(null, {
                    className: "filesTableTypeCol"
                }, [
                    new Icon({
                        className: "fa fa-lg " + iconClass
                    })
                ]),

                // NAME
                new FilesTableCell(null, {
                    className: "filesTableNameCol"
                }, [
                    new Diva([
                        new Div(entry.Name, {
                            onClick: async () => {
                                // if it's a file use ipfs.io to view it
                                if (isFile) {
                                    // if it's a text file we can open it
                                    if (entry.Name.endsWith(".txt") || entry.Name.endsWith(".json")) {
                                        this.openFile(fullName, entry.Name, entry.Hash);
                                    }
                                    // otherwise instead if a download option (which we CAN do (todo-3), we just let users try it in ipfs.io if they want, to cross
                                    // their fingers and hope for the best with the ProtocolLabs server.
                                    else {
                                        const dlg = new ConfirmDlg("Not a text file. View in external Browser Tab from external Gateway?", "Open in Tab");
                                        await dlg.open();
                                        if (dlg.yes) {
                                            window.open("https://ipfs.io/ipfs/" + entry.Hash, "_blank");
                                        }
                                    }
                                }
                                // otherwise open the folder in our own viewer
                                else {
                                    this.openItem(locationToOpen);
                                }
                            }
                        }),
                        this.data.props.listCids ? new Div(entry.Hash) : null
                    ])
                ]),

                // SIZE
                new FilesTableCell(sizeStr, {
                    className: "filesTableSizeCol"
                }),

                // DELETE ICON
                // only show the delete button for local mfsMode stuff.
                mfsMode ? new FilesTableCell(null, {
                    className: "filesTableDelCol"
                    // onClick: () => { this.deleteItem(fullName); }
                }, [
                    !foldersOnly ? new Diva([
                        new Icon({
                            className: "fa fa-trash fa-lg clickable marginRight",
                            title: "Delete",
                            onClick: async () => {
                                const dlg = new ConfirmDlg("Delete File: " + entry.Name + "?", "Confirm Delete",
                                    "btn-danger", "alert alert-info");
                                await dlg.open();
                                if (dlg.yes) {
                                    this.deleteItem(fullName);
                                }
                            }
                        })
                    ]) : null
                ]) : null
            ]);
            propTable.addChild(propTableRow);
        });
    }

    openFile = async (item: string, shortName: string, hash: string) => {
        const res = await S.rpcUtil.rpc<J.GetIPFSContentRequest, J.GetIPFSContentResponse>("getIPFSContent", {
            id: item
        });

        dispatch("showServerInfo", s => {
            S.tabUtil.tabChanging(s.activeTab, C.TAB_SERVERINFO);
            s.activeTab = C.TAB_SERVERINFO;
            s.serverInfoText = shortName + "\n" + hash + "\n\n" + res.content;
            s.serverInfoCommand = "IPFS File Content";
            s.serverInfoTitle = "IPFS File Content";
        });
    }

    deleteItem = (item: string) => {
        // DO NOT DELETE (may decide to do this some day)
        // dispatch("deleteMFSFile", s => {
        //     this.data.props.loading = true;
        // });

        setTimeout(async () => {
            await S.rpcUtil.rpc<J.DeleteMFSFileRequest, J.DeleteMFSFileResponse>("deleteMFSFile", {
                item
            });

            this.refreshFiles();
        }, 100);
    }

    openItem = (folder: string) => {
        // DO NOT DELETE (may decide to do this some day)
        // dispatch("RefreshMFSFiles", s => {
        //     this.data.props.loading = true;
        // });

        setTimeout(async () => {
            // ensure this is never empty string. Server needs to get null instead of empty string.
            if (!folder) folder = null;

            const res = await S.rpcUtil.rpc<J.GetIPFSFilesRequest, J.GetIPFSFilesResponse>("getIPFSFiles", {
                folder
            });

            dispatch("loadIPFSFiles", s => {
                // this condition just makes sure we're not pushing the same thing already at the top of the stack.
                if (!(IPFSFilesView.history.length > 0 && IPFSFilesView.history[IPFSFilesView.history.length - 1] === folder)) {
                    IPFSFilesView.history.push(folder);
                }
                this.data.props.loading = false;
                this.data.props.mfsFiles = res.files;
                this.data.props.cidField.setValue(res.folder);
                this.data.props.mfsFolderCid = res.cid;
            });
        }, 100);
    }

    goBack = () => {
        // going back requires two pops because the first pop gives us the CURRENT location
        let path = IPFSFilesView.history.pop();
        if (path) {
            path = IPFSFilesView.history.pop();
        }
        console.log("popped to: " + path);
        this.openItem(path);
    }

    goToParent = () => {
        const mfsFolder = this.data.props.cidField.getValue();
        // mfsMode means this is a true MFS query on the local server and not a DAG query of CID which can be remote.
        const mfsMode = mfsFolder && mfsFolder.indexOf("/") === 0;
        if (!mfsMode) return;

        const parent = S.util.chopAtLastChar(mfsFolder, "/");
        // console.log("parent = " + parent);
        if (parent) {
            this.openItem(parent);
        }
    }

    refreshFiles = () => {
        setTimeout(async () => {
            const mfsFolder = this.data.props.cidField.getValue();
            this.openItem(mfsFolder);
        }, 100);
    }

    goToRoot = () => {
        setTimeout(async () => {
            this.openItem(null);
        }, 100);
    }
}
