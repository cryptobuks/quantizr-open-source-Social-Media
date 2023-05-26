import express from 'express';
import { SimplePool } from 'nostr-tools';
import 'websocket-polyfill';

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

// todo-0: add api key check
// work in progress.
app.post('/nostr-verify', async (req: any, res: any, next: any) => {
    console.log("nostr-verify: body=" + JSON.stringify(req.body, null, 4));
    return res.send(req.body);
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
        return res.send(ret);
    }
    catch (error) {
        return next(error);
    }
});

const port = process.env.TSERVER_PORT || 4003
app.listen(port, () => {
    console.log('server running on port ' + port);
});
