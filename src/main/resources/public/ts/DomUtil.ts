import { dispatch, getAs } from "./AppContext";
import { Comp } from "./comp/base/Comp";
import { Constants as C } from "./Constants";
import { PasteOrLinkDlg } from "./dlg/PasteOrLinkDlg";
import { UploadResponse } from "./JavaIntf";
import { S } from "./Singletons";

export class DomUtil {
    imgCache: Map<string, string> = new Map<string, string>();

    annotations: HTMLDivElement[] = [];
    mouseX: number;
    mouseY: number;
    mouseEffect: boolean = false;

    static escapeMap: Map<string, string> = new Map<string, string>([
        ["&", "&amp;"],
        ["<", "&lt;"],
        [">", "&gt;"],
        ['"', "&quot;"],
        ["'", "&#39;"],
        ["/", "&#x2F;"],
        ["`", "&#x60;"],
        ["=", "&#x3D;"]
    ]);

    getPropFromDom = (evt: Event, prop: string): string => {
        let val = null;

        // get the id from this node or any parent node.
        if (evt?.target) {
            let target: any = evt.target;
            while (target) {
                val = target.getAttribute(prop);
                if (val) return val;
                target = target.parentElement;
            }
        }

        // It's normal flow to sometimes return null here. Like deleting from the left hand menu where it calls
        // the same method as the onClick method but there's no event info or node attributes passed in that case,
        // and this is fine.
        return val;
    }

    /* set focus to element by id */
    focusId = (id: string) => {
        if (!id) return;
        Comp.focusElmId = id;
        setTimeout(() => {
            const elm = this.domElm(id);
            if (elm) {
                /* This is a fix to a focus bug using the 'safest' way to do this without any redesign.
                 If the current focus is on an 'editor' then don't let this logic focus AWAY
                 from the editor. That breaks user input/keyboard. */
                if (S.quanta.currentFocusId && S.quanta.currentFocusId.startsWith(C.ID_PREFIX_EDIT) && //
                    document.getElementById(S.quanta.currentFocusId)) {
                    return;
                }
                elm.focus({ preventScroll: true });
            }
        }, 750);
    }

    /* Takes textarea dom Id (# optional) and returns its value */
    getTextAreaValById = (id: string): string => {
        const de: HTMLInputElement = <HTMLInputElement>this.domElm(id);
        return de.value;
    }

    setInnerHTML = (elm: HTMLElement, val: string) => {
        if (elm) {
            elm.innerHTML = val;
        }
    }

    // domElmObjCss = (elm: HTMLElement, prop: string, val: string) => {
    //     if (elm) {
    //         elm.style[prop] = val;
    //     }
    // }

    // This may fail. oddly the API where i get the object from here wants to reutrn Elements not HTMLElements.
    domElmObjRemove = (elm: Element) => {
        if (elm) {
            elm.parentNode.removeChild(elm);
        }
    }

    domElmRemove = (id: string) => {
        const elm = this.domElm(id);
        if (elm) {
            elm.parentNode.removeChild(elm);
        }
    }

    /* We return a promise that resolves to the element, but also support a callback function
    that can be used optionally whenver that's more convenient */
    getElm = (id: string): Promise<HTMLElement> => {
        // Promise is used here instead of async/await because of the resolve being done inside the timer.
        return new Promise<HTMLElement>((resolve, reject) => {

            // First we immediately try to get the element.
            const e: HTMLElement = document.getElementById(id);
            if (e) {
                resolve(e);
            }
            // If element not found we just go into a wait for it (polling)
            // (is there a better native JS approach than polling for the element?)
            else {
                let accumWaitTime = 0;
                const timeSlice = 100;

                // don't hang the promise more than 3 seconds, before reporting error and continuing.
                const maxWaitTime = 3000;

                const interval = setInterval(() => {
                    accumWaitTime += timeSlice;
                    if (accumWaitTime >= maxWaitTime) {
                        console.error("waited for but never found element: " + id);
                        clearInterval(interval);
                        resolve(null);
                    }

                    const e: HTMLElement = document.getElementById(id);
                    if (e) {
                        clearInterval(interval);
                        resolve(e);
                    }
                }, timeSlice);
            }
        });
    }

    /*
    * Gets the RAW DOM element and displays an error message if it's not found. Do not prefix with "#"
    */
    domElm = (id: string): HTMLElement => {
        if (!id) return null;
        if (id.startsWith("#")) {
            console.log("domElm removed obsolete preceding # from ID " + id);
            id = id.substring(1);
        }

        if (id.includes("#")) {
            console.log("Invalid # in domElm");
            return null;
        }

        return document.getElementById(id);
    }

    forEachElmBySel = (sel: string, callback: Function) => {
        const elements = document.querySelectorAll(sel);
        Array.prototype.forEach.call(elements, callback);
    }

    escapeHtml = (str: string): string => {
        if (!str) return str;
        return String(str).replace(/[&<>"'`=\/]/g, DomUtil.escapeMapFunc);
    }

    // we have this as a separate static function just to avoid the overhead of creating a new function for every call
    // where this is used.
    static escapeMapFunc(s: any) {
        return DomUtil.escapeMap.get(s) || s;
    }

    resetDropHandler = (attribs: any) => {
        delete attribs.onDragEnter;
        delete attribs.onDragOver;
        delete attribs.onDragLeave;
        delete attribs.onDrop;
    }

    setNodeDragHandler = (attribs: any, nodeId: string) => {
        if (!nodeId) {
            return;
        }
        attribs.draggable = "true";

        attribs.onDragStart = function (ev: any) {
            const ast = getAs();
            if (ast.editNode) {
                return;
            }
            ev.currentTarget.classList.add("dragSource");
            S.quanta.dragElm = ev.target;
            S.quanta.draggingId = nodeId;
            ev.dataTransfer.setData(C.DND_TYPE_NODEID, nodeId);
            ev.dataTransfer.setDragImage(S.quanta.dragImg, 0, 0);
        };

        attribs.onDragEnd = function (ev: any) {
            ev.currentTarget.classList.remove("dragSource");
            S.quanta.dragElm = null;
            S.quanta.draggingId = null;
        };
    }

    enterFetch = () => {
        S.rpcUtil.rpcCounter++;
        S.quanta.setOverlay(true);
    }

    exitFetch = () => {
        S.quanta.setOverlay(false);
        S.rpcUtil.rpcCounter--;
        S.rpcUtil.progressInterval();
    }

    parseFiles = async (files: File[]): Promise<UploadResponse> => {
        if (!files) return;

        return new Promise<UploadResponse>((resolve, reject) => {
            const formData = new FormData();

            files.forEach((file: File) => formData.append("files", file));
            const url = S.rpcUtil.getRpcPath() + "parseFiles";

            this.enterFetch();
            fetch(url, {
                method: "POST",
                body: formData,
                headers: {
                    Bearer: S.quanta.authToken || "",
                    Sig: S.crypto.userSignature || ""
                }
            })
                .then((res: any) => {
                    if (res.status !== 200) {
                        return null;
                    }
                    else {
                        S.util.showMessage("Upload complete", "Success");
                        return res.text();
                    }
                })
                .then((json: string) => {
                    console.log("Upload Response: " + json);
                    /* if we did a reject above in the first 'then' we will get here with json undefined
                    so we ignore that */
                    this.exitFetch();
                    if (json) {
                        // console.log("rpc: " + postName + " " + (new Date().getTime() - startTime) + "ms");
                        resolve(JSON.parse(json));
                    }
                    else {
                        reject(null);
                    }
                }).catch(() => {
                    this.exitFetch();
                    S.util.showMessage("Upload failed", "Warning");
                    reject(null);
                })
        });
    }

    uploadFilesToNode = async (files: File[], nodeId: string, showConfirm: boolean): Promise<Response> => {
        if (!files) return;
        const formData = new FormData();

        files.forEach((file: File) => formData.append("files", file));
        const url = S.rpcUtil.getRpcPath() + "upload";
        formData.append("nodeId", nodeId);

        this.enterFetch();
        const promise = fetch(url, {
            method: "POST",
            body: formData,
            headers: {
                Bearer: S.quanta.authToken || "",
                Sig: S.crypto.userSignature || ""
            }
        });

        promise.then(() => {
            this.exitFetch();
            if (showConfirm) {
                S.util.showMessage("Upload complete", "Success");
            }
        })
            .catch(() => {
                this.exitFetch();
                S.util.showMessage("Upload failed", "Warning");
            });
        return promise;
    }

    // https://www.w3schools.com/jsref/tryit.asp?filename=tryjsref_ondragenter
    setDropHandler = (attribs: any, func: (elm: any) => void) => {
        attribs.onDragEnter = function (event: any) {
            // todo-1: should this also do the same thing onDragOver does? and then
            // maybe we wouldn't need onDragOver in that case?
            event.stopPropagation();
            event.preventDefault();
            event.currentTarget.classList.add("dragTarget");
        };

        attribs.onDragOver = function (event: any) {
            if (event.currentTarget === S.quanta.dragElm ||
                // we do a tiny bit of tight-coupling here and assume that if the attribs has a C.NODE_ID_ATTR property
                // then that represents the nodeId (pretty standard in this app tho)
                S.quanta.draggingId === attribs[C.NODE_ID_ATTR]) {
                return;
            }

            event.stopPropagation();
            event.preventDefault();

            // WARNING: Using 'copy' is very important or drops from outside browser can intermittently fail.
            event.dataTransfer.dropEffect = "copy";
            event.currentTarget.classList.add("dragTarget");
        };

        attribs.onDragLeave = function (event: any) {
            // if (event.currentTarget === S.quanta.dragElm) return;
            event.stopPropagation();
            event.preventDefault();
            event.currentTarget.classList.remove("dragTarget");
        };

        attribs.onDrop = function (event: any) {
            event.stopPropagation();
            event.preventDefault();
            event.currentTarget.classList.remove("dragTarget");
            func(event);
        };
    }

    enableMouseEffect = async () => {
        const mouseEffect = await S.localDB.getVal(C.LOCALDB_MOUSE_EFFECT);
        this.mouseEffect = mouseEffect === "1";
    }

    /* #mouseEffects (do not delete tag) */
    setMouseEffect = (mouseEffect: boolean) => {
        dispatch("ToggleMouseEffect", s => {
            this.mouseEffect = mouseEffect;
            S.localDB.setVal(C.LOCALDB_MOUSE_EFFECT, this.mouseEffect ? "1" : "0");
        });
    }

    /*
    The other part of this is contained in click-effects.scss
    */
    initClickEffect = () => {
        document.addEventListener("click", (e: MouseEvent) => {
            // use a timeout so we can call 'getState()' without a react error.
            setTimeout(() => {
                /* looks like for some events there's not a good mouse position (happened on clicks to drop down cobo boxes),
                 and is apparently 0, 0, so we just check the sanity of the coordinates here */
                if (!this.mouseEffect || (e.clientX < 10 && e.clientY < 10)) return;
                this.runClickAnimation(e.clientX, e.clientY);
            }, 10);
        });
    }

    runClickAnimation = (x: number, y: number) => {
        const d = document.createElement("div");
        d.className = "clickEffect";

        /* todo-2: make this 5 and 12 offset user configurable. I'm using a custom moust pointer that draws a yellow
        circle around my mouse for use with this effect, to record screencast videos, and that icon circle is not centered
        around the actual mouse click arrow tip location, so we have to use an offset here (only when that Linux OS mouse theme is used)
        to get our expanding circle in CSS to be perfectly centered with the one in the mouse theme, because an off center look
        is terrible but the 5 and 12 makes it perfect */
        d.style.left = `${x - 2}px`;
        d.style.top = `${y - 2}px`;
        document.body.appendChild(d);

        setTimeout(() => {
            d.parentElement.removeChild(d);
        }, 300); // this val is in 3 places. put the TS two in a constants file.
    }

    addAnnotation = () => {
        let arrowOption = window.prompt("Annotation Location: tl,tr,bl,br");
        if (!arrowOption) {
            arrowOption = "tl";
        }

        const text = window.prompt("Annotation Text:");
        if (!text) {
            return;
        }

        const d = document.createElement("div");

        const a = document.createElement("div");
        a.className = "arrowUp";
        a.style.left = `${this.mouseX + 15}px`;
        a.style.top = `${this.mouseY - 10}px`;
        document.body.appendChild(a);

        const h = document.createElement("h4");
        h.className = "annotationText";
        const c: any = document.createTextNode(text);
        c.className = "annotationText";
        h.appendChild(c);
        d.appendChild(h);

        d.className = "annotationBox";
        d.style.left = `${this.mouseX}px`;
        d.style.top = `${this.mouseY}px`;
        d.setAttribute(C.ARROW_OPTION_ATTR, arrowOption);
        this.annotations.push(d);
        this.annotations.push(a);
        document.body.appendChild(d);
        this.dragElement(d, a);
    }

    removeAnnotation = () => {
        if (this.annotations.length > 0) {
            const a = this.annotations.pop();
            a.parentElement.removeChild(a);

            const e = this.annotations.pop();
            e.parentElement.removeChild(e);
        }
    }

    // from here: https://www.w3schools.com/howto/howto_js_draggable.asp
    dragElement(elmnt: any, arrow: any) {
        let pos1 = 0;
        let pos2 = 0;
        let pos3 = 0;
        let pos4 = 0;

        // DO NOT DELETE
        // This code can be used in the future for moving something using a dialog header for example
        // if (document.getElementById(elmnt.id + "header")) {
        //     /* if present, the header is where you move the DIV from: */
        //     document.getElementById(elmnt.id + "header").onmousedown = dragMouseDown;
        // } else {
        //     /* otherwise, move the DIV from anywhere inside the DIV: */
        //     elmnt.onmousedown = dragMouseDown;
        // }
        elmnt.onmousedown = dragMouseDown;

        function dragMouseDown(e: any) {
            e = e || window.event;
            e.preventDefault();

            pos3 = e.clientX;
            pos4 = e.clientY;
            document.onmouseup = closeDragElement;

            document.onmousemove = elementDrag;
            elmnt.style.cursor = "move";
        }

        function elementDrag(e: any) {
            e = e || window.event;
            e.preventDefault();

            pos1 = pos3 - e.clientX;
            pos2 = pos4 - e.clientY;
            pos3 = e.clientX;
            pos4 = e.clientY;

            const targX = elmnt.offsetLeft - pos1;
            const targY = elmnt.offsetTop - pos2;

            elmnt.style.left = targX + "px";
            elmnt.style.top = targY + "px";

            if (arrow) {
                switch (elmnt.getAttribute(C.ARROW_OPTION_ATTR)) {
                    case "tl":
                        arrow.style.left = (targX + 15) + "px";
                        arrow.style.top = (targY - 10) + "px";
                        break;
                    case "tr":
                        arrow.style.left = (targX + elmnt.clientWidth - 30) + "px";
                        arrow.style.top = (targY - 10) + "px";
                        break;
                    case "bl":
                        arrow.style.left = (targX + 15) + "px";
                        arrow.style.top = (targY + elmnt.clientHeight - 1) + "px";
                        arrow.className = "arrowDown";
                        break;
                    case "br":
                        arrow.style.left = (targX + elmnt.clientWidth - 30) + "px";
                        arrow.style.top = (targY + elmnt.clientHeight - 1) + "px";
                        arrow.className = "arrowDown";
                        break;
                    default: break;
                }
            }
        }

        function closeDragElement() {
            /* stop moving when mouse button is released: */
            document.onmouseup = null;
            document.onmousemove = null;
            elmnt.style.cursor = "default";
        }
    }

    makeDropTarget = (attribs: any, id: string) => {
        attribs[C.NODE_ID_ATTR] = id;
        S.domUtil.setDropHandler(attribs, (evt: DragEvent) => {
            // todo-2: right now we only actually support one file being dragged? Would be nice to support multiples
            for (const item of evt.dataTransfer.items) {
                // console.log("DROP(b) kind=" + item.kind + " type=" + item.type);

                if (item.type.startsWith("image/") && item.kind === "file") {
                    const file: File = item.getAsFile();

                    // if (file.size > Constants.MAX_UPLOAD_MB * Constants.ONE_MB) {
                    //     S.util.showMessage("That file is too large to upload. Max file size is "+Constants.MAX_UPLOAD_MB+" MB");
                    //     return;
                    // }

                    // todo-1: this is an ugly solution because it displays the upload dialog
                    // and takes the user to the node that got uploade onto. I'd like to NOT do that,
                    // but just make it behind the scenes automatic.
                    S.attachment.openUploadFromFileDlg(false, id, file);
                    return;
                }
                else if (item.type === C.DND_TYPE_NODEID && item.kind === "string") {
                    item.getAsString(async (s) => {
                        if (attribs[C.NODE_ID_ATTR] === s) {
                            S.util.showMessage("Can't copy a node to itself.");
                            return;
                        }
                        const dlg = new PasteOrLinkDlg(id, s);
                        await dlg.open();
                    });
                    return;
                }
            }
        });
    }

    // Allow copy to clipboard when backtick text clicked.
    codeSpanClick = (elm: HTMLElement) => {
        // if user just selected some text we don't do anything, because it won't make sense
        // becuase this code is incompatable and not appliable to that scenario
        if (window.getSelection()?.toString()) return;

        if (elm.parentElement.innerText) {
            S.util.copyToClipboard(elm.parentElement.innerText?.trim());
            S.util.flashMessage("Copied to Clipboard", "Clipboard", true);
        }
    }

    highlightBrowserText = (text: string) => {
        if (!text) return;
        if ((window as any).find) {
            // get this string here, because the delay timer would invalidate the idx.
            let findText = text.trim();
            // if the exact text being read happens to be onscreen highlight it!

            // trying to find strings longer than about 30 seems to just intermittently fail, but strings
            // under 30 always work. This appers to be a bug in the browser.
            findText = findText.substring(0, 30);
            findText = S.util.chopAtLastChar(findText, " ");
            (window as any).find(findText, true, false, true);
        }
    }

    /* Highlights 'text' everywhere it's found in the DOM. Pass 'document.body' as rootElm
       to replace on your whole web page */
    public highlightText = (rootElm: HTMLElement, text: string) => {
        if (text.startsWith("\"") && text.endsWith("\"")) {
            text = text.replace("\"", "");
        }
        const reg = this.escapeRegEx(text);
        const regex = new RegExp(reg, "i"); // case insensitive search
        const allRegex = new RegExp(`(${reg})`, "gi"); // case insensitive replacer

        this.domHighlight(rootElm, regex, allRegex);
    }

    public escapeRegEx = (text: string): string => {
        // todo-1: from StackOverflow (not yet fully vetted)
        return text.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, "\\$&");
    }

    private domHighlight = (elm: HTMLElement, regex: RegExp, allRegex: RegExp): void => {
        if (elm.hasChildNodes()) {
            elm.childNodes.forEach((e: HTMLElement) => this.domHighlight(e, regex, allRegex));
        }
        else if (elm.nodeType === 3) { // 3 == Text.TEXT_NODE
            if (elm.textContent.search(regex) !== -1 && !elm.classList?.contains("highlightText")) {
                const newElement = document.createElement("span");
                newElement.innerHTML = elm.textContent.replace(allRegex, '<span class="highlightText">$1</span>');
                elm.replaceWith(newElement)
            }
        }
    }
}
