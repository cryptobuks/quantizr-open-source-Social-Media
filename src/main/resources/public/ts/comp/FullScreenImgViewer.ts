import { useAppState } from "../AppContext";
import { NodeCompBinary } from "../comp/node/NodeCompBinary";
import { Constants as C } from "../Constants";
import { S } from "../Singletons";
import { Main } from "./Main";
import * as J from "../JavaIntf";

export class FullScreenImgViewer extends Main {
    constructor() {
        super();
        // todo-2: for some reason the 'customScrollBar' isn't having any effect here, but we do have
        // a scrollbar that works.
        this.attribs.className = "fullScreenViewer customScrollBar"
    }

    preRender(): void {
        const ast = useAppState();
        const nodeId = ast.fullScreenConfig.nodeId;
        const node = S.nodeUtil.findNode(nodeId);
        if (!node) {
            console.error("Can't find nodeId " + nodeId);
        }

        const isAnAccountNode = node?.ownerId && node.id === node.ownerId;
        const children = [];

        const list: J.Attachment[] = S.props.getOrderedAttachments(node);
        let attName = null;
        list.forEach(att => {
            if (att.o === ast.fullScreenConfig.ordinal) {
                attName = (att as any).key;
            }
        });

        if (S.props.hasBinary(node) && !isAnAccountNode) {
            children.push(new NodeCompBinary(node, attName, false, true));
        }

        this.setChildren(children);
    }

    domUpdateEvent = () => {
        if (C.DEBUG_SCROLLING) {
            console.log("domUpdateEvent scroll top");
        }
        if (S.view.docElm) {
            // NOTE: Since the docElm component doesn't manage scroll position, we can get away with just
            // setting scrollTop on it directly like this, instead of calling 'elm.setScrollTop()'
            S.view.docElm.scrollTop = 0;
        }
    }
}
