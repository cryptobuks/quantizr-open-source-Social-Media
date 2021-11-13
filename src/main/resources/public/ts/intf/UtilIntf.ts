import { AppState } from "../AppState";
import * as I from "../Interfaces";
import * as J from "../JavaIntf";
import { EventInput } from "@fullcalendar/react";
import { DialogBase } from "../DialogBase";

export interface UtilIntf {
    logAjax: boolean;
    waitCounter: number;
    pgrsDlg: any;

    allowIdFromEvent(evt: Event, id: string): string;
    delayFunc(func: Function): Function;
    validUsername(inputtxt: string): boolean;
    hashOfString(s: string): string;
    hashOfObject(obj: Object);
    isImageFileName(fileName: string): boolean;
    isAudioFileName(fileName: string): boolean;
    isVideoFileName(fileName: string): boolean;
    isEditableFile(fileName: string): boolean;
    prettyPrint(obj: Object): string;
    buf2hex(arr: Uint8Array): string;
    hex2buf(str: string): Uint8Array;
    escapeRegExp(s: string): string;
    escapeHtml(str: string): string;
    replaceAll(s: string, find: string, replace: string): string;
    chopAtLastChar(str: string, char: string): string;
    stripIfStartsWith(s: string, str: string): string;
    stripIfEndsWith(s: string, str: string): string;
    arrayClone(a: any[]): any[];
    arrayIndexOfItemByProp(a: any[], propName: string, propVal: string): number;
    arrayMoveItem(a: any[], fromIndex: number, toIndex: number);
    stdTimezoneOffset(date: Date);
    addTimezoneOffset(date: Date, sign: number): Date;
    getDayOfWeek(date: Date): string;
    dst(date: Date);
    indexOfObject(arr: any[], obj);
    domSelExec(selectors: string[], func: Function, level?: number);

    daylightSavingsTime: boolean;

    getCheckBoxStateById(id: string): boolean;
    toJson(obj: Object): string;

    getParameterByName(name?: any, url?: any): string;
    initProgressMonitor(): void;
    progressInterval(state: AppState): void;
    getHostAndPort(): string;
    getRpcPath(): string;
    getRemoteHost(): string;

    // todo-0: both callbacks can now be eliminated with async/await
    ajax<RequestBase, ResponseType>(postName: string, postData: RequestBase,
        callback?: (response: ResponseType) => void,
        failCallback?: (response: string) => void,
        background?: boolean): Promise<any>;

    logAndThrow(message: string);
    logAndReThrow(message: string, exception: any);
    ajaxReady(requestName): boolean;
    isAjaxWaiting(): boolean;
    isElmVisible(elm: HTMLElement);
    restoreFocus(): void;
    focusId(id: string): void;
    checkSuccess(opFriendlyName, res): boolean;
    flashMessage(message: string, title: string, preformatted?: boolean, sizeStyle?: string): void;
    showMessage(message: string, title?: string, preformatted?: boolean, sizeStyle?: string): Promise<DialogBase>;
    addAllToSet(set: Set<string>, array): void;
    nullOrUndef(obj): boolean;
    elementExists(id): boolean;
    getTextAreaValById(id): string;
    domElm(id: string): HTMLElement;
    domElmObjRemove(elm: Element);
    domElmRemove(id: string): void;
    domElmObjCss(elm: HTMLElement, prop: string, val: string): void;
    setInnerHTMLById(id: string, val: string): void;
    setInnerHTML(elm: HTMLElement, val: string): void;
    isObject(obj: any): boolean;
    currentTimeMillis(): number;
    getInputVal(id: string): any;
    setInputVal(id: string, val: string): boolean;
    verifyType(obj: any, type: any, msg: string);
    setHtml(id: string, content: string): void;
    setElmDisplayById(id: string, showing: boolean);
    setElmDisplay(elm: HTMLElement, showing: boolean);
    getPropertyCount(obj: Object): number;
    forEachElmBySel(sel: string, callback: Function): void;
    forEachProp(obj: Object, callback: I.PropertyIterator): void;
    printKeys(obj: Object): string;
    setEnablement(elmId: string, enable: boolean): void;
    getInstance<T>(context: Object, name: string, ...args: any[]): T;
    copyToClipboard(text: string);
    triggerCustom(elm: HTMLElement, evt: string, obj: Object): void;
    trigger(elm: HTMLElement, evt: string): void;
    formatDate(date): string;
    formatDateShort(date): string;
    updateHistory(node: J.NodeInfo, childNode: J.NodeInfo, appState: AppState): void;
    getElm(id: string, exResolve?: (elm: HTMLElement) => void): Promise<HTMLElement>;
    animateScrollToTop(): any;
    assert(check: boolean, op: string): void;
    formatMemory(val: number): string;
    getBrowserMemoryInfo(): string;
    perfStart(): number;
    perfEnd(message: string, startTime: number): void;
    getPathPartForNamedNode(node: J.NodeInfo): string;
    getPathPartForNamedNodeAttachment(node: J.NodeInfo): string;
    removeHtmlTags(text: string): string;
    setDropHandler(attribs: any, func: (elm: any) => void): void;
    resetDropHandler(attribs: any): void;
    generateNewCryptoKeys(state: AppState): any;
    buildCalendarData(items: J.CalendarItem[]): EventInput[];
    markdown(val: string): any;
    allChildrenAreSameOwner(node: J.NodeInfo): boolean;
    formatCurrency(n: number): string;
    publishNodeToIpfs(node: J.NodeInfo): any;
    loadNodeFromIpfs(node: J.NodeInfo): any;
    getSharingNames(node: J.NodeInfo, multiLine: boolean): string;
    insertString(val: string, text: string, position: number): string;
    showBrowserInfo(): void;
    switchBrowsingMode(state: AppState): void;
    isLocalUserName(userName: string): boolean;
    getPropFromDom(evt: Event, prop: string): string;
    getShortContent(node: J.NodeInfo): string;
    loadOpenGraph(urlRemote: string, callback: Function): void;
}
