import "bootstrap";
import { Factory } from "./Factory";
import React from "react";
import ReactDOM from "react-dom";
import TsxApp from "./TsxApp";

import "../css/quanta.scss";
import "font-awesome/css/font-awesome.min.css";
import PayPalButton from "./PayPalButton";

// set in index.html
declare var __page;

if ((window as any).__page === "index") {
    console.log("Constructing Factory.");
    let factory = new Factory();

    window.addEventListener("load", (event) => {
        console.log("factory.initApp");
        factory.initApp();
    });
}
else if ((window as any).__page === "tsx-test") {
    ReactDOM.render(
        <React.StrictMode>
            <TsxApp />
            <PayPalButton />
        </React.StrictMode>,
        document.getElementById("app")
    );
}

function index() {
    return (<div></div>);
}

export default index;
