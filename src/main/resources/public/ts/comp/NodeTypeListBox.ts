import { getAppState } from "../AppContext";
import { AppState } from "../AppState";
import { ValueIntf } from "../Interfaces";
import { S } from "../Singletons";
import { Comp } from "./base/Comp";
import { ListBox } from "./ListBox";
import { NodeTypeListBoxRow } from "./NodeTypeListBoxRow";

export class NodeTypeListBox extends ListBox {

    constructor(valueIntf: ValueIntf, public appState: AppState) {
        super(valueIntf);
    }

    preRender(): void {
        const children: Comp[] = [];
        const typeHandlers = S.plugin.getAllTypeHandlers();

        typeHandlers.forEach((typeHandler, k) => {
            if (getAppState().isAdminUser || typeHandler.getAllowUserSelect()) {
                children.push(new NodeTypeListBoxRow(typeHandler, () => {
                    this.updateVal(typeHandler.getTypeName());
                }, this.valueIntf.getValue() === typeHandler.getTypeName()));
            }
        });

        this.setChildren(children);
    }
}
