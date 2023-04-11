import Picker from "emoji-picker-react";
import { ReactNode } from "react";
import { ValueIntf } from "../../Interfaces";
import { Comp } from "../base/Comp";

export class EmojiPicker extends Comp {

    constructor(private selectionValueIntf: ValueIntf) {
        super();
        this.attribs.pickerStyle = { width: "100%", marginBottom: "16px" };
        this.attribs.onEmojiClick = (event: any, emojiObject: any) => {
            console.log("emoji: & #x" + emojiObject.unified + ";");
            this.selectionValueIntf.setValue(emojiObject.emoji);
        };
    }

    override compRender = (): ReactNode => {
        return this.tag(Picker);
    }
}
