import highlightjs from "highlight.js";
import "highlight.js/styles/dark.css";
import { marked } from "marked";
import { markedHighlight } from "marked-highlight";
import { toArray } from "react-emoji-render";
import { dispatch, getAs, promiseDispatch } from "./AppContext";
import { Comp } from "./comp/base/Comp";
import { CompIntf } from "./comp/base/CompIntf";
import { AppNavLink } from "./comp/core/AppNavLink";
import { Clearfix } from "./comp/core/Clearfix";
import { CollapsiblePanel } from "./comp/core/CollapsiblePanel";
import { Div } from "./comp/core/Div";
import { Diva } from "./comp/core/Diva";
import { Divc } from "./comp/core/Divc";
import { FlexRowLayout } from "./comp/core/FlexRowLayout";
import { Heading } from "./comp/core/Heading";
import { IconButton } from "./comp/core/IconButton";
import { Img } from "./comp/core/Img";
import { Span } from "./comp/core/Span";
import { Tag } from "./comp/core/Tag";
import { NodeCompBinary } from "./comp/node/NodeCompBinary";
import { NodeCompTableRowLayout } from "./comp/node/NodeCompTableRowLayout";
import { NodeCompVerticalRowLayout } from "./comp/node/NodeCompVerticalRowLayout";
import { Constants as C } from "./Constants";
import { MessageDlg } from "./dlg/MessageDlg";
import { PasteOrLinkDlg } from "./dlg/PasteOrLinkDlg";
import { UserProfileDlg } from "./dlg/UserProfileDlg";
import { FullScreenType } from "./Interfaces";
import { TabIntf } from "./intf/TabIntf";
import { NodeActionType, TypeIntf } from "./intf/TypeIntf";
import * as J from "./JavaIntf";
import { PubSub } from "./PubSub";
import { S } from "./Singletons";
import { MainTab } from "./tabs/data/MainTab";
import { RSSView } from "./tabs/RSSView";

export class Render {
    private debug: boolean = false;
    private markedRenderer: any = null;

    CHAR_CHECKMARK = "&#10004;";

    // After adding the breadcrumb query it's a real challenge to get this fading to work right, so for now
    // I'm disabling it entirely with this flag.
    enableRowFading: boolean = true;

    fadeInId: string;
    allowFadeInId: boolean = false;

    // DO NOT DELETE: #inline-image-rendering
    // There's another part of the code
    // (see forceRender) that I want to be ABLE to bring back
    // which relies on this code
    // forceRender = false;
    // backgroundQueue = setInterval(() => {
    //     if (this.forceRender) {
    //         this.forceRender = false;
    //         dispatch("forceRender", s => { })
    //     }
    // }, 500);

    constructor() {
        // https://github.com/highlightjs/highlight.js
        highlightjs.highlightAll();
    }

    injectSubstitutions = (node: J.NodeInfo, val: string): string => {
        // note: this is only here to get the markdown renderer to have padding in plain text, but also
        // it means we can leave off the language type and get a plaintext as default
        val = S.util.replaceAll(val, "```txt\n", "```plaintext\n");

        val = S.util.replaceAll(val, "{{locationOrigin}}", window.location.origin);
        val = this.injectCustomButtons(val);

        /* These allow us to enter into the markdown things like this:
        [My Link Test]({{url}}?id=:my-test-name)
        [My Other Link Test]({{byName}}my-test-name)
        to be able to have a link to a node of a specific name

        However, this also works and may be the more 'clear' way:
        [Link Test App](?id=:my-test-name)
        */
        val = S.util.replaceAll(val, "{{byName}}", window.location.origin + window.location.pathname + "?id=:");
        val = S.util.replaceAll(val, "{{url}}", window.location.origin + window.location.pathname);

        if (node.attachments) {
            const list: J.Attachment[] = S.props.getOrderedAtts(node);
            let imgHtml = "";

            for (const a of list) {
                let imgSize = a ? a.c : null;
                // 'actual size' designation is stored as prop val == "0"
                if (!imgSize || imgSize === "0") {
                    imgSize = "";
                }

                const key = (a as any).key;
                const imgUrl = S.attachment.getUrlForNodeAttachment(node, key, false);
                let topClass = null;
                let suffix = "";

                // Center Top
                if (a.p === "c") {
                    topClass = "imgUpperCenter";
                }
                // Upper Left
                else if (a.p === "ul") {
                    topClass = "imgUpperLeft";
                    suffix = "<div class=\"clearfix\"/>";
                }
                // Upper Right
                else if (a.p === "ur") {
                    topClass = "imgUpperRight";
                    suffix = "<div class=\"clearfix\"/>";
                }

                if (topClass) {
                    imgHtml += `<img class="${topClass} enlargableImg" width="${imgSize}" src="${imgUrl}" data-nodeid="${node.id}" data-attkey="${key}">` + suffix;
                }

                // ft=at file tag
                else if (a.p === "ft") {
                    val = S.util.replaceAll(val, `{{${a.f}}}`, `\n\n<img class="imgBlock enlargableImg" width="${imgSize}" src="${imgUrl}" data-nodeid="${node.id}" data-attkey="${key}">\n\n`);
                }
            }

            // we have to insert a double space or else we can have the end of the image
            // tag so close to markdown headings (###) that the rendering enging won't translate the headings.
            if (imgHtml) {
                val = imgHtml + "\n\n" + val;
            }
        }

        return val;
    }

    injectCustomButtons = (val: string): string => {
        val = this.injectAdminButton(val, C.ADMIN_COMMAND_FEDIVERSE, "Fediverse Feed");
        val = this.injectAdminButton(val, C.ADMIN_COMMAND_TRENDING, "Trending Hashtags");
        return val;
    }

    injectAdminButton = (val: string, cmd: string, buttonText: string) => {
        // NOTE: Our Singleton class puts a global copy of S on the browser 'window object', so that's why this script works.
        const script = "S.util.adminScriptCommand('" + cmd + "');";
        return val.replace(cmd, `<button class="btn btn-primary marginRight" onClick="${script}">${buttonText}</button>`);
    }

    /**
     * See: https://github.com/highlightjs/highlight.js
     *      https://marked.js.org/using_pro#renderer
     */
    initMarkdown = () => {
        if (this.markedRenderer) return;
        if (!marked) {
            throw new Error("failed to import 'marked' in Render.ts");
        }
        this.markedRenderer = new marked.Renderer();

        // NOTE: This gets called only for 'single backtick' delimited content not code fences with three backticks
        this.markedRenderer.codespan = (code: string) => {
            return `<span class='mkDownCodespan' onclick="S.domUtil.codeSpanClick(this)">${code}</span>`;
        }

        // From Stack Overflow
        // https://github.com/markedjs/marked/issues/882
        this.markedRenderer.link = (href: string, title: string, text: string) => {
            // console.log(`marked.link [${href}][${title}][${text}]`);

            if (href.indexOf("mailto:") === 0) {
                // todo-1: markdown thinks a fediverse username is a 'mailto' because the syntax looks like that.
                return `<span class="userNameInContent">${text}</span>`;
            }

            if (title) {
                return `<a href="${href}" title="${title}" target="_blank">${text}</a>`;
            }
            else {
                return `<a href="${href}" target="_blank">${text}</a>`;
            }
        };

        // https://marked.js.org/using_advanced#highlight
        marked.setOptions({
            renderer: this.markedRenderer,
            gfm: true,
            breaks: false,
            pedantic: false,
            smartypants: false,

            // this stops the markdown from trying to make strings that look like an email address from having
            // any mailto assumptions about them which is good becasue most of the time they're gonna be
            // ActivityPub users and not email
            mangle: false,
            headerIds: false

            // SANITIZE PARAM IS DEPRECATED (LEAVE THIS NOTE HERE)
            // Search for 'DOMPurify.sanitize' to see how we do it currently.
            // sanitize: true
        });

        marked.use(markedHighlight({
            langPrefix: "hljs language-",
            highlight(code, language) {
                if (!language) language = "plaintext";
                const lang = highlightjs.getLanguage(language);

                // Highlight only if the language is valid.
                if (lang) {
                    // NOTE: This 'hljs-copy' is picked up by the Html components and that's where the magic is done,
                    // to make this 'copy text to clipboard' icon work.
                    return `<i class='hljs-copy fa fa-clipboard fa-lg float-end clickable' title='Copy to Clipboard (${lang.name})'></i>` +
                        highlightjs.highlight(code, { language, ignoreIllegals: true }).value;
                }
                else {
                    return code;
                }
            }
        }));

        // DO NOT DELETE: this works.
        // const langs = highlightjs.listLanguages();
        // console.log("highlightJs Languages: " + langs);
    }

    renderLinkLabel = (id: string) => {
        const ast = getAs();
        let linkText = null;
        if (id === ast.linkSource) {
            linkText = "Link Source";
        }
        else if (id === ast.linkTarget) {
            linkText = "Link Target";
        }
        return linkText ? new Div(linkText, {
            className: "linkLabel",
            title: "Choose 'Link Nodes' after you pick Source & Target"
        }) : null;
    }

    setNodeDropHandler = (attribs: any, node: J.NodeInfo) => {
        if (!node) return;

        attribs[C.NODE_ID_ATTR] = node.id;
        S.domUtil.setDropHandler(attribs, (evt: DragEvent) => {
            // todo-2: right now we only actually support one file being dragged? Would be nice to support multiples
            for (const item of evt.dataTransfer.items) {
                // console.log("DROP(a) kind=" + item.kind + " type=" + item.type);

                if (item.type.startsWith("image/") && item.kind === "file") {
                    const file: File = item.getAsFile();

                    // if (file.size > Constants.MAX_UPLOAD_MB * Constants.ONE_MB) {
                    //     S.util.showMessage("That file is too large to upload. Max file size is "+Constants.MAX_UPLOAD_MB+" MB");
                    //     return;
                    // }

                    S.attachment.openUploadFromFileDlg(false, node.id, file);
                    return;
                }
                else if (item.type.match("^text/uri-list") && item.kind === "string") {
                    item.getAsString(async (s) => {
                        /* Disallow dropping from our app onto our app */
                        if (s.startsWith(location.protocol + "//" + location.hostname)) {
                            return;
                        }
                        S.attachment.openUploadFromUrlDlg(node ? node.id : null, s, null);
                    });
                }
                else if (item.type === C.DND_TYPE_NODEID && item.kind === "string") {
                    item.getAsString(async (s) => {
                        if (!s) {
                            return;
                        }

                        // we check both ways if we're doing a self drop.
                        if (S.quanta.draggingId === node.id ||
                            attribs[C.NODE_ID_ATTR] === s) {
                            S.util.showMessage("Can't copy a node to itself.");
                            return;
                        }

                        const dlg = new PasteOrLinkDlg(node.id, s);
                        await dlg.open();

                        S.quanta.draggingId = null;
                        S.quanta.dragElm = null;
                    });
                    return;
                }
            }
        });
    }

    /* nodeId is parent node to query for calendar content */
    showCalendar = async (nodeId: string) => {
        if (!nodeId) {
            const node = S.nodeUtil.getHighlightedNode();
            if (node) {
                nodeId = node.id;
            }
        }
        if (!nodeId) {
            S.util.showMessage("You must first click on a node.", "Warning");
            return;
        }

        const res = await S.rpcUtil.rpc<J.RenderCalendarRequest, J.RenderCalendarResponse>("renderCalendar", {
            nodeId
        });
        dispatch("ShowCalendar", s => {
            s.fullScreenConfig = { type: FullScreenType.CALENDAR, nodeId };

            if (res) console.log("has res");
            s.calendarData = S.util.buildCalendarData(res.items);
        });
    }

    copyLinkToClipboard = (link: string) => {
        S.util.copyToClipboard(link);
        S.util.flashMessage("Copied link to Clipboard", "Clipboard", true);
    }

    showNodeUrl = (node: J.NodeInfo) => {
        if (!node) {
            node = S.nodeUtil.getHighlightedNode();
        }
        if (!node) {
            S.util.showMessage("You must first click on a node.", "Warning");
            return;
        }

        const children = [];

        /* we need this holder object because we don't have the dialog until it's created */
        const dlgHolder: any = {};

        const byIdUrl = window.location.origin + "?id=" + node.id;
        children.push(new Div("Click any link to copy to clipboard.", { className: "alert alert-info" }));

        if (S.nostr.isNostrNode(node)) {
            let nostrId = S.props.getPropStr(J.NodeProp.OBJECT_ID, node);
            if (nostrId) {
                nostrId = nostrId.substring(1);
                children.push(new Heading(6, "Nostr ID"), //
                    new Div(nostrId, {
                        className: "linkDisplay",
                        title: "Click -> Copy to clipboard",
                        onClick: () => this.copyLinkToClipboard(nostrId)
                    }));
            }
        }

        children.push(new Heading(6, "By ID"), //
            new Div(byIdUrl, {
                className: "linkDisplay",
                title: "Click -> Copy to clipboard",
                onClick: () => this.copyLinkToClipboard(byIdUrl)
            }));

        const markdownByIdUrl = "[Link](?id=" + node.id + ")";
        children.push(new Heading(6, "By ID (Markdown)"), //
            new Div(markdownByIdUrl, {
                className: "linkDisplay",
                title: "Click -> Copy to clipboard",
                onClick: () => this.copyLinkToClipboard(markdownByIdUrl)
            }));

        if (node.name) {
            const byNameUrl = window.location.origin + S.nodeUtil.getPathPartForNamedNode(node);
            children.push(new Heading(6, "By Name"), //
                new Div(byNameUrl, {
                    className: "linkDisplay",
                    title: "Click -> Copy to clipboard",
                    onClick: () => this.copyLinkToClipboard(byNameUrl)
                }));

            const markdownByNameUrl = "[Link](" + S.nodeUtil.getPathPartForNamedNode(node) + ")";
            children.push(new Heading(6, "By Name (Markdown)"), //
                new Div(markdownByNameUrl, {
                    className: "linkDisplay",
                    title: "Click -> Copy to clipboard",
                    onClick: () => this.copyLinkToClipboard(markdownByNameUrl)
                }));
        }

        // #rss-disable todo-2: rss feeds disabled for now (need to figure out how to format)
        // const rssFeed = window.location.origin + "/rss?id=" + node.id;
        // children.push(new Heading(5, "Node RSS Feed"), //
        //     new Div(rssFeed, {
        //         className: "linkDisplay",
        //         title: "Click -> Copy to clipboard",
        //         onClick: () => this.showLink(rssFeed)
        //     }));

        const attachmentComps: Comp[] = [];
        if (node.attachments) {
            const atts: J.Attachment[] = S.props.getOrderedAtts(node);
            attachmentComps.push(new Heading(3, "Attachments"));
            for (const att of atts) {
                attachmentComps.push(new Tag("hr"));
                const bin = att ? att.b : null;
                if (bin) {
                    attachmentComps.push(new Divc({ className: "float-end" }, [new NodeCompBinary(node, (att as any).key, true, false, true)]));
                    attachmentComps.push(new Heading(4, att.f + " (" + S.util.formatMemory(att.s) + " " + att.m + ")"));
                    const linkGroup = new Divc({ className: "attachmentLinkGroup" });

                    const attByIdUrl = window.location.origin + "/f/id/" + node.id;
                    linkGroup.addChildren([
                        new Heading(6, "View By Id"), //
                        new Div(attByIdUrl, {
                            className: "linkDisplay",
                            title: "Click -> Copy to clipboard",
                            onClick: () => this.copyLinkToClipboard(attByIdUrl)
                        })
                    ]);

                    const downloadAttByIdUrl = attByIdUrl + "?download=y";
                    linkGroup.addChildren([
                        new Heading(6, "Download By Id"), //
                        new Div(downloadAttByIdUrl, {
                            className: "linkDisplay",
                            title: "Click -> Copy to clipboard",
                            onClick: () => this.copyLinkToClipboard(downloadAttByIdUrl)
                        })
                    ]);

                    if (node.name) {
                        const attByNameUrl = window.location.origin + S.nodeUtil.getPathPartForNamedNodeAttachment(node);
                        linkGroup.addChildren([
                            new Heading(6, "View By Name"), //
                            new Div(attByNameUrl, {
                                className: "linkDisplay",
                                title: "Click -> Copy to clipboard",
                                onClick: () => this.copyLinkToClipboard(attByNameUrl)
                            })
                        ]);

                        const downloadAttByNameUrl = attByNameUrl + "?download=y";
                        linkGroup.addChildren([
                            new Heading(6, "Download By Name"), //
                            new Div(downloadAttByNameUrl, {
                                className: "linkDisplay",
                                title: "Click -> Copy to clipboard",
                                onClick: () => this.copyLinkToClipboard(downloadAttByNameUrl)
                            })
                        ]);
                    }

                    // il = IpfsLink
                    if (att.il) {
                        linkGroup.addChildren([
                            new Heading(6, "IPFS LINK"), //
                            new Div("ipfs://" + att.il, {
                                className: "linkDisplay",
                                title: "Click -> Copy to clipboard",
                                onClick: () => this.copyLinkToClipboard("ipfs://" + att.il)
                            })
                        ]);
                    }

                    attachmentComps.push(linkGroup);
                }
            }

            if (attachmentComps.length > 0) {
                children.push(new CollapsiblePanel("Attachment URLs", "Hide", null, attachmentComps, false, (exp: boolean) => {
                    dispatch("ExpandAttachment", s => s.linksToAttachmentsExpanded = exp);
                }, getAs().linksToAttachmentsExpanded, "marginAll", "attachmentLinksPanel", ""));
            }
        }

        const ipfsCid = S.props.getPropStr(J.NodeProp.IPFS_CID, node);
        if (ipfsCid) {
            children.push(new Heading(6, "IPFS CID"), //
                new Div("ipfs://" + ipfsCid, {
                    className: "linkDisplay",
                    title: "Click -> Copy to clipboard",
                    onClick: () => this.copyLinkToClipboard(ipfsCid)
                }));
        }

        const ipnsCid = S.props.getPropStr(J.NodeProp.IPNS_CID, node);
        if (ipnsCid) {
            children.push(new Heading(6, "IPNS Name"), //
                new Div("ipns://" + ipnsCid, {
                    className: "linkDisplay",
                    title: "Click -> Copy to clipboard",
                    onClick: () => this.copyLinkToClipboard("ipns://" + ipnsCid)
                }));
        }

        dlgHolder.dlg = new MessageDlg(null, "Node URLs", null, new Diva(children), false, 0, null);
        dlgHolder.dlg.open();
    }

    allowAction = (type: TypeIntf, action: NodeActionType, node: J.NodeInfo): boolean => {
        return !type || type.allowAction(action, node);
    }

    renderPage = async (res: J.RenderNodeResponse, scrollToTop: boolean,
        targetNodeId: string, clickTab: boolean = true, allowScroll: boolean = true) => {
        if (res && res.noDataResponse) {
            S.util.showMessage(res.noDataResponse, "Note");
            return;
        }

        try {
            if (C.DEBUG_SCROLLING) {
                console.log("renderPage: scrollToTop=" + scrollToTop + " allowScroll=" + allowScroll);
            }

            await promiseDispatch("RenderPage", s => {
                if (!s.activeTab || clickTab) {
                    S.tabUtil.tabChanging(s.activeTab, C.TAB_MAIN);
                    s.activeTab = C.TAB_MAIN;
                }

                s.pageMessage = null;

                if (MainTab.inst) {
                    MainTab.inst.openGraphComps = [];
                }

                let delay: number = 200;

                /* Note: This try block is solely to enforce the finally block to happen to guarantee setting s.rendering
                back to false, no matter what */
                try {
                    if (res) {
                        S.quanta.configRes.urlIdFailMsg = null;
                        s.node = res.node;
                        s.endReached = res.endReached;
                        s.breadcrumbs = res.breadcrumbs;

                        // if the rendered node has one child and it's an RSS node then render it right away.
                        if (s.node.children?.length === 1 && s.node.children[0].type === J.NodeType.RSS_FEED) {
                            const feedSrc: string = S.props.getPropStr(J.NodeProp.RSS_FEED_SRC, s.node.children[0]);
                            if (feedSrc) {
                                const feedSrcHash = S.util.hashOfString(feedSrc);

                                setTimeout(() => {
                                    dispatch("AutoRSSUpdate", s => {
                                        s.rssFeedCache[feedSrcHash] = "loading";
                                        s.rssFeedPage[feedSrcHash] = 1;
                                        RSSView.loadFeed(s, feedSrcHash, feedSrc);
                                    });
                                }, 250);
                            }
                        }
                    }

                    let targetNode: J.NodeInfo = null;
                    if (targetNodeId) {
                        // If you access /n/myNodeName we get here with targetNodeId being the name (and not the ID)
                        // so we have to call getNodeByName() to get the 'id' that goes with that node name.
                        if (targetNodeId.startsWith(":")) {
                            targetNodeId = targetNodeId.substring(1);
                            targetNode = S.nodeUtil.getNodeByName(res.node, targetNodeId, s);
                            if (targetNode) {
                                targetNodeId = targetNode.id;
                            }
                        }

                        this.fadeInId = targetNodeId;
                        S.quanta.pendingLocationHash = null;
                    }
                    else {
                        if (!this.fadeInId) {
                            this.fadeInId = s.node.id;
                        }
                    }

                    if (s.node && !s.isAnonUser) {
                        // do this async just for performance
                        setTimeout(() => {
                            S.localDB.setVal(C.LOCALDB_LAST_PARENT_NODEID, s.node.id);
                            S.localDB.setVal(C.LOCALDB_LAST_CHILD_NODEID, targetNode?.id);
                            S.util.updateHistory(targetNode);
                        }, 10);
                    }

                    if (this.debug && s.node) {
                        console.log("RENDER NODE: " + s.node.id);
                    }

                    if (S.quanta.pendingLocationHash) {
                        // console.log("highlight: pendingLocationHash");
                        window.location.hash = S.quanta.pendingLocationHash;
                        // Note: the substring(1) trims the "#" character off.
                        if (allowScroll) {
                            // console.log("highlight: pendingLocationHash (allowScroll)");
                            S.nodeUtil.highlightRowById(s, S.quanta.pendingLocationHash.substring(1), true);
                            s.rendering = true;
                        }
                        S.quanta.pendingLocationHash = null;
                    }
                    else if (allowScroll && targetNodeId) {
                        if (C.DEBUG_SCROLLING) {
                            console.log("highlight: byId");
                        }

                        S.nodeUtil.highlightRowById(s, targetNodeId, true);
                        s.rendering = true;
                    }
                    else if (allowScroll && (scrollToTop || !S.nodeUtil.getHighlightedNode())) {
                        if (C.DEBUG_SCROLLING) {
                            console.log("rendering highlight: scrollTop");
                        }
                        S.view.scrollToTop();
                        s.rendering = true;
                    }
                    else if (allowScroll) {
                        if (C.DEBUG_SCROLLING) {
                            console.log("highlight: scrollToSelected");
                        }
                        delay = 2000;
                        S.view.scrollToNode(null, delay);
                        s.rendering = true;
                    }
                }
                finally {
                    if (s.rendering) {
                        /* This event is published when the page has finished the render stage */
                        PubSub.subSingleOnce(C.PUBSUB_postMainWindowScroll, () => {
                            setTimeout(() => {
                                dispatch("settingVisible", s => {
                                    s.rendering = false;
                                    this.allowFadeInId = true;
                                });
                            },
                                /* This delay has to be long enough to be sure scrolling has taken place already, so if we
                                set a longer delay above this delay should be even a bit longer than that.
                                */
                                delay + 200);
                        });
                    }
                    else {
                        this.allowFadeInId = true;
                    }

                    // only focus the TAB if we're not editing, because if editing the edit field will be focused. In other words,
                    // if we're about to initiate editing a TextArea field will be getting focus
                    // so we don't want to set the MAIN tab as the focus and mess that up.
                    // console.log("focus MAIN_TAB during render.");
                    if (!s.editNode) {
                        S.domUtil.focusId(C.TAB_MAIN);
                    }
                }
            });
        }
        catch (e) {
            S.util.logErr(e, "render failed");
        }
    }

    renderChildren = (node: J.NodeInfo, tabData: TabIntf<any>, level: number, allowNodeMove: boolean): Comp => {
        if (!node || !node.children) return null;

        /*
         * Number of rows that have actually made it onto the page to far. Note: some nodes get filtered out on
         * the client side for various reasons.
         */
        const layout = S.props.getPropStr(J.NodeProp.LAYOUT, node);
        const ast = getAs();

        /* Note: for edit mode, or on mobile devices, always use vertical layout. */
        if (ast.userPrefs.editMode || ast.mobileMode || !layout || layout === "v") {
            return new NodeCompVerticalRowLayout(node, tabData, level, allowNodeMove, true);
        }
        else if (layout.indexOf("c") === 0) {
            return new NodeCompTableRowLayout(node, tabData, level, layout, allowNodeMove, true);
        }
        else {
            // of no layout is valid, fall back on vertical.
            return new NodeCompVerticalRowLayout(node, tabData, level, allowNodeMove, true);
        }
    }

    renderBoostHeader = (node: J.NodeInfo, treeRender: boolean) => {
        if (!node.boostedNode) return null;
        let displayName = null;

        const isMine = S.props.isMine(node);
        if (isMine) {
            displayName = "You";
        }
        else {
            // if user has set their displayName
            if (node.displayName) {
                displayName = S.util.insertActPubTags(node.displayName, node);
            }

            // Warning: after running insertActPubTags above that may put us back at an empty displayName,
            // so we DO need to check for displayName here rather than putting this in an else block.
            if (!displayName) {
                displayName = node.owner;
                const atIdx = displayName.indexOf("@");
                if (atIdx !== -1) {
                    displayName = displayName.substring(0, atIdx);
                }
            }
        }

        let clazz = null;
        if (treeRender) {
            clazz = isMine ? "boostedByMe" : "boostedByOther";
        }
        else {
            clazz = isMine ? "boostedByMeOnFeed" : "boostedByOtherOnFeed";
        }

        // if this node is the 'container' (booster of) another node, then show only the "Boosted By" header.
        return new Div("Boosted by " + displayName, {
            className: clazz,
            title: "Show Profile:\n\n" + node.owner,
            onClick: () => {
                new UserProfileDlg(node.ownerId).open();
            }
        });
    }

    getAvatarImgUrlByNode = (node: J.NodeInfo): string => {
        const avatarUrl = S.props.getPropStr(J.NodeProp.USER_ICON_URL, node);
        if (avatarUrl) {
            return avatarUrl;
        }
        return this.getAvatarImgUrl(node.ownerId, node.avatarVer);
    }

    getAvatarImgUrl = (ownerId: string, avatarVer: string) => {
        if (!avatarVer) return null;
        return S.rpcUtil.getRpcPath() + "bin/avatar" + "?nodeId=" + ownerId + "&v=" + avatarVer;
    }

    getProfileHeaderImgUrl = (ownerId: string, avatarVer: string) => {
        if (!avatarVer) return null;
        return S.rpcUtil.getRpcPath() + "bin/profileHeader" + "?nodeId=" + ownerId + "&v=" + avatarVer;
    }

    makeHeaderAvatar = (node: J.NodeInfo) => {
        const src: string = node.apAvatar || this.getAvatarImgUrlByNode(node);
        if (!src) {
            return null;
        }

        // Note: we DO have the image width/height set on the node object (node.width, node.hight) but we don't need it for anything currently
        return new Img({
            src,
            // For Transfer in Progress need a RED border here.
            className: "avatarImage",
            title: "User: @" + node.owner + "\n\nShow Profile",
            // align: "left", // causes text to flow around

            onClick: () => {
                new UserProfileDlg(node.ownerId).open();
            }
        });
    }

    /* Returns true if the logged in user and the type of node allow the property to be edited by the user */
    allowPropertyEdit = (node: J.NodeInfo, propName: string): boolean => {
        const type: TypeIntf = S.plugin.getType(node.type);
        return type ? type.allowPropertyEdit(propName) : true;
    }

    isReadOnlyProperty = (propName: string): boolean => {
        return S.props.readOnlyPropertyList.has(propName);
    }

    showGraph = async (node: J.NodeInfo, searchText: string) => {
        node = node || S.nodeUtil.getHighlightedNode();

        const res = await S.rpcUtil.rpc<J.GraphRequest, J.GraphResponse>("graphNodes", {
            searchText,
            nodeId: node.id
        });

        dispatch("ShowGraph", s => {
            s.fullScreenConfig = { type: FullScreenType.GRAPH, nodeId: node.id };
            s.graphSearchText = searchText;
            s.graphData = res.rootNode;
        });
    }

    parseEmojis = (value: any): any => {
        if (!value) return value;
        const emojisArray = toArray(value);
        if (!emojisArray) return value;
        const newValue = emojisArray.reduce((previous: any, current: any) => {
            if (typeof current === "string") {
                return previous + current;
            }
            if (current && current.props) {
                return previous + current.props.children;
            }
            else {
                return previous;
            }
        }, "");
        return newValue;
    };

    renderTagsDiv = (node: J.NodeInfo, moreClasses: string = ""): Div => {
        if (!node || !node.tags) return null;
        const tags = node.tags.split(" ");
        const spans: Span[] = tags.map(tag => new Span(tag, { className: "nodeTags" }));
        return new Divc({
            title: "Click to copy to clipboard",
            onClick: () => {
                S.util.copyToClipboard(node.tags);
                S.util.flashMessage("Copied hashtags to Clipboard", "Clipboard", true);
            },
            className: "clickable float-end " + moreClasses
        }, spans);
    }

    renderUser(node: J.NodeInfo, user: string, userBio: string, imgSrc: string, actorUrl: string,
        displayName: string, className: string, iconClass: string, showMessageButton: boolean, onClick: Function): Comp {

        const img: Img = imgSrc
            ? new Img({
                className: iconClass,
                src: imgSrc,
                onClick
            }) : null;

        const attribs: any = {};
        if (className) attribs.className = className;
        const tagsDiv = this.renderTagsDiv(node, "microMarginBottom");

        return new Divc(attribs, [
            new FlexRowLayout([
                new Divc({ className: "friendLhs" }, [
                    img
                ]),
                new Divc({ className: "friendRhs" }, [

                    // I'm removing this because we can click on the image and to these thru the Profile Dialog of the user.
                    // new ButtonBar([
                    //     // todo-2: need to make ALL calls be able to do a newSubNode here without so we don't need
                    //     // the showMessagesButton flag.
                    //     showMessageButton ? new Button("Message", S.edit.newSubNode, {
                    //         title: "Send Private Message",
                    //         [C.NODE_ID_ATTR]: nodeId
                    //     }) : null,
                    //     actorUrl ? new Button("Go to User Page", () => {
                    //         window.open(actorUrl, "_blank");
                    //     }) : null
                    // ], null, "float-end"),
                    // new Clearfix(),

                    new Div(displayName, {
                        className: "userName"
                    }),
                    new Diva([
                        // we use a span because the div stretches across empty space and does a mouse click
                        // when you didn't intend to click the actual name sometimes.
                        new Span("@" + user, {
                            className: (displayName ? "" : "userName ") + "clickable",
                            onClick
                        })
                    ]),
                    tagsDiv,
                    new Clearfix()

                    // The page just looks cleaner with the username only. We can click them to see their bio text.
                    // userBio ? new Html(userBio, {
                    //     className: "userBio"
                    // }) : null
                ])

            ], "userInfo")
        ]);
    }

    renderLinks = (node: J.NodeInfo): Div => {
        if (!node.links) return null;

        const linkComps: CompIntf[] = [];
        if (node.links) {
            Object.keys(node.links).forEach(key => {
                const nodeId = node.links[key].i; // i == nodeId
                const linkName = node.links[key].n;
                linkComps.push(new Span(linkName, {
                    title: "Click to Open (CRTL+CLICK for new Browser Tab)",
                    className: "nodeLink",
                    onClick: () => {
                        // if CTRL key down open in separate browser tab
                        if (S.util.ctrlKeyCheck()) {
                            window.open(window.location.origin + "?id=" + nodeId, "_blank");
                        }
                        // else open node in the current browser.
                        else {
                            S.view.jumpToId(nodeId);
                        }
                    }
                }));
            });
        }
        return linkComps.length > 0 ? new Divc({ className: "linksPanel" }, linkComps) : null;
    }

    buildCustomLinks = (configArray: any): CompIntf[] => {
        const items: CompIntf[] = [];

        if (configArray) {
            for (const menuItem of configArray) {
                if (menuItem.name === "separator") {
                    // items.push(new MenuItemSeparator());
                }
                else {
                    const link: string = menuItem.link;
                    let func: Function = null;

                    if (link) {
                        // allows ability to select a tab
                        if (link.startsWith("tab:")) {
                            const tab = link.substring(4);

                            /* special case for feed tab */
                            if (tab === C.TAB_FEED) {
                                func = S.nav.messagesFediverse;
                            }
                            else {
                                func = () => S.tabUtil.selectTab(tab);
                            }
                        }
                        // covers http and https
                        else if (link.startsWith("http")) {
                            func = () => window.open(link);
                        }
                        // named nodes like ":myName"
                        else {
                            func = () => S.nav.openContentNode(link, true);
                        }
                    }

                    items.push(new AppNavLink(menuItem.name, func));
                }
            }
        }
        return items;
    }

    renderTagsStrDiv = (tagsStr: string, removeTag: (val: string) => void, labelClickFunc: () => void): Div => {
        if (!tagsStr) tagsStr = "";
        const tags = tagsStr.split(" ");
        const spans: Span[] = tags.map(tag => {
            if (!tag) return null;
            return new Span(tag, {
                title: "Click to Remove",
                onClick: (evt: Event) => {
                    evt.stopPropagation();
                    removeTag(tag);
                },
                className: "nodeTagInEditor"
            })
        });

        return new Diva([
            new IconButton("fa-tag", "", {
                onClick: (evt: Event) => {
                    evt.stopPropagation();
                    labelClickFunc();
                },
                title: "Select Hashtags"
            }, "marginRight"),
            new Divc({ className: "inlineBlock" }, [
                new Divc({
                    className: "tagsFlexContainer"
                }, spans)
            ])
        ]);
    }
}
