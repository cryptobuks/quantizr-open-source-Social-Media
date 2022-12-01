import { TypeHandlerIntf } from "../intf/TypeHandlerIntf";
import { FlexLayout } from "./core/FlexLayout";
import { Icon } from "./core/Icon";
import { ListBoxRow } from "./ListBoxRow";
import { Span } from "./core/Span";

export class NodeTypeListBoxRow extends ListBoxRow {

    constructor(public type: TypeHandlerIntf, onClickFunc: Function, public isSelected: boolean) {
        super(null, onClickFunc);
    }

    preRender(): void {
        const iconClass = this.type.getIconClass();

        this.setChildren([
            new FlexLayout([
                iconClass ? new Icon({ className: iconClass + " typeListIcon" }) : null,
                new Span(this.type.getName())
            ], this.isSelected ? "selectedListItem" : "unselectedListItem")
        ]);
    }
}
