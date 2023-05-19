import { dispatch, getAs } from "../AppContext";
import { Comp } from "../comp/base/Comp";
import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Checkbox } from "../comp/core/Checkbox";
import { Clearfix } from "../comp/core/Clearfix";
import { Diva } from "../comp/core/Diva";
import { HorizontalLayout } from "../comp/core/HorizontalLayout";
import { IconButton } from "../comp/core/IconButton";
import { Selection } from "../comp/core/Selection";
import { TextField } from "../comp/core/TextField";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { Validator } from "../Validator";
import { ConfirmDlg } from "./ConfirmDlg";
import { LS as SelectTagsDlgLS, SelectTagsDlg } from "./SelectTagsDlg";

interface LS { // Local State
    searchRoot?: string;
    sortField?: string;
    caseSensitive?: boolean;
    fuzzy?: boolean;
    blockedWords?: boolean;
    recursive?: boolean;
    sortDir?: string;
    requirePriority?: boolean;
    requireAttachment?: boolean;
}

export class SearchContentDlg extends DialogBase {
    static defaultSearchText: string = "";
    static dlgState: any = {
        fuzzy: false,
        blockedWords: false,
        caseSensitive: false,
        recursive: true,
        sortField: "0",
        sortDir: "",
        requirePriority: false,
        requireAttachment: false
    };

    searchTextField: TextField;
    searchTextState: Validator = new Validator();

    constructor() {
        super("Search");
        this.onMount(() => { this.searchTextField?.focus(); });
        this.mergeState<LS>(SearchContentDlg.dlgState);
        this.searchTextState.setValue(SearchContentDlg.defaultSearchText);
    }

    renderDlg(): CompIntf[] {
        const ast = getAs();
        let requirePriorityCheckbox = null;
        if (this.getState<LS>().sortField === J.NodeProp.PRIORITY_FULL) {
            requirePriorityCheckbox = new Checkbox("Require Priority", null, {
                setValue: (checked: boolean) => {
                    SearchContentDlg.dlgState.requirePriority = checked;
                    this.mergeState<LS>({ requirePriority: checked });
                },
                getValue: (): boolean => this.getState<LS>().requirePriority
            }, "marginLeft");
        }

        return [
            new Diva([
                new Diva([
                    this.searchTextField = new TextField({ enter: () => this.search(false), val: this.searchTextState })
                ]),
                this.createSearchFieldIconButtons(),
                new Clearfix(),

                new HorizontalLayout([
                    ast.userProfile.blockedWords ? new Checkbox("Blocked Words", null, {
                        setValue: (checked: boolean) => {
                            SearchContentDlg.dlgState.blockedWords = checked;
                            this.mergeState<LS>({ blockedWords: checked });
                            if (checked) {
                                let words = ast.userProfile.blockedWords;
                                words = S.util.replaceAll(words, "\n", " ");
                                words = S.util.replaceAll(words, "\r", " ");
                                words = S.util.replaceAll(words, "\t", " ");

                                this.searchTextState.setValue(words);
                            }
                            else {
                                this.searchTextState.setValue("");
                            }
                        },
                        getValue: (): boolean => this.getState<LS>().blockedWords
                    }) : null,
                    new Checkbox("Substring", null, {
                        setValue: (checked: boolean) => {
                            SearchContentDlg.dlgState.fuzzy = checked;
                            this.mergeState<LS>({ fuzzy: checked });
                        },
                        getValue: (): boolean => this.getState<LS>().fuzzy
                    }),
                    new Checkbox("Case Sensitive", null, {
                        setValue: (checked: boolean) => {
                            SearchContentDlg.dlgState.caseSensitive = checked;
                            this.mergeState<LS>({ caseSensitive: checked });
                        },
                        getValue: (): boolean => this.getState<LS>().caseSensitive
                    }),
                    new Checkbox("Recursive", null, {
                        setValue: (checked: boolean) => {
                            SearchContentDlg.dlgState.recursive = checked;
                            this.mergeState<LS>({ recursive: checked });
                        },
                        getValue: (): boolean => this.getState<LS>().recursive
                    }),
                    new Checkbox("Has Attachment", null, {
                        setValue: (checked: boolean) => {
                            SearchContentDlg.dlgState.requireAttachment = checked;
                            this.mergeState<LS>({ requireAttachment: checked });
                        },
                        getValue: (): boolean => this.getState<LS>().requireAttachment
                    })
                ], "displayTable marginBottom"),

                new HorizontalLayout([
                    new Selection(null, "Search in", [
                        { key: "curNode", val: "Current Node" },
                        { key: "allNodes", val: "My Account" }
                    ], null, "searchDlgSearchRoot", {
                        setValue: (val: string) => {
                            SearchContentDlg.dlgState.searchRoot = val;

                            this.mergeState<LS>({
                                searchRoot: val
                            });
                        },
                        getValue: (): string => this.getState<LS>().searchRoot
                    }),
                    new Diva([
                        new Selection(null, "Sort by", [
                            { key: "0", val: "Relevance" },
                            { key: "ctm", val: "Create Time" },
                            { key: "mtm", val: "Modify Time" },
                            { key: "contentLength", val: "Text Length" },
                            { key: J.NodeProp.PRIORITY_FULL, val: "Priority" }
                        ], null, "searchDlgOrderBy", {
                            setValue: (val: string) => {
                                let sortDir = val === "0" ? "" : "DESC";
                                if (val === J.NodeProp.PRIORITY_FULL) {
                                    sortDir = "asc";
                                }
                                SearchContentDlg.dlgState.sortField = val;
                                SearchContentDlg.dlgState.sortDir = sortDir;

                                const newState: LS = {
                                    sortField: val,
                                    sortDir
                                }
                                if (val === J.NodeProp.PRIORITY_FULL) {
                                    newState.requirePriority = true;
                                }
                                this.mergeState<LS>(newState);
                            },
                            getValue: (): string => this.getState<LS>().sortField
                        })
                    ]),
                    new Diva([
                        requirePriorityCheckbox
                    ])
                ], "horizontalLayoutComp bigMarginBottom"),

                new ButtonBar([
                    new Button("Search", () => this.search(false), null, "btn-primary"),
                    new Button("Graph", this.graph),
                    ast.isAdminUser ? new Button("Delete Matches", this.deleteMatches, null, "btn-danger") : null,
                    new Button("Close", this.close, null, "btn-secondary float-end")
                ], "marginTop")
            ])
        ];
    }

    createSearchFieldIconButtons = (): Comp => {
        return new ButtonBar([
            new Button("Clear", () => {
                this.searchTextState.setValue("");
                dispatch("clearSearch", s => {
                    s.highlightText = null;
                })
            }),
            !getAs().isAnonUser ? new IconButton("fa-tag fa-lg", "", {
                onClick: async () => {
                    const dlg = new SelectTagsDlg("search", this.searchTextState.getValue(), true);
                    await dlg.open();
                    this.addTagsToSearchField(dlg);
                },
                title: "Select Hashtags to Search"
            }, "btn-primary", "off") : null
        ], "float-end tinyMarginTop");
    }

    addTagsToSearchField = (dlg: SelectTagsDlg) => {
        let val = this.searchTextState.getValue();
        val = val.trim();
        const tags: string[] = val.split(" ");

        dlg.getState<SelectTagsDlgLS>().selectedTags.forEach(mtag => {
            const amtags: string[] = mtag.split(" ");
            amtags.forEach(tag => {
                const quoteTag = "\"" + tag + "\"";
                if (!tags.includes(tag) && !tags.includes(quoteTag)) {
                    if (dlg.matchAny) {
                        if (val) val += " ";
                        val += tag;
                        tags.push(tag);
                    }
                    else {
                        if (val) val += " ";
                        val += quoteTag;
                        tags.push(quoteTag);
                    }
                }
            });
        });
        this.searchTextState.setValue(SearchContentDlg.defaultSearchText = val);
    }

    graph = () => {
        // until we have better validation
        const node = S.nodeUtil.getHighlightedNode();
        if (!node) {
            S.util.showMessage("No node is selected to search under.", "Warning");
            return;
        }

        SearchContentDlg.defaultSearchText = this.searchTextState.getValue();
        this.close();
        S.render.showGraph(null, SearchContentDlg.defaultSearchText);
    }

    deleteMatches = async () => {
        const dlg = new ConfirmDlg("Permanently delete ALL MATCHING Nodes", "WARNING",
            "btn-danger", "alert alert-danger");
        await dlg.open();
        if (dlg.yes) {
            this.search(true);
        }
    }

    search = async (deleteMatches: boolean) => {
        // until we have better validation
        const node = S.nodeUtil.getHighlightedNode();
        if (!node) {
            S.util.showMessage("No node is selected to search under.", "Warning");
            return;
        }

        SearchContentDlg.defaultSearchText = this.searchTextState.getValue();
        const desc = SearchContentDlg.defaultSearchText ? ("Content: " + SearchContentDlg.defaultSearchText) : "";
        const state = this.getState<LS>();

        let requirePriority = state.requirePriority;
        if (state.sortField !== J.NodeProp.PRIORITY_FULL) {
            requirePriority = false;
        }

        // If we're deleting matches
        if (SearchContentDlg.defaultSearchText?.trim().length < 5 && deleteMatches) {
            return;
        }

        const success = await S.srch.search(node, null, SearchContentDlg.defaultSearchText, null, desc,
            state.searchRoot,
            state.fuzzy,
            state.caseSensitive, 0,
            state.recursive,
            state.sortField,
            state.sortDir,
            requirePriority,
            state.requireAttachment,
            deleteMatches);
        if (success) {
            this.close();
        }
    }
}
