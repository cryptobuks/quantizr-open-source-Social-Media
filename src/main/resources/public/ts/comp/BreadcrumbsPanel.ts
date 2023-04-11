import { getAs } from "../AppContext";
import { Div } from "../comp/core/Div";
import { Diva } from "../comp/core/Diva";
import { Span } from "../comp/core/Span";
import { S } from "../Singletons";
import { Comp } from "./base/Comp";
import { CompIntf } from "./base/CompIntf";
import { Icon } from "./core/Icon";

export class BreadcrumbsPanel extends Div {
    constructor() {
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
        if (ast.breadcrumbs?.length > 0) {
            children = ast.breadcrumbs.map(bc => {
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

        if (children.length > 0 && !ast.userPrefs.showParents) {
            children.push(new Icon({
                className: "fa fa-level-down fa-lg showParentsIcon",
                title: "Toggle: Show Parent on page",
                onClick: S.edit.toggleShowParents
            }));
        }

        return new Diva(children);
    }
}
