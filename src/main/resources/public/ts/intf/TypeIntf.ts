import { Comp } from "../comp/base/Comp";
import { CompIntf } from "../comp/base/CompIntf";
import { ConfigProp, EditorOptions } from "../Interfaces";
import * as J from "../JavaIntf";
import { TabIntf } from "./TabIntf";

/* This interface is how Type Plugins are handled */
export interface TypeIntf {
    ordinal: number;
    schemaOrg: J.SchemaOrgClass;
    getTypeName(): string;
    getName(): string;
    render(node: J.NodeInfo, tabData: TabIntf<any>, rowStyling: boolean, isTreeView: boolean, isLinkedNode: boolean): Comp;
    getIconClass(): string;
    allowAction(action: NodeActionType, node: J.NodeInfo): boolean;
    allowDeleteProperty(prop: string): boolean;
    getAllowRowHeader(): boolean;
    getAutoExpandProps(): boolean;
    allowPropertyEdit(typeName: string): boolean;
    domPreUpdateFunction(parent: CompIntf): void;

    // if this returns a list of props, then these props are all the EditNodeDlg is allowed to show AND
    // they will all be put outside the collapsible panel if they'd normally be inside he collapse panel
    getCustomProperties(): string[];
    ensureDefaultProperties(node: J.NodeInfo): void;
    getAllowPropertyAdd(): boolean;
    getAllowContentEdit(): boolean;
    getEditLabelForProp(propName: string): string;
    getEditorRowsForProp(propName: string): number;
    getAllowUserSelect(): boolean;
    hasCustomProp(prop: string): boolean;
    hasSelectableProp(prop: string): boolean;
    getEditorHelp(): string;
    isSpecialAccountNode(): boolean;

    // for sorting on client side (namely for items packaged in a collapsable panel on account root page.)
    subOrdinal(): number;
    renderEditorSubPanel(node: J.NodeInfo): Comp;

    // todo-1: now that we have EditorOptions, many of the fixed values can be subsumed into it to simplify
    // this interface
    getEditorOptions(): EditorOptions;
    getType(prop: string): string;

    getPropConfig(prop: string): ConfigProp;
    getSchemaOrgPropComment(prop: string): string;
}

export enum NodeActionType {
    /* eslint-disable no-unused-vars */
    addChild, editNode, insert, upload, delete, share
};
