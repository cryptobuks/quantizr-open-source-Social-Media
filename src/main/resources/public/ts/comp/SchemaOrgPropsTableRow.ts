import { EditPropertyDlg, LS as EditPropertyDlgState } from "../dlg/EditPropertyDlg";
import { SchemaOrgProp } from "../JavaIntf";
import { Checkbox } from "./core/Checkbox";
import { Div } from "./core/Div";
import { Divc } from "./core/Divc";
import { ListBoxRow } from "./ListBoxRow";

export class SchemaOrgPropsTableRow extends ListBoxRow {

    comment: string;

    constructor(public prop: SchemaOrgProp, private dlg: EditPropertyDlg) {
        super(null, null, null);
        this.attribs.className = "propListItem";
    }

    override preRender(): boolean {
        const attr: any = {};
        if (this.prop.comment) {
            attr.title = this.prop.comment;
        }
        this.setChildren([
            new Divc(attr, [
                new Checkbox(null, { className: "propsListItemCheckBox" }, {
                    setValue: (checked: boolean) => {
                        const state: EditPropertyDlgState = this.dlg.getState();
                        if (checked) {
                            state.selections.set(this.prop.label, this.prop);
                        }
                        else {
                            state.selections.delete(this.prop.label);
                        }
                        this.dlg.mergeState(state);
                    },
                    getValue: (): boolean => this.dlg.getState<EditPropertyDlgState>().selections.has(this.prop.label)
                }),
                new Div(this.prop.label, { className: "propNameInList" }),
                new Div(this.makeRangesListString(), { className: "propRangesInList float-end" })
            ])
        ]);
        return true;
    }

    makeRangesListString = () => {
        let ret = "";
        this.prop.ranges?.forEach(r => {
            if (ret) ret += ", ";
            ret += r.id;
        });

        return ret;
    }
}
