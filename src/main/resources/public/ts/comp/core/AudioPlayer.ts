import { ReactNode } from "react";
import { Comp } from "../base/Comp";

export class AudioPlayer extends Comp {
    override compRender = (): ReactNode => {
        return this.tag("audio");
    }
}
