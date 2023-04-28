import DOMPurify from "dompurify";
import { createElement, ReactNode, useEffect, useLayoutEffect, useRef } from "react";
import { Constants as C } from "../../Constants";
import { S } from "../../Singletons";
import { State } from "../../State";
import { CompIntf } from "./CompIntf";

/**
 * Base class for all components which encapsulates a lot of React functionality so that our implementation
 * code can ignore those details.
 */
export abstract class Comp implements CompIntf {
    private parent: Comp = null; // only used for debug logging (can be deleted without impacting app)
    static renderCounter: number = 0;
    static focusElmId: string = null;
    public debug: boolean = false;
    public mounted: boolean = false;
    public rendered: boolean = false;
    private static guid: number = 0;
    private static renderClassInDom: boolean = false;

    // this is a global flag for overriding/disabling scroll setting which we need to do in some cases.
    static allowScrollSets = true;

    attribs: any;

    /* Note: NULL elements are allowed in this array and simply don't render anything, and are required to be tolerated and ignored
    WARNING: TypeScript is NOT enforcing that children be private here.
    */
    private children: CompIntf[];

    // holds queue of functions to be ran once this component exists in the DOM.
    domAddFuncs: ((elm: HTMLElement) => void)[];

    // default all these to null so that unless derived class sets the value we never need
    // to create some of the useEffect calls
    public domPreUpdateEvent: React.EffectCallback = null;
    public domUpdateEvent: React.EffectCallback = null;
    public domRemoveEvent: React.EffectCallback = null;
    public domAddEvent: () => void = null;

    public preRenderRejected: boolean = false;

    // we have the 'target' here to make our purifier keep those rendered
    // see also: #onclick-security-note
    static readonly DOM_PURIFY_CONFIG = { USE_PROFILES: { html: true }, ADD_ATTR: ["target"/*, "onclick" */] };
    public ordinal: number;

    constructor(attribs?: any, private stateMgr?: State) {
        if (typeof attribs === "string") {
            throw new Error("string was passed for 'attribs' in " + this.constructor.name);
        }
        if (this.debug) {
            console.log("construct: " + this.constructor.name);
        }
        this.attribs = attribs || {};

        if (this.attribs.title && !this.attribs.title.startsWith("*\n")) {
            this.attribs.title = "Tip:\n" + this.attribs.title;
        }

        // for debugging, shows classname in every dom element as an attribute.
        if (Comp.renderClassInDom) {
            // if 'c' property not defined from a higher level up define it here as class name
            if (!this.attribs.c) {
                this.attribs = { c: this.constructor.name, ...this.attribs };
            }
        }
        else {
            // components can specify 'c' (to name a generic Div for example), and if not needed we remove it here.
            if (this.attribs.c) {
                delete this.attribs.c;
            }
        }

        /* If an ID was specifically provided, then use it, or else generate one. We prefix with 'c' only because
        IDs can't start with a number. */
        this.setId(this.attribs.id || Comp.getNextId());
    }

    public static getNextId(): string {
        return "c" + (++Comp.guid).toString(36);
    }

    public managesState = (): boolean => {
        return !!this.stateMgr;
    }

    /* Not currently used, but let's keep this */
    public getRefAsync = (warn: boolean = true): Promise<HTMLElement> => {
        return new Promise<HTMLElement>((resolve, reject) => {
            const elm = this.getRef();

            // if we have element already, resolve with it, we're done
            if (elm) {
                resolve(elm);
            }
            else return S.domUtil.getElm(this.getId());
        });
    }

    public getRef = (warn: boolean = true): HTMLElement => {
        if (this.preRenderRejected) {
            // console.warn("getRef was called on id " + this.getId() + " (class: " + this.getCompClass() + ") which will return null because of preRenderRejected");
            return null;
        }

        let ret = null;
        if (this.attribs.ref) {
            // Not sure if isConnected is needed here.
            ret = this.attribs.ref.current?.isConnected ? this.attribs.ref.current : null;
        }

        if (!ret) {
            const elm: HTMLElement = document.getElementById(this.getId());
            if (elm) {
                console.log("Repaired ref: " + this.getId() + "\n" + (new Error().stack));

                // I tried to simply "repair" the 'ref' in this oddball case, but the message below is shown
                // if I do, so for now I'm just setting the return value leaving attribs alone.
                // Warning: Unexpected ref object provided for div. Use either a ref-setter function or React.createRef().
                // this.attribs.ref = elm;
                ret = elm;
            }
        }

        if (!ret && warn) {
            console.log("getRef failed on " + this.getCompClass() + " mounted=" + this.mounted +
                "\nELEMENTS Stack: " + this.getAncestry());
        }
        return ret;
    }

    getId(prefix: string = null): string {
        return prefix ? prefix + this.attribs.id : this.attribs.id;
    }

    public setId(id: string) {
        this.attribs.id = id;
        this.attribs.key = this.attribs.key || id;
    }

    getCompClass = (): string => {
        return this.constructor.name + "_" + this.getId();
    }

    static nextGuid(): number {
        return ++Comp.guid;
    }

    static getCssPrefix(): string {
        return `C${(++Comp.guid).toString(36)}_`;
    }

    /* Schedules a function to get run whenever this element comes into existence, or will cause
     the function to run immediately of the component is already mounted */
    onMount(func: (elm: HTMLElement) => void) {
        if (!func) return;
        // If we happen to already have the ref, we can run the 'func' immediately and be done
        // or else we add 'func' to the queue of functions to call when component does get mounted.
        const elm = this.getRef(false);
        if (elm) {
            func(elm);
            return;
        }

        if (this.debug) {
            console.log("queueing onMount function on " + this.getCompClass());
        }

        // queue up the 'func' to be called once the domAddEvent gets executed.
        this.domAddFuncs = this.domAddFuncs || [];
        this.domAddFuncs.push(func);
    }

    setClass(clazz: string): void {
        this.attribs.className = clazz;
    }

    insertFirstChild(comp: CompIntf): void {
        if (!comp) return;
        this.children = this.children || [];
        this.children.unshift(comp);
    }

    addChild(comp: CompIntf): void {
        if (!comp) return;
        this.children = this.children || [];
        this.children.push(comp);
    }

    addChildren(comps: Comp[]): void {
        if (!comps || comps.length === 0) return;
        this.children = this.children || [];
        this.children.push.apply(this.children, comps);
    }

    /* Returns true if there are any non-null children */
    hasChildren(): boolean {
        return this.children?.some(child => !!child);
    }

    setChildren(comps: CompIntf[]) {
        this.children = comps;
    }

    getChildren(): CompIntf[] {
        return this.children;
    }

    getChildrenWithFirst(first: any): any[] {
        if (!first) return this.children;
        return this.children ? [first, ...this.children] : [first];
    }

    getAttribs(): Object {
        return this.attribs;
    }

    create = (): ReactNode => {
        this.wrapClick(this.attribs);
        return createElement(this.render, this.attribs);
    }

    wrapClick = (obj: any) => {
        // If 'mouseEffect' is turned on we impose a delay before processing each mouse click in order to
        // give the animation time to run.
        if (obj?.onClick) {
            const func = obj.onClick;

            // Not fat arrow, because we need 'arguments',
            // create a new function that injects calls to userActive
            obj.onClick = function (evt: any) {
                S.rpcUtil.userActive();
                func.apply(null, arguments);
            };

            if (S.domUtil.mouseEffect) {
                obj.onClick = S.util.delayFunc(obj.onClick);
            }
        }
    }

    // We take an array of 'any', because some of the children may be strings.
    private createChildren(children: any[]): ReactNode[] {
        if (!children || children.length === 0) return null;

        return children.map((child: any) => {
            if (child instanceof Comp) {
                try {
                    child.parent = this; // only done for debugging.
                    return child.create();
                }
                catch (e) {
                    S.util.logErr(e, "Failed to render child " + child.getCompClass() + " attribs.key=" + child.attribs.key);
                    return null;
                }
            }
            else {
                return child;
            }
        }).filter(c => !!c);
    }

    focus(): void {
        // immediately assign this as the focused element ID
        Comp.focusElmId = this.getId();

        this.onMount(() => {
            // if we're still the focused id, then we do the focus, but due to async nature some other thing
            // could have technically taken over focus and we might do nothing here.
            if (Comp.focusElmId === this.getId()) {
                S.domUtil.focusId(Comp.focusElmId);
            }
        });
    }

    public static getDangerousHtml = (content: string) => {
        return { __html: DOMPurify.sanitize(content, Comp.DOM_PURIFY_CONFIG) };
    }

    /* Renders this node to a specific tag, including support for non-React children anywhere in the subgraph
    Note: Tag can also be a type here, not just a string.
    */
    tag = (type: any, props?: object, childrenArg?: any[]): ReactNode => {
        if (Array.isArray(props)) {
            console.error("tag called with props as array in " + this.getCompClass());
            return;
        }

        props = props ? { ...this.attribs, ...props } : this.attribs;

        // for debugging, shows classname in every dom element as an attribute.
        if (Comp.renderClassInDom) {
            props = { c: this.constructor.name, ...props };
        }

        // If this is a raw HTML component just render using 'attribs', which is what react expects.
        if ((props as any).dangerouslySetInnerHTML) {
            return createElement(type, props);
        }

        childrenArg = childrenArg || this.children;

        try {
            const children = this.createChildren(childrenArg);

            this.wrapClick(props);
            if (children?.length > 0) {
                // special case where tbody always needs to be immediate child of table
                // https://github.com/facebook/react/issues/5652
                if (type === "table") {
                    // this is just wrapping the children in a tbody and giving it a key so react won't panic.
                    return createElement(type, props, [createElement("tbody", { key: (props as any).key + "_tbody" }, children)]);
                }
                else {
                    return createElement(type, props, children);
                }
            }
            else {
                return createElement(type, props);
            }
        }
        catch (e) {
            S.util.logErr(e, "Failed in Comp.tagRender" + this.getCompClass() + " attribs=" + S.util.prettyPrint(this.attribs));
        }
    }

    checkState = (): boolean => {
        if (!this.stateMgr) {
            if (!this.rendered) {
                // we allow a lazy creation of a State as long as component hasn't rendered yet. This is because the
                // 'useState' can only be called inside the render method due to the "Rules of Hooks".
                // The normal pattern is that a component will call mergeState in the constructor to initialize some state
                this.stateMgr = new State();
            }
            else {
                console.error("non-state component " + this.getCompClass() + " attempted to use stateMgr, after renderd");
                return false;
            }
        }
        return true;
    }

    mergeState<T>(moreState: T): void {
        if (!this.checkState()) return;
        this.stateMgr.mergeState<T>(moreState);
    }

    setState = <T>(newState: T) => {
        if (!this.checkState()) return;
        this.stateMgr.setState<T>(newState);
    }

    getState<T>(): T {
        if (!this.checkState()) return null;
        return this.stateMgr.state;
    }

    // Core 'render' function used by react. This is THE function for the functional component this object represents
    render = (): any => {
        this.rendered = true;

        try {
            if (this.stateMgr) {
                this.stateMgr.useState();
            }

            // This 'useEffect' with the empty dependency array (last param) is essentially the 'componentDidMount'
            // effect, and our app maps this to calling the 'domAddEvent'
            useEffect(() => {
                // Supposedly React 18+ will call useEffect twice on a mount (in strict mode, at least)
                // so that's the only reason we're checking for !mounted here in this if condition.
                if (!this.mounted) {
                    this.mounted = true;
                    // because of the empty dependencies array in useEffect this only gets called once when the component mounts.
                    this.domAdd();
                    if (this.domAddEvent) this.domAddEvent(); // call overridable method.

                    if (this.getScrollPos() !== null) {
                        this.scrollDomAddEvent();
                    }
                }

                // the return value of the useEffect function is what will get called when component unmounts, and was
                // in the original pre-hooks React analogous to 'compnentWillUnmount'
                return () => {
                    this.mounted = false;
                    if (this.domRemoveEvent) this.domRemoveEvent();
                };
            }, []);

            // Note: The useEffect with no array as second parameter (dependencies) is how we capture an update event
            // for all state changes.
            if (this.domUpdateEvent) useEffect(this.domUpdateEvent);
            if (this.domPreUpdateEvent) useLayoutEffect(this.domPreUpdateEvent);

            if (this.getScrollPos() !== null) {
                useLayoutEffect(this.scrollDomPreUpdateEvent);
            }

            Comp.renderCounter++;
            if (this.debug) {
                console.log("render: " + this.getCompClass() + " counter=" + Comp.renderCounter + " ID=" + this.getId());
            }

            // React intermittently has a problem where it say use forwardRef. Even after
            // a ton of testing that all went fine sometimes React will just start complaining with the error that
            // we should be using forwardRef, which I haven't yet found a way to do that's compatable with the rest
            // of our framework (i.e. this Comp class)
            this.attribs.ref = useRef();

            if (!this.preRender()) {
                this.preRenderRejected = true;
                return null;
            }
            const ret = this.compRender();

            if (this.debug) {
                // console.log("render done: " + this.getCompClass() + " counter=" + Comp.renderCounter + " ID=" + this.getId());
            }

            return ret;
        }
        catch (e) {
            S.util.logErr(e, "Failed to render child (in render method) " + this.getCompClass() + " attribs.key=" + this.attribs.key + "\nError: " + e +
                "\nELEMENTS Stack: " + this.getAncestry());
            return null;
        }
    }

    /* Get a printable string that contains the parentage of the component as far as we know it back to the root level */
    getAncestry() {
        let stack = "";
        let comp: Comp = this;
        while (comp) {
            stack = comp.getCompClass() + (stack ? " / " : "") + stack;
            comp = comp.parent;
        }
        return stack;
    }

    private domAdd = (): void => {
        // console.log("domAddEvent: " + this.getCompClass());
        const elm = this.getRef(false);
        if (!elm) {
            return;
        }

        this.maybeFocus();
        this.runQueuedFuncs(elm);
    }

    maybeFocus = () => {
        /* React can loose focus so we manage that state ourselves using Comp.focusElmId */
        if (Comp.focusElmId && this.attribs.id === Comp.focusElmId) {
            S.domUtil.focusId(Comp.focusElmId);
        }
    }

    runQueuedFuncs = (elm: HTMLElement) => {
        this.domAddFuncs?.forEach(func => func(elm));
        this.domAddFuncs = null;
    }

    /* Intended to be optionally overridable to set children, and the ONLY thing to be done in this method should be
    just to set the children. To indicate to NOT render the component at all return false from this method */
    preRender(): boolean {
        return true;
    }

    // This is the function you override/define to implement the actual render method, which is simple and decoupled from state
    // managemnt aspects that are wrapped in 'render' which is what calls this, and the ONLY function that calls this.
    abstract compRender(): ReactNode;

    scrollDomAddEvent = () => {
        if (C.DEBUG_SCROLLING) {
            console.log("scrollDomAddEvent: " + this.getCompClass());
        }
        const elm = this.getRef();
        if (elm) {
            if (Comp.allowScrollSets) {
                elm.scrollTop = this.getScrollPos();
            }

            elm.addEventListener("scroll", () => {
                if (C.DEBUG_SCROLLING) {
                    console.log("Scroll Evt [" + this.getCompClass() + "]: elm.scrollTop=" + elm.scrollTop);
                }
                this.setScrollPos(elm.scrollTop);
            }, { passive: true });
        }
    }

    scrollDomPreUpdateEvent = () => {
        if (!Comp.allowScrollSets) return;
        const elm = this.getRef();
        if (elm) {
            if (C.DEBUG_SCROLLING) {
                console.log("scrollDomPreUpdateEvent [" + this.getCompClass() + "]: elm.scrollTop=" + elm.scrollTop + " elm.scrollHeight=" + elm.scrollHeight);
            }
            elm.scrollTop = this.getScrollPos();
        }
    }

    /* If a component wants to persist it's scroll position across re-renders, all that's required is to
     override the getScrollPos and setScrollPos, being sure to use a backing variable that is NOT component-scoped
     (or it could be static var on the component if there's always only one such component to ever exist, as is
     the case for example with the LHS panel (menu) or RHS panels of the main app layout)
     Returning null from getScrollPos (the default behavior of this base class) indicates to NOT do any scroll persistence.
     */
    getScrollPos = (): number => {
        return null;
    }

    setScrollPos = (pos: number): void => {
    }

    /* Components should call this method instad of setting scrollTop directly on an element */
    setScrollTop = (pos: number): void => {
        // if this returns null it means we're not persisting scrolling in this comp and
        // we skip that logic.
        if (this.getScrollPos()) {
            this.setScrollPos(pos);
        }

        const elm = this.getRef();
        if (elm) {
            if (C.DEBUG_SCROLLING) {
                console.log("setScrollTop [" + this.getCompClass() + "]: elm.scrollTop=" + pos + " elm.scrollHeight=" + elm.scrollHeight);
            }
            elm.scrollTop = pos;
        }
    }

    // There are a few very special places where we need to sort components
    // that may have been added in an order we don't want this this is how we do it.
    ordinalSortChildren = () => {
        if (!this.children) return;
        this.children.sort((a, b) => a.ordinal - b.ordinal);
    }
}

export class ScrollPos {
    scrollPos: number = 0;

    getVal = (): number => {
        return this.scrollPos;
    }

    setVal = (p: number): void => {
        this.scrollPos = p;
    }
}
