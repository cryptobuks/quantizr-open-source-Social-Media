import type { Attachment } from "./Attachment";
import type { DomUtil } from "./DomUtil";
import type { Edit } from "./Edit";
import type { Crypto } from "./Crypto";
import type { LocalDB } from "./LocalDB";
import type { Nav } from "./Nav";
import type { NodeUtil } from "./NodeUtil";
import type { PluginMgr } from "./PluginMgr";
import type { Props } from "./Props";
import type { Quanta } from "./Quanta";
import type { Render } from "./Render";
import { RpcUtil } from "./RpcUtil";
import type { Search } from "./Search";
import type { ServerPush } from "./ServerPush";
import type { SpeechEngine } from "./SpeechEngine";
import type { TabUtil } from "./TabUtil";
import type { User } from "./User";
import type { Util } from "./Util";
import type { View } from "./View";
import { Nostr } from "./Nostr";

/* Similar to a SpringContext in a Java app, these singletons are all pre-instantiated and guaranteed not
to result in any circular-references during load time, because they instantiate only other interfaces */
export interface Singletons {
    quanta: Quanta;
    plugin: PluginMgr;
    util: Util;
    domUtil: DomUtil;
    rpcUtil: RpcUtil;
    nodeUtil: NodeUtil;
    tabUtil: TabUtil;
    push: ServerPush;
    edit: Edit;
    attachment: Attachment;
    crypto: Crypto;
    nav: Nav;
    props: Props;
    render: Render;
    srch: Search;
    user: User;
    view: View;
    localDB: LocalDB;
    speech: SpeechEngine;
    nostr: Nostr;
}

const S: Singletons = {} as any;
(window as any).S = S;
export { S };
