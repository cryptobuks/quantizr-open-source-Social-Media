import { getAs } from "../AppContext";
import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Checkbox } from "../comp/core/Checkbox";
import { Div } from "../comp/core/Div";
import { Diva } from "../comp/core/Diva";
import { Divc } from "../comp/core/Divc";
import { Heading } from "../comp/core/Heading";
import { TextField } from "../comp/core/TextField";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { Validator } from "../Validator";

export interface LS { // Local State
    tags: Tag[];
    suggestedTags: Tag[];

    // holds the set of all tags selected by the user (via. checkboxes)
    selectedTags?: Set<string>;
    suggestTags: boolean;
}

interface Tag {
    // Note: A Tag with only a description (no 'tag') is considered to be a heading/section
    tag?: string;
    description: string;
}

export class SelectTagsDlg extends DialogBase {
    matchAny = false;
    matchAll = false;
    indenting = false;
    editFieldState: Validator = new Validator();

    /* modeOption = search | edit */
    constructor(private modeOption: string, private curTags: string, private allowSuggestTags: boolean) {
        super("Select Hashtags", "appModalContMediumWidth");

        this.mergeState<LS>({
            selectedTags: this.makeDefaultSelectedTags(),
            tags: this.parseTags(),
            suggestedTags: [],
            suggestTags: false
        });
    }

    makeDefaultSelectedTags = (): Set<string> => {
        const tagSet = new Set<string>();

        if (this.curTags) {
            const tags = this.curTags.split(" ");
            tags?.forEach(t => {
                t = S.util.replaceAll(t, "\"", "");
                tagSet.add(t);
            });
        }

        return tagSet;
    }

    renderDlg(): CompIntf[] {
        let buttons: Button[] = [];
        const state = this.getState<LS>();

        if (state.tags?.length > 0) {
            switch (this.modeOption) {
                case "search":
                    buttons = [
                        new Button("Match All", () => {
                            this.matchAll = true;
                            this.select();
                        }, null, "btn-primary"),
                        new Button("Match Any", () => {
                            this.matchAny = true;
                            this.select();
                        })
                    ];
                    break;
                case "edit":
                    buttons = [
                        new Button("Select", () => this.select(), null, "btn-primary"),
                        new Button("Clear", () => this.clear())
                    ];
                    break;
            }
        }

        return [
            new Diva([
                new TextField({
                    label: "Tag",
                    outterClass: "noPaddingRight marginBottom",
                    val: this.editFieldState,
                    labelClass: "txtFieldLabelShort"
                }),
                this.allowSuggestTags ? new Checkbox("Suggest Tags", { className: "float-end" }, {
                    setValue: (checked: boolean) => {
                        this.mergeState({ suggestTags: checked });
                        if (checked && this.getState<LS>().suggestedTags.length === 0) {
                            setTimeout(this.updateSuggestTags, 250);
                        }
                    },
                    getValue: (): boolean => this.getState<LS>().suggestTags
                }, "form-switch form-check-inline") : null,
                this.createTagsPickerList(),
                new ButtonBar([
                    ...buttons,
                    new Button("Edit Tags", this.edit),
                    new Button("Cancel", () => {
                        this.clear();
                        this.close();
                    }, null, "btn-secondary float-end")
                ], "marginTop")
            ])
        ];
    }

    updateSuggestTags = async () => {
        const node = getAs().node;

        const res = await S.rpcUtil.rpc<J.GetNodeStatsRequest, J.GetNodeStatsResponse>("getNodeStats", {
            nodeId: node ? node.id : null,
            trending: false,
            feed: false,
            getWords: false,
            getTags: true,
            getMentions: false,
            signatureVerify: false
        });

        if (res.topTags?.length > 0) {
            const suggestedTags: Tag[] = [];
            res.topTags.forEach(tag => suggestedTags.push({ tag, description: null }));
            this.mergeState({ suggestedTags });
        }
    }

    /* returns an array of objects like {tag, description}
    Example format:
    <pre>
    My Heading
      Hidden from gui text
    #tag1
    #tag2:Tag Two Description
    // also hidden from GUI text
    </pre>
    */
    parseTags = (): Tag[] => {
        if (!getAs().userProfile?.userTags) return null;
        const tags: Tag[] = [];
        // todo-1: in the TTS engine we have something like this done differently. Research which is best
        const lines: string[] = getAs().userProfile.userTags.split(/\r?\n/);
        lines.forEach(line => {
            if (line?.startsWith("#")) {
                let tag = null;
                let description = null;
                const delimIdx = line.indexOf(":");

                if (delimIdx !== -1) {
                    tag = line.substring(0, delimIdx);
                    description = line.substring(delimIdx + 1).trim();
                }
                else {
                    tag = line;
                }
                tags.push({ tag, description });
            }
            else if (line?.startsWith("//")) {
                // ignore comments
            }
            else {
                // additional lines below a heading to describe the heading can be done as long as they are indented by
                // at least one space on lines below the heading
                if (line && !line.startsWith(" ")) {
                    tags.push({ description: line });
                }
            }
        });
        return tags;
    }

    createTagsPickerList = (): Div => {
        const state = this.getState<LS>();
        let div: Div = null;

        if (state.tags?.length > 0) {
            div = new Divc({ className: "marginBottom" });

            state.tags.forEach(tagObj => this.processAddCheckboxOrHeading(div, tagObj));

            if (state.suggestTags && state.suggestedTags.length > 0) {
                div.addChild(new Heading(4, "Suggestions", { className: "marginTop" }));

                state.suggestedTags.forEach(tagObj => {
                    // don't duplicate any we've already added above
                    if (state.tags.find(o => o.tag === tagObj.tag)) {
                        return;
                    }

                    this.processAddCheckboxOrHeading(div, tagObj);
                });
            }
        }

        if (!div || !div.hasChildren()) {
            div = new Div("You haven't added any tags yet. Use the `Edit Tags` button to add some.");
        }

        return div;
    }

    processAddCheckboxOrHeading = (div: Div, tagObj: Tag) => {
        let attribs: any = null;
        if (tagObj.description && tagObj.tag) {
            // we only prefix with Tag: to move it over so the mouse doesn't cover it up.
            attribs = { title: "Tag: " + tagObj.tag };
        }

        if (!tagObj.tag) {
            div.addChild(new Heading(4, tagObj.description, { className: "marginTop" }));
            this.indenting = true;
        }
        else {
            const checkbox = new Checkbox(tagObj.description || tagObj.tag, attribs, {
                setValue: (checked: boolean) => {
                    const state = this.getState<LS>();
                    if (checked) {
                        state.selectedTags.add(tagObj.tag);
                    }
                    else {
                        state.selectedTags.delete(tagObj.tag);
                    }
                    this.mergeState(state);
                },
                getValue: (): boolean => this.getState<LS>().selectedTags.has(tagObj.tag)
            });
            div.addChild(new Divc({ className: this.indenting ? "tagIndent" : "" }, [checkbox]));
        }
    }

    select = () => {
        const state = this.getState<LS>();
        const editVal = this.editFieldState.getValue();
        if (editVal) {
            state.selectedTags.add(editVal);
        }
        this.mergeState(state);
        this.close();
    }

    clear = () => {
        this.mergeState({ selectedTags: new Set<string>() });
    }

    edit = async () => {
        await S.edit.editHashtags();
        const tags = this.parseTags();
        this.mergeState({ tags });
    }
}
