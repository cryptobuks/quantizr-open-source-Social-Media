import { EditorOptions } from "../Interfaces";
import { TypeBase } from "./base/TypeBase";

export class SchemaOrgType extends TypeBase {
    constructor(typeName: string, displayName: string) {
        super(typeName, displayName, "fa-cube", true);
    }

    override getEditorOptions(): EditorOptions {
        return {
            tags: true,
            nodeName: true,
            priority: true,
            wordWrap: true,
            encrypt: true,
            sign: true,
            inlineChildren: true
        };
    }
}
