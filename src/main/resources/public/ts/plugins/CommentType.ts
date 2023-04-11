import { EditorOptions } from "../Interfaces";
import * as J from "../JavaIntf";
import { TypeBase } from "./base/TypeBase";

export class CommentType extends TypeBase {
    constructor() {
        super(J.NodeType.COMMENT, "Comment", "fa-comment", true);
    }

    // For now i'm not sure how we should indicate visibly that a
    // node is a comment, so I'm just not doing it, but this code DOES work.
    // getExtraMarkdownClass(): string {
    //     return "commentMarkdownClass";
    // }

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
