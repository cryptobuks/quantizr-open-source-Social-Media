import { getAs } from "../AppContext";
import { ValueIntf } from "../Interfaces";
import { S } from "../Singletons";
import { Comp } from "./base/Comp";
import { ListBox } from "./ListBox";
import { NodeTypeListBoxRow } from "./NodeTypeListBoxRow";

export class NodeTypeListBox extends ListBox {
    private static scrollPos: number = 0;

    constructor(valueIntf: ValueIntf, private searchText: string) {
        super(valueIntf);

        const maxHeight: number = window.innerHeight > 300 ? (window.innerHeight * 0.7) : 300;
        this.attribs.className = "scrollY scrollBorder customScrollbar";
        this.attribs.style = { maxHeight: maxHeight + "px" };
    }

    override preRender(): boolean {
        const ast = getAs();
        const children: Comp[] = [];
        const types = S.plugin.getOrderedTypesArray(ast.showRecentProps);
        const lcSearchText = this.searchText.toLowerCase();
        const showSchemaOrg = ast.showSchemaOrgProps;

        types.forEach((type, k) => {
            if (type.schemaOrg && !showSchemaOrg) {
                return;
            }
            if (!this.searchText || type.getName().toLowerCase().indexOf(lcSearchText) !== -1) {
                if (ast.isAdminUser || type.getAllowUserSelect()) {
                    children.push(new NodeTypeListBoxRow(type, () => {
                        this.updateVal(type.getTypeName());
                    }, this.valueIntf.getValue() === type.getTypeName()));
                }
            }
        });

        this.setChildren(children);
        return true;
    }

    override getScrollPos = (): number => {
        return NodeTypeListBox.scrollPos;
    }

    override setScrollPos = (pos: number): void => {
        NodeTypeListBox.scrollPos = pos;
    }
}
