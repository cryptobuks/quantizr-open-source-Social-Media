import {
    Event,
    Kind,
    Relay,
    generatePrivateKey,
    getEventHash,
    getPublicKey,
    nip05,
    nip19,
    SimplePool,
    parseReferences,
    relayInit,
    signEvent,
    validateEvent, verifySignature
} from "nostr-tools";
import { Constants as C } from "./Constants";
import * as J from "./JavaIntf";
import { S } from "./Singletons";

/* This class holds our initial experimentation with Nostr, and the only GUI for this is a single
link on the Admin Console that can run the "test()" method

References:
https://github.com/nostr-protocol/nips/blob/master/01.md
https://github.com/nbd-wtf/nostr-tools
*/
export class Nostr {
    // TEST_RELAY_URL: string = "wss://nostr-pub.wellorder.net"; // "wss://relay.damus.io/";
    // TEST_USER_KEY: string = "35d26e4690cbe1a898af61cc3515661eb5fa763b57bd0b42e45099c8b32fd50f";

    TEST_RELAY_URL: string = "wss://nos.lol\nwss://relay.damus.io";;
    TEST_USER_KEY: string = "32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245";

    sk: string = null; // secret key, hex string
    pk: string = null; // public key, hex string
    npub: string = null; // npub (of public key)

    // This can be run from Admin Console
    test = async () => {
        await this.initKeys();

        // this.testNpub();
        await this.readPosts(this.TEST_USER_KEY, this.TEST_RELAY_URL, 1680899831);

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

    testNpub = () => {
        const npub = nip19.decode("npub1xtscya34g58tk0z605fvr788k263gsu6cy9x0mhnm87echrgufzsevkk5s"); // relay: wss://nos.lol
        console.log("npub as hex: " + S.util.prettyPrint(npub));
    }

    // Logs keys to JS console
    printKeys = () => {
        console.log("Nostr Keys:");
        console.log("    Priv: " + this.sk);
        console.log("    PubKey: " + this.pk);
        console.log("    npub: " + this.npub);
    }

    // Initializes our keys
    initKeys = async () => {
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
        if (this.pk !== this.translateUserKey(this.npub)) {
            console.error("Problem with npub key");
        }

        this.printKeys();
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
        // console.log("NEW EVENT: " + S.util.prettyPrint(event));
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

    /* Opens a relay, retrieves a single event from the relay, and then shuts down the relay.
    todo-1: call 'reject' if any error happens.
    */
    getEvent = async (rurl: string, id: string): Promise<void> => {
        return new Promise<void>(async (resolve, reject) => {
            const relay = await this.openRelay(rurl);

            // subscribe to the relay to pull up just the one ID we're interested in.
            const sub = relay.sub([{ ids: [id] }]);

            console.log("Waiting for some events...");
            sub.on("event", event => {
                console.log("event received: ", S.util.prettyPrint(event));
            });

            sub.on("eose", () => {
                console.log("eose event. Closing relay.");
                sub.unsub();
                relay.close();
                resolve();
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
            console.log(`connected to ${relay.url}`)
        })
        relay.on("error", () => {
            console.log(`failed to connect to ${relay.url}`)
        })

        console.log("Opening Relay: " + rurl);
        await relay.connect();
        return relay;
    }

    // todo-1: handle reject case.
    // Creates a test event and publishes it to our test relay. We can then call 'getEvent()' to verify
    // that we can read it back from the relay.
    publishEvent = async (): Promise<void> => {
        return new Promise<void>(async (resolve, reject) => {
            const event = this.createEvent();
            const relay = await this.openRelay(this.TEST_RELAY_URL);

            const pub = relay.publish(event);
            pub.on("ok", () => {
                console.log(`${relay.url} has accepted our event`);
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

    translateUserKey = (user: string) => {
        if (user.startsWith("npub")) {
            const npub = nip19.decode(user);
            if (npub.type === "npub") {
                user = npub.data as string;
            }
            else {
                console.log("Unhandled npub type: " + S.util.prettyPrint(npub));
                return null;
            }
        }
        return user;
    }

    // user can be the hex, npub, or NIP05 address of the identity.
    readUserMetadata = async (user: string, relayUrl: string, isNip05: boolean): Promise<J.SaveNostrEventResponse> => {
        let relays = this.getRelays(relayUrl);
        if (isNip05) {
            const profile = await nip05.queryProfile(user);
            if (!profile) return null;
            // console.log("NIP05: " + S.util.prettyPrint(profile));
            user = profile.pubkey;

            if (profile.relays) {
                relays = relays.concat(profile.relays);
            }
        }

        if (relays.length === 0) {
            console.warn("No relays. Can't lookup user: " + user);
            return null;
        }

        user = this.translateUserKey(user);

        const query: any = {
            authors: [user],
            kinds: [Kind.Metadata],
            limit: 1
        };

        const events = await this.queryRelays(relays, query);
        return await this.persistEvents(events, relayUrl);
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
    readPosts = async (userKey: string, relayUrl: string, since: number): Promise<J.SaveNostrEventResponse> => {
        // console.log("readPosts for userKey: " + userKey);
        const relays: string[] = this.getRelays(relayUrl);
        userKey = this.translateUserKey(userKey);

        const query: any = {
            authors: [userKey],
            kinds: [Kind.Text],
            limit: 25
        };
        if (since !== -1) {
            query.since = since;
        }

        const events = await this.queryRelays(relays, query);
        return await this.persistEvents(events, relayUrl);
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
            relays = relays.map(r => r ? r.trim() : null).filter(r => !!r);
        }
        return relays;
    }

    persistEvents = async (events: Event[], relays: string): Promise<J.SaveNostrEventResponse> => {
        if (!events || events.length === 0) return;

        let idx = 0;
        events.forEach(event => {
            console.log("PERSIST EVENT[" + (idx++) + "]: " + S.util.prettyPrint(event));
            if (!this.checkEvent(event)) {
                console.log("eventCheck Failed.");
            }

            this.dumpEventRefs(event);
        });

        // Push the events up to the server for storage
        return await S.rpcUtil.rpc<J.SaveNostrEventRequest, J.SaveNostrEventResponse>("saveNostrEvent", {
            events: this.makeEventsList(events),
            relays
        });
    }

    /* References are basically 'mentions', but can point to other things besides people too I think. But
    we're not supporting this yet.
    */
    dumpEventRefs = (event: Event): void => {
        const references = parseReferences(event);
        console.log("REFS=" + S.util.prettyPrint(references));

        // DO NOT DELETE
        // let simpleAugmentedContent = event.content
        // for (let i = 0; i < references.length; i++) {
        // let { text, profile, event, address } = references[i];
        // let augmentedReference = profile
        //     ? `<strong>@${profilesCache[profile.pubkey].name}</strong>`
        //     : event
        //         ? `<em>${eventsCache[event.id].content.slice(0, 5)}</em>`
        //         : address
        //             ? `<a href="${text}">[link]</a>`
        //             : text
        // simpleAugmentedContent.replaceAll(text, augmentedReference)
        // }
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
                timestamp: event.created_at
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

    getFriends = async (): Promise<void> => {
        const res = await S.rpcUtil.rpc<J.GetPeopleRequest, J.GetPeopleResponse>("getPeople", {
            nodeId: null,
            type: "friends",
            subType: "nostr"
        });

        if (!res.people) return;
        for (const person of res.people) {
            debugger;
            let userName = person.userName;
            if (!userName.startsWith(".")) return;
            userName = userName.substring(1);

            // todo-0: for now, run each person individually. We will eventually optimize into efficient batch
            // queries to do this all it once, as optimally as possible and transmit up to server efficiently
            await this.readPosts(userName, person.relays, -1);
        }
    }

    isNostrNode = (node: J.NodeInfo) => {
        const id = S.props.getPropStr(J.NodeProp.OBJECT_ID, node);
        return id?.startsWith(".");
    }

    private async singleRelayQuery(relayUrl: string, query: any) {
        const relay = await this.openRelay(relayUrl);
        return await relay.list([query]);
    }

    private async multiRelayQuery(relays: string[], query: any) {
        const pool = new SimplePool()

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

        return await pool.list(relays, [query]);
    }
}
