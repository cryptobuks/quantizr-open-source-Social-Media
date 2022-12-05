import highlightjs from "highlight.js";
import "highlight.js/styles/github.css";
import { marked } from "marked";
import { toArray } from "react-emoji-render";
import { dispatch, getAppState } from "./AppContext";
import { AppState } from "./AppState";
import { Comp } from "./comp/base/Comp";
import { CollapsiblePanel } from "./comp/core/CollapsiblePanel";
import { Div } from "./comp/core/Div";
import { Heading } from "./comp/core/Heading";
import { HorizontalLayout } from "./comp/core/HorizontalLayout";
import { Icon } from "./comp/core/Icon";
import { Img } from "./comp/core/Img";
import { Span } from "./comp/core/Span";
import { Tag } from "./comp/core/Tag";
import { NodeCompBinary } from "./comp/node/NodeCompBinary";
import { NodeCompTableRowLayout } from "./comp/node/NodeCompTableRowLayout";
import { NodeCompVerticalRowLayout } from "./comp/node/NodeCompVerticalRowLayout";
import { Constants as C } from "./Constants";
import { MessageDlg } from "./dlg/MessageDlg";
import { UserProfileDlg } from "./dlg/UserProfileDlg";
import { FullScreenType } from "./Interfaces";
import { TabIntf } from "./intf/TabIntf";
import { NodeActionType, TypeIntf } from "./intf/TypeIntf";
import * as J from "./JavaIntf";
import { RssType } from "./plugins/RssType";
import { PubSub } from "./PubSub";
import { S } from "./Singletons";
import { MainTab } from "./tabs/data/MainTab";

// eslint-disable-next-line no-unused-vars
declare let g_urlIdFailMsg: string;
declare let g_info: string;

export class Render {
    private debug: boolean = false;
    private markedRenderer: any = null;

    CHAR_CHECKMARK = "&#10004;";

    // After adding the breadcrumb query it's a real challenge to get this fading to work right, so for now
    // I'm disabling it entirely with this flag.
    enableRowFading: boolean = true;

    fadeInId: string;
    allowFadeInId: boolean = false;

    constructor() {
        highlightjs.highlightAll();
    }

    injectSubstitutions = (node: J.NodeInfo, val: string): string => {
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
            const list: J.Attachment[] = S.props.getOrderedAttachments(node);
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
                    topClass = "img-upper-center";
                }
                // Upper Left
                else if (a.p === "ul") {
                    topClass = "img-upper-left";
                    suffix = "<div class=\"clearfix\"/>";
                }
                // Upper Right
                else if (a.p === "ur") {
                    topClass = "img-upper-right";
                    suffix = "<div class=\"clearfix\"/>";
                }

                if (topClass) {
                    imgHtml += `<img class="${topClass} enlargable-img" width="${imgSize}" src="${imgUrl}" nodeid="${node.id}" attkey="${key}">` + suffix;
                }

                // ft=at file tag
                else if (a.p === "ft") {
                    val = S.util.replaceAll(val, `{{${a.f}}}`, `\n\n<img class="img-block enlargable-img" width="${imgSize}" src="${imgUrl}" nodeid="${node.id}" attkey="${key}">\n\n`);
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

        this.markedRenderer.codespan = (code: string) => {
            return `<span class='markdown-codespan'>${code}</span>`;
        }

        // From Stack Overflow
        // https://github.com/markedjs/marked/issues/882
        this.markedRenderer.link = (href: string, title: string, text: string) => {
            // console.log(`marked.link [${href}][${title}][${text}]`);
            if (href.indexOf("mailto:") === 0) {
                // todo-1: markdown thinks a fediverse username is a 'mailto' becuase the syntax looks like that.
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

            highlight: (code, language) => {
                // Check whether the given language is valid for highlight.js.
                const validLang: boolean = !!(language && highlightjs.getLanguage(language));

                // Highlight only if the language is valid.
                const highlighted: string = validLang ? highlightjs.highlight(language, code).value : code;

                // Render the highlighted code with `hljs` class.
                // return `<pre><code class="hljs ${language}">${highlighted}</code></pre>`;
                return highlighted;
            },

            // example from marked website...
            // highlight: function(code, lang) {
            //     const language = highlightjs.getLanguage(lang) ? lang : "plaintext";
            //     return highlightjs.highlight(code, { language }).value;
            // },

            // our original highlight function...
            // highlight: function (code) {
            //     return highlightjs.highlightAuto(code).value;
            // },
            langPrefix: "hljs language-", // highlight.js css expects a top-level 'hljs' class.

            gfm: true,
            breaks: false,
            pedantic: false,
            smartLists: true,
            smartypants: false

            // SANITIZE PARAM IS DEPRECATED (LEAVE THIS NOTE HERE)
            // Search for 'DOMPurify.sanitize' to see how we do it currently.
            // sanitize: true
        });
    }

    setNodeDropHandler = (attribs: any, node: J.NodeInfo, isFirst: boolean, ast: AppState) => {
        if (!node) return;

        S.domUtil.setDropHandler(attribs, false, (evt: DragEvent) => {
            // todo-2: right now we only actually support one file being dragged? Would be nice to support multiples
            for (const item of evt.dataTransfer.items) {
                // console.log("DROP[" + i + "] kind=" + d.kind + " type=" + d.type);

                if (item.kind === "string") {
                    item.getAsString((s) => {
                        if (item.type.match("^text/uri-list")) {
                            /* Disallow dropping from our app onto our app */
                            if (s.startsWith(location.protocol + "//" + location.hostname)) {
                                return;
                            }
                            S.attachment.openUploadFromUrlDlg(node ? node.id : null, s, null, ast);
                        }
                        /* this is the case where a user is moving a node by dragging it over another node */
                        else {
                            S.edit.moveNodeByDrop(node.id, s, isFirst ? "inline-above" : "inline", false);
                        }
                    });
                    return;
                }
                else if (item.kind === "string" && item.type.match("^text/html")) {
                }
                else if (item.kind === "file" /* && d.type.match('^image/') */) {
                    const file: File = item.getAsFile();

                    // if (file.size > Constants.MAX_UPLOAD_MB * Constants.ONE_MB) {
                    //     S.util.showMessage("That file is too large to upload. Max file size is "+Constants.MAX_UPLOAD_MB+" MB");
                    //     return;
                    // }

                    S.attachment.openUploadFromFileDlg(false, node, file, ast);
                    return;
                }
            }
        });
    }

    /* nodeId is parent node to query for calendar content */
    showCalendar = async (nodeId: string, ast: AppState) => {
        if (!nodeId) {
            const node = S.nodeUtil.getHighlightedNode(ast);
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
            s.calendarData = S.util.buildCalendarData(res.items);
            return s;
        });
    }

    showLink = (link: string) => {
        S.util.copyToClipboard(link);
        S.util.flashMessage("Copied link to Clipboard", "Clipboard", true);
    }

    showNodeUrl = (node: J.NodeInfo, ast: AppState) => {
        if (!node) {
            node = S.nodeUtil.getHighlightedNode(ast);
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
        children.push(new Heading(5, "By ID"), //
            new Div(byIdUrl, {
                className: "linkDisplay",
                title: "Click -> Copy to clipboard",
                onClick: () => this.showLink(byIdUrl)
            }));

        if (node.name) {
            const byNameUrl = window.location.origin + S.nodeUtil.getPathPartForNamedNode(node);
            children.push(new Heading(5, "By Name"), //
                new Div(byNameUrl, {
                    className: "linkDisplay",
                    title: "Click -> Copy to clipboard",
                    onClick: () => this.showLink(byNameUrl)
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
            const atts: J.Attachment[] = S.props.getOrderedAttachments(node);
            attachmentComps.push(new Heading(3, "Attachments"));
            for (const att of atts) {
                attachmentComps.push(new Tag("hr"));
                const bin = att ? att.b : null;
                if (bin) {
                    attachmentComps.push(new Div(null, { className: "float-end" }, [new NodeCompBinary(node, (att as any).key, true, false)]));
                    attachmentComps.push(new Heading(4, att.f + " (" + S.util.formatMemory(att.s) + " " + att.m + ")"));
                    const linkGroup = new Div(null, { className: "attachmentLinkGroup" });

                    const attByIdUrl = window.location.origin + "/f/id/" + node.id;
                    linkGroup.addChildren([
                        new Heading(5, "View By Id"), //
                        new Div(attByIdUrl, {
                            className: "linkDisplay",
                            title: "Click -> Copy to clipboard",
                            onClick: () => this.showLink(attByIdUrl)
                        })
                    ]);

                    const downloadAttByIdUrl = attByIdUrl + "?download=y";
                    linkGroup.addChildren([
                        new Heading(5, "Download By Id"), //
                        new Div(downloadAttByIdUrl, {
                            className: "linkDisplay",
                            title: "Click -> Copy to clipboard",
                            onClick: () => this.showLink(downloadAttByIdUrl)
                        })
                    ]);

                    if (node.name) {
                        const attByNameUrl = window.location.origin + S.nodeUtil.getPathPartForNamedNodeAttachment(node);
                        linkGroup.addChildren([
                            new Heading(5, "View By Name"), //
                            new Div(attByNameUrl, {
                                className: "linkDisplay",
                                title: "Click -> Copy to clipboard",
                                onClick: () => this.showLink(attByNameUrl)
                            })
                        ]);

                        const downloadAttByNameUrl = attByNameUrl + "?download=y";
                        linkGroup.addChildren([
                            new Heading(5, "Download By Name"), //
                            new Div(downloadAttByNameUrl, {
                                className: "linkDisplay",
                                title: "Click -> Copy to clipboard",
                                onClick: () => this.showLink(downloadAttByNameUrl)
                            })
                        ]);
                    }

                    // il = IpfsLink
                    if (att.il) {
                        linkGroup.addChildren([
                            new Heading(5, "IPFS LINK"), //
                            new Div("ipfs://" + att.il, {
                                className: "linkDisplay",
                                title: "Click -> Copy to clipboard",
                                onClick: () => this.showLink("ipfs://" + att.il)
                            })
                        ]);
                    }

                    attachmentComps.push(linkGroup);
                }
            }

            if (attachmentComps.length > 0) {
                children.push(new CollapsiblePanel("Attachment URLs", "Hide", null, attachmentComps, false, (s: boolean) => {
                    ast.linksToAttachmentsExpanded = s;
                }, ast.linksToAttachmentsExpanded, "marginAll", "attachmentLinksPanel", ""));
            }
        }

        const ipfsCid = S.props.getPropStr(J.NodeProp.IPFS_CID, node);
        if (ipfsCid) {
            children.push(new Heading(5, "IPFS CID"), //
                new Div("ipfs://" + ipfsCid, {
                    className: "linkDisplay",
                    title: "Click -> Copy to clipboard",
                    onClick: () => this.showLink(ipfsCid)
                }));
        }

        const ipnsCid = S.props.getPropStr(J.NodeProp.IPNS_CID, node);
        if (ipnsCid) {
            children.push(new Heading(5, "IPNS Name"), //
                new Div("ipns://" + ipnsCid, {
                    className: "linkDisplay",
                    title: "Click -> Copy to clipboard",
                    onClick: () => this.showLink("ipns://" + ipnsCid)
                }));
        }

        dlgHolder.dlg = new MessageDlg(null, "Node URLs", null, new Div(null, null, children), false, 0, null);
        dlgHolder.dlg.open();
    }

    allowAction = (type: TypeIntf, action: NodeActionType, node: J.NodeInfo, ast: AppState): boolean => {
        return !type || type.allowAction(action, node, ast);
    }

    renderPage = (res: J.RenderNodeResponse, scrollToTop: boolean, targetNodeId: string, clickTab: boolean = true, allowScroll: boolean = true) => {
        if (res && res.noDataResponse) {
            S.util.showMessage(res.noDataResponse, "Note");
            return;
        }

        try {
            if (C.DEBUG_SCROLLING) {
                console.log("renderPage: scrollToTop=" + scrollToTop + " allowScroll=" + allowScroll);
            }

            dispatch("RenderPage", s => {
                if (!s.activeTab || clickTab) {
                    S.tabUtil.tabChanging(s.activeTab, C.TAB_MAIN, s);
                    s.activeTab = S.quanta.activeTab = C.TAB_MAIN;
                }

                if (g_info && s.userPrefs) {
                    s.userPrefs.showMetaData = true;
                    g_info = null;
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
                        g_urlIdFailMsg = null;
                        s.node = res.node;
                        s.endReached = res.endReached;
                        s.breadcrumbs = res.breadcrumbs;

                        // if the rendered node has one child and it's an RSS node then render it right away.
                        if (s.node.children && s.node.children.length === 1 && s.node.children[0].type === J.NodeType.RSS_FEED) {
                            const feedSrc: string = S.props.getPropStr(J.NodeProp.RSS_FEED_SRC, s.node.children[0]);
                            if (feedSrc) {
                                const feedSrcHash = S.util.hashOfString(feedSrc);

                                setTimeout(() => {
                                    dispatch("AutoRSSUpdate", s => {
                                        s.rssFeedCache[feedSrcHash] = "loading";
                                        s.rssFeedPage[feedSrcHash] = 1;
                                        RssType.loadFeed(s, feedSrcHash, feedSrc);
                                        return s;
                                    });
                                }, 250);
                            }
                        }

                        /* Slight hack to make viewing 'posts' or chat rooms nodes turn on metaData */
                        if (s.node.type === J.NodeType.POSTS ||
                            s.node.type === J.NodeType.ROOM) {
                            S.edit.setMetadataOption(true);
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
                        s.pendingLocationHash = null;
                    }
                    else {
                        if (!this.fadeInId) {
                            this.fadeInId = s.node.id;
                        }
                    }

                    if (s.node && !s.isAnonUser) {
                        // do this async just for performance
                        setTimeout(() => {
                            S.util.updateHistory(s.node, targetNode, s);
                        }, 10);
                    }

                    if (this.debug && s.node) {
                        console.log("RENDER NODE: " + s.node.id);
                    }

                    if (s.pendingLocationHash) {
                        // console.log("highlight: pendingLocationHash");
                        window.location.hash = s.pendingLocationHash;
                        // Note: the substring(1) trims the "#" character off.
                        if (allowScroll) {
                            // console.log("highlight: pendingLocationHash (allowScroll)");
                            S.nodeUtil.highlightRowById(s.pendingLocationHash.substring(1), true, s);
                            s.rendering = true;
                        }
                        s.pendingLocationHash = null;
                    }
                    else if (allowScroll && targetNodeId) {
                        if (C.DEBUG_SCROLLING) {
                            console.log("highlight: byId");
                        }

                        if (!S.nodeUtil.highlightRowById(targetNodeId, true, s)) {
                            // console.log("highlight: byId...didn't find node: " + targetNodeId);
                        }

                        s.rendering = true;
                    }
                    else if (allowScroll && (scrollToTop || !S.nodeUtil.getHighlightedNode(s))) {
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
                        S.view.scrollToNode(s, null, delay);
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
                                    return s;
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
                return s;
            });
        }
        catch (err) {
            console.error("render failed: " + S.util.prettyPrint(err));
        }
    }

    renderChildren = (node: J.NodeInfo, tabData: TabIntf<any>, level: number, allowNodeMove: boolean, ast: AppState): Comp => {
        if (!node || !node.children) return null;

        /*
         * Number of rows that have actually made it onto the page to far. Note: some nodes get filtered out on
         * the client side for various reasons.
         */
        const layout = S.props.getPropStr(J.NodeProp.LAYOUT, node);

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

    getAvatarImgUrl = (ownerId: string, avatarVer: string) => {
        if (!avatarVer) return null;
        return S.rpcUtil.getRpcPath() + "bin/avatar" + "?nodeId=" + ownerId + "&v=" + avatarVer;
    }

    getProfileHeaderImgUrl = (ownerId: string, avatarVer: string) => {
        if (!avatarVer) return null;
        return S.rpcUtil.getRpcPath() + "bin/profileHeader" + "?nodeId=" + ownerId + "&v=" + avatarVer;
    }

    makeAvatarImage = (node: J.NodeInfo, ast: AppState) => {
        const src: string = node.apAvatar || this.getAvatarImgUrl(node.ownerId, node.avatarVer);
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
    allowPropertyEdit = (node: J.NodeInfo, propName: string, ast: AppState): boolean => {
        const type: TypeIntf = S.plugin.getType(node.type);
        return type ? type.allowPropertyEdit(propName, ast) : true;
    }

    isReadOnlyProperty = (propName: string): boolean => {
        return S.props.readOnlyPropertyList.has(propName);
    }

    showGraph = async (node: J.NodeInfo, searchText: string, ast: AppState) => {
        node = node || S.nodeUtil.getHighlightedNode(ast);

        const res = await S.rpcUtil.rpc<J.GraphRequest, J.GraphResponse>("graphNodes", {
            searchText,
            nodeId: node.id
        });

        dispatch("ShowGraph", s => {
            s.fullScreenConfig = { type: FullScreenType.GRAPH, nodeId: node.id };
            s.graphSearchText = searchText;
            s.graphData = res.rootNode;
            return s;
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
        const tagsDiv = node?.tags ? new Div(node.tags, { className: "nodeTags float-end " }) : null;

        return new Div(null, attribs, [
            new HorizontalLayout([
                new Div(null, { className: "friendLhs" }, [
                    img
                ]),
                new Div(null, { className: "friendRhs" }, [

                    // I'm removing this becasue we can click on the image and to these thru the Profile Dialog of the user.
                    // new ButtonBar([
                    //     // todo-2: need to make ALL calls be able to do a newSubNode here without so we don't need
                    //     // the showMessagesButton flag.
                    //     showMessageButton ? new Button("Message", S.edit.newSubNode, {
                    //         title: "Send Private Message",
                    //         nid: nodeId
                    //     }) : null,
                    //     actorUrl ? new Button("Go to User Page", () => {
                    //         window.open(actorUrl, "_blank");
                    //     }) : null
                    // ], null, "float-end"),
                    // new Clearfix(),

                    new Div(displayName, {
                        className: "userName"
                    }),
                    new Div(null, null, [
                        // we use a span because the div stretches across empty space and does a mouse click
                        // when you didn't intend to click the actual name sometimes.
                        new Span("@" + user, {
                            className: (displayName ? "" : "userName ") + "clickable",
                            onClick
                        })
                    ]),
                    tagsDiv

                    // The page just looks cleaner with the username only. We can click them to see their bio text.
                    // userBio ? new Html(userBio, {
                    //     className: "userBio"
                    // }) : null
                ])

            ], "displayTable userInfo", null)
        ]);
    }

    makeWidthSizerPanel = (): Span => {
        const ast = getAppState();
        const panelCols = ast.userPrefs.mainPanelCols || 6;

        return !ast.mobileMode ? new Span(null, { className: "widthSizerPanel float-end" }, [
            panelCols > 4 ? new Icon({
                className: "fa fa-step-backward widthSizerIcon",
                title: "Narrower view",
                onClick: () => {
                    dispatch("widthAdjust", s => {
                        S.edit.setMainPanelCols(--s.userPrefs.mainPanelCols);
                        return s;
                    });
                }
            }) : null,
            panelCols < 8 ? new Icon({
                className: "fa fa-step-forward widthSizerIcon",
                title: "Wider view",
                onClick: () => {
                    dispatch("widthAdjust", s => {
                        S.edit.setMainPanelCols(++s.userPrefs.mainPanelCols);
                        return s;
                    });
                }
            }) : null
        ]) : null;
    }
}
