import { FriendInfo } from "../JavaIntf";
import { CompIntf } from "../comp/base/CompIntf";
import { FriendsTableRow } from "./FriendsTableRow";
import { ListBox } from "./ListBox";
import { Div } from "./core/Div";

export class FriendsTable extends ListBox {
    static scrollPos: number = 0;

    constructor(public friends: FriendInfo[], private selectableRows: boolean, private dlg: CompIntf) {
        super();

        const maxHeight: number = window.innerHeight > 300 ? (window.innerHeight * 0.5) : 300;
        this.attribs.className = "scrollY scrollBorder personsList customScrollbar";
        this.attribs.style = { maxHeight: maxHeight + "px" };
    }

    override preRender(): boolean {
        if (this.friends) {
            const friendsComps = this.friends.map(friend => {
                if (!friend) return null;
                return new FriendsTableRow(friend, this.selectableRows, this.dlg);
            });

            if (friendsComps?.length > 0) {
                this.setChildren(friendsComps);
            }
            else {
                this.setChildren([new Div("No matching users.")]);
            }
        }
        return true;
    }

    override getScrollPos = (): number => {
        return FriendsTable.scrollPos;
    }

    override setScrollPos = (pos: number): void => {
        FriendsTable.scrollPos = pos;
    }
}
