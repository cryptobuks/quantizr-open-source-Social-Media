import { ReactNode } from "react";
import { dispatch, getAs } from "../../AppContext";
import { AppState } from "../../AppState";
import { Diva } from "../../comp/core/Diva";
import { Comp } from "../base/Comp";
import { Anchor } from "./Anchor";
import { Button } from "./Button";
import { Checkbox } from "./Checkbox";
import { Div } from "./Div";
import { Li } from "./Li";
import { Span } from "./Span";
import { Ul } from "./Ul";

interface LS { // Local State
    content?: string;
}

/*
This is a Simple app that will be running at `http://localhost:8182/demo/tutorial`, which shows a bare minimum
example of using the Quanta React Framework.
*/
export class TutorialApp extends Comp {
    private static checkboxVal: boolean = false;

    constructor() {
        super();
        this.mergeState<LS>({ content: "Quanta GUI Framework works!" });
    }

    override compRender = (): ReactNode => {
        const ast = getAs();

        return this.tag("div", null, [
            this.getState<LS>().content,
            ast?.userName ? new Div("userName: " + ast.userName) : null,
            new Diva([
                new Anchor("https://someserver.com", "My Link")
            ]),
            new Span("Times[&times;]", null, null, true),
            new Div("Child Div", null, [
                new Ul("Unordered List", null, [
                    new Li("item 1"),
                    new Li("item 2"),
                    new Li("item 3")
                ]),
                new Div("SubDiv2"),
                new Div("SubDiv3"),
                new Checkbox("Test Checkbox", null, {
                    setValue: (checked: boolean) => TutorialApp.checkboxVal = checked,
                    getValue: (): boolean => TutorialApp.checkboxVal
                })
            ]),
            new Button("My Button", () => {
                this.mergeState<LS>({ content: "You clicked a button!" });
                dispatch("ButtonClick",
                    (s: AppState) => {
                        s.userName = "clay";
                    });
            })
        ]);
    }
}
