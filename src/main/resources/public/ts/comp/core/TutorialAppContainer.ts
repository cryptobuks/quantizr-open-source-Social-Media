import { createElement } from "react";
import { AppContext, getAs } from "../../AppContext";
import { TutorialApp } from "./TutorialApp";

export default function TutorialAppContainer() {
    const app = new TutorialApp();
    return createElement(AppContext.Provider, { value: getAs() }, app.create());
}
