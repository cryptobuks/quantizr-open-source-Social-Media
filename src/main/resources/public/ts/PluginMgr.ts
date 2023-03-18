import { TypeIntf } from "./intf/TypeIntf";
import { AccountType } from "./plugins/AccountType";
import { APPostsType } from "./plugins/APPostsType";
import { BlockedUsersType } from "./plugins/BlockedUsersType";
import { BookmarkListType } from "./plugins/BookmarkListType";
import { BookmarkType } from "./plugins/BookmarkType";
import { CommentType } from "./plugins/CommentType";
import { CalcType } from "./plugins/CalcType";
import { CalendarType } from "./plugins/CalendarType";
import { ExportsType } from "./plugins/ExportsType";
import { FriendsListType } from "./plugins/FriendsListType";
import { FriendType } from "./plugins/FriendType";
import { InboxEntryType } from "./plugins/InboxEntryType";
import { InboxNodeType } from "./plugins/InboxNodeType";
import { IPFSNodeType } from "./plugins/IPFSNodeType";
import { MarkdownType } from "./plugins/MarkdownType";
import { NotesNodeType } from "./plugins/NotesNodeType";
import { PostsType } from "./plugins/PostsType";
import { RepoRootType } from "./plugins/RepoRootType";
import { RoomType } from "./plugins/RoomType";
import { RssFeedsType } from "./plugins/RssFeedsType";
import { RssType } from "./plugins/RssType";
import { TextType } from "./plugins/TextType";
import * as J from "./JavaIntf";
import { S } from "./Singletons";
import { SchemaOrgType } from "./plugins/SchemaOrgType";

export class PluginMgr {
    private types: Map<string, TypeIntf> = new Map<string, TypeIntf>();

    addType = (type: TypeIntf) => {
        if (this.types.get(type.getTypeName())) {
            throw new Error("duplicate type handler: " + type.getTypeName());
        }
        this.types.set(type.getTypeName(), type);
    }

    getType = (typeName: string): TypeIntf => {
        const type = this.types.get(typeName);
        if (!type) {
            console.warn("No type handler for: " + typeName);
        }
        return type;
    }

    getAllTypes = (): Map<string, TypeIntf> => {
        return this.types;
    }

    // todo-2: make it so that some plugins can be flagged as 'admin only' and not show up on the menu to pick them.
    initPlugins = () => {
        console.log("initPlugins()");

        /* We could have made each type base-class automatically register here, but they'd executed in nondeterminisitic order
        so it's better to just have this one place where we define all them in the order we want */
        this.addType(new MarkdownType());
        this.addType(new TextType());
        this.addType(new RssFeedsType());
        this.addType(new RssType());
        this.addType(new CalcType());
        this.addType(new RoomType());
        this.addType(new CalendarType());
        this.addType(new IPFSNodeType());
        this.addType(new RepoRootType());
        this.addType(new AccountType());
        this.addType(new PostsType());
        this.addType(new APPostsType());
        this.addType(new ExportsType());
        this.addType(new InboxNodeType());
        this.addType(new InboxEntryType());
        this.addType(new NotesNodeType());
        this.addType(new BookmarkType());
        this.addType(new CommentType());
        this.addType(new BookmarkListType());
        this.addType(new FriendsListType());
        this.addType(new BlockedUsersType());
        this.addType(new FriendType());

        // It's intentional that we don't do an await here, but let it complete async
        this.addSchemaOrgTypes();
    }

    addSchemaOrgTypes = async () => {
        const res = await S.rpcUtil.rpc<J.GetSchemaOrgTypesRequest, J.GetSchemaOrgTypesResponse>("getSchemaOrgTypes", {
        });

        // console.log("res=" + S.util.prettyPrint(res.classes));

        res.classes.forEach(soc => {
            const type = new SchemaOrgType(soc.id, soc.label);
            type.schemaOrg = soc;
            this.addType(type);
        })
    }
}
