import { Comp } from "../comp/base/Comp";
import { Button } from "../comp/core/Button";
import { Diva } from "../comp/core/Diva";
import { NodeCompMarkdown } from "../comp/node/NodeCompMarkdown";
import { AudioPlayerDlg } from "../dlg/AudioPlayerDlg";
import { TabIntf } from "../intf/TabIntf";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { TypeBase } from "./base/TypeBase";

export class BookmarkType extends TypeBase {
    constructor() {
        super(J.NodeType.BOOKMARK, "Bookmark", "fa-bookmark", false);
    }

    override getAllowRowHeader(): boolean {
        return false;
    }

    override render = (node: J.NodeInfo, tabData: TabIntf<any>, rowStyling: boolean, isTreeView: boolean, isLinkedNode: boolean): Comp => {
        const audioUrl = S.props.getPropStr(J.NodeProp.AUDIO_URL, node);
        return new Diva([
            new NodeCompMarkdown(node, null, tabData),
            audioUrl ? new Button("Play Audio", () => {
                new AudioPlayerDlg("", "Audio: " + audioUrl, null, audioUrl, 0).open();
            }, null, "btn-primary marginLeft") : null
        ]);
    }
}
