import { Constants as C } from "./Constants";
import { DocumentRSInfo } from "./DocumentRSInfo";
import { S } from "./Singletons";
import { Div } from "./comp/core/Div";
import { TabIntf } from "./intf/TabIntf";
import * as J from "./JavaIntf";
import { Html } from "./comp/core/Html";
import { getAs } from "./AppContext";
import { Clearfix } from "./comp/core/Clearfix";

export class DocIndexPanel extends Div {
    static initialized: boolean = false;

    constructor() {
        super();
        this.attribs.className = "docIndexPanel";
    }

    override preRender(): boolean {
        const data: TabIntf = S.tabUtil.getAppTabData(C.TAB_DOCUMENT);
        if (!data || !data.props) return false;
        const info = data.props as DocumentRSInfo;
        if (!info.results || info.results.length < 2) return false;
        const ast = getAs();

        let index = "";
        let count = 0;
        const baseSlashCount = S.util.countChars(info.results[0].path, "/");
        for (const node of info.results) {
            if (node.hasChildren) {
                const level = S.util.countChars(node.path, "/") - baseSlashCount;
                const clazz = ast.indexHighlightNode == node.id ? "docIndexLinkHighlight" : "docIndexLink";
                index += `<div style="margin-left: ${level * 12}px" class="${clazz}" onClick="S.view.jumpToIdFromIndexPanel('${node.id}')">${this.getLevelBullet(level)}&nbsp;${this.getShortContent(node)}</div>`;
                count++;
            }
        }
        if (count < 2) return false;
        const html = new Html(index);
        html.purifyHtml = false;

        let backToDocLink = null;
        if (ast.activeTab != C.TAB_DOCUMENT) {
            backToDocLink = new Div("Back to Doc", { className: "backToDocLink float-end", onClick: () => S.tabUtil.selectTab(C.TAB_DOCUMENT) })
        }
        this.setChildren([backToDocLink, backToDocLink ? new Clearfix() : null, html]);
        return true;
    }

    getLevelBullet = (level: number) => {
        switch (level) {
            case 1: return "&#9688;";
            case 2: return "&#8227;";
            case 3: return "&#8226;";
            case 4: return "&#9702;";
        }
        return "";
    }

    getShortContent = (node: J.NodeInfo) => {
        let content = node.content;
        const idx = content.indexOf("\n");
        if (idx !== -1) {
            content = content.substring(0, idx);
        }
        content = S.util.trimLeadingChars(content, "#");

        if (content.length > 80) content = content.substring(0, 80) + "...";
        return S.domUtil.escapeHtml(content).trim();
    }
}
