import { IndexedDBObj } from "./Interfaces";
import { S } from "./Singletons";
import * as J from "./JavaIntf";

// We need to prefix the store name and not the individual keys.

/* Wraps a transaction of the CRUD operations for access to JavaScript local storage IndexedDB API */
export class LocalDB {
    debug: boolean = false;
    db: IDBDatabase = null;

    STORE_NOSTR_MD = "nostr-md";
    STORE_NOSTR_TXT = "nostr-txt";
    STORE_NOSTR_PERSIST = "nostr-persist";
    STORE_DEFAULT = "store";

    /* Name of logged in user or 'null' if anonymous (user not logged in) */
    userName: string = J.PrincipalName.ANON;
    allStoreNames: Set<string> = new Set<string>();
    dbVersion: number = 1;

    static ACCESS_READWRITE: IDBTransactionMode = "readwrite";
    static ACCESS_READONLY: IDBTransactionMode = "readonly";
    static KEY_NAME = "k";

    private openDB = (bumpVersion: boolean = false): Promise<IDBDatabase> => {
        if (!indexedDB) {
            throw new Error("IndexedDB API not available in browser.");
        }

        return new Promise<IDBDatabase>((resolve, reject) => {
            if (this.debug) {
                console.log("opening IndexedDB: userName=" + this.userName);
            }
            let req: IDBOpenDBRequest = null;
            if (bumpVersion) {
                req = indexedDB.open(this.userName, this.dbVersion + 1);
            }
            else {
                req = indexedDB.open(this.userName);
            }

            req.onupgradeneeded = () => {
                this.createStore(req.result, this.STORE_NOSTR_MD);
                this.createStore(req.result, this.STORE_NOSTR_TXT);
                this.createStore(req.result, this.STORE_NOSTR_PERSIST);
                this.createStore(req.result, this.STORE_DEFAULT);
            };

            req.onsuccess = () => {
                if (this.debug) {
                    this.dbVersion = req.result.version;

                    // load allStoreNames
                    const namesArray = Array.from(req.result.objectStoreNames);
                    if (namesArray) {
                        namesArray.forEach(a => this.allStoreNames.add(a));
                    }

                    console.log("local DB (ver=" + this.dbVersion + " opened ok: Stores=" + S.util.prettyPrint(req.result.objectStoreNames));
                }

                // this is a one level recursion, just as a design choice. This isn't inherently a recursive operation.
                if (req.result) {
                    resolve(req.result);
                }
            };

            req.onerror = () => {
                console.warn("indexedDB.open failed");
                reject(null);
            }
        });
    }

    clearStores = (): Promise<void[]> => {
        return Promise.all([
            this.clearStore(this.STORE_NOSTR_MD),
            this.clearStore(this.STORE_NOSTR_TXT),
            this.clearStore(this.STORE_NOSTR_PERSIST),
            this.clearStore(this.STORE_DEFAULT)
        ]);
    }

    clearStore = (storeName: string): Promise<void> => {
        // don't await, just return promise
        return new Promise<void>(async (resolve, reject) => {
            this.runTrans(LocalDB.ACCESS_READWRITE, storeName,
                (store: IDBObjectStore) => {
                    if (this.debug) {
                        console.log("clearing store: " + storeName);
                    }
                    const req = store.clear();
                    req.onsuccess = () => {
                        console.log("store " + storeName + " clear Ok");
                        resolve();
                    };
                    req.onerror = () => {
                        console.log("store " + storeName + "clear Failed");
                        resolve();
                    };
                });
        });
    }

    createStore = (db: IDBDatabase, storeName: string) => {
        // this try/catch is important to ignore times the store already exists, like upgrading the DB.
        try {
            db.createObjectStore(storeName, { keyPath: LocalDB.KEY_NAME });
        }
        catch (e) {
            // ignoring. we get this if store alrady exists.
        }
    }

    /* Runs a transaction by first opening the database, and then running the transaction */
    private runTrans = async (access: IDBTransactionMode, storeName: string, runner: (store: IDBObjectStore) => void) => {
        // if keeping db open and we have it open, then use it.
        if (!this.db) {
            this.db = await this.openDB();
        }

        if (this.debug) {
            console.log("runTrans on store: " + storeName);
        }

        const tx = this.db.transaction(storeName, access);
        const store = tx.objectStore(storeName);

        if (store) {
            runner(store);
        }
        else {
            console.error("Failed to open indexDb store");
        }

        tx.oncomplete = () => {
        };

        tx.onabort = () => {
            console.log("tx fail");
        }

        tx.onerror = () => {
            console.log("tx err");
        }
    }

    // gets the value stored under the key (like a simple map/keystore)
    public getVal = async (k: string, storeName: string = null): Promise<any> => {
        if (!storeName) storeName = this.STORE_DEFAULT;
        const obj: IndexedDBObj = await this.readObject(k, storeName);
        const ret = obj?.v;
        if (this.debug) {
            console.log("Queried for user: " + this.userName + " k=" + k + " and found " + S.util.prettyPrint(ret));
        }
        return ret;
    }

    // stores the value under this key  (like a simple map/keystore)
    public setVal = async (k: string, v: any, storeName: string = null) => {
        if (!storeName) storeName = this.STORE_DEFAULT;
        await this.writeObject({ k, v }, storeName);
        if (this.debug) {
            console.log("Saved for user: " + this.userName + " k=" + k + " val=" + v);
        }
    }

    public writeObject = async (obj: IndexedDBObj, storeName: string = null): Promise<void> => {
        if (!storeName) storeName = this.STORE_DEFAULT;
        if (!obj.k) {
            console.error("key property 'k' is missing from object: " + S.util.prettyPrint(obj));
            return;
        }
        return new Promise<void>(async (resolve, reject) => {
            this.runTrans(LocalDB.ACCESS_READWRITE, storeName,
                (store: IDBObjectStore) => {
                    if (this.debug) {
                        console.log("writeObj: " + S.util.prettyPrint(obj));
                    }
                    const req = store.put(obj);
                    req.onsuccess = () => {
                        resolve();
                    };
                    req.onerror = () => {
                        resolve();
                    };
                });
        });
    }

    /* Looks up the object and returns that object which will have the 'name' as a propety in it
    just like it did when stored under that 'name' as the key */
    public readObject = async (k: string, storeName: string = null): Promise<IndexedDBObj> => {
        return new Promise<IndexedDBObj>((resolve, reject) => {
            if (!storeName) storeName = this.STORE_DEFAULT;

            this.runTrans(LocalDB.ACCESS_READONLY, storeName,
                (store: IDBObjectStore) => {
                    // NOTE: name is the "keyPath" value.
                        const req = store.get(k);
                        req.onsuccess = () => {
                            resolve(req.result);
                        };
                        req.onerror = () => {
                            console.warn("readObject failed: k=" + k);
                            resolve(null);
                        };
                });
        });
    }

    // Our DB is determined by the user, so if we set a newUser that will trigger a new DB to be opened
    public setUser = async (userName: string) => {
        // closes last DB and sets the userName so any future DB calls will reopen with new user.
        if (this.userName === userName) return;

        if (this.db) {
            if (this.debug) {
                console.log("closing db. New User going into effect.");
            }
            this.db.close();
            this.db = null;
        }
        this.userName = userName;

        // we need a pubsub mechanism here so this nostr logic is decoupled
        S.quanta.invalidateKeys()
        await this.openDB();
        await S.quanta.initKeys(userName);
    }

    public dumpStore = async (storeName: string): Promise<void> => {
        return new Promise<void>((resolve, reject) => {
            this.runTrans(LocalDB.ACCESS_READONLY, storeName,
                (store: IDBObjectStore) => {
                    const req = store.getAll();
                    req.onsuccess = () => {
                        console.log("DumpStore (user=" + this.userName + "): " + S.util.prettyPrint(req.result));
                        resolve();
                    };
                    req.onerror = () => {
                        console.warn("readObject failed: name=" + name);
                        resolve();
                    };
                });
        });
    }
}
