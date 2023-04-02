import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Clearfix } from "../comp/core/Clearfix";
import { Divc } from "../comp/core/Divc";
import { Heading } from "../comp/core/Heading";
import { IconButton } from "../comp/core/IconButton";
import { TextField } from "../comp/core/TextField";
import { DialogBase } from "../DialogBase";
import { S } from "../Singletons";
import { Validator } from "../Validator";
import { LS as SelectTagsDlgLS, SelectTagsDlg } from "./SelectTagsDlg";

export class PasteOrLinkDlg extends DialogBase {
    yes: boolean = false;

    nameState: Validator = new Validator("");

    constructor(private nodeId: string, private sourceId: string) {
        super("Select Action", "appModalContNarrowWidth");
    }

    renderDlg(): CompIntf[] {
        return [
            new Divc({ className: "dragTargetDlgSection" }, [
                new Heading(4, "Paste"),
                new ButtonBar([
                    new Button("Inside", () => {
                        S.edit.moveNodeByDrop(this.nodeId, this.sourceId, "inside");
                        this.close();
                    }),
                    new Button("Inline", () => {
                        S.edit.moveNodeByDrop(this.nodeId, this.sourceId, "inline");
                        this.close();
                    })
                ], "marginTop")
            ]),
            new Divc({ className: "dragTargetDlgSection" }, [
                new Heading(4, "Link Nodes"),
                new TextField({ label: "Link Name", val: this.nameState }),
                new ButtonBar([
                    new Button("Link", () => {
                        const name = this.nameState.getValue();

                        /* verify first that this property doesn't already exist */
                        if (!name) {
                            S.util.showMessage("A Name is required for links", "Warning");
                            return;
                        }
                        S.edit.linkNodes(this.sourceId, this.nodeId, name, "forward-link");
                        this.close();
                    }),
                    new IconButton("fa-tag fa-lg", "", {
                        onClick: async () => {
                            const dlg = new SelectTagsDlg("edit", "", false);
                            await dlg.open();
                            let val: string = null;
                            dlg.getState<SelectTagsDlgLS>().selectedTags.forEach(tag => {
                                if (!val) {
                                    val = tag;
                                }
                            });
                            this.nameState.setValue(val);
                        },
                        title: "Select Hashtags"
                    })
                ], "marginTop")
            ]),
            new ButtonBar([
                new Button("Cancel", this.close)
            ], "marginTop float-end"),
            new Clearfix()
        ];
    }
}
