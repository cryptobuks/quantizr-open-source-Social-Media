import { IndexedDBObj } from "./Interfaces";
import { S } from "./Singletons";

// We need to prefix the store name and not the individual keys.

/* Wraps a transaction of the CRUD operations for access to JavaScript local storage IndexedDB API */
export class LocalDB {
    debug: boolean = true;
    db: IDBDatabase = null; // only used if KEEP_DB_OPEN

    /* Name of logged in user or 'null' if anonymous (user not logged in) */
    userName: string;
    storeName = "store";
    allStoreNames: Set<string> = new Set<string>();
    dbVersion: number = 0;
    static DB_NAME = "db";

    /* WARNING: boosting the version will WIPE OUT the old database, and create a brand new one */
    // static VERSION = 2;

    static ACCESS_READWRITE: IDBTransactionMode = "readwrite";
    static ACCESS_READONLY: IDBTransactionMode = "readonly";
    static KEY_NAME = "k";

    private openDB = (bumpVersion: boolean = false): Promise<IDBDatabase> => {
        if (!indexedDB) {
            throw new Error("IndexedDB API not available in browser.");
        }

        return new Promise<IDBDatabase>((resolve, reject) => {
            if (this.debug) {
                console.log("opening IndexedDB")
            }
            let req: IDBOpenDBRequest = null;
            if (bumpVersion) {
                req = indexedDB.open(LocalDB.DB_NAME, this.dbVersion + 1);
            }
            else {
                req = indexedDB.open(LocalDB.DB_NAME);
            }

            req.onupgradeneeded = () => {
                this.storeName = this.makeStoreName();
                if (this.debug) {
                    console.log("IndexedDb: onupgradeneeded: Creating store " + this.storeName);
                }

                try {
                    if (!this.allStoreNames.has(this.storeName)) {
                        console.log("createObjectStore: " + this.storeName);
                        req.result.createObjectStore(this.storeName, { keyPath: LocalDB.KEY_NAME });
                    }
                }
                catch (e) {
                    // ignoring. we get this if store alrady exists.
                }
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

    /* Runs a transaction by first opening the database, and then running the transaction */
    private runTrans = async (access: IDBTransactionMode, runner: (store: IDBObjectStore) => void) => {
        // if keeping db open and we have it open, then use it.
        if (!this.db) {
            this.db = await this.openDB();
        }

        if (this.debug) {
            console.log("runTrans on store: " + this.storeName);
        }

        const tx = this.db.transaction(this.storeName, access);
        const store = tx.objectStore(this.storeName);

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
    public getVal = async (k: string): Promise<any> => {
        const obj: IndexedDBObj = await this.readObject(k);
        const ret = obj?.v;
        if (this.debug) {
            console.log("Queried for k=" + k + " and found " + S.util.prettyPrint(ret));
        }
        return ret;
    }

    // stores the value under this key  (like a simple map/keystore)
    public setVal = async (k: string, v: any) => {
        await this.writeObject({ k, v });
        if (this.debug) {
            console.log("Saved for k=" + k + " val " + v);
        }
    }

    public writeObject = async (obj: IndexedDBObj): Promise<void> => {
        if (!obj.k) {
            console.error("key property 'k' is missing from object: " + S.util.prettyPrint(obj));
            return;
        }
        return new Promise<void>(async (resolve, reject) => {
            this.runTrans(LocalDB.ACCESS_READWRITE,
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
    // todo-0: make this method private. We can alway call thru 'getVal()' as the public method
    public readObject = async (k: string): Promise<IndexedDBObj> => {
        return new Promise<IndexedDBObj>((resolve, reject) => {
            this.runTrans(LocalDB.ACCESS_READONLY,
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

    // Setting user makes is use a store named like "store-${userName}". This is not very straightforward
    // however becasue we have to detect if this is a NEW store name, and if so bump up the DB version which
    // is required to get IndexedDb to run the upgrade method so we can create the new store.
    public setUser = async (userName: string): Promise<void> => {
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
        const newStore = !this.allStoreNames.has(this.makeStoreName());

        // bumping up DB with a 'true' arg here is required in order to generate a new store
        await this.openDB(newStore);

        // todo-0: remove this line, soon.
        await S.localDB.dumpStore();
    }

    makeStoreName = () => {
        return this.userName ? ("store" + "-" + this.userName) : "store";
    }

    public dumpStore = async (): Promise<void> => {
        return new Promise<void>((resolve, reject) => {
            this.runTrans(LocalDB.ACCESS_READONLY,
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
