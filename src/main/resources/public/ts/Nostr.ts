import {
    Event,
    Kind,
    Pub,
    Relay,
    SimplePool,
    generatePrivateKey,
    getEventHash,
    getPublicKey,
    nip04,
    nip05,
    nip19,
    parseReferences,
    relayInit,
    getSignature,
    validateEvent, verifySignature
} from "nostr-tools";
import { dispatch, getAs } from "./AppContext";
import { Constants as C } from "./Constants";
import * as J from "./JavaIntf";
import { S } from "./Singletons";
import { Val } from "./Val";
import { Comp } from "./comp/base/Comp";
import { ConfirmDlg } from "./dlg/ConfirmDlg";
import { SetNostrPrivateKeyDlg } from "./dlg/SetNostrPrivateKeyDlg";
import { NostrMetadataDispInfo } from "./Interfaces";

/* This class holds our initial experimentation with Nostr, and the only GUI for this is a single
link on the Admin Console that can run the "test()" method

References:
https://github.com/nostr-protocol/nips/blob/master/01.md
https://github.com/nbd-wtf/nostr-tools
*/

export class Nostr {
    // nip07 feature is not yet complete
    enableNip07 = false;

    sk: string = null; // our secret key, hex string
    pk: string = null; // our public key, hex string
    npub: string = null; // our npub (of public key)

    // We maintain this set of all encountered relays as they're found so that if we ever need to look
    // up something and have no known relay we can at least try these. Potentially we could save these
    // on the server maybe specific to the given user? Or should we hold ONLY in local/browser storage?
    knownRelays: Set<string> = new Set<string>();
    metadataQueue: Set<string> = new Set<string>(); // Holds pending pubkeys whose metadata is pending being rendered in the DOM
    domRenderPending: boolean = false;

    // Holds pubkeys for which we DO want to persist the metadata once found
    persistMetadataForKeys: Set<string> = new Set<string>();

    // cache for rapid injecting of user info during react renders
    dispInfoCache: Map<string, NostrMetadataDispInfo> = new Map<string, NostrMetadataDispInfo>();

    // (map key==Quanta UserAccount NodeId)
    userRelaysCache: Map<string, string[]> = new Map<string, string[]>();

    bigQueryRunning: boolean = false;
    queryCounter: number = 0;

    // This will be non-null once we query for it, and only set back to null if this browser instance
    // knows it's added a new friend
    myFriends: J.FriendInfo[] = null;

    backgroundQueue = setInterval(async () => {
        await this.processMetadataQueue();
    }, 2000);

    invalidateKeys = () => {
        this.sk = null;
        this.pk = null;
        this.npub = null;
    }

    decrypt = async (pk: string, cipherText: string) => {
        try {
            // get hash of the encrypted data
            const cipherHash: string = S.util.hashOfString(cipherText);
            let clearText = S.quanta.decryptCache.get(cipherHash);
            // if we have already decrypted this data return the result.
            if (clearText) {
                return clearText;
            }

            clearText = await nip04.decrypt(this.sk, pk, cipherText);
            S.quanta.decryptCache.set(cipherHash, clearText);
            return clearText;
        }
        catch (e) {
            return null;
        }
    }

    checkInit = (): boolean => {
        return !!this.pk;
    }

    publishUserMetadata = async (): Promise<void> => {
        console.log("Updating Nostr Metadata to relays.");
        if (!this.checkInit()) return;

        // get the relays string for this user
        const userRelays = getAs().userProfile?.relays;
        if (!userRelays) {
            console.warn("No relays added yet.");
            return;
        }

        // split relays to array of relays
        const relays = this.getRelays(userRelays);
        if (!relays || relays.length === 0) return;

        const currentMetaPayload = this.createMetaPayload();
        const currentMeta = this.createMetadataEvent(currentMetaPayload);

        if (relays.length > 0) {
            // we need to scan one relay at a time to verify we have our identity on each one.
            for (const relay of relays) {
                const eventVal = new Val<Event>();

                // try to read the metadata from the relay
                await S.nostr.readUserMetadata(this.pk, relay, false, eventVal);

                // if the relay didn't have matching metadata we need to publish it to this relay
                if (!this.metadataMatches(currentMetaPayload, eventVal.val)) {
                    console.log("RELAY UPDATE: Pushing new meta to relay: " + relay);

                    // don't await for this, we can let them all run in parallel
                    await this.publishEvent(currentMeta, relay);
                }
                else {
                    console.log("RELAY UPDATE: Meta is up to date on relay: " + relay);
                }
            }
        }
    }

    metadataMatches(meta: J.NostrMetadata, event: Event): boolean {
        if (!event) {
            return false;
        }
        try {
            const eventMeta: J.NostrMetadata = JSON.parse(event.content);
            if (!eventMeta) {
                return false;
            }
            const same = eventMeta.name === meta.name &&
                eventMeta.username === meta.username &&
                eventMeta.about === meta.about &&
                eventMeta.picture === meta.picture &&
                eventMeta.banner === meta.banner &&
                eventMeta.website === meta.website &&
                eventMeta.nip05 === meta.nip05 &&
                eventMeta.reactions === meta.reactions &&
                eventMeta.display_name === meta.display_name;
            if (!same) {
                console.log("OUTDATED META: " + S.util.prettyPrint(meta));
            }
            return same;
        }
        catch (e) {
            return false;
        }
    }

    cacheEvents = (events: Event[]) => {
        events?.forEach(e => this.cacheEvent(e));
    }

    cacheEvent = (event: Event) => {
        switch (event.kind) {
            case Kind.EncryptedDirectMessage:
            case Kind.Text:
                S.localDB.setVal(event.id, event, S.localDB.STORE_NOSTR_TXT);
                break;
            case Kind.Metadata:
                S.localDB.setVal(event.pubkey, event, S.localDB.STORE_NOSTR_MD);
                break;
            default:
                console.warn("Event not cached: " + event.id + " kind=" + event.kind);
                break;
        }
    }

    testNpub = () => {
        // "content": "Hi nostr:npub1r0ccr27yxfm20lacgqfl8xwt4vl4j3ggs7nc29nkll6sthdk742stk6qn7",

        const npub = nip19.decode("npub1r0ccr27yxfm20lacgqfl8xwt4vl4j3ggs7nc29nkll6sthdk742stk6qn7");
        console.log("npub as hex: " + S.util.prettyPrint(npub));
    }

    // Logs keys to JS console
    printKeys = () => {
        if (!this.checkInit()) return;
        console.log("Nostr Keys:");
        // console.log("  Priv: " + this.sk); // keep this one secret by default
        console.log("  PubKey: " + this.pk);
        console.log("  npub: " + this.npub);
    }

    setPrivateKey = async (sk: string, userName: string) => {
        this.sk = sk;
        this.pk = getPublicKey(this.sk);
        this.npub = nip19.npubEncode(this.pk);
        await S.localDB.setVal(C.LOCALDB_NOSTR_PRIVATE_KEY, sk);

        this.printKeys();
    }

    // Initializes our keys, and returns the npub key
    initKeys = async (userName: string): Promise<void> => {
        if (userName === J.PrincipalName.ANON) {
            console.log("not using nostr keys: user=" + userName);
            return;
        }
        // if already initialized do nothing.
        if (this.pk && this.sk) return;

        // Yes this is bad practice to save key this way, but this is just a prototype!
        this.sk = await S.localDB.getVal(C.LOCALDB_NOSTR_PRIVATE_KEY);
        if (!this.sk) {
            await this.generateNewKey(userName, false);
        }
        else {
            this.pk = getPublicKey(this.sk);
            this.npub = nip19.npubEncode(this.pk);
        }
    }

    generateNewKey = async (userName: string, forceServerUpdate: boolean): Promise<void> => {
        // If key was not yet created, then create one and save it.
        this.setPrivateKey(generatePrivateKey(), userName);

        dispatch("UpdateNpub", s => {
            s.userProfile.nostrNpub = this.npub;
        });

        if (forceServerUpdate) {
            await S.rpcUtil.rpc<J.SavePublicKeyRequest, J.SavePublicKeyResponse>("savePublicKeys", {
                asymEncKey: null,
                sigKey: null,
                nostrNpub: this.npub,
                nostrPubKey: this.pk
            });
        }

        // if (this.pk !== this.translateNip19(this.npub)) {
        //     console.error("Problem with npub key");
        // }
    }

    // Builds all the nodes in the thread (by traversing up the tree of replies) going back in time towards
    // the original post.
    loadReplyChain = async (node: J.NodeInfo, maxQueries: number): Promise<J.SaveNostrEventResponse> => {
        console.log("nostr.loadReplyChain() for node: " + S.util.prettyPrint(node));
        const tags: any = S.props.getPropObj(J.NodeProp.NOSTR_TAGS, node);
        if (!Array.isArray(tags)) {
            console.log("No NOSTR_TAGs found");
            return null;
        }
        let pool: SimplePool = null;
        let relaySet: Set<string> = null;

        try {
            pool = new SimplePool();
            S.rpcUtil.incRpcCounter();

            // Get userRelays associated with this the owner of 'node'
            let relays: string[] = await this.getRelaysForUser(node);

            // only add from MY relays if no relays were found for user
            if (relays.length === 0) {
                console.log("No relays specific to the node, so we use our relays");
                relays = this.addMyRelays(relays);
            }

            if (relays.length === 0) {
                console.warn("No relays!");
                return null;
            }

            console.log("relays in use: " + S.util.prettyPrint(relays));

            // collections we'll be adding to as we walk up the reply tree
            const events: Event[] = [];
            relaySet = new Set<string>(relays);

            // now recursively walk up the the entire thread one reply back at a time.
            await this.traverseUpReplyChain(events, tags, pool, relaySet, maxQueries);

            if (!events || events.length === 0) {
                console.log("No reply info found.");
                return;
            }
            console.log("Persisting " + events.length + " events.");
            const ret = await this.persistEvents(events);
            return ret;
        }
        finally {
            S.rpcUtil.decRpcCounter();
            pool?.close(this.toRelayArray(relaySet));
        }
    }

    // gets relays to use for logged in users or anon ysers
    getSessionRelaysStr = (): string => {
        const ast = getAs();
        if (ast.userProfile?.relays) {
            return ast.userProfile.relays;
        }

        return S.quanta.configRes.nostrRelays;
    }

    // Recursive method. As we walk up the chain we maintain the set of all relays used during the walk, so we're likely to
    // be only looking at the relays we will find parts of this thread on.
    private traverseUpReplyChain = async (events: Event[], tags: string[][], pool: SimplePool, relaySet: Set<string>,
        queriesLeft: number): Promise<void> => {
        console.log("nostr.traverseUpReplyChain: via tags " + S.util.prettyPrint(tags));
        // get the array representing what event (with 'tags' in it) is a reply to.
        const repliedToArray: string[] = this.getRepliedToItem(tags);

        // if node wasn't a reply to anything, return null, we're done.
        if (!repliedToArray || repliedToArray.length < 2) {
            console.log("repliedToArray empty or too short");
            return null;
        }

        const eventRepliedTo = repliedToArray[1];
        const relayRepliedTo = repliedToArray.length > 2 ? repliedToArray[2] : null;

        // if we found what event the 'tags' had as it's replyTo
        if (eventRepliedTo) {
            console.log("LOADING ThreadItem: " + eventRepliedTo);

            let event = null;

            // it's more efficient to query for the event ONLY on a known preferrable relay if possible
            // so we try this first if we have a relayRepliedTo value
            if (relayRepliedTo) {
                console.log("Querying specific relay: " + relayRepliedTo);
                const localPool = new SimplePool();
                const localRelays = this.getRelays(relayRepliedTo);
                try {
                    event = await this.getEvent(eventRepliedTo, localPool, localRelays);
                }
                finally {
                    localPool.close(localRelays);
                }
                if (!event) {
                    console.log("Specific relay didn't have event: " + relayRepliedTo);
                }
            }

            // if we still don't have an event, try getting from the full pool of relays in use by this processing
            if (!event) {
                const queryRelays = this.toRelayArray(relaySet);
                event = await this.getEvent(eventRepliedTo, pool, queryRelays);
                if (!event) {
                    console.log("Pooled relays didn't have event.");
                }
            }

            // and as a last resort we try using our own relays
            if (!event) {
                console.log("Last resort. Querying with *our* relays");
                const localPool = new SimplePool();
                const localRelays = this.getMyRelays();
                try {
                    event = await this.getEvent(eventRepliedTo, localPool, localRelays);
                }
                finally {
                    localPool.close(localRelays);
                }
                if (!event) {
                    console.log("Our own relays didn't have event either: " + relayRepliedTo);
                }
            }

            // add to relaySet only now that we know we would've tried relayRepliedTo first and used it or not
            // but regardless we add it to the known set for this processing workload.
            // todo-1: This may not be needed to add the specific relay, and might indeed even be counterproductive, but I'm
            // throwing this in for not to err on the side of getting data rather than failing.
            if (relayRepliedTo) {
                relaySet.add(relayRepliedTo);
            }

            if (event) {
                console.log("REPLY: Chain Event: " + S.util.prettyPrint(event));
                // add to front of array so the chronological ordering is top down.
                events.unshift(event);

                if (Array.isArray(event.tags)) {
                    if (queriesLeft === 0) {
                        // console.log("Enough Notes were gotten, but there are more to get");
                        return;
                    }
                    else {
                        console.log("Event has tags, so we try try to go up the chain now.");
                        await this.traverseUpReplyChain(events, event.tags, pool, relaySet, --queriesLeft);
                    }
                }
            }
            else {
                console.warn("Event was not found on any relays: " + eventRepliedTo);
            }
        }
    }

    toUserArray = (usersSet: Set<string>): string[] => {
        const users: string[] = [];
        usersSet?.forEach(r => users.push(r));
        return users;
    }

    toRelayArray = (relaySet: Set<string>): string[] => {
        const relays: string[] = [];
        relaySet?.forEach(r => {
            relays.push(r);
            this.knownRelays.add(r);
        });
        return relays;
    }

    /* Returns the Nostr ID of whatever the node is a reply to if it's a reply or else null
    ref: https://github.com/nostr-protocol/nips/blob/master/10.md
    */
    getRepliedToItemOfNode = (node: J.NodeInfo): string[] => {
        const tags: any = S.props.getPropObj(J.NodeProp.NOSTR_TAGS, node);
        return this.getRepliedToItem(tags);
    }

    // Returns the tags array entry that represents what the Event is a reply to, or null of not a reply
    getRepliedToItem = (tags: string[][]): string[] => {
        if (!Array.isArray(tags)) {
            // console.log("no tags array.");
            return null;
        }
        let anyEvent: string[] = null;
        let replyEvent: string[] = null;
        let rootEvent: string[] = null;

        // if we have an array of "e" tags
        for (const ta of tags) {
            if (Array.isArray(ta)) {
                if (ta[0] === "e") {
                    // deprecated positional array (["e", <event-id>, <relay-url>] as per NIP-01.)
                    if (ta.length < 4) {
                        // console.log("anyEvent=" + S.util.prettyPrint(ta));
                        anyEvent = ta;
                    }
                    // Preferred non-deprecated way (["e", <event-id>, <relay-url>, <marker>])
                    else if (ta.length === 4) {
                        if (ta[3] === "reply") {
                            // console.log("replyEvent=" + S.util.prettyPrint(ta))
                            replyEvent = ta;
                        }
                        else if (ta[3] === "root") {
                            // console.log("rootEvent=" + S.util.prettyPrint(ta))
                            rootEvent = ta;
                        }
                    }
                }
            }
        }
        return replyEvent || rootEvent || anyEvent;
    }

    encodeToNpub = (hex: string): string => {
        if (!hex || hex.startsWith("npub")) return hex;
        return nip19.npubEncode(hex);
    }

    // Creates a test event (Social Media post) that we can send to a relay
    createEvent = (): any => {
        let event: any = {
            kind: Kind.Text,
            created_at: Math.floor(Date.now() / 1000),
            tags: [],
            content: "test from Quanta.wiki",
            pubkey: this.pk
        };

        event = this.signEvent(event);
        this.cacheEvent(event);
        // console.log("NEW EVENT: " + S.util.prettyPrint(event));
        return event;
    }

    signEvent = (event: Event): Event => {
        const nostr = (window as any).nostr;
        if (this.enableNip07 && nostr) {
            // This code branch was experimental and it does work.
            // console.log("Signing with Plugin (NIP-07)");
            return nostr.signEvent(event);
        }
        else {
            // console.log("Signing with Browser Key");
            event.id = getEventHash(event);
            event.sig = getSignature(event, this.sk);
            return event;
        }
    }

    createMetaPayload = (): J.NostrMetadata => {
        const userProfile = getAs().userProfile;

        const meta: J.NostrMetadata = {
            name: userProfile.userName,
            username: userProfile.userName,
            about: userProfile.userBio,
            picture: S.render.getAvatarImgUrl(userProfile.userNodeId, userProfile.avatarVer),
            banner: S.render.getProfileHeaderImgUrl(userProfile.userNodeId, userProfile.avatarVer),
            website: null,
            nip05: null,
            display_name: userProfile.displayName,
            reactions: null
        };

        console.log("CURRENT META: " + S.util.prettyPrint(meta));
        return meta;
    }

    // Creates the Nostr Metadata event for this user
    createMetadataEvent = (meta: J.NostrMetadata): any => {
        let event: any = {
            kind: Kind.Metadata,
            created_at: Math.floor(Date.now() / 1000),
            tags: [],
            content: JSON.stringify(meta),
            pubkey: this.pk
        };

        event = this.signEvent(event);
        this.cacheEvent(event);
        console.log("NEW METADATA EVENT: " + S.util.prettyPrint(event));
        return event;
    }

    checkEvent = (event: Event): boolean => {
        const ok = validateEvent(event);
        const verifyOk = verifySignature(event);
        // console.log("Event: " + event.id + " StatusOk=" + ok + " SigOk=" + verifyOk);

        // DO NOT DELETE: Useful for troubleshooting.
        // console.log("SERIALIZED(" + JSON.stringify([
        //     0,
        //     event.pubkey,
        //     event.created_at,
        //     event.kind,
        //     event.tags,
        //     event.content
        // ]) + ")");

        return ok && verifyOk;
    }

    queryAndDisplayNodeInfo = async (node: J.NodeInfo) => {
        let nostrId = S.props.getPropStr(J.NodeProp.OBJECT_ID, node);
        nostrId = nostrId.substring(1);
        const event = await this.getEvent(nostrId, null, this.getMyRelays());
        const e = new Val<Event>();
        if (event) {
            await this.readUserMetadata(event.pubkey, this.getSessionRelaysStr(), false, e);
        }

        let report = "Event: \n" + S.util.prettyPrint(event);

        if (e.val) {
            report += "\n\nOwner: \n" + S.util.prettyPrint(e.val);
        }
        else {
            report += "\n\nUnable to find owner on your relays.";
        }
        report += "\n";

        dispatch("showServerInfo", s => {
            S.tabUtil.tabChanging(s.activeTab, C.TAB_SERVERINFO);
            s.activeTab = C.TAB_SERVERINFO;
            s.serverInfoText = report;
            s.serverInfoCommand = "";
            s.serverInfoTitle = "Nostr Info";
        });
    }

    /* To find any other nodes that mention this node, we can do this.
    WARNING: This code ended up not being need [yet] and has not been tested.
    */
    getEventMentions = async (node: J.NodeInfo): Promise<Event> => {
        const relays = this.getMyRelays();
        const id = this.translateNip19(node.nostrPubKey);

        // query for up to 10 events just so we can get the latest one
        const query: any = {};
        query["#e"] = [id];

        try {
            S.rpcUtil.incRpcCounter();
            const events = await this.queryRelays(relays, query);
            console.log("Associated Events: " + S.util.prettyPrint(events));
            if (events?.length > 0) {
                this.cacheEvents(events);
                return events[0];
            }
            else {
                console.log("Unable to load event: " + id + " (searched " + relays.length + " relays)");
                return null;
            }
        }
        finally {
            S.rpcUtil.decRpcCounter();
        }
    }

    /* persistResponse.res will contain the data saved on the server, but we accept null for persistResonse
    to indicate that no persistence on the server should be done,

    'pool' arg is optional and if not passed then relays will be used for making a new pool. When pool is passed in
    we DO ensure all the mentioned relays *are* added to it if not already in it.
    */
    getEvent = async (id: string, pool: SimplePool, relays: string[], background: boolean = false): Promise<Event> => {
        // console.log("getEvent: nostrId=" + id);
        id = this.translateNip19(id);

        // return the cached event if we have it.
        const cachedEvent = await S.localDB.getVal(id, S.localDB.STORE_NOSTR_TXT);
        if (cachedEvent) {
            return cachedEvent;
        }

        // query for up to 10 events just so we can get the latest one
        const query: any = {
            ids: [id],
            limit: 1
        };

        try {
            if (!background) S.rpcUtil.incRpcCounter();
            let events = null;

            // if a pool was provided use it.
            if (pool) {
                // if we have relays make sure our pool does contain them all.
                relays?.forEach(r => pool.ensureRelay(r));
                events = await pool.list(relays, [query]);
            }
            // else call queryRelays which does automatic pooling if it can.
            else {
                events = await this.queryRelays(relays, query);
            }
            // console.log("getEvent: " + S.util.prettyPrint(events));

            if (events?.length > 0) {
                this.cacheEvents(events);
                return events[0];
            }
            else {
                console.log("Unable to load event: " + id + " (searched " + relays.length + " relays)");
                return null;
            }
        }
        finally {
            if (!background) S.rpcUtil.decRpcCounter();
        }
    }

    /* Opens a relay, retrieves a single event from the relay, and then shuts down the relay.
    todo-1: call 'reject' if any error happens.
    */
    getEvent_subscriptionBased = async (rurl: string, id: string): Promise<Event> => {
        return new Promise<Event>(async (resolve, reject) => {
            id = this.translateNip19(id);
            const relay = await this.openRelay(rurl);
            let resolved = false;

            // subscribe to the relay to pull up just the one ID we're interested in.
            const sub = relay.sub([{ ids: [id] }]);

            console.log("Waiting for some events...");
            sub.on("event", event => {
                console.log("event received: ", S.util.prettyPrint(event));
                resolved = true;
                resolve(event);
            });

            sub.on("eose", () => {
                console.log("eose event. Closing relay.");
                sub.unsub();
                relay.close();
                if (!resolved) resolve(null);
            });
        });
    }

    // Opens a relay and only completes the promise when it's fully connected
    openRelay = async (rurl: string): Promise<Relay> => {
        if (!rurl.startsWith("wss://") && !rurl.startsWith("ws://")) {
            rurl = "wss://" + rurl;
        }
        const relay = relayInit(rurl);

        relay.on("connect", () => {
            console.log(`connected to ${relay.url}`);
            this.knownRelays.add(rurl);
        })
        relay.on("error", () => {
            console.log(`failed to connect to ${relay.url}`);
        })

        console.log("Opening Relay: " + rurl);
        await relay.connect();
        return relay;
    }

    publishEvent = async (event: Event, relayUrl: string): Promise<void> => {
        if (!this.checkInit()) return;
        return new Promise<void>(async (resolve, reject) => {
            // console.log("Publishing Event: " + event.id + " to relay " + relayStr);
            const relay = await this.openRelay(relayUrl);
            const pub = relay.publish(event);

            pub.on("ok", () => {
                console.log(`accepted by relay: ${relay.url}`);
                relay.close();
                resolve();
            });

            pub.on("failed", () => {
                console.log(`rejected by relay: ${relay.url}`);
                relay.close();
                resolve();
            });
        });
    }

    translateNip19 = (val: string) => {
        // remove the "nostr:" prefix if it exists
        if (val.startsWith("nostr:npub") || val.startsWith("nostr:note")) {
            val = val.substring(6);
        }

        if (val.startsWith("npub")) {
            const npub = nip19.decode(val);
            if (npub.type === "npub") {
                val = npub.data as string;
            }
            else {
                console.log("Unhandled npub type: " + S.util.prettyPrint(npub));
                return null;
            }
        }
        else if (val.startsWith("note")) {
            const note = nip19.decode(val);
            if (note.type === "note") {
                val = note.data as string;
            }
            else {
                console.log("Unhandled note type: " + S.util.prettyPrint(note));
                return null;
            }
        }
        return val;
    }

    processMetadataQueue = async () => {
        if (this.metadataQueue.size === 0) {
            if (this.domRenderPending) {
                this.domRenderPending = false;
                this.updateAllNodesMetadata();
            }
            return;
        }
        const authors: string[] = Array.from(this.metadataQueue);
        this.metadataQueue.clear();

        const query: any = {
            authors,
            kinds: [Kind.Metadata]
        };

        // todo-1: querying only from our own relays is not technically correct here: The metadataQueue entries really need
        // to have the specific relays discovered on each user instead
        const events = await this.queryRelays(this.getMyRelays(), query, true, true);
        // if (events) {
        //     console.log("Result of processMetadataQueue Lookup: " + S.util.prettyPrint(events));
        // }

        if (events?.length > 0) {
            for (const event of events) {
                this.cacheMetadataEvent(event);
            }

            this.updateAllNodesMetadata();
        }

        // Any events that exist in persistMetatataForKeys gets persisted here.
        // Persist these without using an await
        const eventsToPersist = events.filter(event => this.persistMetadataForKeys.has(event.pubkey));
        this.persistMetadataForKeys.clear();
        this.persistEvents(eventsToPersist, true);
    }

    updateAllNodesMetadata = () => {
        const ast = getAs();
        ast.tabData.forEach(td => {
            td.processNode(ast, node => {
                if (this.isNostrUserName(node.owner)) {
                    const pubKey = node.owner.substring(1);
                    const dispInfo = this.dispInfoCache.get(pubKey);
                    if (dispInfo && (node.displayName !== dispInfo.display ||
                        node.apAvatar !== dispInfo.picture)) {
                        node.displayName = dispInfo.display;
                        node.apAvatar = dispInfo.picture;
                    }
                }
            })
        });

        // we can now simply refresh the page, and we know the 'queryRelays' will have loaded all the users
        // we had queued and the page will now render the names.
        dispatch("ForceRefreshMetadata", s => { });
    }

    // todo-1: need to have a localDb.setVal that takes an array of objects and only inserts the ones that don't exist
    // and run it all in a transaction
    cacheMetadataEvent = async (event: Event) => {
        const cachedEvent = await S.localDB.getVal(event.pubkey, S.localDB.STORE_NOSTR_MD);
        if (!cachedEvent) {
            await S.localDB.setVal(event.pubkey, event, S.localDB.STORE_NOSTR_MD);
        }

        this.cacheDispInfoForEvent(event);
    }

    cacheDispInfoForEvent = (event: Event): NostrMetadataDispInfo => {
        let dispInfo = this.dispInfoCache.get(event.pubkey);

        // if we have the metadata cached we can render it immediately
        if (!dispInfo) {
            dispInfo = this.getMetadataDisplayInfo(event);
            if (!dispInfo) {
                dispInfo = { display: null, title: null, picture: null };
            }
            this.dispInfoCache.set(event.pubkey, dispInfo);
        }
        return dispInfo;
    }

    /* This handles the server push where the server is sending down a list of userinfo that needs to
    be queued up for querying, on the client.
    (todo-0: NOTE: We could now do this entirely in tserver on server side)
    */
    loadUserMetadata = async (userInfo: J.NewNostrUsersPushInfo): Promise<void> => {
        if (userInfo.users?.length > 0) {
            for (const user of userInfo.users) {
                // console.log("Queueing PK pushed from server: " + user.pk);
                // If pk not already cached, then queue it up for being cached
                this.addToMetadataQueue(user.pk, true);
            }
        }

        return null;
    }

    addToMetadataQueue = async (pubKey: string, persist: boolean) => {
        // try to get metadata from local store
        const cachedMd = await S.localDB.getVal(pubKey, S.localDB.STORE_NOSTR_MD);

        // if not found in local store add it to metadataQueue for async retrieval
        if (!cachedMd) {
            // console.log("queued for query: " + pubKey);
            this.metadataQueue.add(pubKey);
            if (persist) {
                this.persistMetadataForKeys.add(pubKey);
            }
        }
        // if we got from local store chen cach into the 'dispInfo' memory variable
        else {
            // console.log("found in store: " + pubKey);
            this.cacheDispInfoForEvent(cachedMd);
        }
        this.domRenderPending = true;
    }

    /* Tries to read from 'relayUrl' first and falls back to current user's relays if it fails */
    readUserMetadataEx = async (user: string, relayUrl: string, persist: boolean, outEvent: Val<Event>,
        background: boolean = false): Promise<J.SaveNostrEventResponse> => {
        const e = new Val<Event>();
        let ret = await this.readUserMetadata(user, relayUrl, persist, e, background);

        // if we didn't find the metadata fallback to using our own relays to try.
        if (!e.val) {
            ret = await this.readUserMetadata(user, this.getSessionRelaysStr(), persist, e, background);
        }

        if (outEvent) {
            outEvent.val = e.val;
        }
        return ret;
    }

    // user can be the hex, npub, or NIP05 address of the identity.
    // isNip05 must be set to true if 'user' is a nip05.
    //
    // If output argument 'outEvent' is passed as non-null then the event is sent back in 'outEvent.val'
    readUserMetadata = async (user: string, relayUrl: string, persist: boolean, outEvent: Val<Event>,
        background: boolean = false): Promise<J.SaveNostrEventResponse> => {
        console.log("Getting Metadata for Identity: " + user);
        const isNip05 = user.indexOf("@") !== -1 || user.indexOf(":") !== -1;
        let relays = this.getRelays(relayUrl);
        let profile = null;
        if (isNip05) {
            profile = await nip05.queryProfile(user);
            if (!profile) return null;
            console.log("NIP05: " + S.util.prettyPrint(profile));

            // todo-1: we should transfer the NIP05 URL up to the server so it can be stored
            // in use account node to be displayed in UserProfile.
            user = profile.pubkey;
            // console.log("Found NIP05 pubkey: " + user);

            if (profile.relays) {
                relays = relays.concat(profile.relays);

                // remove any dupliates
                relays = [...new Set(relays)];
            }
        }

        if (relays.length === 0) {
            console.warn("No relays. Can't lookup user: " + user);
            return null;
        }

        user = this.translateNip19(user);
        // NOTE: By the time we get here 'user' will be a PublicKey (not npub or nip05)

        const query: any = {
            authors: [user],
            kinds: [Kind.Metadata],
            limit: 1
        };

        const events = await this.queryRelays(relays, query, background);
        // if (events) {
        //     console.log("Result of Metadata Lookup: " + S.util.prettyPrint(events));
        // }

        if (events?.length > 0) {
            const event: any = events[0];
            event.npub = nip19.npubEncode(user);

            if (profile?.relays) {
                event.relays = profile.relays.join("\n");
            }

            if (outEvent) {
                outEvent.val = event;
            }
        }
        else {
            console.log("Failed to find user: pubKey=" + user);
            return null;
        }

        if (persist) {
            return await this.persistEvents(events, background);
        }
        return null;
    }

    revChronSort = (events: Event[]): void => {
        if (!events || events.length < 2) return;
        events.sort((a, b) => b.created_at - a.created_at);
    }

    // Possible Filter Params
    // ----------------------
    // "ids": <a list of event ids or prefixes>,
    // "authors": <a list of pubkeys or prefixes, the pubkey of an event must be one of these>,
    // "kinds": <a list of a kind numbers>,
    // "#e": <a list of event ids that are referenced in an "e" tag>,
    // "#p": <a list of pubkeys that are referenced in a "p" tag>,
    // "since": <an integer unix timestamp, events must be newer than this to pass>,
    // "until": <an integer unix timestamp, events must be older than this to pass>,
    // "limit": <maximum number of events to be returned in the initial query>
    readPosts = async (authors: string[], relays: string[], since: number, background: boolean, dmsToMe: boolean, limit: number): Promise<J.SaveNostrEventResponse> => {
        authors = authors.map(u => this.translateNip19(u));

        // WARNING: When adding new kinds here don't forget to update NostrService.java#saveEvent()
        const kinds = [Kind.Text];
        if (dmsToMe) {
            kinds.push(Kind.EncryptedDirectMessage);
        }

        const query: any = {
            authors,
            kinds,
            limit
        };

        // our "QueryKey (LOCALDB_NOSTR_LAST_USER_QUERY_KEY) needs to be updated if we're going to use 'since', so for now
        // the since optimization is disabled here. Or else we just need two QueryKeys, where one is for
        // DM-inclusive query and the other isn't
        // if (since !== -1) {
        //     query.since = since;
        // }

        if (dmsToMe) {
            query["#p"] = [this.pk];
        }

        let ret = null;
        try {
            this.nostrQueryBegin(background);
            const events = await this.queryRelays(relays, query, background);
            ret = await this.persistEvents(events, background);
        }
        finally {
            this.nostrQueryEnd(background);
        }

        return ret;
    }

    // oops, everyone can have nostrNpub, not just foreign users.
    // hasNostrAcls = (node: J.NodeInfo): boolean => {
    //     if (!node || !node.ac || node.ac.length === 0) return false;
    //     return !!node.ac.find(acl => !!acl.nostrNpub);
    // }

    hasNostrShareTags = (node: J.NodeInfo): boolean => {
        const event = this.makeUnsignedEventFromNode(node);
        const refs = parseReferences(event);
        if (refs) {
            // console.log("Node Refs: " + S.util.prettyPrint(refs));
            return !!refs.find(ref => !!ref.profile);
        }

        return false;
    }

    /* Creates an event node to send to nostr relays and also performs the following side effects:
    *
    * - for each acl on the node, add a "p" into the tags array, and sets the tags array onto the node
    * - substitutes npub tags into node.content
    * - build relaysStr based on acl list
    *
    * The actual 'node' may have encrypted content, so we always rely on clearText instead for 'content'
    */
    prepareOutboundEvent = async (node: J.NodeInfo, clearText: string, relays: string[]): Promise<Event> => {
        if (!node || !node.ac || node.ac.length === 0) return null;
        // console.log("Prepare Outbound for Node: " + S.util.prettyPrint(node));
        const tags: string[][] = S.props.getPropObj(J.NodeProp.NOSTR_TAGS, node) || [];
        const npubs: string[] = [];

        let isPublic = false;
        let relaysStr = "";
        let shareToPubKey = null;
        let nostrShareCount = 0;

        node.ac.forEach(acl => {
            if (acl.principalName === J.PrincipalName.PUBLIC) {
                isPublic = true;
            }
            else if (acl.nostrNpub) {
                shareToPubKey = this.translateNip19(acl.nostrNpub);
                tags.push(["p", shareToPubKey]);
                nostrShareCount++;
                npubs.push(acl.nostrNpub);
                if (relaysStr) {
                    relaysStr += "\n";
                }
                relaysStr += acl.nostrRelays;
            }
        });

        // if nothing nostrish to share to, then do nothing.
        if (!isPublic && tags.length === 0) {
            return null;
        }

        const words = clearText?.split(/[ \n\r\t]+/g);
        words?.forEach(w => {
            if (w.startsWith("npub")) {
                // const acl = node.ac.find(acl => acl.nostrNpub?.startsWith(w));
                // clearText = clearText.replace(w, acl.nostrNpub);
                for (let i = 0; i < npubs.length; i++) {
                    if (npubs[i].startsWith(w)) {
                        clearText = clearText.replace(w, `#[${i}]`);
                    }
                }
            }
        });

        // Nostr's way of adding attached files is just to mention their URL in the content, so let's add all that.
        clearText = this.getContentWithUrlsAdded(node, clearText);

        const kind = node.type === J.NodeType.NOSTR_ENC_DM ? Kind.EncryptedDirectMessage : Kind.Text;
        let content = clearText;
        if (kind === Kind.EncryptedDirectMessage) {
            if (nostrShareCount > 1) {
                console.warn("Warning: Nostr DMs can only share to one person.");
                return null;
            }
            // console.log("Nostr Encrypting outbound conent: " + content);
            content = await nip04.encrypt(this.sk, shareToPubKey, content);
            // console.log("Nostr Cipher conent: " + content);
        }

        let event: any = {
            kind,
            pubkey: this.pk,
            created_at: Math.floor(Date.now() / 1000),
            tags,
            content
        };
        event = this.signEvent(event);
        this.cacheEvent(event);

        relays.push(...this.getRelays((relaysStr || "") + "\n" + (this.getSessionRelaysStr() || "")));
        return event;
    }

    getContentWithUrlsAdded = (node: J.NodeInfo, clearText: string): string => {
        let ret = clearText || "";
        let idx = 0;
        S.props.getOrderedAtts(node).forEach(att => {
            if (idx++ === 0) {
                ret += "\n"
            }
            ret += "\n" + S.attachment.getAttUrl("bin", att, node.id, false);
        });
        return ret;
    }

    sendMessage = async (event: Event, relays: string[]) => {
        if (!this.checkInit()) return;
        return new Promise<boolean>(async (resolve, reject) => {
            // DO NOT DELETE (until Nostr testing is finished.)
            console.log("Sending Outbound Nostr Event: " + S.util.prettyPrint(event));

            let pub: Pub = null;
            let relay: Relay = null;
            let pool: SimplePool = null;
            let poolRemainder = 0;

            this.cacheEvent(event);

            if (relays.length === 1) {
                relay = await this.openRelay(relays[0]);
                pub = relay.publish(event);
            } else {
                pool = new SimplePool();
                pub = pool.publish(relays, event);
                poolRemainder = relays.length;
            }

            pub.on("ok", (relay: any) => {
                console.log(`accepted by relay: ${relay}`);
                if (relay) {
                    relay.close();
                    relay = null;
                }

                if (pool && --poolRemainder === 0) {
                    console.log("Relays Done. Closing pool.");
                    pool.close(relays);
                    pool = null;
                }
                resolve(true);
            });

            pub.on("failed", (relay: any) => {
                console.log(`rejected by relay: ${relay}`);
                if (relay) {
                    relay.close();
                    relay = null;
                }
                if (pool && --poolRemainder === 0) {
                    console.log("Relays Done. Closing pool.");
                    pool.close(relays);
                    pool = null;
                }
                resolve(false);
            });
        });
    }

    queryRelays = async (relays: string[], query: any, background: boolean = false, silent: boolean = false): Promise<Event[]> => {
        if (relays.length === 1) {
            return await this.singleRelayQuery(relays[0], query, background, silent);
        }
        else {
            return await this.multiRelayQuery(relays, query, background, silent);
        }
    }

    getRelays = (relayUrls: string): string[] => {
        if (!relayUrls) return [];
        let relays: string[] = relayUrls.split("\n");
        if (relays) {
            relays = relays.map(r => {
                if (!r) return r;
                if (!r.startsWith("wss://")) {
                    r = "wss://" + r;
                }
                r = this.normalizeURL(r);
                r = S.util.stripIfEndsWith(r, "/");
                this.knownRelays.add(r);
                return r;
            }).filter(r => !!r);
        }

        // this Set trick removes is simply for removing duplicates from array
        const ret = [...new Set(relays)];
        // console.log("parsed relay string " + relayUrls + " to Array: " + S.util.prettyPrint(ret));
        return ret;
    }

    persistEvents = async (events: Event[], background: boolean = false, forceResend: boolean = false): Promise<J.SaveNostrEventResponse> => {
        if (!this.checkInit()) return;
        if (!events || events.length === 0) return;

        if (!forceResend) {
            // remove any events we know we've already persisted
            events = events.filter(async (e) => {
                const ev = await S.localDB.getVal(e.id, S.localDB.STORE_NOSTR_PERSIST);
                // if (ev) {
                //     console.log("filtering out e.id " + e.id + " from events to persist. Already persisted it.");
                // }
                return !ev;
            });
        }

        // map key is 'pk'.
        const userSet: Map<String, J.NostrUserInfo> = new Map<String, J.NostrUserInfo>();

        // let idx = 0;
        events.forEach(event => {
            // console.log("PERSIST EVENT[" + (idx++) + "]: " + S.util.prettyPrint(event));
            if (!this.checkEvent(event)) {
                console.log("eventCheck Failed.");
            }

            // To persit events well be sending up to server this unique set of info for each user so the
            // server can know all the npub values for each pubkey without the server knowing how to generate that.
            const refs = parseReferences(event);
            refs?.forEach(ref => {
                if (ref.profile) {
                    userSet.set(ref.profile.pubkey, {
                        pk: ref.profile.pubkey,
                        npub: nip19.npubEncode(ref.profile.pubkey),

                        // I'm adding relays here just for consistency but when this code was originally written
                        // we didn't have a relays property on the NostrUserInfo object.
                        relays: ref.profile.relays ? ref.profile.relays.join("\n") : null
                    });
                }
            });

            // this.dumpEventRefs(event);
        });

        const userInfo: J.NostrUserInfo[] = Array.from(userSet.values());
        // console.log("saveNostrEvents has this userInfo: " + S.util.prettyPrint(userInfo));

        // Push the events up to the server for storage
        const res = await S.rpcUtil.rpc<J.SaveNostrEventRequest, J.SaveNostrEventResponse>("saveNostrEvents", {
            events: events.map(e => this.makeNostrEventWrapper(e, null)),
            userInfo
        }, background);

        // keep track of what we've just sent to server.
        events.forEach(async e => await S.localDB.setVal(e.id, true, S.localDB.STORE_NOSTR_PERSIST));
        // console.log("PERSIST EVENTS Resp: " + S.util.prettyPrint(res));
        return res;
    }

    /* Called by the markdown component renderer to process the content display for this node */
    replaceNostrRefs = (node: J.NodeInfo, val: string): string => {
        if (!this.hasNostrTags(node)) return val;

        try {
            const event = this.makeUnsignedEventFromNode(node);
            const references = parseReferences(event);
            if (!references || references.length === 0) {
                return val;
            }
            // console.log("REFS=" + S.util.prettyPrint(references));
            for (const ref of references) {
                if (ref.profile) {
                    const elmId = Comp.getNextId();
                    const dispInfo = this.dispInfoCache.get(ref.profile.pubkey);

                    // if we know the dispInfo render it.
                    if (dispInfo) {
                        // console.log("***** QUEUE DONE: PK: " + ref.profile.pubkey);
                        val = val.replace(ref.text, `<span class='nostrLink' id='${elmId}'>@${dispInfo.display}</span>`);
                    }
                    // else render a placeholder and queue up the pubkey to be queries asynchronously
                    else {
                        // console.log("QUEUED PK: " + ref.profile.pubkey);
                        this.addToMetadataQueue(ref.profile.pubkey, false);
                        const keyAbbrev = ref.profile.pubkey.substring(0, 10);
                        val = val.replace(ref.text, `<span class='nostrLink' id='${elmId}'>[User ${keyAbbrev}]</span>`);
                    }

                    // note: HTML Sanitizer won't allow onClick in text so we do it this way.
                    setTimeout(() => {
                        const e = document.getElementById(elmId);
                        if (e) {
                            e.addEventListener("click", () => {
                                S.user.showUserProfileByNostrKey(ref.profile.pubkey);
                            });
                        }
                    }, 750);
                }
                else if (ref.event) {
                    const elmId = Comp.getNextId();
                    const shortId = ref.event.id.substring(0, 8) + "...";
                    // Note: 'nostr-note' class in here is so that our OpenGraph link detector can ignore this and leave
                    // it as a regular anchor tag link
                    val = val.replace(ref.text, `<span id='${elmId}' class="nostr-note nostrLink">[Note ${shortId}]</span>`);

                    // note: HTML Sanitizer won't allow onClick in text so we do it this way.
                    setTimeout(() => {
                        const e = document.getElementById(elmId);
                        if (e) {
                            e.addEventListener("click", () => {
                                S.nostr.searchId(ref.event.id, ref.event.relays, false, node);
                            });
                        }
                    }, 750);
                }
                else if (ref.address) {
                    // todo-1: add support for address
                }
            }
        }
        catch (ex) {
            S.util.logErr(ex, "Failed processing Nostr Refs on: " + S.util.prettyPrint(node));
        }
        return val;
    }

    getMetadataDisplayInfo = (event: any): NostrMetadataDispInfo => {
        if (!event?.content) {
            console.log("metadata has no content: " + S.util.prettyPrint(event));
            return null;
        }
        let ev = null;
        try {
            ev = JSON.parse(event.content);
        }
        catch (e) {
            S.util.logErr(e, "Unable to parse metatata content from :" + S.util.prettyPrint(event));
        }
        if (!ev) return null;
        const title = S.domUtil.escapeHtml(ev.displayName + ": " + ev.about);
        const display = S.domUtil.escapeHtml(ev.displayName || ev.display_name || ev.name || ev.username);
        if (!display) return null;
        return { display, title, picture: ev.picture };
    }

    /* Creates an unsigned event */
    makeUnsignedEventFromNode = (node: J.NodeInfo): any => {
        const event: any = {
            kind: 1,
            pubkey: node.nostrPubKey ? this.translateNip19(node.nostrPubKey) : null,
            created_at: node.lastModified / 1000,
            tags: S.props.getPropObj(J.NodeProp.NOSTR_TAGS, node) || [],
            content: node.content
        };
        return event;
    }

    /* References are basically 'mentions', but can point to other things besides people too I think. But
    we're not supporting this yet.
    */
    dumpEventRefs = (event: Event): void => {
        const references = parseReferences(event);
        if (references?.length > 0) {
            console.log("REFS=" + S.util.prettyPrint(references));
        }
    }

    makeNostrEventWrapper = (event: Event, nodeId: string): J.NostrEventWrapper => {
        if (!event) return null;
        return {
            event: this.makeNostrEvent(event),
            npub: (event as any).npub,
            relays: (event as any).relays,
            nodeId
        };
    }

    makeNostrEvent = (event: Event): J.NostrEvent => {
        return {
            id: event.id,
            sig: event.sig,
            pubkey: event.pubkey,
            kind: event.kind,
            content: event.content,
            tags: event.tags,
            createdAt: event.created_at
        };
    }

    updateProfile = async () => {
        let profile = await nip05.queryProfile("jb55.com");
        console.log("PROFILE: " + S.util.prettyPrint(profile));

        profile = await nip05.queryProfile("jb55@jb55.com");
        console.log("PROFILE: " + S.util.prettyPrint(profile));
    }

    queryNetwork = async (background: boolean = false, dmsToMe: boolean): Promise<void> => {
        if (this.bigQueryRunning) {
            console.log("Avoiding large concurrent queries.");
            return;
        }

        const bigQueryStartTime = new Date().getTime();
        let ret: J.SaveNostrEventResponse = null;
        try {
            this.bigQueryRunning = true;

            if (!this.myFriends) {
                const res = await S.rpcUtil.rpc<J.GetPeopleRequest, J.GetPeopleResponse>("getPeople", {
                    nodeId: null,
                    type: "friends",
                    subType: J.Constant.NETWORK_NOSTR
                }, background);

                // console.log("readPostsFromFriends: " + S.util.prettyPrint(res.people));
                if (res.people?.length > 0) {
                    this.myFriends = res.people;
                }
                else {
                    this.myFriends = []; // set to empty array so we don't query again.
                    console.debug("No friends defined.");
                    return;
                }
            }

            const userNames: string[] = [];
            const relaysSet: Set<String> = new Set<String>();

            // scan all people to build list of users (PublicKeys) and relays to read from
            for (const person of this.myFriends) {
                if (!S.nostr.isNostrUserName(person.userName)) continue;
                userNames.push(person.userName.substring(1));
                const personRelays = this.getRelays(person.relays);
                if (personRelays) {
                    personRelays.forEach(r => relaysSet.add(r));
                }
            }

            let relaysArray: string[] = [];
            relaysSet.forEach((r: any) => relaysArray.push(r));
            relaysArray = this.addMyRelays(relaysArray);

            if (relaysArray.length === 0) {
                console.warn("no relays to read from.");
            }

            // if (userNames.length > 0 && relaysArray.length > 0) {
            //     console.log("Reading users from " + relaysArray.length + " relays. List=" + S.util.prettyPrint(userNames));
            // }
            ret = await this.readPosts(userNames, relaysArray, -1, background, dmsToMe, 100);
        }
        finally {
            this.bigQueryRunning = false;

            // if the querying happened fast enough (< 3 seconds) we can just refresh the results
            // and the user will not be confused by what just happened.
            if (new Date().getTime() - bigQueryStartTime < 3000) {
                S.srch.refreshFeed();
            }
            // but if query took a few seconds we just show the message rather than distrupting the GUI,
            // since they might be scrolling already or whatever.
            else {
                if (ret?.saveCount) {
                    dispatch("SetNostrNewMessageCount", s => {
                        s.nostrNewMessageCount = ret.saveCount;
                    });
                }
            }
        }
    }

    makeQueryKey = (users: string[], relays: string[]) => {
        users.sort();
        relays.sort();
        return users.join("\n") + "\n" + relays.join("\n");
    }

    addMyRelays = (relays: string[]): string[] => {
        if (relays == null) relays = [];
        const myRelays = this.getMyRelays();
        if (myRelays) {
            relays = relays.concat(myRelays);
        }
        return [...new Set(relays)];
    }

    getMyRelays = (): string[] => {
        return this.getRelays(this.getSessionRelaysStr());
    }

    isNostrNode = (node: J.NodeInfo) => {
        const id = S.props.getPropStr(J.NodeProp.OBJECT_ID, node);
        return id?.startsWith(".");
    }

    hasNostrTags = (node: J.NodeInfo) => {
        return !!S.props.getPropStr(J.NodeProp.NOSTR_TAGS, node);
    }

    isActPubNode = (node: J.NodeInfo) => {
        const id = S.props.getPropStr(J.NodeProp.OBJECT_ID, node);
        return id && !id.startsWith(".");
    }

    isNostrUserName = (userName: string) => {
        return userName?.startsWith(".") && userName.indexOf("@") === -1;
    }

    private async getRelaysForUser(node: J.NodeInfo) {
        if (getAs().isAnonUser) {
            return this.getMyRelays();
        }

        let relays: string[] = this.userRelaysCache.get(node.ownerId);

        // if not found in cache get from server
        if (!relays) {
            console.log("no relays cached for ownerId: " + node.ownerId + " so querying for them.");
            // todo-1: need a server call just for 'getUserRelays' so this is slightly more efficient.
            const res = await S.rpcUtil.rpc<J.GetUserProfileRequest, J.GetUserProfileResponse>("getUserProfile", {
                userId: node.ownerId,
                nostrPubKey: null
            });

            if (!res.success) {
                console.log("Unable to query user profile for userId: " + node.ownerId);
            }
            console.log("loadReplyChain userProfile (getting relays from this obj): " + S.util.prettyPrint(res.userProfile));
            relays = this.getRelays(res.userProfile.relays);

            // save relays in cache
            this.userRelaysCache.set(node.ownerId, relays);
        }
        else {
            console.log("found relays for ownerId " + node.ownerId + " from cache as: " + S.util.prettyPrint(relays));
        }
        return relays;
    }

    private async singleRelayQuery(relayUrl: string, query: any, background: boolean = false, silent: boolean = false): Promise<Event[]> {
        try {
            if (!silent) this.nostrQueryBegin(background);
            const relay = await this.openRelay(relayUrl);
            const ret = await relay.list([query]);
            relay.close();
            this.cacheEvents(ret);
            return ret;
        }
        finally {
            if (!silent) this.nostrQueryEnd(background);
        }
    }

    private async multiRelayQuery(relays: string[], query: any, background: boolean = false, silent: boolean = false): Promise<Event[]> {
        if (!relays) return null;

        // update knownRelays set.
        relays.forEach(r => this.knownRelays.add(r));
        const pool = new SimplePool();

        // DO NOT DELETE
        // -------------
        // const sub = pool.sub(relays, [{
        //     authors: [
        //         userKey
        //     ]
        // }]);
        // sub.on("event", event => {
        // });
        // DO NOT DELETE
        // -----------
        // const pubs = pool.publish(relays, newEvent)
        // pubs.on("ok", () => {
        //     // this may be called multiple times, once for every relay that accepts the event
        //     // ...
        // })

        try {
            if (!silent) this.nostrQueryBegin(background);
            const ret = await pool.list(relays, [query]);
            pool.close(relays);
            this.cacheEvents(ret);
            return ret;
        }
        finally {
            if (!silent) this.nostrQueryEnd(background);
        }
    }

    nostrQueryBegin = (background: boolean) => {
        if (!background) S.rpcUtil.incRpcCounter();
        this.queryCounter++;
        if (this.queryCounter === 1) {
            dispatch("NostrQueryBegin", s => {
                s.nostrQueryRunning = true;
            });
        }
    }

    nostrQueryEnd = (background: boolean) => {
        if (!background) S.rpcUtil.decRpcCounter();
        this.queryCounter--;
        if (this.queryCounter < 0) this.queryCounter = 0; // sanity check (should never be necessary)
        if (this.queryCounter === 0) {
            dispatch("NostrQueryEnd", s => {
                s.nostrQueryRunning = false;
            });
        }
    }

    normalizeURL = (url: string): string => {
        const p = new URL(url);
        p.pathname = p.pathname.replace(/\/+/g, "/");
        if (p.pathname.endsWith("/")) p.pathname = p.pathname.slice(0, -1);
        if ((p.port === "80" && p.protocol === "ws:") ||
            (p.port === "443" && p.protocol === "wss:")) {
            p.port = "";
        }

        p.searchParams.sort();
        p.hash = "";
        return p.toString();
    }

    showPrivateKey = async () => {
        const dlg = new ConfirmDlg("Are you sure? Show your Secret Key?", "Warning",
            "btn-danger", "alert alert-danger");
        await dlg.open();
        if (dlg.yes) {
            const ast = getAs();
            const msg = "Public ID: " + ast.userProfile.nostrNpub + "\n" +
                "Public Key (Hex): " + this.pk + "\n\n" +
                "Private Key (Hex): " + this.sk;

            S.util.showMessage(msg, "Nostr Identity", true);
        }
    }

    editPrivateKey = async (): Promise<void> => {
        const dlg = new SetNostrPrivateKeyDlg();
        await dlg.open();
    }

    // relays is optional and can be null or empty
    searchId = async (eventId: string, relays: string[] = null, background: boolean = false, sourceNode: J.NodeInfo = null) => {
        let event = null;
        try {
            if (!background) S.rpcUtil.incRpcCounter();
            const find = S.nostr.translateNip19(eventId);
            if (!relays || relays.length === 0) {
                relays = S.nostr.getMyRelays();
            }
            event = await S.nostr.getEvent(find, null, relays, background);
            if (event) {
                // Note: we must do a forceResend=true here to be sure we can get back the eventNodeId from the
                // server because we don't cache that on the client.
                const res = await S.nostr.persistEvents([event], background, true);
                if (res?.eventNodeIds?.length > 0) {
                    const desc = "For ID: " + eventId;

                    if (sourceNode) {
                        dispatch("settingSearchViewFromVals", s => {
                            s.searchViewFromTab = s.activeTab;
                            s.searchViewFromNode = sourceNode;
                        });
                    }

                    await S.srch.search(null, "node.id", res.eventNodeIds[0], null, desc, null, false,
                        false, 0, true, null, null, false, false, false);
                }
            }
            else {
                S.util.showMessage("Could not find NostrId from relays: " + eventId, "Nostr", true);
            }
        }
        finally {
            if (!background) S.rpcUtil.decRpcCounter();
        }
        return event;
    }
}
