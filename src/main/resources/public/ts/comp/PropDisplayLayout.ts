import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { CompIntf } from "./base/CompIntf";
import { Div } from "./core/Div";
import { Divc } from "./core/Divc";

export class PropDisplayLayout extends Div {

    constructor(private node: J.NodeInfo) {
        super(null, {
            className: "fieldDisplayPanel"
        });
    }

    override preRender = (): boolean => {
        const children: CompIntf[] = [];
        const type = S.plugin.getType(this.node.type);
        if (this.node.properties) {
            this.node.properties.forEach(prop => {
                if (S.props.isGuiControlBasedProp(prop) || S.props.isHiddenProp(prop)) return;

                const propConfig = type.getPropConfig(prop.name);
                const ordinal: number = propConfig?.ord || 200; // 200 is just a high enough number to fall below numered ones
                const label = propConfig?.label || (type ? type.getEditLabelForProp(prop.name) : prop.name);
                const comment = type.getSchemaOrgPropComment(prop.name);
                const w: number = propConfig?.width || 100;
                const widthStr = "" + w + "%";

                // warning: don't put any margin or padding on this div. It depends on precise layouts using precise widths.
                const attrs: any = {
                    className: "fieldDisplayCell",
                    title: "Property: " + prop.name + (comment ? ("\n\n" + comment) : "")
                };
                attrs.style = { width: widthStr, maxWidth: widthStr };
                const propType = type.getType(prop.name);
                const displayVal = S.util.formatProperty(prop.value, propType, propConfig) || "?";

                const tableRow = new Divc(attrs, [
                    new Div(label, { className: "fieldDisplayLabel" }),
                    new Div(displayVal, { className: "fieldDisplayVal" })
                ]);
                tableRow.ordinal = ordinal;
                children.push(tableRow);
            });
        }
        const innerDiv = new Divc({ className: "flexPropsDisplayPanel" }, children)
        innerDiv.ordinalSortChildren();
        this.setChildren([innerDiv]);
        return true;
    }
}
