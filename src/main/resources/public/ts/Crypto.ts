import { IndexedDBObj } from "./Interfaces";
import * as J from "./JavaIntf";
import { S } from "./Singletons";

/*
SYMMETRIC ENCRYPTION and PUBLIC KEY ENCRYPTION
---------------------
We will be using LocalDB.ts implementation to store the keys in the browser, but we will also support
allowing the user to cut-n-paste they Key JSON, so that if something goes wrong with the
browser storage the user will not loose their keys because they will be able
to reimport the JSON key text back in at any time, or install the keys in a different browser.

At no point in time does the users' Private Key ever leave their own browser storage.

TIP: (Not currenty used)
Original way I had for creating a hash-based key from a password:

    let hashPromise = this.crypto.subtle.digest({ name: "SHA-256" }, this.convertStringToByteArray(password));
    hashPromise.then((hash: any) => {
    let keyPromise = this.crypto.subtle.importKey("raw", hash, { name: "AES-CBC" }, false, ["encrypt", "decrypt"]);
*/

export class Crypto {
    readonly avail: boolean = !!(crypto?.subtle);
    warningShown: boolean = false;

    // cache the keys here for faster access.
    privateEncKey: CryptoKey = null;
    publicEncKey: CryptoKey = null;
    privateSigKey: CryptoKey = null;
    publicSigKey: CryptoKey = null;

    static FORMAT_PEM: string = "pem";

    // asymetric ENCRYPTION keys (public/private)
    STORE_ASYMKEY = "asymkey";

    // symmetric ENCRYPTION key
    STORE_SYMKEY = "symkey";

    // signature sign/verify keys (public/private)
    STORE_SIGKEY = "sigkey";

    // 'Public Key' AES Encryption algo.
    ASYM_ALGO = "RSA-OAEP";

    SIG_ALGO = "RSASSA-PKCS1-v1_5";

    // todo-1: need to vet these parameters
    SIG_ALGO_OBJ: any = {
        name: this.SIG_ALGO,
        modulusLength: 2048,
        publicExponent: new Uint8Array([1, 0, 1]),
        hash: { name: "SHA-256" }
    };

    // Symmetric Algo. We use GCM mode of AES because it detects data corruptions during decryption
    SYM_ALGO = "AES-GCM";

    HASH_ALGO = "SHA-256";

    ASYM_IMPORT_ALGO = {
        name: "RSA-OAEP",
        hash: "SHA-256"
    };

    OP_SIGN_VERIFY: KeyUsage[] = ["sign", "verify"];
    OP_SIGN: KeyUsage[] = ["sign"];
    OP_VERIFY: KeyUsage[] = ["verify"];

    OP_ENC_DEC: KeyUsage[] = ["encrypt", "decrypt"];
    OP_ENC: KeyUsage[] = ["encrypt"];
    OP_DEC: KeyUsage[] = ["decrypt"];

    vector: Uint8Array = null;
    logKeys: boolean = false;

    sigKey: string = null;
    asymEncKey: string = null;
    userSignature: string = null;

    constructor() {
        /* WARNING: Crypto (or at least subtle) will not be available except on Secure Origin, which means a SSL (https)
        web address plus also localhost */

        if (!this.avail) {
            console.log("Crypto API not available");
            return;
        }

        /*
        Note: This vector is merely required
        to be large enough and random enough, but is not required to be secret. 16 randomly chosen prime numbers.
        WARNING: If you change this you will NEVER be able to recover any data encrypted with it in effect, even with the correct password. So
        beware if you change this you've basically lost ALL your passwords. So just don't change it.

        todo-2: According to some crypto experts, this initialization vector should not be reused like this but instead stored
        along with the encryption key.
        */
        // iv = window.crypto.getRandomValues(new Uint8Array(16)); <--- I saw this in a reputable example. Try it out!
        this.vector = new Uint8Array([71, 73, 79, 83, 89, 37, 41, 47, 53, 67, 97, 103, 107, 109, 127, 131]);
    }

    invalidateKeys = () => {
        this.sigKey = null;
        this.userSignature = null;

        this.privateEncKey = null;
        this.publicEncKey = null;
        this.privateSigKey = null;
        this.publicSigKey = null;
    }

    /* Runs a full test of all encryption code.

       Assumes that Encryption.initKeys() has previously been called, which is
       safe to assume because we run it during app initialization.
    */
    encryptionTest = async (): Promise<string> => {
        this.runConversionTest();
        await this.runPublicKeyTest();
        await this.symetricEncryptionTest();
        await this.secureMessagingTest();
        console.log("All Encryption Tests: OK");
        return "";
    }

    signatureTest = async (): Promise<string> => {
        const myData = "Data to be Signed";
        const signature = await this.sign(null, myData);
        console.log("Signature: " + signature);
        const verified = await this.verify(null, signature, myData);
        console.log("signatureTest Encryption Tests: Verified=" + verified);
        return "";
    }

    /* Returns hex string representing the signature data */
    sign = async (privateKey: CryptoKey, data: string): Promise<string> => {
        if (!this.avail) return null;
        if (!privateKey) {
            privateKey = await this.getPrivateSigKey();
        }

        if (!privateKey) return null;

        const sigBuf: ArrayBuffer = await crypto.subtle.sign(this.SIG_ALGO,
            privateKey,
            new TextEncoder().encode(data));

        return S.util.buf2hex(new Uint8Array(sigBuf));
    }

    verify = async (publicKey: CryptoKey, sigBuf: string, data: string): Promise<boolean> => {
        if (!this.avail) return null;
        publicKey = publicKey || await this.getPublicSigKey();

        return await crypto.subtle.verify(this.SIG_ALGO,
            publicKey,
            S.util.hex2buf(sigBuf),
            new TextEncoder().encode(data));
    }

    secureMessagingTest = async () => {
        console.log("running secureMessagingTest...");
        const clearText = "This is cleartext";
        const skdp: SymKeyDataPackage = await this.encryptSharableString(null, clearText);
        const checkText = await this.decryptSharableString(null, skdp);
        S.util.assert(checkText === clearText, "verifying cleartext");
        console.log("secureMessagingTest: OK");
    }

    symetricEncryptionTest = async (): Promise<boolean> => {
        const clearText = "Encrypt this string.";

        // test symetric encryption
        const obj: IndexedDBObj = await S.localDB.readObject(this.STORE_SYMKEY);
        if (obj) {
            // simple encrypt/decrypt
            const key: CryptoKey = obj.v;
            const encHex = await this.symEncryptString(key, clearText);
            const unencText = await this.symDecryptString(key, encHex);
            S.util.assert(clearText === unencText, "Symmetric decrypt");

            // test symetric key export/import
            const keyDat: JsonWebKey = await crypto.subtle.exportKey("jwk", key) as JsonWebKey;

            const key2: CryptoKey = await crypto.subtle.importKey("jwk", keyDat, this.SYM_ALGO /* as AlgorithmIdentifier */, true, this.OP_ENC_DEC as KeyUsage[]);

            const encHex2 = await this.symEncryptString(key2, clearText);
            const unencText2 = await this.symDecryptString(key2, encHex2);
            S.util.assert(clearText === unencText2, "Symetric decrypt, using imported key");
            console.log("sym enc test: OK");
            return true;
        }
        return false;
    }

    runPublicKeyTest = async (): Promise<boolean> => {
        const clearText = "Encrypt this string.";
        let ret: boolean = false;

        // test public key encryption
        const obj: IndexedDBObj = await S.localDB.readObject(this.STORE_ASYMKEY);
        if (obj) {
            // results += "STORE_ASYMKEY: \n"+S.util.prettyPrint(obj)+"\n\n";

            // simple encrypt/decrypt
            const encHex = await this.asymEncryptString(obj.v.publicKey, clearText);
            const unencText = await this.asymDecryptString(obj.v.privateKey, encHex);
            S.util.assert(clearText === unencText, "Asym encryption");

            // Export keys to a string format
            const publicKeyStr = await crypto.subtle.exportKey("jwk", obj.v.publicKey);
            // console.log("EXPORTED PUBLIC KEY: " + S.util.toJson(publicKeyStr) + "\n");
            const privateKeyStr = await crypto.subtle.exportKey("jwk", obj.v.privateKey);
            // console.log("EXPORTED PRIVATE KEY: " + S.util.toJson(publicKeyStr) + "\n");

            const publicKey = await crypto.subtle.importKey("jwk", publicKeyStr, {
                name: this.ASYM_ALGO,
                hash: this.HASH_ALGO
            }, true, this.OP_ENC);

            const privateKey = await crypto.subtle.importKey("jwk", privateKeyStr, {
                name: this.ASYM_ALGO,
                hash: this.HASH_ALGO
            }, true, this.OP_DEC);

            const encHex2 = await this.asymEncryptString(publicKey, clearText);
            const unencText2 = await this.asymDecryptString(privateKey, encHex2);
            S.util.assert(clearText === unencText2, "Asym encrypt test using imported keys.");

            console.log("publicKeyTest: OK");
            ret = true;
        }
        return ret;
    }

    runConversionTest = () => {
        // First test conversion of clear-text string to hex texct, and back.
        const clearText = "Encrypt this string.";
        const clearTextBytes: Uint8Array = this.convertStringToByteArray(clearText);
        const hexOfClearText: string = S.util.buf2hex(clearTextBytes);
        const verifyClearTextBytes: Uint8Array = S.util.hex2buf(hexOfClearText);
        const verifyClearText: string = this.convertByteArrayToString(verifyClearTextBytes);
        S.util.assert(clearText === verifyClearText, "encryption encodings");
        console.log("runConversionTest OK.");
    }

    importKey = async (key: JsonWebKey, algos: any, extractable: boolean, keyUsages: KeyUsage[]): Promise<CryptoKey> => {
        if (!this.avail) return null;
        return crypto.subtle.importKey("jwk", key, algos, extractable, keyUsages);
    }

    importSigKeyPair = async (keyJson: string): Promise<boolean> => {
        return this.importKeyPair(keyJson, this.STORE_SIGKEY, this.SIG_ALGO_OBJ,
            this.OP_VERIFY as KeyUsage[], this.OP_SIGN as KeyUsage[]);
    }

    importAsymKeyPair = async (keyJson: string): Promise<boolean> => {
        return this.importKeyPair(keyJson, this.STORE_ASYMKEY, this.ASYM_IMPORT_ALGO,
            this.OP_ENC as KeyUsage[], this.OP_DEC as KeyUsage[]);
    }

    importKeyPair = async (keyPair: string, keyName: string, algoObj: any,
        publicOps: KeyUsage[], privateOps: KeyUsage[]): Promise<boolean> => {
        if (!this.avail) return false;
        const keyPairObj: EncryptionKeyPair = JSON.parse(keyPair);

        const publicKey = await crypto.subtle.importKey("jwk", keyPairObj.publicKey, algoObj, true, publicOps);
        const privateKey = await crypto.subtle.importKey("jwk", keyPairObj.privateKey, algoObj, true, privateOps);

        if (publicKey && privateKey) {
            const newKeyPair: EncryptionKeyPair = new EncryptionKeyPair(publicKey, privateKey);
            await S.localDB.writeObject({ k: keyName, v: newKeyPair });
        }
        return true;
    }

    // todo-1: need to make this require the password and username to be more secure.
    initKeys = async (user: string, forceUpdate: boolean, republish: boolean, showConfirm: boolean, keyType: string) => {
        console.log("Crypto.initKeys");
        if (user === J.PrincipalName.ANON) {
            console.log("not using crypto: user=" + user);
            return;
        }

        if (!crypto || !crypto.subtle) {
            console.error("Crypto Not Available.");
            return;
        }

        let newAsymEncKey = null;
        let newSigKey = null;
        let newUserSignature = null;

        if (keyType === "all" || keyType === "asym") {
            newAsymEncKey = await this.initAsymetricKeys(forceUpdate);
        }

        if (keyType === "all" || keyType === "sym") {
            await this.initSymetricKey(forceUpdate);
        }

        if (keyType === "all" || keyType === "sig") {
            newSigKey = await this.initSigKeys(forceUpdate);
            newUserSignature = await S.crypto.sign(null, user);

            S.crypto.sigKey = newSigKey;
            S.crypto.userSignature = newUserSignature;
            // console.log("newSigKey: user=" + user + " key=" + newSigKey);
        }

        if (republish && (newAsymEncKey || newSigKey)) {
            // console.log("Pushing PublicKeys");
            const res = await S.rpcUtil.rpc<J.SavePublicKeyRequest, J.SavePublicKeyResponse>("savePublicKeys", {
                // todo-1: I'm not sure I want to keep these as escaped JSON or convert to hex
                asymEncKey: newAsymEncKey,
                sigKey: newSigKey,
                nostrNpub: S.nostr.npub,
                nostrPubKey: S.nostr.pk
            });

            if (res.success) {
                // note, even though we only update these if successful on the server the client side will still definitely
                // have the new keys in the LocalDB already
                if (newAsymEncKey) {
                    S.crypto.asymEncKey = newAsymEncKey;
                }

                if (newSigKey) {
                    S.crypto.sigKey = newSigKey;
                    S.crypto.userSignature = newUserSignature;
                }

                if (showConfirm) {
                    S.util.showMessage("Successfully published Public Keys");
                }
            }
            else {
                S.util.showMessage("Failed saving keys to the server." + res.message, "Keys");
            }
        }
        else {
            if (newAsymEncKey) {
                S.crypto.asymEncKey = newAsymEncKey;
            }

            if (newSigKey) {
                S.crypto.sigKey = newSigKey;
                S.crypto.userSignature = newUserSignature;
            }
        }
    }

    getPrivateEncKey = async (): Promise<CryptoKey> => {
        if (this.privateEncKey) return this.privateEncKey;
        this.privateEncKey = await this.getPrivateKey(S.crypto.STORE_ASYMKEY);
        return this.privateEncKey;
    }

    getPublicEncKey = async (): Promise<CryptoKey> => {
        if (this.publicEncKey) return this.publicEncKey;
        this.publicEncKey = await this.getPublicKey(S.crypto.STORE_ASYMKEY);
        return this.publicEncKey;
    }

    getPrivateSigKey = async (): Promise<CryptoKey> => {
        if (this.privateSigKey) return this.privateSigKey;
        this.privateSigKey = await this.getPrivateKey(S.crypto.STORE_SIGKEY);
        return this.privateSigKey;
    }

    getPublicSigKey = async (): Promise<CryptoKey> => {
        if (this.publicSigKey) return this.publicSigKey;
        this.publicSigKey = await this.getPublicKey(S.crypto.STORE_SIGKEY);
        return this.publicSigKey;
    }

    getPrivateKey = async (storeName: string): Promise<CryptoKey> => {
        const val: IndexedDBObj = await S.localDB.readObject(storeName);
        if (!val || !val.v) {
            console.error("Unable to get private key.");
            return null;
        }
        else {
            // console.log("getPrivateKey returning: " + S.util.prettyPrint(val.val.privateKey));
            return val.v.privateKey;
        }
    }

    getPublicKey = async (storeName: string): Promise<CryptoKey> => {
        const val: IndexedDBObj = await S.localDB.readObject(storeName);
        if (!val || !val.v) {
            console.error("Unable to get public key.");
            return null;
        }
        else {
            // console.log("getPublicKey returning: " + S.util.prettyPrint(val.val.publicKey));
            return val.v.publicKey;
        }
    }

    initSymetricKey = async (forceUpdate: boolean = false) => {
        if (!this.avail) {
            return;
        }

        const val: IndexedDBObj = await S.localDB.readObject(this.STORE_SYMKEY);
        if (!val) {
            forceUpdate = true;
        }

        if (val && !forceUpdate) {
            if (this.logKeys) {
                const cryptoKey: CryptoKey = val.v;
                await crypto.subtle.exportKey("jwk", cryptoKey);
                // let symKeyStr = await crypto.subtle.exportKey(this.DEFAULT_KEY_FORMAT, cryptoKey);
                // console.log("symkey: " + S.util.toJson(symKeyStr));
            }
        }
        else {
            const key: CryptoKey = await this.genSymKey();
            await S.localDB.writeObject({ k: this.STORE_SYMKEY, v: key });
        }
    }

    /*
    Initialize keys for sign/verify.
    Note: a 'forceUpdate' always triggers the 'republish'
    */
    initSigKeys = async (forceUpdate: boolean = false): Promise<string> => {
        if (!this.avail) {
            console.log("crypto not available.");
            return null;
        }
        let keyPair: CryptoKeyPair = null;
        let pubKeyStr: string = null;

        if (!forceUpdate) {
            // console.log("force update key");
            /* Check to see if there is a key stored, and if not force it to be created
               val.val is the EncryptionKeyPair here.
            */
            const val: IndexedDBObj = await S.localDB.readObject(this.STORE_SIGKEY);
            if (!val) {
                // console.log("Forcing sig key update: key not found.");
                forceUpdate = true;
            }
            else {
                // console.log("key found ok for user " + S.localDB.userName);
                keyPair = val.v;
            }
        }

        if (forceUpdate || !keyPair) {
            keyPair = await crypto.subtle.generateKey(this.SIG_ALGO_OBJ, true, this.OP_SIGN_VERIFY);

            await S.localDB.writeObject({ k: this.STORE_SIGKEY, v: keyPair });

            const pubKeyDat = await crypto.subtle.exportKey("jwk", keyPair.publicKey);
            pubKeyStr = JSON.stringify(pubKeyDat);
            console.log("Exporting key string jwk: " + pubKeyStr);
        }

        if (!keyPair) {
            const val: IndexedDBObj = await S.localDB.readObject(this.STORE_SIGKEY);
            keyPair = val.v;
            // console.log("tried to get sigkey");
        }

        if (!pubKeyStr) {
            const publicKeyDat = await crypto.subtle.exportKey("jwk", keyPair.publicKey);
            pubKeyStr = JSON.stringify(publicKeyDat);
            // console.log("jwk: " + pubKeyStr);
        }

        return pubKeyStr;
    }

    /*
    Init keys for encryption.
    Note: a 'forceUpdate' always triggers the 'republish'
    */
    initAsymetricKeys = async (forceUpdate: boolean = false): Promise<string> => {
        if (!this.avail) {
            console.log("crypto not available.");
            return null;
        }
        let keyPair: CryptoKeyPair = null;
        let pubKeyStr: string = null;

        if (!forceUpdate) {
            /* Check to see if there is a key stored, and if not force it to be created
               val.val is the EncryptionKeyPair here.
            */
            const val: IndexedDBObj = await S.localDB.readObject(this.STORE_ASYMKEY);
            if (!val) {
                forceUpdate = true;
            }
            else {
                keyPair = val.v;
            }
        }

        if (forceUpdate || !keyPair) {
            keyPair = await crypto.subtle.generateKey({ //
                name: this.ASYM_ALGO, //
                modulusLength: 2048, //
                publicExponent: new Uint8Array([0x01, 0x00, 0x01]), //
                hash: { name: this.HASH_ALGO } //
            }, true, this.OP_ENC_DEC);

            await S.localDB.writeObject({ k: this.STORE_ASYMKEY, v: keyPair });

            const pubKeyDat = await crypto.subtle.exportKey("jwk", keyPair.publicKey);
            pubKeyStr = JSON.stringify(pubKeyDat);
            // console.log("Exporting key string: " + pubKeyStr);
        }

        if (!keyPair) {
            const val: IndexedDBObj = await S.localDB.readObject(this.STORE_ASYMKEY);
            keyPair = val.v;
        }

        if (!pubKeyStr) {
            const publicKeyDat = await crypto.subtle.exportKey("jwk", keyPair.publicKey);
            pubKeyStr = JSON.stringify(publicKeyDat);
        }

        return pubKeyStr;
    }

    genSymKey = async (): Promise<CryptoKey> => {
        if (!this.avail) return null;
        const key: CryptoKey = await window.crypto.subtle.generateKey({
            name: this.SYM_ALGO,
            length: 256
        }, true, this.OP_ENC_DEC);
        return key;
    }

    exportAsymKeys = async (): Promise<string> => {
        if (!this.avail) return null;
        let ret = "";
        const obj: IndexedDBObj = await S.localDB.readObject(this.STORE_ASYMKEY);
        if (obj) {
            const keyPair: EncryptionKeyPair = obj.v;

            const pubDat = await crypto.subtle.exportKey("jwk", keyPair.publicKey);
            // this.importKey(this.OP_ENCRYPT, "public", this.publicKeyJson);

            const privDat = await crypto.subtle.exportKey("jwk", keyPair.privateKey);
            // this.importKey(this.OP_DECRYPT, "private", this.privateKeyJson);

            ret = S.util.prettyPrint({ publicKey: pubDat, privateKey: privDat });

            // yes we export to spki for PEM (not a bug)
            // const privDatSpki = await crypto.subtle.exportKey("spki", keyPair.publicKey);
            // const pem = this.spkiToPEM(privDatSpki);
            // ret += "Public Key (PEM Format):\n" + pem + "\n\n";
        }
        return ret;
    }

    exportSymKey = async (): Promise<string> => {
        if (!this.avail) return null;
        let ret = "";
        const obj: IndexedDBObj = await S.localDB.readObject(this.STORE_SYMKEY);
        if (obj) {
            const key: CryptoKey = obj.v;
            const dat = await crypto.subtle.exportKey("jwk", key);
            ret = S.util.prettyPrint(dat);
            // todo-3: no PEM export for Symmetric key?
        }
        return ret;
    }

    /**
     * Returns a string the user can save locally containing all encryption keys stored  in the browser.
     *
     * Export is in JWK format: https://tools.ietf.org/html/rfc7517
     */
    exportSigKeys = async (): Promise<string> => {
        if (!this.avail) return null;
        let ret = "";

        const obj: IndexedDBObj = await S.localDB.readObject(this.STORE_SIGKEY);
        if (obj) {
            const keyPair: EncryptionKeyPair = obj.v;

            const pubDat = await crypto.subtle.exportKey("jwk", keyPair.publicKey);
            // this.importKey(this.OP_ENCRYPT, "public", this.publicKeyJson);

            const privDat = await crypto.subtle.exportKey("jwk", keyPair.privateKey);
            // this.importKey(this.OP_DECRYPT, "private", this.privateKeyJson);

            ret = S.util.prettyPrint({ publicKey: pubDat, privateKey: privDat });

            // yes we export to spki for PEM (not a bug)
            // const privDatSpki = await crypto.subtle.exportKey("spki", keyPair.publicKey);
            // const pem = this.spkiToPEM(privDatSpki);
            // ret += "Public Key (PEM Format):\n" + pem + "\n\n";
        }
        return ret;
    }

    spkiToPEM(keydata: any): any {
        const keydataS = this.arrayBufferToString(keydata);
        const keydataB64 = window.btoa(keydataS);
        const keydataB64Pem = this.formatAsPem(keydataB64);
        return keydataB64Pem;
    }

    arrayBufferToString(buffer: any): any {
        let binary = "";
        const bytes = new Uint8Array(buffer);
        for (let i = 0; i < bytes.byteLength; i++) {
            binary += String.fromCharCode(bytes[i]);
        }
        return binary;
    }

    formatAsPem(str: any): any {
        let finalString = "-----BEGIN PUBLIC KEY-----\n";

        while (str.length > 0) {
            finalString += str.substring(0, 64) + "\n";
            str = str.substring(64);
        }

        finalString = finalString + "-----END PUBLIC KEY-----";
        return finalString;
    }

    asymEncryptString = async (key: CryptoKey, data: string): Promise<string> => {
        return this.encryptString(key, this.ASYM_ALGO, data);
    }

    /**
     * Does a simplel symmetric encryption of the data using the given key, and if the key
     * is not provided assumes the STORE_SYMKEY
     */
    symEncryptString = async (key: CryptoKey, data: string): Promise<string> => {
        if (!key) {
            const obj: IndexedDBObj = await S.localDB.readObject(this.STORE_SYMKEY);
            if (obj) {
                key = obj.v;
            }
        }
        return this.encryptString(key, this.SYM_ALGO, data);
    }

    symEncryptStringWithCipherKey = async (cipherKey: string, data: string): Promise<string> => {
        const privateKey = await S.crypto.getPrivateEncKey();
        const symKeyJsonStr: string = await S.crypto.asymDecryptString(privateKey, cipherKey);
        const symKeyJsonObj: JsonWebKey = JSON.parse(symKeyJsonStr);
        const symKey = await S.crypto.importKey(symKeyJsonObj, S.crypto.SYM_ALGO, true, S.crypto.OP_ENC_DEC);
        return await S.crypto.symEncryptString(symKey, data);
    }

    /**
     * This is the primary way of encrypting data that uses a randomly generated symmetric key to
     * do the encryption and then encrypts that symmetric key itself using the Public Key provided, or
     * public key of current user.
     *
     * This is a very standard approach in the crypto world, and it allows the owner of the associated
     * keypair (i.e. private key) to be able to share the data securely with arbitrary other users by simply publishing
     * this symmetric key (to the actual data) to individuals by encrypting said symmetric key with that
     * user's public key.
     *
     * Of course, this means the process is that when a user wants to read data shared to them they just use
     * their private key to decrypt the symmetric key to the data, and use that key to get the data.
     *
     * This function returns an object that contains two properties: ciphertext, cipherkey, which is the encrypted data
     * and the encrypted "JWK" formatted key to the data, respectively
     *
     * 'publicKey' argument should be the public key of the person doing the encryption (the person doing the encryption)
     * and if null, it's automatically retrieved from the localDB
     */
    encryptSharableString = async (publicKey: CryptoKey, data: string): Promise<SymKeyDataPackage> => {
        publicKey = publicKey || await this.getPublicEncKey();

        // generate random symmetric key
        const key: CryptoKey = await this.genSymKey();
        // console.log("Cleartext Sym Key: " + S.util.prettyPrint(key));

        // get JWK formatted key
        const jwk = await crypto.subtle.exportKey("jwk", key);

        // get JSON string of jwk
        const jwkJson = S.util.prettyPrint(jwk);

        // const pubKeyJson = await crypto.subtle.exportKey("jwk", publicKey);
        // console.log("Initial KEY encrypted with owner publicKey: " + S.util.prettyPrint(pubKeyJson));

        // encrypt the symetric key
        const cipherKey = await this.asymEncryptString(publicKey, jwkJson);

        // encrypt the data with the symetric key
        const cipherText = await this.symEncryptString(key, data);

        const ret: SymKeyDataPackage = { cipherText, cipherKey };
        return ret;
    }

    /* Inverse of encryptSharableString() function */
    decryptSharableString = async (privateKey: CryptoKey, skpd: SymKeyDataPackage): Promise<string> => {
        // get hash of the encrypted data
        const cipherHash: string = S.util.hashOfString(skpd.cipherText);

        let ret = S.quanta.decryptCache.get(cipherHash);
        // if we have already decrypted this data return the result.
        if (ret) {
            // console.log("decryption cache hit!");
            return ret;
        }

        try {
            // console.log("decrypting [" + skpd.cipherText + "] with cipherKey: " + skpd.cipherKey);
            privateKey = privateKey || await this.getPrivateEncKey();

            if (!privateKey) {
                console.log("unable to get privateKey");
                return null;
            }

            // const privKeyJson = await crypto.subtle.exportKey("jwk", privateKey);
            // console.log("calling asymDecryptString to get key: userPrivateKey=" + S.util.prettyPrint(privKeyJson));

            // Decrypt the symmetric key using our private key
            const symKeyJsonStr: string = await this.asymDecryptString(privateKey, skpd.cipherKey);

            const symKeyJsonObj: JsonWebKey = JSON.parse(symKeyJsonStr);
            const symKey = await crypto.subtle.importKey("jwk", symKeyJsonObj, this.SYM_ALGO, true, this.OP_ENC_DEC);
            ret = await this.symDecryptString(symKey, skpd.cipherText);
            // console.log("            output: [" + ret + "]");
            S.quanta.decryptCache.set(cipherHash, ret);
            return ret;
        }
        catch (ex) {
            // todo-2: this was happening when 'importKey' failed for admin user, but I think admin user may not store keys? Need to just
            // retest encryption
            S.util.logErr(ex, "decryptSharableString failed");
            return null;
        }
    }

    /* Encrypts 'data' string and returns a hex representation of the ciphertext */
    encryptString = async (key: CryptoKey, algo: string, data: string): Promise<string> => {
        const result: ArrayBuffer = await crypto.subtle.encrypt({ name: algo, iv: this.vector }, //
            key, this.convertStringToByteArray(data));

        const encData = new Uint8Array(result);
        const encHex: string = S.util.buf2hex(encData);
        return encHex;
    }

    asymDecryptString = async (key: CryptoKey, encHex: string): Promise<string> => {
        return this.decryptString(key, this.ASYM_ALGO, encHex);
    }

    symDecryptString = async (key: CryptoKey, encHex: string): Promise<string> => {
        if (!key) {
            const obj: IndexedDBObj = await S.localDB.readObject(this.STORE_SYMKEY);
            if (obj) {
                key = obj.v;
            }
        }
        return this.decryptString(key, this.SYM_ALGO, encHex);
    }

    /* Takes the input as a hex string, and decrypts it into the original non-hex string */
    decryptString = async (key: CryptoKey, algo: string, encHex: string): Promise<string> => {
        try {
            const encArray: Uint8Array = S.util.hex2buf(encHex);
            const result: ArrayBuffer = await crypto.subtle.decrypt({ name: algo, iv: this.vector }, //
                key, encArray);
            const resArray = new Uint8Array(result);
            const resStr: string = this.convertByteArrayToString(resArray);
            return resStr;
        }
        catch (ex) {
            S.util.logErr(ex, "decrypt FAILED.");
            throw ex;
        }
    }

    // NOTE: TextEncoder() and TextDecoder() don't support this yet, so we have these two
    // functions.
    // This can work?? in browser?
    // const messageData = new TextEncoder().encode(message);
    convertStringToByteArray = (str: string): Uint8Array => {
        const bytes = new Uint8Array(str.length);
        for (let i = 0; i < str.length; i++) {
            bytes[i] = str.charCodeAt(i);
        }
        return bytes;
    }

    convertByteArrayToString = (buffer: Uint8Array): string => {
        let str = "";
        for (let i = 0; i < buffer.byteLength; i++) {
            str += String.fromCharCode(buffer[i]);
        }
        return str;
    }

    /* This method will simply sign all the strings in 'dataToSign' and then send it up to the server when done */
    generateAndSendSigs = async (dataToSign: J.NodeSigPushInfo): Promise<void> => {

        if (!this.sigKeyOk()) {
            return null;
        }

        for (const dat of dataToSign.listToSign) {
            // convert all the data to be signed into the signatures
            dat.data = await S.crypto.sign(null, dat.data);
        }

        // then we can send this same object right back up with all signatures.
        await S.rpcUtil.rpc<J.SignNodesRequest, J.SignNodesResponse>("signNodes", dataToSign);
        // console.log("Sent Signatures: "+S.util.prettyPrint(dataToSign));
        return null;
    }

    signNode = async (node: J.NodeInfo): Promise<void> => {
        if (!this.sigKeyOk()) {
            return null;
        }

        if (!this.avail) {
            throw new Error("Crypto not available.");
        }

        let path: string = node.path;
        // convert any 'pending (p)' path to a final verion of the path (no '/p/')
        if (path.startsWith("/r/p/")) {
            path = "/r/" + path.substring(5);
        }

        // see: #signature-format
        let signData: string = path + "-" + node.ownerId;
        if (node.content) {
            signData += "-" + node.content;
        }

        if (node.attachments) {
            S.props.getOrderedAtts(node).forEach(att => {
                if (att.b) {
                    signData += "-" + att.b;
                }
                if (att.d) {
                    signData += "-" + att.d;
                }
            });
        }

        // we need to concat the path+content
        try {
            const sig: string = await S.crypto.sign(null, signData);
            // console.log("signData: nodeId=" + node.id + " data[" + signData + "] sig: " + sig);
            // const verified = await S.crypto.verify(null, sig, signData);
            // console.log("local verify: " + verified);
            S.props.setPropVal(J.NodeProp.CRYPTO_SIG, node, sig);
        } catch (e) {
            S.util.logErr(e, "Failed to sign data.");
            throw e;
        }
        return null;
    }

    cryptoWarning = () => {
        if (!this.warningShown) {
            this.warningShown = true;
            S.util.showMessage("Crypto not available in browser.", "Crypto");
        }
    }

    // returns true of key is ok to use
    sigKeyOk = () => {
        if (!this.avail) {
            this.cryptoWarning();
            return false;
        }
        return true;
    }

    encKeyOk = () => {
        if (!this.avail) {
            this.cryptoWarning();
            return false;
        }
        return true;
    }

    showEncryptionKeyProblem = (keyName: string, featureName: string) => {
        // run in async timout to be sure not to interfere with any react state flow (recursive dispatching)
        setTimeout(() => {
            S.util.showMessage("Your " + keyName + " doesn't match the public key that the server has for you: \n" +
                "\nYou need to fix this before you can use " + featureName + " features.\n\n" +
                "Three ways to fix: \n" +
                "* Use the original browser where your keys got initialized from.\n" +
                "* or, Go to `Account -> Manage Keys` and publish the key(s) from this browser.\n" +
                "* or, Import the key(s) from your original browser into this browser."
                , "Unknown Security Key", true);
        }, 100);
    }

    // ab2str = (buf: ArrayBuffer) => {
    //     return String.fromCharCode.apply(null, new Uint16Array(buf));
    // }

    // str2ab = (str) => {
    //     var buf = new ArrayBuffer(str.length * 2); // 2 bytes for each char
    //     var bufView = new Uint16Array(buf);
    //     for (var i = 0, strLen = str.length; i < strLen; i++) {
    //         bufView[i] = str.charCodeAt(i);
    //     }
    //     return buf;
    // }
}

export interface SymKeyDataPackage {
    cipherText: string;
    cipherKey: string;
    symKey?: CryptoKey;
}

export class EncryptionKeyPair {
    constructor(public publicKey: any, public privateKey: any) {
    }
}
