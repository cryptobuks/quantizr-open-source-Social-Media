import { Anchor } from "../../comp/core/Anchor";
import { Div } from "../../comp/core/Div";
import * as J from "../../JavaIntf";
import { S } from "../../Singletons";

export class NodeCompRowFooter extends Div {

    constructor(private node: J.NodeInfo) {
        super(null, {
            className: "rowFooter float-end"
        });
    }

    override preRender(): boolean {
        const children = [];
        /* When rendering local Quanta nodes, on the browser, we have no need to show a LINK to the parent node, or a link
         to the actual node because all that's internal. */
        if (this.node.owner.indexOf("@") !== -1) {
            const inReplyTo = S.props.getPropStr(J.NodeProp.INREPLYTO, this.node);
            if (inReplyTo) {
                // if this is not our own host then show the Remote Parent link
                if (inReplyTo.indexOf(location.protocol + "//" + location.hostname) === -1) {
                    children.push(new Anchor(inReplyTo, "Parent", {
                        className: "footerLink",
                        target: "_blank",
                        title: "Go to post's parent on it's home Fediverse instance"
                    }));
                }
            }

            const objUrl = S.props.getPropStr(J.NodeProp.ACT_PUB_OBJ_URL, this.node);
            if (objUrl) {
                // check to see if it's a link to our server, and don't show 'foreign link' link if so.
                // todo-3: we should make a util.ts method for this.
                if (objUrl.indexOf(location.protocol + "//" + location.hostname) === -1) {
                    children.push(new Anchor(objUrl, "Link", {
                        className: "footerLink",
                        target: "_blank",
                        title: "Go to Original Post/Instance"
                    }));
                }
            }
        }

        this.setChildren(children);
        return true;
    }
}
