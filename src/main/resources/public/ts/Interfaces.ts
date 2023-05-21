import * as J from "./JavaIntf";

/* These are Client-side only models, and are not seen on the server side ever */

export class IndexedDBObj {
    k: string;
    v: any;
}

export class NostrMetadataDispInfo {
    display: string;
    title: string;
    picture: string;
}

export enum FullScreenType {
    // eslint-disable-next-line no-unused-vars
    NONE, IMAGE, GRAPH, CALENDAR
}

export class DomainType {
    // eslint-disable-next-line no-unused-vars
    public static Text: string = "Text";
    public static Date: string = "Date";
    public static Number: string = "Number";
}

// For prop types defined in config-text.yaml
export interface ConfigProp {
    label: string;
    width: number;
    ord: number;
    showTime: boolean;
}

export interface EditorOptions {
    tags?: boolean;
    nodeName?: boolean;
    priority?: boolean;
    wordWrap?: boolean;
    encrypt?: boolean;
    sign?: boolean;
    inlineChildren?: boolean;
}

export interface FullScreenConfig {
    type: FullScreenType;
    nodeId?: string;
    ordinal?: number;
}

export interface ValueIntf {
    getValue(): any;
    setValue(val: any): void;
    getValidationError?(): string;
}

/* Function Prototype/Signatore, It is assumed that a return value of false, will abort the iteration, and true continues iterating */
export interface PropertyIterator {
    (prop: string, val: any): boolean;
}

/* Models a time-range in some media where an AD starts and stops */
export class AdSegment {
    constructor(public beginTime: number, public endTime: number) {
    }
}

export interface TextEditorIntf {
    setWordWrap(wordWrap: boolean): void;
    setMode(mode: string): void;
    insertTextAtCursor(text: string, pos?: number): void;
    getSelStart(): number;
    getValue(): string;
    setValue(val: string): void;
    setEnabled(val: boolean): void;
    focus(): void;
    onMount(func: Function): void;
    setError(error: string): void;
}

export interface CheckboxIntf {
    setChecked(checked: boolean): void;
    getChecked(): boolean;
}

export interface PrivilegeInfo {
    privilegeName: string;
}

export interface NodePrivilegesInfo {
    aclEntries: J.AccessControlInfo[];
    owners: string[];
}

export interface MenuPanelState {
    expanded: Set<string>;
}
