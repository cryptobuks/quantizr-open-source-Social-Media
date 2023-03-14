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

    preRender(): void {
        const children: Comp[] = [];
        const types = S.plugin.getAllTypes();

        const lcSearchText = this.searchText.toLowerCase();
        const showSchemaOrg = getAs().schemaOrgProps;

        types.forEach((type, k) => {
            if (type.schemaOrg && !showSchemaOrg) {
                return;
            }
            if (!this.searchText || type.getName().toLowerCase().indexOf(lcSearchText) !== -1) {
                if (getAs().isAdminUser || type.getAllowUserSelect()) {
                    children.push(new NodeTypeListBoxRow(type, () => {
                        this.updateVal(type.getTypeName());
                    }, this.valueIntf.getValue() === type.getTypeName()));
                }
            }
        });

        this.setChildren(children);
    }

    getScrollPos = (): number => {
        return NodeTypeListBox.scrollPos;
    }

    setScrollPos = (pos: number): void => {
        NodeTypeListBox.scrollPos = pos;
    }
}
