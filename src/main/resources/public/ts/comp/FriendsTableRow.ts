import { getAs } from "../AppContext";
import { Div } from "../comp/core/Div";
import { Diva } from "../comp/core/Diva";
import { Img } from "../comp/core/Img";
import { LS as FriendsDlgState } from "../dlg/FriendsDlg";
import { LS as SelectTagsDlgLS, SelectTagsDlg } from "../dlg/SelectTagsDlg";
import { UserProfileDlg } from "../dlg/UserProfileDlg";
import * as J from "../JavaIntf";
import { FriendInfo } from "../JavaIntf";
import { S } from "../Singletons";
import { CompIntf } from "./base/CompIntf";
import { Checkbox } from "./core/Checkbox";
import { Divc } from "./core/Divc";
import { Icon } from "./core/Icon";
import { ListBoxRow } from "./ListBoxRow";

export class FriendsTableRow extends ListBoxRow {

    constructor(public friend: FriendInfo, private selectableRows: boolean, private dlg: CompIntf) {
        super(null, null, null);
        this.attribs.className = "personsListItem";
    }

    override preRender(): boolean {
        const ast = getAs();
        let src: string = null;

        // foreign users have this kind of avatar
        if (this.friend.foreignAvatarUrl) {
            src = this.friend.foreignAvatarUrl;
        }
        // local users will have this kind of avatar
        else if (this.friend.avatarVer) {
            src = S.render.getAvatarImgUrl(this.friend.userNodeId, this.friend.avatarVer);
        }
        else {
            console.log("no avatarVer on friend: " + this.friend.userNodeId);
        }

        const isNostr = S.nostr.isNostrUserName(this.friend.userName);
        const nameSuffix = isNostr ? " (Nostr)" : "";

        this.setChildren([
            new Diva([
                this.selectableRows ? new Checkbox(null, { className: "personsListItemCheckBox" }, {
                    setValue: (checked: boolean) => {
                        const state: FriendsDlgState = this.dlg.getState();
                        if (checked) {
                            state.selections.add(this.friend.userName);
                        }
                        else {
                            state.selections.delete(this.friend.userName);
                        }
                        this.dlg.mergeState(state);
                    },
                    getValue: (): boolean => this.dlg.getState().selections.has(this.friend.userName)
                }) : null,
                new Divc({ className: "friendListImgDivCont" }, [
                    new Divc({ className: "friendListImgDiv centerChild" }, [
                        src ? new Img({
                            className: "friendListImage",
                            src,
                            onClick: () => new UserProfileDlg(this.friend.userNodeId).open()
                        }) : null
                    ])
                ]),
                new Divc({
                    className: "friendListText",
                    onClick: () => new UserProfileDlg(this.friend.userNodeId).open()
                }, [
                    new Diva([
                        this.friend.displayName ? new Div(this.friend.displayName + nameSuffix, { className: "friendName" }) : null,
                        this.friend.userName && !isNostr ? new Div("@" + this.friend.userName) : null
                    ]),

                    // Only if we know the friendNodeId here (set on server) do we have the ability to show friend-specific tags,
                    // because if friendNodeId is null it just means this is a user independent of anything to do with Friends.
                    this.friend.friendNodeId ? S.render.renderTagsStrDiv(this.friend.tags, this.removeTag, this.editTags) : null
                ]),
                this.friend.liked ? new Icon({
                    title: "This person Liked the Node",
                    className: "fa fa-star fa-lg marginTop marginRight float-end " +
                        (this.friend.userName === ast.userName ? "likedByMeIcon" : "")
                }) : null
            ])
        ]);
        return true;
    }

    editTags = async () => {
        const dlg = new SelectTagsDlg("edit", this.friend.tags, false);
        await dlg.open();
        this.addTags(dlg);
    }

    addTags = (dlg: SelectTagsDlg) => {
        let val = this.friend.tags || "";
        val = val.trim();
        const tags: string[] = val.split(" ");
        dlg.getState<SelectTagsDlgLS>().selectedTags.forEach(tag => {
            if (!tag.startsWith("#")) {
                tag = "#" + tag;
            }
            if (!tags.includes(tag)) {
                if (val) val += " ";
                val += tag;
            }
        });

        this.friend.tags = this.sortTags(val);
        S.rpcUtil.rpc<J.UpdateFriendNodeRequest, J.UpdateFriendNodeResponse>("updateFriendNode", {
            nodeId: this.friend.friendNodeId,
            tags: this.friend.tags
        });

        this.dlg.mergeState({});
    }

    sortTags = (tagStr: string): string => {
        if (!tagStr) return tagStr;
        const tags: string[] = tagStr.split(" ");
        tags.sort();
        return tags.join(" ");
    }

    removeTag = (removeTag: string) => {
        let val = this.friend.tags || "";
        val = val.trim();
        const tags: string[] = val.split(" ");
        let newTags = "";

        tags.forEach(tag => {
            if (removeTag !== tag) {
                if (newTags) newTags += " ";
                newTags += tag;
            }
        });

        this.friend.tags = newTags;
        S.rpcUtil.rpc<J.UpdateFriendNodeRequest, J.UpdateFriendNodeResponse>("updateFriendNode", {
            nodeId: this.friend.friendNodeId,
            tags: this.friend.tags
        });
        this.dlg.mergeState({});
    }
}
