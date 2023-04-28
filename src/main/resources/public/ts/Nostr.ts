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

/* This class holds our initial experimentation with Nostr, and the only GUI for this is a single
link on the Admin Console that can run the "test()" method

References:
https://github.com/nostr-protocol/nips/blob/master/01.md
https://github.com/nbd-wtf/nostr-tools
*/

export class Nostr {
    TEST_RELAY_URL: string = "wss://nostr-pub.wellorder.net"; // "wss://relay.damus.io/";
    TEST_USER_KEY: string = "35d26e4690cbe1a898af61cc3515661eb5fa763b57bd0b42e45099c8b32fd50f";

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
            const outEvent: any = {};

            // try to read the metadata from the relay
            await S.nostr.readUserMetadata(this.pk, relay, false, false, outEvent);

            // if the relay didn't have matching out metadata we need to publish it to this relay
            if (!this.metadataMatches(currentMetaPayload, outEvent.val)) {
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

        // this.printKeys();
    }

    // todo-0: Need to investigate how we can reuse SimplePool in this process.
    loadReplyChain = async (node: J.NodeInfo): Promise<J.SaveNostrEventResponse> => {
        const tags: any = S.props.getPropObj(J.NodeProp.NOSTR_TAGS, node);
        if (!Array.isArray(tags)) return null;
        // console.log("loadReplyChain of nodeId: " + node.id);
        try {
            S.rpcUtil.incRpcCounter();

            // try to get userRelays from local cache
            let relays: string[] = await this.getRelaysForUser(node);

            const events: Event[] = [];
            const preferredRelays: Set<string> = new Set<string>();
            const threadUsers: Set<string> = new Set<string>();

            // if no relays for this user fall back to our own relays
            if (relays.length === 0) {
                relays = this.getRelays(getAs().userProfile.relays);
            }
            relays.forEach(r => preferredRelays.add(r));

            await this.traverseUpReplyChain(events, tags, preferredRelays, threadUsers);

            // before we persist events, we need to prefix all the events with
            // the event metadata for all users involved so that when the server processes
            // the "persistEvents" it will encounter the users ahead of all the data which is
            // required or else it would fail trying to save data but not having a user accuont
            // to put something under.
            //
            const userMetadata = await this.readMultiUserMetadata(this.toUserArray(threadUsers), relays);
            // console.log("metadataForUsers: " + S.util.prettyPrint(userMetadata));

            if (!events || !userMetadata) return;
            console.log("PERSISTING THREAD EVENTS: ");
            const ret = await this.persistEvents([...userMetadata, ...events], null);
            return ret;
        }
        finally {
            S.rpcUtil.decRpcCounter();
        }
    }

    // Recursive method. As we walk up the chain we maintain the set of all relays used during the walk, so we're likely to
    // be only looking at the relays we will find parts of this thread on.
    traverseUpReplyChain = async (events: Event[], tags: string[][], preferredRelays: Set<string>,
        threadUsers: Set<string>): Promise<void> => {
        // if we have an array of "e" tags
        let eventRepliedTo: string = null;
        let relayRepliedTo: string = null;

        // todo-0: we could have used 'getReferences' here...
        // iterate all tags and whatever the right most (last) values arefor the *RepliedTo vars will be what we want
        // becasue according to spec that's what is being replied to.
        for (const ta of tags) {
            // todo-0: are quanta outbound notes correctly setting the "relay replied to" value ?
            if (Array.isArray(ta)) {
                if (ta[0] === "e") {
                    // deprecated positional array (["e", <event-id>, <relay-url>] as per NIP-01.)
                    if (ta.length < 4) {
                        eventRepliedTo = ta[1];
                        relayRepliedTo = ta[2];
                    }
                    // Preferred non-deprecated way (["e", <event-id>, <relay-url>, <marker>])
                    else if (ta[3] === "reply") {
                        eventRepliedTo = ta[1];
                        relayRepliedTo = ta[2];
                    }
                }
            }
        }

        if (eventRepliedTo) {
            if (!relayRepliedTo) {
                if (preferredRelays.size === 0) {
                    console.log("Failed to traverse thread because eventId " + eventRepliedTo + " didn't have a relay in tag array.")
                    return null;
                }
            }
            else {
                preferredRelays.add(relayRepliedTo);
            }

            // console.log("LOADING ThreadItem: " + eventRepliedTo);
            const event = await this.getEvent(this.toRelayArray(preferredRelays), eventRepliedTo, null);
            if (event) {
                threadUsers.add(event.pubkey);
                // console.log("REPLY: Chain Event: " + S.util.prettyPrint(event));
                // add to front of array so the chronological ordering is top down.
                events.unshift(event);

                if (events.length > 50) {
                    console.warn("stopping after enough thread events: " + events.length);
                    return;
                }
                if (Array.isArray(event.tags)) {
                    await this.traverseUpReplyChain(events, event.tags, preferredRelays, threadUsers);
                }
            }
        }
    }

    toUserArray = (usersSet: Set<string>): string[] => {
        const users: string[] = [];
        if (usersSet) {
            usersSet.forEach(r => {
                users.push(r);
            });
        }
        return users;
    }

    toRelayArray = (relaySet: Set<string>): string[] => {
        const relays: string[] = [];
        if (relaySet) {
            relaySet.forEach(r => {
                relays.push(r);
                this.knownRelays.add(r);
            });
        }
        return relays;
    }

    /* Returns the Nostr ID of whatever the node is a reply to if it's a reply or else null
    ref: https://github.com/nostr-protocol/nips/blob/master/10.md
    */
    getRepliedToItem = (node: J.NodeInfo): string[] => {
        const tags: any = S.props.getPropObj(J.NodeProp.NOSTR_TAGS, node);
        if (!Array.isArray(tags)) return null;

        // if we have an array of "e" tags
        for (const ta of tags) {
            if (Array.isArray(ta)) {
                if (ta[0] === "e") {
                    // deprecated positional array (["e", <event-id>, <relay-url>] as per NIP-01.)
                    if (ta.length < 4) {
                        return ta;
                    }

                    // Preferred non-deprecated way (["e", <event-id>, <relay-url>, <marker>])
                    if (ta.length === 4 && ta[3] === "reply") {
                        return ta;
                    }
                }
            }
        }
        return null;
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
    to indicate that no persistence on the server should be done */
    getEvent = async (relays: string[], id: string, persistResponse: any): Promise<Event> => {
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
            limit: 10
        };

        try {
            S.rpcUtil.incRpcCounter();
            const events = await this.queryRelays(relays, query);
            // console.log("getEvent: " + S.util.prettyPrint(events));
            if (events?.length > 0) {
                this.revChronSort(events);
                const oneEvent = events[0];
                if (persistResponse) {
                    persistResponse.res = await this.persistEvents([oneEvent], relays.join("\n"));
                }
                return oneEvent;
            }
            else {
                console.log("Unable to load event: " + id + " (tried on " + relays.length + " relays)");
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
    readUserMetadata = async (user: string, relayUrl: string, isNip05: boolean, persist: boolean, outEvent: any): Promise<J.SaveNostrEventResponse> => {
        console.log("Getting Metadata for Identity: " + user);
        let relays = this.getRelays(relayUrl);
        if (isNip05) {
            const profile = await nip05.queryProfile(user);
            if (!profile) return null;
            console.log("NIP05: " + S.util.prettyPrint(profile));

            // todo-0: we should transfer the NIP05 URL up to the server so it can be stored
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
            limit: 10
        };

        const events = await this.queryRelays(relays, query);
        if (events) {
            console.log("Result of Metadata Lookup: " + S.util.prettyPrint(events));
        }

        if (events?.length > 0) {
            this.revChronSort(events);
            const event: any = events[0];
            event.npub = nip19.npubEncode(user);

            if (outEvent) {
                outEvent.val = event;
            }
        }
        else {
            console.log("Failed to find user: pubKey=" + user);
            return null;
        }

        if (persist) {
            return await this.persistEvents(events, relayUrl);
        }
        return null;
    }

    revChronSort = (events: Event[]): void => {
        if (!events || events.length === 0) return;
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
    // NOTE: We can set a limit and the relay is supposed to send the most *recent* ones up to around
    // that value.
    //
    readPosts = async (userKeys: string[], relays: string[], since: number): Promise<J.SaveNostrEventResponse> => {
        // console.log("readPosts for userKey: " + userKey);
        userKeys = userKeys.map(u => this.translateNip19(u));

        const query: any = {
            authors: userKeys,
            kinds: [Kind.Text],
            limit: 25
        };
        if (since !== -1) {
            query.since = since;
        }

        let ret = null;
        try {
            S.rpcUtil.incRpcCounter();
            const events = await this.queryRelays(relays, query);
            ret = await this.persistEvents(events, null);
        }
        finally {
            S.rpcUtil.decRpcCounter();
        }

        return ret;
    }

    /* Creates an event node to send to nostr relays and also performs the following side effects:
    *
    * - for each ac on the node, add a "p" into the tags array, and sets the tags array onto the node
    * - substitutes npub tags into node.content
    * - build relaysStr based on acl list
    * - sets NOSTR_TAGS and NOSTR_ID onto the node
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

        S.props.setPropVal(J.NodeProp.NOSTR_TAGS, node, tags);

        const event: any = {
            kind: 1,
            pubkey: this.pk,
            created_at: Math.floor(Date.now() / 1000),
            tags,
            content: node.content
        };
        event.id = getEventHash(event);
        event.sig = signEvent(event, this.sk);
        this.cacheEvent(event);

        S.props.setPropVal(J.NodeProp.NOSTR_ID, node, event.id);
        relays.push(...this.getRelays(relaysStr + "\n" + getAs().userProfile.relays));
        return event;
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

    persistEvents = async (events: Event[], relays: string): Promise<J.SaveNostrEventResponse> => {
        if (!events || events.length === 0) return;

        let idx = 0;
        events.forEach(event => {
            this.cacheEvent(event);
            console.log("PERSIST EVENT[" + (idx++) + "]: " + S.util.prettyPrint(event));
            if (!this.checkEvent(event)) {
                console.log("eventCheck Failed.");
            }

            // this.dumpEventRefs(event);
        });

        // Push the events up to the server for storage
        const res = await S.rpcUtil.rpc<J.SaveNostrEventRequest, J.SaveNostrEventResponse>("saveNostrEvents", {
            events: this.makeEventsList(events),
            relays
        });
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

    makeEventsList = (events: any[]): J.NostrEvent[] => {
        const ret: J.NostrEvent[] = [];
        for (const event of events) {
            ret.push({
                id: event.id,
                sig: event.sig,
                pk: event.pubkey,
                kind: event.kind,
                content: event.content,
                tags: event.tags,
                timestamp: event.created_at,

                // note: npub on this Event is Quanta-specific
                npub: event.npub
            });
        }
        return ret;
    }

    updateProfile = async () => {
        let profile = await nip05.queryProfile("jb55.com");
        console.log("PROFILE: " + S.util.prettyPrint(profile));

        profile = await nip05.queryProfile("jb55@jb55.com");
        console.log("PROFILE: " + S.util.prettyPrint(profile));
    }

    readPostsFromFriends = async (): Promise<void> => {
        const res = await S.rpcUtil.rpc<J.GetPeopleRequest, J.GetPeopleResponse>("getPeople", {
            nodeId: null,
            type: "friends",
            subType: "nostr"
        });

        if (!res.people) return;
        const userNames: string[] = [];
        const relaysSet: Set<String> = new Set<String>();

        // scan all people to build list of users and relays to read from
        for (const person of res.people) {
            if (!S.nostr.isNostrUserName(person.userName)) continue;
            userNames.push(person.userName.substring(1));
            const personRelays = this.getRelays(person.relays);
            if (personRelays) {
                personRelays.forEach(r => relaysSet.add(r));
            }
        }

        const relaysArray: string[] = [];
        relaysSet.forEach((r: any) => relaysArray.push(r));

        if (userNames.length > 0 && relaysArray.length > 0) {
            console.log("Reading " + userNames.length + " users from " + relaysArray.length + " relays.");
        }
        await this.readPosts(userNames, relaysArray, -1);
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
}
