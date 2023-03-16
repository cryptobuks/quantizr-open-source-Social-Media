import { ReactNode } from "react";
import { Comp } from "./base/Comp";
import * as J from "../JavaIntf";
import { PropTableCell } from "../comp/PropTableCell";
import { PropTableRow } from "../comp/PropTableRow";
import { S } from "../Singletons";

export class PropTable extends Comp {

    constructor(private node: J.NodeInfo) {
        super({
            className: "property-table"
        });
    }

    compRender = (): ReactNode => {
        this.setChildren([]);
        const type = S.plugin.getType(this.node.type);
        if (this.node.properties) {
            this.node.properties.forEach(prop => {
                const propConfig: any = type.getPropConfig(prop.name);
                const label = propConfig?.label || (type ? type.getEditLabelForProp(prop.name) : prop.name);

                if (S.props.isGuiControlBasedProp(prop)) return;
                const ptr = new PropTableRow({
                    className: "prop-table-row"
                }, [
                    new PropTableCell(label, {
                        title: "Property: " + prop.name,
                        className: "prop-table-name-col"
                    }),
                    new PropTableCell(prop.value, {
                        className: "prop-table-val-col"
                    })
                ]);
                ptr.ordinal = propConfig?.ord || 200;
                this.addChild(ptr);
            });
        }
        this.ordinalSortChildren();

        return this.tag("div", { className: "scrollingPropsTable" }, [
            this.tag("table", { className: "customScrollBar smallMarginRight" })
        ]);
    }
}
