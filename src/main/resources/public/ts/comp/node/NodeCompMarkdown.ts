import { getAs } from "../../AppContext";
import { Html } from "../../comp/core/Html";
import { TabIntf } from "../../intf/TabIntf";
import * as J from "../../JavaIntf";
import { S } from "../../Singletons";

interface LS {
    content: string;
    pendingDecrypt?: string;
}

export class NodeCompMarkdown extends Html {
    // detects URLs in a string (from Stack Overflow, not fully vetted yet)
    static urlRegex = /(https?:\/\/[^\s]+)/g;
    static nostrRegex = /(nostr:[^\s]+)/g;

    // I had this named 'content' but it confused TypeScript and interfered with the Html constructor,
    // but is ok named as 'cont'
    cont: string;

    /* This makes the encrypted text visible without editing the node which is important to have
    on so nodes shared to you can be seen, because a user can't edit nodes they don't own */
    private autoDecrypting: boolean = true;

    // When the rendered content contains urls we will load the "Open Graph" data and display it below the content.
    urls: string[];

    constructor(public node: J.NodeInfo, extraContainerClass: string, tabData: TabIntf<any>) {
        super(null, { key: "ncmkd_" + node.id });
        this.cont = node.renderContent || node.content;

        // if this is admin owned node we set the prop on this object to trigger base class to render without DOMPurifier
        // so that admin nodes can inject scripted content (like buttons with an onClick on them)
        this.purifyHtml = node.owner !== J.PrincipalName.ADMIN;

        if (!getAs().mobileMode) {
            const widthStyle = this.cont && this.cont.indexOf("```") !== -1 ? "contentWide" : "contentNarrow";
            this.attribs.className = "mkCont " + widthStyle;
        }
        else {
            this.attribs.className = "mkCont";
        }

        if (extraContainerClass) {
            this.attribs.className += " " + extraContainerClass;
        }

        const content = this.cont || "";
        const att: LS = {
            content: null
        };

        /* If this content is encrypted we set it in 'pendingDecrypt' to decrypt it asynchronously */
        if (content.startsWith(J.Constant.ENC_TAG)) {
            att.content = "[Encrypted]";
            att.pendingDecrypt = content;
        }
        /* otherwise it's not encrypted and we display the normal way */
        else {
            att.content = this.renderRawMarkdown(node);
        }

        this.mergeState<LS>(att);
    }

    /* If content is passed in it will be used. It will only be passed in when the node is encrypted and the text
    has been decrypted and needs to be rendered, in which case we don't need the node.content, but use the 'content' parameter here */
    renderRawMarkdown(node: J.NodeInfo, content: string = null): string {
        content = content || this.cont || "";
        let val = "";
        this.urls = null;

        // todo-2: put some more thought into this...
        // turning this off because when it appears in a url, blows up the link. Need to find some better way.
        // if (S.srch.searchText) {
        //     /* This results in a <strong><em> wrapping the text, which we have a special styling for with a green background for each
        //     search term so it's easy to see them highlighted on the page */
        //     content = content.replace(S.srch.searchText, "**_" + S.srch.searchText + "_**");
        // }

        val = S.render.injectSubstitutions(node, content);
        val = this.replaceOgImgFileNames(val);
        val = this.replaceNostrLinks(val);
        val = S.util.markdown(val);
        val = S.util.insertActPubTags(val, node);

        /* parse tags, to build OpenGraph */
        this.parseAnchorTags(val, content);
        return val;
    }

    /* It's kind of ugly here to see the URLs disappearing as the image is loaded
    so think about a way to fix that. Maybe render nothing for urls until we know
    their mime type or OpenGraph result...becasue it's better for things to magically
    appear on the screen than to disappear */
    replaceOgImgFileNames = (val: string): string => {
        // find all the urls in the val, and remove the ones that we know are doing go
        // be rendered as plain Images when OpenGraph rendering is complete.
        return val.replace(NodeCompMarkdown.urlRegex, (url: string) => {
            if (S.quanta.imageUrls.has(url)) {
                return `<img src='${url}' class='insImgInRow'>`;
            }
            else {
                return url;
            }
        });
    }

    // need to make this be an href that with target=_blank that opens the URL in a new tab.
    // so we need a new URL format like nostrId=? that checks when index.html is loading if the
    // node is not available and if not triggers the client to load from a relay and display it.
    replaceNostrLinks = (val: string): string => {
        return val.replace(NodeCompMarkdown.nostrRegex, (url: string) => {
            if (url.startsWith("nostr:note")) {
                url = url.substring(6);
                url = S.nostr.translateNip19(url);
                console.log("nip19 translated to: " + url);
                const shortId = url.substring(0, 10) + "...";
                // Note: 'nostr-note' class in here is so that our OpenGraph link detector can ignore this and leave
                // it as a regular anchor tag link
                return `<a href='${window.location.origin}?nostrId=${url}&refNodeId=${this.node.ownerId}' class='nostr-note' target='_blank'>${shortId}</a>`;
            }
            else return url;
        });
    }

    parseAnchorTags = (val: string, content: string) => {
        if (val.indexOf("<") === -1 ||
            val.indexOf(">") === -1) return;

        const elm = document.createElement("html");
        elm.innerHTML = val;

        // BEWARE: The elements we scan here are NOT part of the DOM, we are just extracting out
        // the urls here.
        elm.querySelectorAll("a").forEach((e: HTMLAnchorElement) => {
            if (!e.href) return;
            let href = e.href.trim();
            href = S.util.stripIfEndsWith(href, "/");
            const hrefWithSlash = href;
            href = S.util.replaceAll(href, "/?", "?");

            /* Mastodon has HTML content that uses hrefs for each mention or hashtag, so in order to avoid
            trying to process those for OpenGraph we detect them using the 'mention' and 'hashtag' classes */
            if (e.classList.contains("mention") ||
                e.classList.contains("hashtag") ||
                e.classList.contains("u-url") ||
                e.classList.contains("nostr-note")) return;

            // Detect if this link is part of a Markdown Named link and if so then we don't generate the OpenGraph for that either
            if (content.indexOf("(" + href + ")") !== -1) return;
            if (content.indexOf("* " + href) !== -1) return;
            if (content.indexOf("* " + hrefWithSlash) !== -1) return;

            // lazy instantiate
            this.urls = this.urls || [];
            this.urls.push(href);
        });
    }

    override preRender(): boolean {
        const state: LS = this.getState<LS>();

        if (this.autoDecrypting && state.pendingDecrypt) {
            const cipherText = state.pendingDecrypt.substring(J.Constant.ENC_TAG.length);
            const cipherHash = S.util.hashOfString(cipherText);

            // if we have already decrypted this data use the result.
            if (S.quanta.decryptCache.get(cipherHash)) {

                let clearText = S.quanta.decryptCache.get(cipherHash);
                clearText = this.renderRawMarkdown(this.node, clearText);

                this.mergeState<LS>({
                    content: clearText,
                    pendingDecrypt: null
                });
            }
            else {
                setTimeout(async () => {
                    this.decrypt();
                }, 10);
            }
        }
        return true;
    }

    decrypt = async () => {
        if (!S.crypto.avail) return;
        const state: LS = this.getState<LS>();
        if (!state.pendingDecrypt) return;
        const cipherText = state.pendingDecrypt.substring(J.Constant.ENC_TAG.length);
        // console.log("decrypting CIPHERTEXT (in NodeCompMarkdown): " + cipherText);

        const cipherKey = S.props.getCryptoKey(this.node);
        if (cipherKey) {
            // console.log("CIPHERKEY " + cipherKey);
            let clearText = await S.crypto.decryptSharableString(null, { cipherKey, cipherText });

            // Warning clearText can be "" (which is a 'falsy' value and a valid decrypted string!)
            clearText = clearText !== null ? clearText : "[Decrypt Failed]";
            clearText = this.renderRawMarkdown(this.node, clearText);

            this.mergeState<LS>({
                content: clearText,
                pendingDecrypt: null
            });
        }
    }
}
