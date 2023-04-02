import { getAs } from "../AppContext";
import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Divc } from "../comp/core/Divc";
import { Heading } from "../comp/core/Heading";
import { Span } from "../comp/core/Span";
import { TextContent } from "../comp/core/TextContent";
import { Constants as C } from "../Constants";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { FeedTab } from "../tabs/data/FeedTab";
import { SearchContentDlg } from "./SearchContentDlg";

export class NodeStatsDlg extends DialogBase {
    constructor(private res: J.GetNodeStatsResponse, public trending: boolean, public feed: boolean) {
        super(trending ? "Trending (Top 100s)" : "SubNode Statistics");
    }

    renderDlg = (): CompIntf[] => {
        const tagPanel = new Divc({ className: "wordStatsArea" });
        const ast = getAs();

        if (this.res.topVotes?.length > 0) {
            tagPanel.addChild(new Heading(4, "Votes", { className: "trendingSectionTitle alert alert-primary" }));
            this.res.topVotes.forEach(word => {
                tagPanel.addChild(new Span(word, {
                    className: ast.mobileMode ? "statsWordMobile" : "statsWord",
                    [C.WORD_ATTR]: "\"" + word + "\""
                }));
            });
        }

        if (this.res.topTags?.length > 0) {
            tagPanel.addChild(new Heading(4, "Hashtags", { className: "trendingSectionTitle alert alert-primary" }));
            this.res.topTags.forEach(word => {
                tagPanel.addChild(new Span(word, {
                    className: ast.mobileMode ? "statsWordMobile" : "statsWord",
                    [C.WORD_ATTR]: "\"" + word + "\"",
                    onClick: this.searchWord
                }));
            });
        }

        const mentionPanel = new Divc({ className: "wordStatsArea" });
        if (this.res.topMentions?.length > 0) {
            mentionPanel.addChild(new Heading(4, "Mentions", { className: "trendingSectionTitle alert alert-primary" }));
            this.res.topMentions.forEach(word => {
                mentionPanel.addChild(new Span(word, {
                    className: ast.mobileMode ? "statsWordMobile" : "statsWord",
                    [C.WORD_ATTR]: "\"" + word + "\"",
                    onClick: this.searchWord
                }));
            });
        }

        const wordPanel = new Divc({ className: "wordStatsArea" });
        if (this.res.topWords?.length > 0) {
            wordPanel.addChild(new Heading(4, "Words", { className: "trendingSectionTitle alert alert-primary" }));
            this.res.topWords.forEach(word => {
                wordPanel.addChild(new Span(word, {
                    className: ast.mobileMode ? "statsWordMobile" : "statsWord",
                    word,
                    onClick: this.searchWord
                }));
            });
        }

        return [
            this.trending ? null : new TextContent(this.res.stats, null, true),
            tagPanel?.hasChildren() ? tagPanel : null,
            mentionPanel?.hasChildren() ? mentionPanel : null,
            wordPanel?.hasChildren() ? wordPanel : null,

            new ButtonBar([
                new Button("Ok", () => {
                    this.close();
                }, null, "btn-primary")
            ], "marginTop")
        ];
    }

    searchWord = (evt: Event) => {
        this.close();

        const word = S.domUtil.getPropFromDom(evt, C.WORD_ATTR);
        if (!word) return;

        if (this.feed) {
            if (FeedTab.inst) {
                FeedTab.inst.props.searchTextState.setValue(word);
            }

            S.nav.messages({
                feedFilterFriends: false,
                feedFilterToMe: false,
                feedFilterMyMentions: false,
                feedFilterFromMe: false,
                feedFilterToUser: null,
                feedFilterToPublic: true,
                feedFilterLocalServer: false,
                feedFilterRootNode: null,
                feedResults: null,
                applyAdminBlocks: true,
                name: J.Constant.FEED_PUB
            });
        }
        else {
            SearchContentDlg.defaultSearchText = word;
            new SearchContentDlg().open();
        }
    }
}
