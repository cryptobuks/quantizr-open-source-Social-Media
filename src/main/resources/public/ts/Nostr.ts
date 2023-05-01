import {
    Event,
    Kind,
    Pub,
    Relay,
    SimplePool,
    generatePrivateKey,
    getEventHash,
    getPublicKey,
    nip05,
    nip19,
    parseReferences,
    relayInit,
    signEvent,
    validateEvent, verifySignature
} from "nostr-tools";
import { Constants as C } from "./Constants";
import * as J from "./JavaIntf";
import { S } from "./Singletons";
import { Comp } from "./comp/base/Comp";
import { getAs } from "./AppContext";
import { Val } from "./Val";
import { ConfirmDlg } from "./dlg/ConfirmDlg";

/* This class holds our initial experimentation with Nostr, and the only GUI for this is a single
link on the Admin Console that can run the "test()" method

References:
https://github.com/nostr-protocol/nips/blob/master/01.md
https://github.com/nbd-wtf/nostr-tools
*/

export class Nostr {
    // TEST_RELAY_URL: string = "wss://nostr-pub.wellorder.net"; // "wss://relay.damus.io/";
    // TEST_USER_KEY: string = "35d26e4690cbe1a898af61cc3515661eb5fa763b57bd0b42e45099c8b32fd50f";

    // TEST_RELAY_URL: string = "wss://nostr-pub.wellorder.net\nwss://nos.lol\nwss://relay.damus.io";
    // TEST_USER_KEY: string = "1qguf67wjaq05snx0nfwgrpnhls8a94stquu58lzpnr0q2355u45sjs9fsr"; // "32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245";

    sk: string = null; // secret key, hex string
    pk: string = null; // public key, hex string
    npub: string = null; // npub (of public key)

    // We maintain this set of all encountered relays as they're found so that if we ever need to look
    // up something and have no known relay we can at least try these. Potentially we could save these
    // on the server maybe specific to the given user? Or should we hold ONLY in local/browser storage?
    knownRelays: Set<string> = new Set<string>();

    // hold any data we've already encountered so we can avoid looking in relays when possible
    metadataCache: Map<string, Event> = new Map<string, Event>(); // Kind.Metata (map key==event id)
    textCache: Map<string, Event> = new Map<string, Event>(); // Kind.Text (map key==user's pubkey)
    userRelaysCache: Map<string, string[]> = new Map<string, string[]>(); // (map key==Quanta UserAccount NodeId)
    persistedEvents: Set<string> = new Set<string>(); // keeps track of what we've already posted to server

    // This can be run from Admin Console
    test = async () => {
        await this.initKeys();

        // this.readUserMetadata(this.TEST_USER_KEY, this.TEST_RELAY_URL, false, false, null);

        // this.testNpub();

        // await this.getEvent(["wss://relay.snort.social"], "20cfef67ce5fd1a99e2bb7993be0e0cc3ad59fb78fc8898bc998c1864b8a08e2", false);

        // const event = await this.getEvent(["wss://relay.snort.social",
        //     "wss://nostr-pub.wellorder.net",
        //     "wss://nos.lol",
        //     "wss://relay.damus.io"
        // ],
        //     // "nostr:note1px3tv34c0mh8qxwslnpsqye36jr5hgqes67kls5w9p29zk4ye94qjtu2tn",
        //     "73fe45d89cf4872ea17ee0fb232ea1b31543c327cee9c264188cd14c6c146a30",
        //     false);
        // if (event) {
        //     console.log("test(). event=" + S.util.prettyPrint(event));
        //     this.dumpEventRefs(event);
        // }

        // this.sendMessageToUser("Hi Clay!", ["wss://relay.snort.social"], "npub1r0ccr27yxfm20lacgqfl8xwt4vl4j3ggs7nc29nkll6sthdk742stk6qn7");

        // this.testNpub();
        // await this.readPosts(this.TEST_USER_KEY, this.TEST_RELAY_URL, 1680899831);

        // await this.updateProfile();
        // await this.readUserMetadata(this.TEST_USER_KEY, this.TEST_RELAY_URL, false);
        // console.log("SaveCount: " + res.saveCount);

        // this.saveEvent();
        // this.createEvent();

        // Object from original examples:
        // await this.getEvent(this.TEST_RELAY_URL, "d7dd5eb3ab747e16f8d0212d53032ea2a7cadef53837e5a6c66d42849fcb9027");

        // Object posted from Quanta
        // await this.getEvent(this.TEST_RELAY_URL, "fec9091d99d8aa4dc1d544563cecb587fea5c3ccb744ca668c8b4021daced097");
        // await this.publishEvent();
    }

    // todo-0: this won't run unless user does a "Save" from their userprofile dlg right? But we really need to consider
    // them a "live" user whenever they edit their relays tho instead right?
    publishUserMetadata = async (): Promise<void> => {
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

        // we need to scan one relay at a time to verify we have our identity on each one.
        relays.forEach(async (relay: string) => {
            const eventVal = new Val<Event>();

            // try to read the metadata from the relay
            await S.nostr.readUserMetadata(this.pk, relay, false, false, eventVal);

            // if the relay didn't have matching out metadata we need to publish it to this relay
            if (!this.metadataMatches(currentMetaPayload, eventVal.val)) {
                console.log("Pushing new meta to relay: " + relay);

                // don't await for this, we can let them all run in parallel
                this.publishEvent(currentMeta, relay);
            }
            else {
                console.log("Meta is up to date on relay: " + relay);
            }
        });
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

    cacheEvent = (event: Event): void => {
        switch (event.kind) {
            case Kind.Text:
                if (this.textCache.size > 1000) {
                    this.textCache.clear();
                }
                this.textCache.set(event.id, event);
                break;
            case Kind.Metadata:
                if (this.metadataCache.size > 1000) {
                    this.metadataCache.clear();
                }
                this.metadataCache.set(event.pubkey, event);
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
        console.log("Nostr Keys:");
        // console.log("  Priv: " + this.sk); // keep this one secret by default
        console.log("  PubKey: " + this.pk);
        console.log("  npub: " + this.npub);
    }

    // Initializes our keys, and returns the npub key
    initKeys = async (): Promise<void> => {
        // if already initialized do nothing.
        if (this.pk) return;

        // Yes this is bad practice to save key this way, but this is just a prototype!
        this.sk = await S.localDB.getVal(C.LOCALDB_NOSTR_PRIVATE_KEY);

        // If key was not yet created, then create one and save it.
        if (!this.sk) {
            this.sk = generatePrivateKey();
            S.localDB.setVal(C.LOCALDB_NOSTR_PRIVATE_KEY, this.sk);
        }

        this.pk = getPublicKey(this.sk);
        this.npub = nip19.npubEncode(this.pk);
        if (this.pk !== this.translateNip19(this.npub)) {
            console.error("Problem with npub key");
        }
    }

    // Builds all the nodes in the thread (by traversing up the tree of replies) going back in time towards
    // the original post.
    loadReplyChain = async (node: J.NodeInfo): Promise<J.SaveNostrEventResponse> => {
        const tags: any = S.props.getPropObj(J.NodeProp.NOSTR_TAGS, node);
        if (!Array.isArray(tags)) return null;
        // console.log("loadReplyChain of nodeId: " + node.id);
        let pool: SimplePool = null;
        let relaySet: Set<string> = null;

        try {
            pool = new SimplePool();
            S.rpcUtil.incRpcCounter();

            // Get userRelays associated with this the owner of 'node'
            let relays: string[] = await this.getRelaysForUser(node);
            relays = this.addMyRelays(relays);

            if (relays.length === 0) {
                console.warn("No relays!");
                return null;
            }

            // collections we'll be adding to as we walk up the reply tree
            const events: Event[] = [];
            relaySet = new Set<string>(relays);

            // now recursively walk up the the entire thread one reply back at a time.
            await this.traverseUpReplyChain(events, tags, pool, relaySet);

            if (!events) {
                console.log("No reply info found.");
                return;
            }
            const ret = await this.persistEvents(events);
            return ret;
        }
        finally {
            S.rpcUtil.decRpcCounter();
            pool?.close(this.toRelayArray(relaySet));
        }
    }

    // Recursive method. As we walk up the chain we maintain the set of all relays used during the walk, so we're likely to
    // be only looking at the relays we will find parts of this thread on.
    traverseUpReplyChain = async (events: Event[], tags: string[][], pool: SimplePool, relaySet: Set<string>): Promise<void> => {

        // get the array representing what event (with 'tags' in it) is a reply to.
        const repliedToArray: string[] = this.getRepliedToItem(tags);

        // if node wasn't a reply to anything, return null, we're done.
        if (!repliedToArray || repliedToArray.length < 3) return null;

        const eventRepliedTo = repliedToArray[1];
        const relayRepliedTo = repliedToArray[2];

        // if we found what event the 'tags' had as it's replyTo
        if (eventRepliedTo) {
            // if no relays were listed in replyTo
            if (relayRepliedTo) {
                relaySet.add(relayRepliedTo);
            }

            // console.log("LOADING ThreadItem: " + eventRepliedTo);
            const event = await this.getEvent(eventRepliedTo, pool, this.toRelayArray(relaySet));
            if (event) {
                // console.log("REPLY: Chain Event: " + S.util.prettyPrint(event));
                // add to front of array so the chronological ordering is top down.
                events.unshift(event);

                // todo-0: put this 50 in a var.
                if (events.length > 50) {
                    console.warn("stopping after max thread events: " + events.length);
                    return;
                }

                if (Array.isArray(event.tags)) {
                    await this.traverseUpReplyChain(events, event.tags, pool, relaySet);
                }
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
        if (!Array.isArray(tags)) return null;
        let anyEvent: string[] = null;
        let replyEvent: string[] = null;
        let rootEvent: string[] = null;

        // if we have an array of "e" tags
        for (const ta of tags) {
            if (Array.isArray(ta)) {
                if (ta[0] === "e") {
                    // deprecated positional array (["e", <event-id>, <relay-url>] as per NIP-01.)
                    if (ta.length < 4) {
                        anyEvent = ta;
                    }
                    // Preferred non-deprecated way (["e", <event-id>, <relay-url>, <marker>])
                    else if (ta.length === 4) {
                        if (ta[3] === "reply") {
                            replyEvent = ta;
                        }
                        else if (ta[3] === "root") {
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
        const event: any = {
            kind: Kind.Text,
            created_at: Math.floor(Date.now() / 1000),
            tags: [],
            content: "test from Quanta.wiki",
            pubkey: this.pk
        };

        event.id = getEventHash(event);
        event.sig = signEvent(event, this.sk);
        this.cacheEvent(event);
        // console.log("NEW EVENT: " + S.util.prettyPrint(event));
        return event;
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
        const event: any = {
            kind: Kind.Metadata,
            created_at: Math.floor(Date.now() / 1000),
            tags: [],
            content: JSON.stringify(meta),
            pubkey: this.pk
        };

        event.id = getEventHash(event);
        event.sig = signEvent(event, this.sk);
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

    /* persistResponse.res will contain the data saved on the server, but we accept null for persistResonse
    to indicate that no persistence on the server should be done,

    'pool' arg is optional and if not passed then relays will be used for making a new pool. When pool is passed in
    we DO ensure all the mentioned relays *are* added to it if not already in it.
    */
    getEvent = async (id: string, pool: SimplePool, relays: string[]): Promise<Event> => {
        // console.log("getEvent: nostrId=" + id);
        id = this.translateNip19(id);

        // return the cached event if we have it.
        const cachedEvent = this.textCache.get(id);
        if (cachedEvent) {
            return cachedEvent;
        }

        // query for up to 10 events just so we can get the latest one
        const query: any = {
            ids: [id],
            limit: 1
        };

        try {
            S.rpcUtil.incRpcCounter();
            let events = null;

            // if a pool was provided use it.
            if (pool) {
                // if we have relays make sure our pool does contain them all.
                relays?.forEach(r => pool.ensureRelay(r));
                events = await pool.list(relays, [query]);
            }
            // else call queryRelays which does automiatic pooling if it can.
            else {
                events = await this.queryRelays(relays, query);
            }
            // console.log("getEvent: " + S.util.prettyPrint(events));

            if (events?.length > 0) {
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

    publishEvent = async (event: Event, relayStr: string): Promise<void> => {
        return new Promise<void>(async (resolve, reject) => {
            // console.log("Publishing Event: " + event.id + " to relay " + relayStr);
            const relay = await this.openRelay(relayStr);
            const pub = relay.publish(event);

            pub.on("ok", () => {
                console.log(`${relay.url} accepted event`);
                relay.close();
                resolve();
            });

            pub.on("failed", (reason: any) => {
                console.log(`failed to publish to ${relay.url}: ${reason}`);
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

    loadUserMetadata = async (userInfo: J.NewNostrUsersPushInfo): Promise<void> => {
        // todo-0: the userInfo items needs to hold relays too!?? per user?
        const relays = this.getRelays(getAs().userProfile.relays);
        if (relays.length === 0) {
            console.log("loadUserMetadata ignored. No relays.");
            return;
        }
        userInfo.users?.forEach(user => {
            console.log("SERVER REQ. USER LOAD: " + user);

            // todo-0: Important to queue these and persist all at once! for now we persist each individually instead of queueing them up.
            S.nostr.readUserMetadata(user, getAs().userProfile.relays, false, true, null);
        });
        return null;
    }

    // todo-0: this function is not good. We should only read one user at a time so that when we get multiple
    // results back we know we can take the latest one and that will be perfect.
    readMultiUserMetadata = async (users: string[], relays: string[]): Promise<Event[]> => {
        if (relays.length === 0) {
            console.warn("No relays. Can't lookup users");
            return null;
        }

        users = users.map(u => this.translateNip19(u));
        // NOTE: By the time we get here 'user' will be a PublicKey (not npub or nip05)

        const query: any = {
            authors: users,
            kinds: [Kind.Metadata]
            // limit: users.length * 3 // <== this is a WAG to try to get only what we need
        };

        const events = await this.queryRelays(relays, query);
        if (events) {
            events.forEach(e => (e as any).npub = nip19.npubEncode(e.pubkey));
        }

        this.revChronSort(events);
        const retEvents: Event[] = [];
        const usersFound = new Set<string>();
        events.forEach(e => {
            if (!usersFound.has(e.pubkey)) {
                usersFound.add(e.pubkey);
                retEvents.push(e);
            }
        });

        if (usersFound.size < users.length) {
            console.warn("NOT ALL USER METADATA WAS FOUND: missing " + (users.length - usersFound.size));
        }

        return retEvents;
    }

    // user can be the hex, npub, or NIP05 address of the identity. isNip05 must be set to true if 'user' is a nip05.
    // If output argument 'outEvent' is passed as non-null then the event is sent back in 'outEvent.val'
    readUserMetadata = async (user: string, relayUrl: string, isNip05: boolean, persist: boolean, outEvent: Val<Event>): Promise<J.SaveNostrEventResponse> => {
        console.log("Getting Metadata for Identity: " + user);
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

        const events = await this.queryRelays(relays, query);
        if (events) {
            console.log("Result of Metadata Lookup: " + S.util.prettyPrint(events));
        }

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
            return await this.persistEvents(events);
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
    //
    readPosts = async (userKeys: string[], relays: string[], since: number): Promise<J.SaveNostrEventResponse> => {
        userKeys = userKeys.map(u => this.translateNip19(u));

        const query: any = {
            authors: userKeys,
            kinds: [Kind.Text],
            limit: 50
        };
        if (since !== -1) {
            query.since = since;
        }

        let ret = null;
        try {
            S.rpcUtil.incRpcCounter();
            const events = await this.queryRelays(relays, query);
            ret = await this.persistEvents(events);
        }
        finally {
            S.rpcUtil.decRpcCounter();
        }

        return ret;
    }

    hasNostrAcls = (node: J.NodeInfo): boolean => {
        if (!node || !node.ac || node.ac.length === 0) return false;
        return !!node.ac.find(acl => !!acl.nostrNpub);
    }

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
    */
    prepareOutboundEvent = async (node: J.NodeInfo, relays: string[]): Promise<Event> => {
        if (!node || !node.ac || node.ac.length === 0) return null;
        const tags: string[][] = [];
        const npubs: string[] = [];

        let isPublic = false;
        let relaysStr = "";
        node.ac.forEach(acl => {
            if (acl.principalName === J.PrincipalName.PUBLIC) {
                isPublic = true;
            }
            else if (acl.nostrNpub) {
                const pubkey = this.translateNip19(acl.nostrNpub);
                tags.push(["p", pubkey]);
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

        const words = node.content?.split(/[ \n\r\t]+/g);
        words?.forEach(w => {
            if (w.startsWith("npub")) {
                // const acl = node.ac.find(acl => acl.nostrNpub?.startsWith(w));
                // node.content = node.content.replace(w, acl.nostrNpub);
                for (let i = 0; i < npubs.length; i++) {
                    if (npubs[i].startsWith(w)) {
                        node.content = node.content.replace(w, `#[${i}]`);
                    }
                }
            }
        });

        // Nostr's way of adding attached files is just to mention their URL in the content, so let's add all that.
        const content = this.getContentWithUrlsAdded(node);

        const event: any = {
            kind: 1,
            pubkey: this.pk,
            created_at: Math.floor(Date.now() / 1000),
            tags,
            content
        };
        event.id = getEventHash(event);
        event.sig = signEvent(event, this.sk);
        this.cacheEvent(event);

        relays.push(...this.getRelays((relaysStr || "") + "\n" + (getAs().userProfile.relays) || ""));
        return event;
    }

    getContentWithUrlsAdded = (node: J.NodeInfo): string => {
        let ret = node.content || "";
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
        await this.initKeys();
        return new Promise<boolean>(async (resolve, reject) => {
            // DO NOT DELETE (until Nostr testing is finished.)
            console.log("Outbound Nostr Event: " + S.util.prettyPrint(event));

            let pub: Pub = null;
            let relay: Relay = null;
            let pool: SimplePool = null;
            let poolRemainder = 0;

            if (relays.length === 1) {
                relay = await this.openRelay(relays[0]);
                pub = relay.publish(event);
            } else {
                pool = new SimplePool();
                pub = pool.publish(relays, event);
                poolRemainder = relays.length;
            }

            pub.on("ok", () => {
                console.log("relay accepted event");
                if (relay) {
                    relay.close();
                    relay = null;
                }

                if (pool && --poolRemainder === 0) {
                    pool.close(relays);
                    pool = null;
                }
                resolve(true);
            });

            pub.on("failed", (reason: any) => {
                console.log(`relay failed: ${reason}`);
                if (relay) {
                    relay.close();
                    relay = null;
                }
                if (pool && --poolRemainder === 0) {
                    pool.close(relays);
                    pool = null;
                }
                resolve(false);
            });
        });
    }

    queryRelays = async (relays: string[], query: any): Promise<Event[]> => {
        if (relays.length === 1) {
            return await this.singleRelayQuery(relays[0], query);
        }
        else {
            return await this.multiRelayQuery(relays, query);
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

    persistEvents = async (events: Event[]): Promise<J.SaveNostrEventResponse> => {
        if (!events || events.length === 0) return;

        // remove any events we know we've already persisted
        events = events.filter(e => !this.persistedEvents.has(e.id));

        // map key is 'pk'.
        const userSet: Map<String, J.NostrUserInfo> = new Map<String, J.NostrUserInfo>();

        let idx = 0;
        events.forEach(event => {
            this.cacheEvent(event);
            console.log("PERSIST EVENT[" + (idx++) + "]: " + S.util.prettyPrint(event));
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
                        npub: nip19.npubEncode(ref.profile.pubkey)
                    });
                }
            });

            // this.dumpEventRefs(event);
        });

        const userInfo: J.NostrUserInfo[] = Array.from(userSet.values());
        // console.log("saveNostrEvents has this userInfo: " + userInfo);

        // Push the events up to the server for storage
        const res = await S.rpcUtil.rpc<J.SaveNostrEventRequest, J.SaveNostrEventResponse>("saveNostrEvents", {
            events: events.map(e => this.makeNostrEvent(e)),
            userInfo
        });

        // keep track of what we've just sent to server.
        events.forEach(e => this.persistedEvents.add(e.id));

        // console.log("PERSIST EVENTS Resp: " + S.util.prettyPrint(res));
        return res;
    }

    replaceNostrRefs = (node: J.NodeInfo, val: string): string => {
        if (!this.hasNostrTags(node) || !node.nostrPubKey) return val;

        try {
            const event = this.makeUnsignedEventFromNode(node);
            const references = parseReferences(event);
            if (!references || references.length === 0) {
                return val;
            }
            // console.log("REFS=" + S.util.prettyPrint(references));
            references.forEach((ref: any) => {
                if (ref.profile) {
                    // todo-0: need a background thread that queues up these pubkeys, and queries for them
                    // first on the server, and secondarily on relays until it can eventually render with
                    // the actual username in this span, and also be sure they're cached on the client too
                    const elmId = Comp.getNextId();
                    const keyAbbrev = ref.profile.pubkey.substring(0, 10);
                    val = val.replace(ref.text, `<span class='nostrLink' id='${elmId}'>[User ${keyAbbrev}]</span>`);
                    setTimeout(() => {
                        const e: HTMLElement = document.getElementById(elmId);
                        if (e) {
                            e.addEventListener("click", () => {
                                S.user.showUserProfileByNostrKey(ref.profile.pubkey);
                            });
                        }
                    }, 1000);
                }
                else if (ref.event) {
                    const text = ref.text.substring(6);
                    const shortId = text.substring(5, 13) + "...";
                    // Note: 'nostr-note' class in here is so that our OpenGraph link detector can ignore this and leave
                    // it as a regular anchor tag link
                    val = val.replace(ref.text, `<a href='${window.location.origin}?nostrId=${ref.event.id}&refNodeId=${node.ownerId}' class='nostr-note nostrLink' target='_blank'>[Note ${shortId}]</a>`);
                }
                else if (ref.address) {
                    // todo-1: add support for address
                }
            });
        }
        catch (ex) {
            S.util.logErr(ex, "Failed processing Nostr Refs on: " + S.util.prettyPrint(node));
        }
        return val;
    }

    /* Creates an unsigned event */
    makeUnsignedEventFromNode = (node: J.NodeInfo): any => {
        const event: any = {
            kind: 1,
            pubkey: this.translateNip19(node.nostrPubKey),
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

    makeNostrEvent = (event: Event): J.NostrEvent => {
        if (!event) return null;
        return {
            id: event.id,
            sig: event.sig,
            pk: event.pubkey,
            kind: event.kind,
            content: event.content,
            tags: event.tags,
            timestamp: event.created_at,

            // note: npub on this Event is Quanta-specific
            npub: (event as any).npub,
            relays: (event as any).relays
        };
    }

    updateProfile = async () => {
        let profile = await nip05.queryProfile("jb55.com");
        console.log("PROFILE: " + S.util.prettyPrint(profile));

        profile = await nip05.queryProfile("jb55@jb55.com");
        console.log("PROFILE: " + S.util.prettyPrint(profile));
    }

    readPostsFromFriends = async (): Promise<void> => {
        const lastUsersQueryTime: number = await S.localDB.getVal(C.LOCALDB_NOSTR_LAST_USER_QUERY_TIME) || 0;
        const lastUsersQueryKey: string = await S.localDB.getVal(C.LOCALDB_NOSTR_LAST_USER_QUERY_KEY);
        const curTime = Math.floor(Date.now() / 1000);

        const res = await S.rpcUtil.rpc<J.GetPeopleRequest, J.GetPeopleResponse>("getPeople", {
            nodeId: null,
            type: "friends",
            subType: "nostr"
        });

        // console.log("readPostsFromFriends: " + S.util.prettyPrint(res.people));
        if (!res.people || res.people.length === 0) {
            // console.debug("No friends defined.");
            return;
        }
        const userNames: string[] = [];
        const relaysSet: Set<String> = new Set<String>();

        // scan all people to build list of users (PublicKeys) and relays to read from
        for (const person of res.people) {
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

        if (userNames.length > 0 && relaysArray.length > 0) {
            console.log("Reading " + userNames.length + " users from " + relaysArray.length + " relays.");
        }

        const thisQueryKey = this.makeQueryKey(userNames, relaysArray);
        let since = -1;

        // if this is the same users and relays we last queried (key matches) then we set the
        // 'since' query time, so we only get new stuff we didn't already see
        if (thisQueryKey === lastUsersQueryKey) {
            if (lastUsersQueryTime > 0 && (curTime - lastUsersQueryTime) < 30) {
                console.log("Skipping Nostr query. Identical query was less that 30 secs ago.");
                return;
            }
            since = lastUsersQueryTime;
        }
        else {
            S.localDB.setVal(C.LOCALDB_NOSTR_LAST_USER_QUERY_KEY, thisQueryKey);
        }

        S.localDB.setVal(C.LOCALDB_NOSTR_LAST_USER_QUERY_TIME, curTime);
        console.log("readPosts: since=" + since);
        await this.readPosts(userNames, relaysArray, since);
    }

    makeQueryKey = (users: string[], relays: string[]) => {
        users.sort();
        relays.sort();
        return users.join("\n") + "\n" + relays.join("\b");
    }

    addMyRelays = (relays: string[]): string[] => {
        if (relays == null) relays = [];
        const myRelays = this.getRelays(getAs().userProfile.relays);
        if (myRelays) {
            relays = relays.concat(myRelays);
        }
        return [...new Set(relays)];
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
        let relays: string[] = this.userRelaysCache.get(node.ownerId);

        // if not found in cache get from server
        if (!relays) {
            // todo-1: need a server call just for 'getUserRelays' so this is slightly more efficient.
            const res = await S.rpcUtil.rpc<J.GetUserProfileRequest, J.GetUserProfileResponse>("getUserProfile", {
                userId: node.ownerId,
                nostrPubKey: null
            });

            if (!res.success) {
                console.log("Unable to query user profile for userId: " + node.ownerId);
            }
            // console.log("loadReplyChain userProfile: " + S.util.prettyPrint(res.userProfile));
            relays = this.getRelays(res.userProfile.relays);

            // save relays in cache
            this.userRelaysCache.set(node.ownerId, relays);
        }
        return relays;
    }

    private async singleRelayQuery(relayUrl: string, query: any): Promise<Event[]> {
        const relay = await this.openRelay(relayUrl);
        const ret = await relay.list([query]);
        relay.close();
        return ret;
    }

    private async multiRelayQuery(relays: string[], query: any): Promise<Event[]> {
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

        const ret = await pool.list(relays, [query]);
        pool.close(relays);
        return ret;
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
}
