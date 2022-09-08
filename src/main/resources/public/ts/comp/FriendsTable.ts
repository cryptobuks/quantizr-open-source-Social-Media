import { FriendInfo } from "../JavaIntf";
import { FriendsTableRow } from "./FriendsTableRow";
import { ListBox } from "./ListBox";
import { CompIntf } from "../comp/base/CompIntf";

export class FriendsTable extends ListBox {
    static scrollPos: number = 0;

    constructor(public friends: FriendInfo[], private dlg: CompIntf) {
        super();
        const maxHeight: number = window.innerHeight > 300 ? (window.innerHeight*0.75) : 300;
        this.attribs.className = "scrollY scrollBorder";
        this.attribs.style = { "max-height": maxHeight + "px" };
    }

    preRender(): void {
        if (this.friends) {
            this.setChildren(this.friends.map(friend => {
                return new FriendsTableRow(friend, this.dlg);
            }));
        }
    }

    getScrollPos = (): number => {
        return FriendsTable.scrollPos;
    }

    setScrollPos = (pos: number): void => {
        FriendsTable.scrollPos = pos;
    }
}
