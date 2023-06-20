import { EditorOptions } from "../Interfaces";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { TypeBase } from "./base/TypeBase";

/* Type for 'untyped' types. That is, if the user has not set a type explicitly this type will be the default */
export class NostrEncryptedDMType extends TypeBase {
    constructor() {
        // WARNING: There are places in the code where "Markdown" string is hardcoded.
        super(J.NodeType.NOSTR_ENC_DM, "Nostr DM", "fa-bullseye", true);
    }

    override getEditorHelp(): string {
        return S.quanta.cfg.help?.editor?.dialog;
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
