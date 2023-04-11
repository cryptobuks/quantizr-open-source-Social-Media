import { dispatch, getAs } from "../../AppContext";
import { Anchor } from "../../comp/core/Anchor";
import { Div } from "../../comp/core/Div";
import { Icon } from "../../comp/core/Icon";
import { IconButton } from "../../comp/core/IconButton";
import { Span } from "../../comp/core/Span";
import { Constants as C } from "../../Constants";
import { DialogMode } from "../../DialogBase";
import { AudioPlayerDlg } from "../../dlg/AudioPlayerDlg";
import { VideoPlayerDlg } from "../../dlg/VideoPlayerDlg";
import { FullScreenType } from "../../Interfaces";
import * as J from "../../JavaIntf";
import { S } from "../../Singletons";
import { HorizontalLayout } from "../core/HorizontalLayout";
import { Img } from "../core/Img";

interface LS {
    node: J.NodeInfo;
}

export class NodeCompBinary extends Div {

    /* editorEmbed is true when this component is inside the node editor dialog */
    constructor(public node: J.NodeInfo, public attName: string, private isEditorEmbed: boolean,
        private isFullScreenEmbed: boolean, private allowRightMargin: boolean) {
        super();
        this.mergeState<LS>({ node });
    }

    makeImageComp = (node: J.NodeInfo): Img => {
        const ast = getAs();
        if (!node) return null;
        const att = S.props.getAttachment(this.attName, node);
        if (!att) return null;
        const src: string = S.attachment.getUrlForNodeAttachment(node, this.attName, false);
        const style: any = {};
        let size = "";
        let rtMargin = true;
        if (this.isFullScreenEmbed) {
            size = ast.fullScreenImageSize;
        }
        else if (this.isEditorEmbed) {
            // same as 'imgInEditor' class width
            style.width = "75px";
        }
        else {
            size = att.c;
            if (size==="0" || size==="100%") {
                rtMargin = false;
            }
        }

        if (!this.isEditorEmbed) {
            if (!size || size === "0") {
                style.maxWidth = "";
                style.width = "";
            }
            else {
                size = size.trim();

                // for backwards compatability if no units are given assume percent
                if (!size.endsWith("%") && !size.endsWith("px")) {
                    size += "%";
                }

                // I forgot why I needed the "-24px" here, let's run without it.
                // style.maxWidth = `calc(${size} - 24px)`;
                // style.width = `calc(${size} - 24px)`;
                style.maxWidth = size;
                style.width = size;
            }
        }

        const imgTitleSuffix = att.f ? "\n\n" + att.f + "\n(" + att.m + ")" : "";
        const className = this.isFullScreenEmbed ? "fullScreenImg" : (this.isEditorEmbed ? "imgInEditor" : "imgInRow")
        const imgAttrs: any = {
            src,
            className,
            title: this.isEditorEmbed ? "Attached image" + imgTitleSuffix : "Click image to enlarge/reduce" + imgTitleSuffix,
            onClick: () => NodeCompBinary.clickOnImage(node.id, (att as any).key, this.isEditorEmbed, this.isFullScreenEmbed)
        };

        if (this.isFullScreenEmbed) {
            imgAttrs.style = style;
        }
        else {
            this.attribs.style = style;
            if (!this.isEditorEmbed) {
                this.attribs.className = rtMargin && this.allowRightMargin ? "nodeCompBinary" : "nodeCompBinaryNoRtMargin";
            }
        }

        return new Img(imgAttrs);
    }

    /* This method needs to be called statically and we cannot use 'this' in it,
    because it's referenced by the plain HTML text that's used when positioned images are inserted in the content */
    static clickOnImage = (id: string, attName: string, isEditorEmbed: boolean, isFullScreenEmbed: boolean) => {
        const node = S.nodeUtil.findNode(id);
        const att = S.props.getAttachment(attName, node);

        if (isEditorEmbed) {
            S.util.copyToClipboard(att.f);
            S.util.flashMessage("Copied to Clipboard: " + att.f, "Clipboard", true);
            return;
        }

        dispatch("ClickImage", s => {
            if (s.fullScreenConfig.type === FullScreenType.IMAGE && isFullScreenEmbed) {
                s.fullScreenImageSize = s.fullScreenImageSize ? "" : C.FULL_SCREEN_MAX_WIDTH;
            }
            s.fullScreenConfig.type = FullScreenType.IMAGE;

            // if clicking this node first time.
            if (!s.fullScreenConfig.nodeId) {
                if (node) {
                    s.fullScreenConfig.ordinal = att.o || 0;
                }
            }
            s.fullScreenConfig.nodeId = id;
        });
    }

    override preRender(): boolean {
        const node = this.getState<LS>().node;
        if (!node) {
            this.setChildren(null);
            return;
        }

        /* If this is an image render the image directly onto the page as a visible image */
        if (S.props.hasImage(node, this.attName)) {
            this.setChildren([this.makeImageComp(node)]);
        }
        else if (S.props.hasVideo(node, this.attName)) {
            this.setChildren([new HorizontalLayout([
                new IconButton("fa-play", "Play Video", {
                    onClick: () => {
                        new VideoPlayerDlg("vidPlayer-" + node.id, S.attachment.getStreamUrlForNodeAttachment(node, this.attName), null, DialogMode.FULLSCREEN).open();
                    }
                }, "btn-primary"),
                new Span("", {
                    className: "downloadLink"
                }, [new Anchor(S.attachment.getUrlForNodeAttachment(node, this.attName, true), "Download", { target: "_blank" })])
            ])]);
        }
        else if (S.props.hasAudio(node, this.attName)) {
            this.setChildren([new HorizontalLayout([
                new IconButton("fa-play", "Play Audio", {
                    onClick: () => {
                        new AudioPlayerDlg(null, null, null, S.attachment.getStreamUrlForNodeAttachment(node, this.attName), 0).open();
                    }
                }, "btn-primary"),
                new Span("", {
                    className: "downloadLink"
                }, [new Anchor(S.attachment.getUrlForNodeAttachment(node, this.attName, true), "Download", { target: "_blank" })])
            ], "horizontalLayoutCompCompact")]);
        }
        /*
         * If not an image we render a link to the attachment, so that it can be downloaded.
         */
        else {
            const att = S.props.getAttachment(this.attName, node);
            const fileName = att ? att.f : null;
            const fileSize = att ? att.s : null;
            const fileType = att ? att.m : null;

            let viewFileLink: Anchor = null;
            if (fileType === "application/pdf" || fileType?.startsWith("text/")) {
                viewFileLink = new Anchor(S.attachment.getUrlForNodeAttachment(node, this.attName, false), "View", {
                    target: "_blank",
                    className: "downloadLink",
                    title: "Click to open and view in your browser"
                });
            }

            const titleSuffix = `File Size:${fileSize}\n\nType:${fileType}`;
            this.setChildren([new Div("", {
                className: "binary-link"
            }, [
                new Icon({
                    className: "fa fa-file fa-lg"
                }),
                new Span(null, null, [
                    new Anchor(S.attachment.getUrlForNodeAttachment(node, this.attName, true), fileName, {
                        className: "downloadLink",
                        title: "Click to download attachment\n\n" + titleSuffix
                    }),
                    viewFileLink
                ])
            ])]);
        }
        return true;
    }
}
