import express from 'express';
import { SimplePool, validateEvent, verifySignature } from 'nostr-tools';
import { nostr, utils } from "quanta-common";
import 'websocket-polyfill';

console.log("Express Server starting: TSERVER_API_KEY=" + process.env.TSERVER_API_KEY);

// NOTE: I was originally doing this instead of the polyfill, and it was working, btw.
// import { WebSocket } from "ws";
// Object.assign(global, { WebSocket });

const app = express();
app.use(express.urlencoded({ extended: true }));
app.use(express.json());

app.get('/', (req: any, res: any, next: any) => {
    // todo-1: eventually we can remove this call we do to verify package is working.
    res.send("Quanta TServer ok! " + utils.testCall("server NPM Package!"));
});

app.post('/nostr-verify', async (req: any, res: any, next: any) => {
    // console.log("nostr-verify: req body=" + JSON.stringify(req.body, null, 4));
    const events: nostr.NostrEventWrapper[] = req.body.events;
    const ids: string[] = [];
    for (const event of events) {
        const evt = nostr.makeEvent(event.event);
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
        return res.send(ret.map(m => nostr.makeNostrEvent(m)));
    }
    catch (error) {
        return next(error);
    }
});

const port = process.env.TSERVER_PORT || 4003
app.listen(port, () => {
    console.log('server running on port ' + port);
});
