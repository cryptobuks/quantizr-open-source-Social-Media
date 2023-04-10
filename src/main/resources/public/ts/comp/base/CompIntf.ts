import { ReactNode } from "react";

export interface CompIntf {
    attribs: any;
    debug: boolean;
    ordinal: number;

    getId(prefix?: string): string;
    onMount(func: Function): void;

    mergeState<T = any>(moreState: T): void;
    setState<T = any>(newState: T): void;
    getState<T = any>(): T;

    setClass(clazz: string): void;
    getAttribs() : Object;
    compRender(): ReactNode;
    addChild(comp: CompIntf): void;
    insertFirstChild(comp: CompIntf): void;
    hasChildren(): boolean;
    setChildren(comps: CompIntf[]): void;
    getChildren(): CompIntf[];
    getRef(warn: boolean): HTMLElement;
    render(): any;
    getCompClass(): string;
    create(): ReactNode;
    tag(type: any, props?: object, childrenArg?: any[]): ReactNode;
    ordinalSortChildren(): void;
    preRender(): boolean;
}
