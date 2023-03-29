import { Html } from "./Html";
import { S } from "../../Singletons";

export class Markdown extends Html {

    constructor(text: string) {
        super(S.util.markdown(text));
        this.attribs.className = "mkCont";
    }
}
