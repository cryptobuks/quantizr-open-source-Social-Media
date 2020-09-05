import { useSelector } from "react-redux";
import { dispatch } from "../AppRedux";
import { AppState } from "../AppState";
import { runClassDemoTest } from "../ClassDemoTest";
import { Constants as C } from "../Constants";
import { PubSub } from "../PubSub";
import { Singletons } from "../Singletons";
import { Comp } from "./base/Comp";
import { Button } from "./Button";
import { CompDemo } from "./CompDemo";
import { Div } from "./Div";
import { HorizontalLayout } from "./HorizontalLayout";

let S: Singletons;
PubSub.sub(C.PUBSUB_SingletonsReady, (ctx: Singletons) => {
    S = ctx;
});

export class AppDemo extends Div {

    constructor() {
        super();
        runClassDemoTest();
    }

    preRender(): void {
        let state: AppState = useSelector((state: AppState) => state);

        this.setChildren([
            new HorizontalLayout([
                new Button("Inc AppState.counter=" + state.counter + " compDemoIdActive=" + state.compDemoIdActive, () => {
                    Comp.renderCounter = 0;
                    dispatch({
                        type: "Action_DemoAppIncCounter", state,
                        update: (s: AppState): void => {
                            s.counter++;
                        },
                    });
                })
            ]),

            new HorizontalLayout([
                new CompDemo()
            ])
        ]);
    }
}
