import { Comp } from "../comp/base/Comp";
import { Div } from "../comp/core/Div";
import { Heading } from "../comp/core/Heading";
import { TabIntf } from "../intf/TabIntf";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { TypeBase } from "./base/TypeBase";

export class IPFSNodeType extends TypeBase {

    constructor() {
        // todo-3: use a different icon here, our sitemap icon is being used elsewhere.
        super(J.NodeType.IPFS_NODE, "IPFS Node", "fa-sitemap", false);
    }

    allowPropertyEdit(propName: string): boolean {
        return true;
    }

    render = (node: J.NodeInfo, tabData: TabIntf<any>, rowStyling: boolean, isTreeView: boolean, isLinkedNode: boolean): Comp => {
        const cid = S.props.getPropStr(J.NodeProp.IPFS_CID, node) || "";
        return new Div(null, null, [new Heading(6, "CID: " + cid, { className: "ipfsText" })]);
    }
}
