import { ReactNode } from "react";
import { Comp } from "../base/Comp";

export class VideoPlayer extends Comp {

    constructor(attrib: any) {
        super(attrib);
    }

    getVideoElement(): HTMLVideoElement {
        return <HTMLVideoElement>this.getRef();
    }

    override compRender = (): ReactNode => {
        return this.tag("video");
    }
}
