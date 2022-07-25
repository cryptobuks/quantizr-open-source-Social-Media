import { getAppState } from "../AppRedux";
import { UserProfileDlg } from "../dlg/UserProfileDlg";
import { FriendInfo } from "../JavaIntf";
import { S } from "../Singletons";
import { Div } from "../comp/core/Div";
import { Img } from "../comp/core/Img";
import { ListBoxRow } from "./ListBoxRow";
import { Span } from "../comp/core/Span";

export class FriendsTableRow extends ListBoxRow {

    constructor(public friend: FriendInfo, onClickFunc: Function, public isSelected: boolean) {
        super(null, onClickFunc);
    }

    preRender(): void {
        let src: string = null;

        // local users will have this kind of avatar
        if (this.friend.avatarVer) {
            src = S.render.getAvatarImgUrl(this.friend.userNodeId, this.friend.avatarVer);
        }
        // foreign users have this kind of avatar
        else if (this.friend.foreignAvatarUrl) {
            src = this.friend.foreignAvatarUrl;
        }
        else {
            console.log("no avatarVer on friend: " + this.friend.userNodeId);
        }

        let img: Img = null;
        if (src) {
            img = new Img(null, {
                className: "friendListImage",
                src: src,
                onClick: (evt: any) => {
                    new UserProfileDlg(this.friend.userNodeId, getAppState(null)).open();
                }
            });
        }

        let friendDisplay = this.friend.displayName
            ? this.friend.displayName + " (@" + this.friend.userName + ")"
            : ("@" + this.friend.userName);

        this.setChildren([
            new Div(null, {
                className: (this.isSelected ? " selectedListItem" : " unselectedListItem")
            }, [
                img,
                new Span(friendDisplay, { className: "friendListText" })
            ])
        ]);
    }
}
