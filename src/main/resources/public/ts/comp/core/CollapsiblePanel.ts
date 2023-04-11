import { ReactNode } from "react";
import { Comp } from "../base/Comp";
import { Divc } from "./Divc";
import { Span } from "./Span";

interface LS { // Local State
    expanded?: boolean;
}

export class CollapsiblePanel extends Comp {

    constructor(private collapsedButtonText: string,
        private expandedButtonText: string,
        attribs: Object = {},
        children: Comp[] = null,
        private textLink: boolean = false,
        private stateCallback: Function = null,
        expanded: boolean = false,
        private extraToggleButtonClass = "",
        private extraDivStyleExpanded: string = "",
        private extraDivStyleCollapsed: string = "",
        private elementName: string = "div") {
        super(attribs);
        this.setChildren(children);
        this.collapsedButtonText = collapsedButtonText || "More ";
        this.expandedButtonText = expandedButtonText || "Less ";
        this.mergeState<LS>({ expanded });
    }

    setExpanded(expanded: boolean) {
        this.mergeState<LS>({ expanded });
    }

    override compRender = (): ReactNode => {
        const state = this.getState<LS>();
        const style = this.textLink ? "collapsePanelLink" : "btn btn-primary ";
        const collapseClass = state.expanded ? "expand" : "collapse";

        /* If the component is expanded we render the button INSIDE the main area,
        which is the area that would be HIDDEN when the component is NOT expanded. */
        if (state.expanded) {
            return this.tag(this.elementName, {
                className: this.extraDivStyleExpanded
            }, [
                // This div and it's children holds the actual collapsible content.
                new Divc({
                    className: collapseClass
                }, [
                    // This span is the expande/collapse button itself
                    new Span(this.expandedButtonText === "n/a" ? null : (this.expandedButtonText + "   "), {
                        className: style + " " + this.extraToggleButtonClass + (state.expanded ? " iconUp" : " iconDown"),
                        // Warning: This can't be camel case!
                        "data-bs-toggle": collapseClass,
                        onClick: this.onToggle
                    }),
                    ...this.getChildren()
                ])
            ]);
        }
        else {
            return this.tag(this.elementName, {
                className: this.extraDivStyleCollapsed
            }, [
                // This span is the expande/collapse button itself
                new Span(this.collapsedButtonText === "n/a" ? null : (this.collapsedButtonText + "   "), {
                    className: style + " " + this.extraToggleButtonClass + (state.expanded ? " iconUp" : " iconDown"),
                    // Warning: This can't be camel case!
                    "data-bs-toggle": collapseClass,
                    onClick: this.onToggle
                }),

                // This div and it's children holds the actual collapsible content.
                new Divc({
                    className: collapseClass
                },
                    this.getChildren())
            ]);
        }
    }

    onToggle = () => {
        const expanded = !this.getState<LS>().expanded;
        this.setExpanded(expanded);
        if (this.stateCallback) {
            this.stateCallback(expanded);
        }
    }
}
