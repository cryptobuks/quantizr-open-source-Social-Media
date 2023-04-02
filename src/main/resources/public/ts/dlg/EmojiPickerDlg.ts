import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Diva } from "../comp/core/Diva";
import { EmojiPicker } from "../comp/core/EmojiPicker";
import { DialogBase } from "../DialogBase";
import { ValueIntf } from "../Interfaces";

export interface LS { // Local State
    selectedEmoji: string;
}

export class EmojiPickerDlg extends DialogBase {
    selectionValueIntf: ValueIntf;

    constructor() {
        super("Emojis", "appModalContNarrowWidth");

        this.selectionValueIntf = {
            setValue: (val: string) => {
                this.mergeState<LS>({ selectedEmoji: val });
                this.close();
            },
            getValue: (): string => this.getState<LS>().selectedEmoji
        };
        this.mergeState<LS>({ selectedEmoji: null });
    }

    renderDlg(): CompIntf[] {
        return [
            new Diva([
                new EmojiPicker(this.selectionValueIntf),
                new ButtonBar([
                    new Button("Cancel", this.close)
                ], "marginTop")
            ])
        ];
    }
}
