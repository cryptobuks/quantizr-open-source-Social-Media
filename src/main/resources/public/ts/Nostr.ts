import {
    Event,
    Kind,
    Relay,
    generatePrivateKey,
    getEventHash,
    getPublicKey,
    nip05,
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
*/
export class Nostr {
    TEST_RELAY_URL: string = "wss://nostr-pub.wellorder.net"; // "wss://relay.damus.io/";
    TEST_USER_KEY: string = "35d26e4690cbe1a898af61cc3515661eb5fa763b57bd0b42e45099c8b32fd50f";

    sk: string = null; // secret key, hex string
    pk: string = null; // public key, hex string

    // This can be run from Admin Console
    test = async () => {
        await this.initKeys();
        const res = await this.readPosts(this.TEST_USER_KEY, this.TEST_RELAY_URL, 1680899831);

        // await this.updateProfile();
        // const res = await this.readUserMetadata(this.TEST_USER_KEY, this.TEST_RELAY_URL);
        console.log("SaveCount: " + res.saveCount);

        // this.saveEvent();

        // this.createEvent();

        // Object from original examples:
        // await this.getEvent(this.TEST_RELAY_URL, "d7dd5eb3ab747e16f8d0212d53032ea2a7cadef53837e5a6c66d42849fcb9027");

        // Object posted from Quanta
        // await this.getEvent(this.TEST_RELAY_URL, "fec9091d99d8aa4dc1d544563cecb587fea5c3ccb744ca668c8b4021daced097");
        // await this.publishEvent();
    }

    // Logs keys to JS console
    printKeys = () => {
        console.log("Nostr Keys:\n  Priv:" + this.sk + "\n  PubKey:" + this.pk);
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
        this.printKeys();
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

        const ok = validateEvent(event);
        console.log("Event Status: ok=" + ok);
        const veryOk = verifySignature(event);
        console.log("Sig Status: ok=" + veryOk);

        console.log("NEW EVENT: " + S.util.prettyPrint(event));
        return event;
    }

    /* Opens a relay, retrieves a single event from the relay, and then shuts down the relay.
    todo-1: call 'reject' if any error happens.
    */
    getEvent = async (rurl: string, id: string): Promise<void> => {
        return new Promise<void>(async (resolve, reject) => {
            const relay = await this.openRelay(rurl);

            // subscribe to the relay to pull up just the one ID we're interested in.
            const sub = relay.sub([
                {
                    ids: [id]
                }
            ]);

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
        if (!rurl.startsWith("wss://")) {
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

    readUserMetadata = async (userKey: string, relayUrl: string): Promise<J.SaveNostrEventResponse> => {
        const relay = await this.openRelay(relayUrl);
        const events = await relay.list([{
            authors: [userKey],
            kinds: [Kind.Metadata],
            limit: 1
        }]);
        return await this.persistEvents(events, relayUrl);
    }

    // Possible Filter Params
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
    // todo-0: need to allow relayUrl to be multiple newline delimited URLs
    //
    readPosts = async (userKey: string, relayUrl: string, since: number): Promise<J.SaveNostrEventResponse> => {
        console.log("readPosts for userKey: " + userKey);
        const relay = await this.openRelay(relayUrl);
        const query: any = {
            authors: [userKey],
            kinds: [Kind.Text],
            limit: 25
        };
        if (since !== -1) {
            query.since = since;
        }
        const events = await relay.list([query]);
        return await this.persistEvents(events, relayUrl);
    }

    persistEvents = async (events: Event[], relays: string): Promise<J.SaveNostrEventResponse> => {
        if (!events || events.length === 0) return;

        let idx = 0;
        events.forEach(event => {
            console.log("PERSIST EVENT[" + (idx++) + "]: " + S.util.prettyPrint(event));
        });

        // Push the events up to the server for storage
        return await S.rpcUtil.rpc<J.SaveNostrEventRequest, J.SaveNostrEventResponse>("saveNostrEvent", {
            events: this.makeEventsList(events),
            relays
        });
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
        const id = S.props.getPropStr(J.NodeProp.ACT_PUB_ID, node);
        return id?.startsWith(".");
    }
}
