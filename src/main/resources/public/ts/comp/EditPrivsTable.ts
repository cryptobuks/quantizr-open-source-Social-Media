import * as J from "../JavaIntf";
import { AccessControlInfo } from "../JavaIntf";
import { TextContent } from "./core/TextContent";
import { EditPrivsTableRow } from "./EditPrivsTableRow";
import { ListBox } from "./ListBox";

export class EditPrivsTable extends ListBox {

    constructor(public shareNodeToUserFunc: Function, public acl: AccessControlInfo[], private removePrivilege: (principalNodeId: string, privilege: string) => void) {
        super(null);

        const maxHeight: number = window.innerHeight > 300 ? (window.innerHeight * 0.5) : 300;
        this.attribs.className = "scrollY privsTable scrollBorder customScrollbar";
        this.attribs.style = { maxHeight: maxHeight + "px" };
    }

    override preRender(): boolean {
        const children = [];

        if (this.acl) {
            // first add public, so it's at the top
            this.acl.forEach(aclEntry => {
                if (aclEntry.principalName?.toLowerCase() === J.PrincipalName.PUBLIC) {
                    children.push(new EditPrivsTableRow(this.shareNodeToUserFunc, aclEntry, this.removePrivilege));
                }
            });

            this.acl.forEach(aclEntry => {
                if (aclEntry.principalName?.toLowerCase() !== J.PrincipalName.PUBLIC) {
                    children.push(new EditPrivsTableRow(this.shareNodeToUserFunc, aclEntry, this.removePrivilege));
                }
            });
        }

        if (children.length === 0) {
            this.attribs.className = null;
            children.push(new TextContent("Node is not currently shared. Add people or make it public to share."));
        }

        this.setChildren(children);
        return true;
    }
}
