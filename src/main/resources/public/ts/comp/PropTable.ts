import { ReactNode } from "react";
import { Comp } from "./base/Comp";
import * as J from "../JavaIntf";
import { PropTableCell } from "../comp/PropTableCell";
import { PropTableRow } from "../comp/PropTableRow";
import { S } from "../Singletons";

export class PropTable extends Comp {

    constructor(private node: J.NodeInfo) {
        super({
            className: "propertyTable"
        });
    }

    override compRender = (): ReactNode => {
        this.setChildren([]);
        const type = S.plugin.getType(this.node.type);
        if (this.node.properties) {
            this.node.properties.forEach(prop => {
                const propConfig = type.getPropConfig(prop.name);
                const label = propConfig?.label || (type ? type.getEditLabelForProp(prop.name) : prop.name);
                const comment = type.getSchemaOrgPropComment(prop.name);
                const propType = type.getType(prop.name);
                const displayVal = S.util.formatProperty(prop.value, propType);

                if (S.props.isGuiControlBasedProp(prop) || S.props.isHiddenProp(prop)) return;
                const ptr = new PropTableRow({
                    title: "Property: " + prop.name + (comment ? ("\n\n" + comment) : ""),
                    className: "propTableRow"
                }, [
                    new PropTableCell(label, {
                        className: "propTableNameCol"
                    }),
                    new PropTableCell(displayVal, {
                        className: "propTableValCol"
                    })
                ]);
                ptr.ordinal = propConfig?.ord || 200;
                this.addChild(ptr);
            });
        }
        this.ordinalSortChildren();

        if (!this.hasChildren()) return null;
        return this.tag("div", { className: "scrollingPropsTable" }, [
            this.tag("table", { className: "customScrollBar smallMarginRight" })
        ]);
    }
}
