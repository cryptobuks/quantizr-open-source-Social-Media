import { getAs } from "../AppContext";
import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Clearfix } from "../comp/core/Clearfix";
import { Div } from "../comp/core/Div";
import { Diva } from "../comp/core/Diva";
import { Divc } from "../comp/core/Divc";
import { FlexRowLayout } from "../comp/core/FlexRowLayout";
import { IconButton } from "../comp/core/IconButton";
import { Selection } from "../comp/core/Selection";
import { TextField } from "../comp/core/TextField";
import { FriendsTable } from "../comp/FriendsTable";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { Validator } from "../Validator";

export interface LS { // Local State
    loading?: boolean;
    friends?: J.FriendInfo[];
}

export class BlockedUsersDlg extends DialogBase {
    searchTextState: Validator = new Validator();
    friendsTagSearch: string;
    searchTextField: TextField;

    constructor(title: string) {
        super(title);

        this.mergeState<LS>({
            loading: true
        });
    }

   override preLoad = async () => {
        const res = await S.rpcUtil.rpc<J.GetPeopleRequest, J.GetPeopleResponse>("getPeople", {
            nodeId: null,
            type: "blocks",
            subType: null
        });

        let friends: J.FriendInfo[] = [];
        if (res.nodeOwner) {
            friends.push(res.nodeOwner);
        }
        if (res.people) {
            friends = friends.concat(res.people);
        }

        this.mergeState<LS>({
            friends,
            loading: false
        });
    }

    renderDlg(): CompIntf[] {
        const state: LS = this.getState();
        const ast = getAs();
        let message = null;
        if (state.loading) {
            message = "Loading...";
        }
        else if (!state.friends || state.friends.length === 0) {
            message = "You haven't blocked anyone";
        }

        let friendsTagDropDown: Selection = null;

        if (ast.friendHashTags && ast.friendHashTags.length > 0) {
            const items: any[] = [
                { key: "", val: "All Tags" }
            ];

            for (const tag of ast.friendHashTags) {
                items.push({ key: tag, val: tag });
            }

            friendsTagDropDown = new Selection(null, "Filter By Tag",
                items,
                null, "friendsTagPickerOnEditor alignBottom", {
                setValue: (val: string) => {
                    this.friendsTagSearch = val;
                    this.userSearch();
                },
                getValue: (): string => this.friendsTagSearch
            });
        }

        let searchText = this.searchTextState.getValue();
        let tagSearch = this.friendsTagSearch;

        searchText = searchText?.toLowerCase();
        tagSearch = tagSearch?.toLowerCase();

        const filteredFriends = state.friends?.map(friend => {
            if (!friend) return null;

            if ((!searchText || this.friendMatchesString(friend, searchText)) &&
                (!tagSearch || this.friendMatchesString(friend, tagSearch))) {
                return friend;
            }
            else {
                return null;
            }
        });

        return [
            new Diva([
                !message ? new FlexRowLayout([
                    (this.searchTextField = new TextField({
                        labelClass: "txtFieldLabelShort",
                        label: "Search",
                        val: this.searchTextState,
                        placeholder: "Search for...",
                        enter: this.userSearch,
                        outterClass: "friendSearchField"
                    })),

                    // This div wrapper is to keep the button from stretching wrong
                    new Divc({ className: "friendSearchButtonDiv" }, [
                        new IconButton("fa-search", null, {
                            onClick: this.userSearch,
                            title: "Search"
                        }, "btn-secondary")
                    ]),

                    friendsTagDropDown
                ], "flexRowAlignBottom marginBottom") : null,
                message ? new Div(message)
                    : new FriendsTable(filteredFriends, false, this),
                state.friends?.length > 1 ? new Clearfix() : null,
                new ButtonBar([
                    new Button("Close", this.cancel, null, "btn-secondary float-end")
                ], "marginTop"),
                new Clearfix() // required in case only ButtonBar children are float-end, which would break layout
            ])
        ];
    }

    userSearch = () => {
        this.mergeState<LS>({});
        // warning: keep the fat arrow function here.
        setTimeout(() => this.searchTextField.focus(), 50);
    }

    cancel = () => {
        this.close();
    }

    friendMatchesString = (friend: J.FriendInfo, text: string) => {
        const ret = (friend.displayName && friend.displayName.toLowerCase().indexOf(text) !== -1) || //
            (friend.userName && friend.userName.toLowerCase().indexOf(text) !== -1) || //
            (friend.tags && friend.tags.toLowerCase().indexOf(text) !== -1);
        return ret;
    }
}
