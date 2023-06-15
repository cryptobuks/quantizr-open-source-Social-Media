import { getAs } from "../AppContext";
import { Div } from "../comp/core/Div";
import { Diva } from "../comp/core/Diva";
import { Span } from "../comp/core/Span";
import { S } from "../Singletons";
import { Comp } from "./base/Comp";
import { CompIntf } from "./base/CompIntf";
import * as J from "../JavaIntf";

export class BreadcrumbsPanel extends Div {
    constructor(public breadcrumbs: J.BreadcrumbInfo[]) {
        super(null, {
            className: "breadcrumbPanel"
        });
    }

    override preRender(): boolean {
        this.setChildren([this.createBreadcrumbs()]);
        return true;
    }

    createBreadcrumbs = (): Comp => {
        let children: CompIntf[] = [];

        const ast = getAs();
        if (this.breadcrumbs?.length > 0) {
            children = this.breadcrumbs.map(bc => {
                if (bc.id === ast.node.id) {
                    // ignore root node or page root node. we don't need it.
                    return null;
                }
                else if (bc.id) {
                    if (!bc.name) {
                        const type = S.plugin.getType(bc.type);
                        bc.name = type ? type.getName() : "???";
                    }

                    const span = new Span(S.util.removeHtmlTags(bc.name), {
                        onClick: () => S.view.jumpToId(bc.id),
                        className: "breadcrumbItem"
                    });
                    S.domUtil.makeDropTarget(span.attribs, bc.id);
                    return span;
                }
                else {
                    return new Span("...", { className: "marginRight" });
                }
            }).filter(c => !!c);
        }

        return new Diva(children);
    }
}
