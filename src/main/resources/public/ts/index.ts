
// DO NOT DELETE (I want to keep the capability to go back to this if needed)
// (see also: #css-imports-disabled)
// import "bootstrap";
// import "font-awesome/css/font-awesome.min.css";

import React from "react";
import { createRoot } from "react-dom/client";

// I decided there's far too much unnecessary complexity involved in combining the CSS into
// the JS, because we don't have any component-specific CSS or any other reason to do this
// and the net effect is that it just makes the JS bundle even larger when it's already too big.
// DO NOT DELETE (I want to keep the capability to go back to this if needed)
// (see also: #css-imports-disabled)
// import "../css/quanta.scss";
import { Factory } from "./Factory";
import { ImportTest } from "./ImportTest";
import { S } from "./Singletons";
import TsxApp from "./TsxApp";
import AppContainer from "./comp/core/AppContainer";
import TutorialAppContainer from "./comp/core/TutorialAppContainer";

// we have this as the first import for troubleshooting how browsers are
// able to handle the 'import' statement.
ImportTest.check();
console.log("index.ts finished imports");
let initialized = false;

const processAppLoad = async () => {
    new Factory();
    S.quanta.config = (window as any).g_config;
    S.quanta.cfg = S.quanta.config.config || {};

    // todo-1: get this url param from server just like other g_config stuff does
    const urlParams = new URLSearchParams(window.location.search);
    const app = urlParams.get("app");
    console.log("app=" + app);
    const root = createRoot(document.getElementById("app"));

    switch (app) {
        // http://127.0.0.1:8182/?app=TxsApp
        case "TsxApp":
            // NOTE: We can use JSX like this if we want... just by renaming this file to ".tsx" extension
            // and of source updating the reference ti this index file in the webpack common config file.
            // root.render(<AppContainer />);
            console.log("TsxApp");
            root.render(React.createElement(TsxApp));
            break;

        // http://127.0.0.1:8182/?app=TutorialAppContainer
        case "TutorialAppContainer":
            console.log("TutorialAppContainer");
            root.render(React.createElement(TutorialAppContainer));
            break;

        // normal Quanta app if no "app=" parameter is given.
        default:
            console.log("AppContainer");
            root.render(React.createElement(AppContainer));
            break;
    }

    S.quanta.initApp();
}

window.addEventListener("load", async (event) => {
    if (!initialized) {
        initialized = true;
        console.log("Load event.");
        processAppLoad();
    }
    else {
        console.log("Ignoring load event.");
    }
});
