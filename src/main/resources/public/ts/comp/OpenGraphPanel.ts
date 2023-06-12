import { getAs } from "../AppContext";
import { CompIntf } from "../comp/base/CompIntf";
import { Anchor } from "../comp/core/Anchor";
import { Div } from "../comp/core/Div";
import { Diva } from "../comp/core/Diva";
import { Icon } from "../comp/core/Icon";
import { Img } from "../comp/core/Img";
import { TabIntf } from "../intf/TabIntf";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { Divc } from "./core/Divc";
import { FlexRowLayout } from "./core/FlexRowLayout";
import { Html } from "./core/Html";

interface LS { // Local State
    og: J.OpenGraph;
    loading?: boolean;
}

export class OpenGraphPanel extends Div {
    loading: boolean;
    observer: IntersectionObserver;

    constructor(private tabData: TabIntf<any>, key: string, private url: string, private wrapperClass: string,
        private imageClass: string, private showTitle: boolean, private allowBookmarkIcon: boolean, private includeImage: boolean) {
        super(null, {
            title: url,
            key
        });

        /* The state should always contain loading==true (if currently querying the server) or a non-null 'og'. A completed but failed
         pull of the open graph data should result in og being an empty object and not null. */
        const og: J.OpenGraph = S.quanta.openGraphData.get(url);
        if (og) {
            this.mergeState<LS>({ og });
        }
    }

    override domAddEvent = () => {
        const elm: HTMLElement = this.getRef();
        if (!elm || !elm.isConnected || this.getState<LS>().og) return;
        const og = S.quanta.openGraphData.get(this.url);
        if (!og) {
            this.observer = new IntersectionObserver(entries => //
                // note: processOgEntry itself is async but we should not await for it here, because this is part of the
                // intersection observer we don't want to block.
                entries.forEach(entry => this.processOgEntry(entry, elm)));
            this.observer.observe(elm.parentElement);
        }
        else {
            this.mergeState<LS>({ og });
        }
    }

    processOgEntry = async (entry: any, elm: HTMLElement) => {
        if (!entry.isIntersecting) return;
        this.disconnect();
        await this.loadOpenGraph();
        await this.loadNext();
    }

    // DO NOT DELETE (#inline-image-rendering)
    // This can support injecting images directly into the location where they're mentioned in
    // the text but we're not doing this for now because we have a cleaner way to render images by having
    // them all be at the end of the content just like normal non-Image OpenGraph does.
    // processOgImage = (url: string, og: J.OpenGraph) => {
    //     if (og.mime?.startsWith("image/")) {
    //         if (!S.quanta.imageUrls.has(url)) {
    //             S.quanta.imageUrls.add(url);
    //             S.render.forceRender = true;
    //         }
    //     }
    // }

    /* This loads the next upcomming OpenGraph assuming the user is scrolling down. This is purely a
    performance optimization to help the user experience and is not a core part of the logic for
     'correct' functioning, but it does offer an extremely nice smooth experience when scrolling down thru content
     even including content with lots and lots of openGraph queries happening in the background. */
    loadNext = async () => {
        let found = false;
        let count = 0;
        if (!this.tabData || !this.tabData.openGraphComps) return;

        for (const comp of this.tabData.openGraphComps) {
            if (found) {
                /* I think it's counterproductive for smooth scrolling to preload more than one */
                if (count++ < 1) {
                    comp.loadOpenGraph();
                }
            }
            else if (comp.getId() === this.getId()) {
                found = true;
            }
        }
    }

    private loadOpenGraph = async () => {
        if (this.loading) return;
        let og = S.quanta.openGraphData.get(this.url);
        if (!og) {
            if (!this.loading) {
                try {
                    this.loading = true;
                    og = await this.queryOpenGraph(this.url);
                } finally {
                    this.loading = false;
                }

                og = og || {
                    title: null,
                    description: null,
                    image: null,
                    url: this.url,
                    mime: null
                };
    
                S.quanta.openGraphData.set(this.url, og);
                // this.processOgImage(o.url, og); // <-- DO NOT DELETE
                if (!this.getRef()) {
                    return;
                }
                this.mergeState<LS>({ og });
            }
        }
        else {
            // this.processOgImage(o.url, og); // <-- DO NOT DELETE
            this.mergeState<LS>({ og });
        }
    }

    disconnect = () => {
        if (this.observer) {
            this.observer.disconnect();
            this.observer = null;
        }
    }

    // Queries the url for 'Open Graph' data and sendes it back using the callback. All types of NodeInfo objects
    // we ever get from the server should already have the open graph property (sn:og) set on them so normally
    // the only time this method ever runs will be when browsing an RSS feed.
    queryOpenGraph = async (url: string): Promise<J.OpenGraph> => {
        if (!url) return null;
        // console.log("QUERY OG for " + url);
        try {
            const res: J.GetOpenGraphResponse = await S.rpcUtil.rpc<J.GetOpenGraphRequest, J.GetOpenGraphResponse>("getOpenGraph", {
                url
            }, true);
            return res.openGraph;
        }
        catch (e) {
            S.util.logErr(e);
            return null;
        }
    }

    override preRender(): boolean {
        const state = this.getState<LS>();
        const ast = getAs();
        if (state.loading || !state.og) {
            this.setChildren(null);
            return true;
        }

        // see #inline-image-rendering
        if (state.og.mime?.startsWith("image/")) {
            this.setChildren([new Img({ src: this.url, className: "insImgInRow" })]);
            return true;
        }

        /* If neither a description nor image exists, this will not be interesting enough so don't render */
        if (!state.og.description && !state.og.image) {
            this.setChildren(null);
            return false;
        }

        if (!state.og.url) {
            state.og.url = this.url;
        }

        const bookmarkIcon = this.allowBookmarkIcon && state.og.url && !ast.isAnonUser ? new Icon({
            className: "fa fa-bookmark fa-lg ogBookmarkIcon float-end",
            onClick: () => S.edit.addLinkBookmark(state.og.url, null)
        }) : null;

        if (state.og?.description?.length > 804) {
            state.og.description = state.og.description.substring(0, 800) + "...";
        }

        let imgAndDesc: CompIntf = null;
        if (state.og.image && this.includeImage) {
            // According to my test results this can cause a scrolling glitch, where the browser throws an error and somehow
            // apparently that interfered with rendering. Wasn't able to repro on localhost because of using http I think, so
            // this code is probably harmless even if I'm making a mistake blaming the scrolling glitch on this.
            state.og.image = S.util.replaceAll(state.og.image, "http://", "https://");

            // if mobile portrait mode render image above (not beside) description
            if (ast.mobileMode && window.innerWidth < window.innerHeight) {
                imgAndDesc = new Diva([
                    new Img({
                        className: "openGraphImageVert",
                        src: state.og.image
                    }),
                    new Div(state.og.description)
                ]);
            }
            else {
                // if we have an image then render a left-hand side and right-hand side.
                imgAndDesc = new FlexRowLayout([
                    !S.quanta.brokenImages.has(state.og.image) ? new Divc({ className: "openGraphLhs" }, [
                        new Img({
                            className: this.imageClass,
                            src: state.og.image
                        })
                    ]) : null,
                    new Divc({ className: "openGraphRhs" }, [
                        new Html(state.og.description, { className: "openGraphDesc" })
                    ])
                ], "smallMarginBottom");
            }
        }
        // if no image just display the description in a div
        else {
            imgAndDesc = new Divc({ className: "openGraphNoImage" }, [
                new Div(state.og.description)
            ]);
        }

        this.attribs.className = this.wrapperClass;
        this.setChildren([
            bookmarkIcon,
            this.showTitle ? (state.og.url ? new Anchor(this.url, state.og.title, {
                target: "_blank",
                className: "openGraphTitle"
            }) : new Div(state.og.title, {
                className: "openGraphTitle"
            })) : null,
            imgAndDesc
        ]);
        return true;
    }
}
