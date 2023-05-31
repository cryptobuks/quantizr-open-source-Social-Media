import express from 'express';
import { Event, SimplePool, validateEvent, verifySignature } from 'nostr-tools';
import 'websocket-polyfill';

// warning: for now, these two interfaces are duplicates and identical copies of what's compiled
// into the webpack bundle also.

interface NostrEvent {
    id: string;
    sig: string;
    pubkey: string;
    kind: number;
    content: string;
    tags: string[][];
    createdAt: number;
}

interface NostrEventWrapper {
    event: NostrEvent;
    nodeId: string;
    npub: string;
    relays: string;
}

console.log("Express Server starting: TSERVER_API_KEY=" + process.env.TSERVER_API_KEY);

// NOTE: I was originally doing this instead of the polyfill, and it was working, btw.
// import { WebSocket } from "ws";
// Object.assign(global, { WebSocket });

const app = express();
app.use(express.urlencoded({ extended: true }));
app.use(express.json());

app.get('/', (req: any, res: any, next: any) => {
    res.send("Quanta TServer ok!");
});

const makeNostrEvent = (event: NostrEvent): Event => {
    return {
        id: event.id,
        sig: event.sig,
        pubkey: event.pubkey,
        kind: event.kind,
        content: event.content,
        tags: event.tags,
        created_at: event.createdAt
    };
}

const makeJavaNostrEvent = (event: Event): NostrEvent => {
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

app.post('/nostr-verify', async (req: any, res: any, next: any) => {
    // console.log("nostr-verify: req body=" + JSON.stringify(req.body, null, 4));
    const events: NostrEventWrapper[] = req.body.events;
    const ids: string[] = [];
    for (const event of events) {
        const evt = makeNostrEvent(event.event);
        if (!validateEvent(evt) || !verifySignature(evt)) {
            ids.push(event.nodeId);
        }
        else {
            // console.log("Verified: event " + event.event.id);
        }
    }
    // console.log("nostr-verify: response ids=" + JSON.stringify(ids, null, 4));
    return res.send(ids);
});

app.post('/nostr-query', async (req: any, res: any, next: any) => {
    try {
        if (req.body.apiKey !== process.env.TSERVER_API_KEY) {
            return res.send({ status: "Bad API KEY" });
        }

        const pool = new SimplePool();
        let ret = await pool.list(req.body.relays, [req.body.query]);
        if (!ret || ret.length == 0) {
            ret = [];
        }
        pool.close(req.body.relays);
        return res.send(ret.map(m => makeJavaNostrEvent(m)));
    }
    catch (error) {
        return next(error);
    }
});

const port = process.env.TSERVER_PORT || 4003
app.listen(port, () => {
    console.log('server running on port ' + port);
});
