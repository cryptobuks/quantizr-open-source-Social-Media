import { Comp } from "../base/Comp";
import { CompIntf } from "../base/CompIntf";
import { NodeCompBinary } from "../node/NodeCompBinary";
import { S } from "../../Singletons";

// see: https://www.npmjs.com/package/react-emoji-render
// https://codesandbox.io/s/xjpy58llxq

// ************ DO NOT DELETE
//
// const parseEmojisAndHtml = value => {
//     const emojisArray = toArray(value);
//     const newValue = emojisArray.map((node: any) => {
//         if (typeof node === "string") {
//             return <span dangerouslySetInnerHTML={{ __html: node }} />;
//         }
//         return node.props.children;
//     });
//     return newValue;
// };

interface LS { // Local State
    content?: string;
}

export class Html extends Comp {
    public purifyHtml = true;

    constructor(content: string = "", attribs: Object = {}, children: CompIntf[] = null) {
        super(attribs);
        this.setChildren(children);
        this.setText(content);
    }

    setText = (content: string) => {
        this.mergeState<LS>({ content });
    }

    compRender(): React.ReactNode {
        if (this.hasChildren()) {
            console.error("dangerouslySetInnerHTML component had children. This is a bug: id=" + this.getId() + " constructor.name=" + this.constructor.name);
        }

        this.attribs.dangerouslySetInnerHTML = this.purifyHtml ? Comp.getDangerousHtml(this.getState<LS>().content)
            : { __html: this.getState<LS>().content };
        return this.tag("div");

        // ************* DO NOT DELETE.
        // Method 1 and 2 both work, except #2 would need to be updated to
        // enable the attribs! These are the two older ways of parsing emojis. For now we're just letting
        // the font itself do all the work, and don't need this.
        // METHOD 1:
        // this.attribs.dangerouslySetInnerHTML = { __html: S.render.parseEmojis(this.getState<LS>().content) };
        // return this.tag("div", this.attribs);
        //
        // METHOD 2: (note: You'll need to rename this file to '.tsx' extention to use JSX here)
        // return <div>{parseEmojisAndHtml(this.getState<LS>().content)}</div>;
    }

    override domPreUpdateEvent = (): void => {
        const elm = this.getRef();
        if (!elm) return;

        // make all "a" tags inside this div to have a target=_blank
        elm.querySelectorAll("a").forEach(e => e.setAttribute("target", "_blank"));

        // adds the click handler function to all .enlargableImg images
        elm.querySelectorAll(".enlargableImg").forEach(e => {
            e.addEventListener("click", (evt: MouseEvent) => {
                NodeCompBinary.clickOnImage(e.getAttribute("data-nodeid"), e.getAttribute("data-attkey"), false, false);
            });
        });

        elm.querySelectorAll(".hljs-copy").forEach((e: HTMLElement) => {
            e.addEventListener("click", () => S.domUtil.codeSpanClick(e));
        });

        /* When using tabs to indent code, highlightjs for some reason is rendering a code tag in a pre tag
        but not putting the 'hljs' class on them, so we do that here, to make the styling look ok consistent
        with how the 'fenced code blocks' look. */
        elm.querySelectorAll("pre code").forEach((e: HTMLElement) => {
            e.classList.add("hljs");
        });
    }
}
