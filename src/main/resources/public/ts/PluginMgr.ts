import { getAs } from "./AppContext";
import { TypeIntf } from "./intf/TypeIntf";
import * as J from "./JavaIntf";
import { AccountType } from "./plugins/AccountType";
import { APPostsType } from "./plugins/APPostsType";
import { BlockedUsersType } from "./plugins/BlockedUsersType";
import { BookmarkListType } from "./plugins/BookmarkListType";
import { BookmarkType } from "./plugins/BookmarkType";
import { CalcType } from "./plugins/CalcType";
import { CalendarType } from "./plugins/CalendarType";
import { CommentType } from "./plugins/CommentType";
import { ExportsType } from "./plugins/ExportsType";
import { FriendsListType } from "./plugins/FriendsListType";
import { FriendType } from "./plugins/FriendType";
import { InboxEntryType } from "./plugins/InboxEntryType";
import { InboxNodeType } from "./plugins/InboxNodeType";
import { IPFSNodeType } from "./plugins/IPFSNodeType";
import { MarkdownType } from "./plugins/MarkdownType";
import { NostrEncryptedDMType } from "./plugins/NostrEncryptedDMType";
import { NotesNodeType } from "./plugins/NotesNodeType";
import { PostsType } from "./plugins/PostsType";
import { RepoRootType } from "./plugins/RepoRootType";
import { RssFeedsType } from "./plugins/RssFeedsType";
import { RssType } from "./plugins/RssType";
import { SchemaOrgType } from "./plugins/SchemaOrgType";
import { TextType } from "./plugins/TextType";
import { S } from "./Singletons";

export class PluginMgr {
    private types: Map<string, TypeIntf> = new Map<string, TypeIntf>();

    addType = (ordinal: number, type: TypeIntf) => {
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

    getOrderedTypesArray = (recentOnly: boolean): TypeIntf[] => {
        const ast = getAs();
        const ret: TypeIntf[] = [];
        const recentTypes = recentOnly ? ast.userProfile.recentTypes?.split(",") : null;
        this.types.forEach((v, k) => {
            if (!recentTypes || recentTypes.includes(k)) {
                ret.push(v);
            }
        });
        ret.sort((a, b) => a.ordinal - b.ordinal);
        return ret;
    }

    // todo-2: make it so that some plugins can be flagged as 'admin only' and not show up on the menu to pick them.
    initPlugins = () => {
        console.log("initPlugins()");

        let ordinal = 0;
        /* We could have made each type base-class automatically register here, but they'd executed in nondeterminisitic order
        so it's better to just have this one place where we define all them in the order we want */
        this.addType(ordinal++, new MarkdownType());
        this.addType(ordinal++, new NostrEncryptedDMType());
        this.addType(ordinal++, new TextType());
        this.addType(ordinal++, new RssFeedsType());
        this.addType(ordinal++, new RssType());
        this.addType(ordinal++, new CalcType());
        // backing out chat room feature for now.
        // this.addType(ordinal++, new RoomType());
        this.addType(ordinal++, new CalendarType());
        this.addType(ordinal++, new IPFSNodeType());
        this.addType(ordinal++, new RepoRootType());
        this.addType(ordinal++, new AccountType());
        this.addType(ordinal++, new PostsType());
        this.addType(ordinal++, new APPostsType());
        this.addType(ordinal++, new ExportsType());
        this.addType(ordinal++, new InboxNodeType());
        this.addType(ordinal++, new InboxEntryType());
        this.addType(ordinal++, new NotesNodeType());
        this.addType(ordinal++, new BookmarkType());
        this.addType(ordinal++, new CommentType());
        this.addType(ordinal++, new BookmarkListType());
        this.addType(ordinal++, new FriendsListType());
        this.addType(ordinal++, new BlockedUsersType());
        this.addType(ordinal++, new FriendType());

        // It's intentional that we don't do an await here, but let it complete async
        this.addSchemaOrgTypes(ordinal);
    }

    addSchemaOrgTypes = async (ordinal: number) => {
        const res = await S.rpcUtil.rpc<J.GetSchemaOrgTypesRequest, J.GetSchemaOrgTypesResponse>("getSchemaOrgTypes", {
        });

        // console.log("res=" + S.util.prettyPrint(res.classes));

        res.classes.forEach(soc => {
            const type = new SchemaOrgType(soc.id, soc.label);
            type.schemaOrg = soc;
            this.addType(ordinal++, type);
        })
    }
}
