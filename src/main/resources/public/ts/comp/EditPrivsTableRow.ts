import { Button } from "../comp/core//Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Div } from "../comp/core/Div";
import { Diva } from "../comp/core/Diva";
import { Img } from "../comp/core/Img";
import { UserProfileDlg } from "../dlg/UserProfileDlg";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { Checkbox } from "./core/Checkbox";
import { Divc } from "./core/Divc";
import { Icon } from "./core/Icon";
import { ListBoxRow } from "./ListBoxRow";

export class EditPrivsTableRow extends ListBoxRow {

    constructor(private shareNodeToUserFunc: Function, public aclEntry: J.AccessControlInfo, private removePrivilege: (principalNodeId: string, privilege: string) => void) {
        super();
    }

    renderAclPrivileges(aclEntry: J.AccessControlInfo): Div {
        const writable = S.props.hasPrivilege(this.aclEntry, J.PrivilegeType.WRITE);
        const div = new Divc({ className: "float-end tinyMarginAll" });

        aclEntry.privileges.forEach(privilege => {
            div.addChild(
                new Diva([
                    new ButtonBar([
                        new Checkbox("Allow Replies", { className: "marginRight" }, {
                            setValue: (checked: boolean) => this.shareNodeToUserFunc(this.aclEntry.principalName, checked),
                            getValue: (): boolean => writable
                        }),
                        new Button("Remove", () => {
                            this.removePrivilege(aclEntry.principalNodeId, privilege.privilegeName);
                        })
                    ], "marginLeft")
                ])
            );
        });
        return div;
    }

    override preRender(): boolean {
        let src: string = null;
        if (this.aclEntry.avatarVer) {
            src = S.render.getAvatarImgUrl(this.aclEntry.principalNodeId, this.aclEntry.avatarVer);
        }
        // foreign users have this kind of avatar
        else if (this.aclEntry.foreignAvatarUrl) {
            src = this.aclEntry.foreignAvatarUrl;
        }

        const displayName = this.aclEntry.displayName;
        const userNameDisp = S.util.getFriendlyPrincipalName(this.aclEntry);
        const isPublic = this.aclEntry.principalName === J.PrincipalName.PUBLIC;

        this.setChildren([
            new Diva([
                this.renderAclPrivileges(this.aclEntry),
                new Divc({ className: "friendListImgDivCont" }, [
                    !isPublic ? new Divc({ className: "friendListImgDiv centerChild" }, [
                        src ? new Img({
                            className: "friendListImage",
                            src,
                            onClick: () => {
                                new UserProfileDlg(this.aclEntry.principalNodeId).open();
                            }
                        }) : null
                    ]) : null,
                    isPublic ? new Divc({ className: "friendListImgDiv centerChild" }, [
                        new Icon({
                            className: "fa fa-globe fa-3x sharingIcon marginAll",
                            title: "Node is Public"
                        })
                    ]) : null
                ]),
                new Divc({ className: "sharingDisplayName" }, [
                    isPublic ? new Div("Public (Everyone)", { className: "largeFont" })
                        : new Div(displayName, {
                            className: "friendName",
                            onClick: () => {
                                new UserProfileDlg(this.aclEntry.principalNodeId).open();
                            }
                        }),
                    isPublic ? null : new Div(userNameDisp, {
                        onClick: () => {
                            new UserProfileDlg(this.aclEntry.principalNodeId).open();
                        }
                    })
                ])
            ])
        ]);
        return true;
    }
}
