import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { CompIntf } from "./base/CompIntf";
import { Div } from "./core/Div";

export class PropDisplayLayout extends Div {

    constructor(private node: J.NodeInfo) {
        super(null, {
            className: "fieldDisplayPanel"
        });
    }

    preRender = (): void => {
        const children: CompIntf[] = [];
        const type = S.plugin.getType(this.node.type);
        if (this.node.properties) {
            this.node.properties.forEach(prop => {
                if (S.props.isGuiControlBasedProp(prop)) return;

                const propConfig = type.getPropConfig(prop.name);
                const ordinal: number = propConfig?.ord || 200; // 200 is just a high enough number to fall below numered ones
                const label = propConfig?.label || (type ? type.getEditLabelForProp(prop.name) : prop.name);
                const w: number = propConfig?.width || 100;
                const widthStr = "" + w + "%";

                // warning: don't put any margin or padding on this div. It depends on precise layouts using precise widths.
                const attrs: any = { className: "fieldDisplayCell", title: "Property: " + prop.name };
                attrs.style = { width: widthStr, maxWidth: widthStr };
                const propType = type.getType(prop.name);
                const displayVal = S.util.formatProperty(prop.value, propType) || "?";

                const tableRow = new Div(null, attrs, [
                    new Div(label, { className: "fieldDisplayLabel" }),
                    new Div(displayVal, { className: "fieldDisplayVal" })
                ]);
                tableRow.ordinal = ordinal;
                children.push(tableRow);
            });
        }
        const innerDiv = new Div(null, { className: "flexPropsDisplayPanel" }, children)
        innerDiv.ordinalSortChildren();
        this.setChildren([innerDiv]);
    }
}
