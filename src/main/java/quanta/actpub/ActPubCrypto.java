package quanta.actpub;

import static quanta.actpub.model.AP.apObj;
import static quanta.actpub.model.AP.apStr;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.actpub.model.APOActor;
import quanta.actpub.model.APObj;
import quanta.config.ServiceBase;
import quanta.model.client.NodeProp;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.service.UserManagerService;
import quanta.util.XString;
import quanta.util.val.Val;

/**
 * Crypto functions for AP
 */
@Component
@Slf4j 
public class ActPubCrypto extends ServiceBase {
    public static final String SIGNATURE_ALGO = "SHA256withRSA";
    public static final String DIGEST_ALGO = "SHA-256";

    /* Gets private RSA key from current user session */
    public String getPrivateKey(MongoSession ms, String userName) {
        /* First try to return the key from the cache */
        String privateKey = UserManagerService.privateKeysByUserName.get(userName);
        if (privateKey != null) {
            return privateKey;
        }

        /* get the userNode for the current user who edited a node */
        SubNode userNode = read.getUserNodeByUserName(ms, userName);
        if (userNode == null) {
            return null;
        }

        /* get private key of this user so we can sign the outbound message */
        privateKey = userNode.getStr(NodeProp.CRYPTO_KEY_PRIVATE);
        if (privateKey == null) {
            log.debug("Unable to update federated users. User didn't have a private key on his userNode: " + userName);
            return null;
        }

        // add to cache.
        UserManagerService.privateKeysByUserName.put(userName, privateKey);
        return privateKey;
    }

    public void parseHttpHeaderSig(HttpServletRequest httpReq, Val<String> keyId, Val<String> signature, boolean requireDigest,
            Val<List<String>> headers) {
        String reqSig = httpReq.getHeader("Signature");
        if (reqSig == null) {
            throw new RuntimeException("Signature missing from http header.");
        }

        final List<String> sigToks = XString.tokenize(reqSig, ",", true);
        if (sigToks == null || sigToks.size() < 3) {
            throw new RuntimeException("Signature tokens missing from http header.");
        }

        for (String sigTok : sigToks) {
            int equalIdx = sigTok.indexOf("=");

            // ignore tokens not containing equals
            if (equalIdx == -1)
                continue;

            String key = sigTok.substring(0, equalIdx);
            String val = sigTok.substring(equalIdx + 1);

            if (val.charAt(0) == '"') {
                val = val.substring(1, val.length() - 1);
            }

            if (key.equalsIgnoreCase("keyId")) {
                keyId.setVal(val);
            } else if (key.equalsIgnoreCase("headers")) {
                headers.setVal(Arrays.asList(val.split(" ")));
            } else if (key.equalsIgnoreCase("signature")) {
                signature.setVal(val);
            }
        }

        if (keyId.getVal() == null)
            throw new RuntimeException("Header signature missing 'keyId'");
        if (headers == null)
            throw new RuntimeException("Header signature missing 'headers'");
        if (signature == null)
            throw new RuntimeException("Header signature missing 'signature'");
        if (!headers.getVal().contains("(request-target)"))
            throw new RuntimeException("(request-target) is not in signed headers");
        if (!headers.getVal().contains("date"))
            throw new RuntimeException("date is not in signed headers");
        if (!headers.getVal().contains("host"))
            throw new RuntimeException("host is not in signed headers");
        if (requireDigest && !headers.getVal().contains("digest"))
            throw new RuntimeException("digest is not in signed headers");
    }

    // todo-2: need a version of this method that wraps the logic of going and getting the publickey
    // off the original server and updating it into our local db if necessary, and then trying THAT
    // key before finally failing.
    public void verifySignature(HttpServletRequest httpReq, PublicKey pubKey, byte[] bodyBytes) {
        if (pubKey == null) {
            throw new RuntimeException("no pubKey");
        }
        Val<String> keyId = new Val<>();
        Val<String> signature = new Val<>();
        Val<List<String>> headers = new Val<>();

        parseHttpHeaderSig(httpReq, keyId, signature, bodyBytes != null, headers);

        // todo-2: currently not validating time
        // String date = httpReq.getHeader("date");
        // apUtil.validateRequestTime(date);

        /*
         * NOTE: keyId will be the actor url with "#main-key" appended to it, and if we wanted to verify
         * that only incomming messages from users we 'know' are allowed, we could do that, but for now we
         * simply verify that they are who they claim to be using the signature check below, and that is all
         * we want. (i.e. unknown users can post in)
         */
        byte[] signableBytes = getHeaderSignatureBytes(httpReq, headers.getVal(), bodyBytes);
        byte[] sigBytes = Base64.getDecoder().decode(signature.getVal());

        try {
            Signature verifier = Signature.getInstance(SIGNATURE_ALGO);
            verifier.initVerify(pubKey);
            verifier.update(signableBytes);
            if (!verifier.verify(sigBytes)) {
                throw new RuntimeException("Signature verify failed.");
            }
            // log.debug("Signature ok. bodyBytes=" + (ok(bodyBytes) ? String.valueOf(bodyBytes.length) :
            // "none"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getEncodedPubKeyFromActorObj(APOActor actorObj) {
        Object pubKey = apObj(actorObj, APObj.publicKey);
        if (pubKey == null)
            return null;

        String pubKeyPem = apStr(pubKey, APObj.publicKeyPem);
        if (pubKeyPem == null)
            return null;

        // WARNING: This is a REGEX. replaceAll() uses REGEX., we extract out just the data part of the key
        return pubKeyPem.replaceAll("-----(BEGIN|END) (RSA )?PUBLIC KEY-----", "").replace("\n", "").trim();
    }

    public PublicKey getPubKeyFromActorUrl(String userDoingAction, String actorUrl, Val<String> keyVal) {
        return (PublicKey) arun.run(as -> {
            PublicKey pkey = null;

            // get account node for this actorUrl
            SubNode accntNode = apub.getAcctNodeByActorUrl(as, userDoingAction, actorUrl);

            // if we have account node
            if (accntNode != null) {
                // get pkey property off account
                String pkEncoded = accntNode.getStr(NodeProp.ACT_PUB_KEYPEM);

                // if the key was there decode it.
                if (pkEncoded != null) {
                    // if the output param wants the encoded key set it
                    if (keyVal != null) {
                        keyVal.setVal(pkEncoded);
                    }
                    pkey = getPublicKeyFromEncoding(pkEncoded);
                    // log.debug("Got PK by Node: " + accntNode.getIdStr());
                }
            }

            // #todo-optimization: this block will eventually be unneeded once all accounts have pkey in them.
            if (pkey == null) {
                log.debug("NOTE: actorUrl " + actorUrl + " doesn't have pkey in account node yet");

                // Get ActorObject from actor url.
                APOActor actorObj = apUtil.getActorByUrl(as, userDoingAction, actorUrl);
                if (actorObj == null) {
                    log.warn("Unable to load actorUrl: " + actorUrl);
                    return null;
                }

                String pkeyEncoded = getEncodedPubKeyFromActorObj(actorObj);

                if (keyVal != null) {
                    keyVal.setVal(pkeyEncoded);
                }

                pkey = getPublicKeyFromEncoding(pkeyEncoded);
            }
            return pkey;
        });
    }

    public PublicKey getPublicKeyFromActor(APOActor actor) {
        String pkeyEncoded = getEncodedPubKeyFromActorObj(actor);
        return getPublicKeyFromEncoding(pkeyEncoded);
    }

    public PublicKey getPublicKeyFromEncoding(String pkeyEncoded) {
        if (pkeyEncoded == null)
            return null;

        PublicKey pubKey = null;
        byte[] key = Base64.getDecoder().decode(pkeyEncoded);
        try {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(key);
            pubKey = KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception ex) {
            log.debug("Failed to generate publicKey from encoded: " + pkeyEncoded);
        }

        return pubKey;
    }

    // bodyBytes can be null and they simply won't be checked.
    private byte[] getHeaderSignatureBytes(HttpServletRequest httpReq, List<String> headers, byte[] bodyBytes) {
        ArrayList<String> sigParts = new ArrayList<>();

        for (String header : headers) {
            String value;

            // request-target
            if (header.equals("(request-target)")) {
                value = httpReq.getMethod().toLowerCase() + " " + httpReq.getRequestURI();
            }
            // digest
            else if (header.equals("digest")) {
                value = httpReq.getHeader(header);

                /*
                 * if we have body bytes and they don't hash to be what the header claims they should be then that's
                 * a fail
                 */
                if (bodyBytes != null) {
                    if (!digestFromBodyBytes(bodyBytes).equals(value)) {
                        throw new RuntimeException("body digest compare fail.");
                    }
                }
            }
            // all other headers
            else {
                value = httpReq.getHeader(header);
            }
            sigParts.add(header + ": " + value);
        }

        byte[] signableBytes = String.join("\n", sigParts).getBytes(StandardCharsets.UTF_8);
        return signableBytes;
    }

    /*
     * Returns true only if the account node identified by ownerId (i.e. accountNode.id==ownerId) has
     * the matching ActivityPub key on it.
     */
    public boolean ownerHasKey(MongoSession ms, SubNode node, String key) {
        acl.failIfAdminOwned(node);

        SubNode accntNode = read.getNode(ms, node.getOwner());
        if (accntNode != null) {
            return key.equals(accntNode.getStr(NodeProp.ACT_PUB_KEYPEM));
        }
        return false;
    }

    public void loadSignatureHeaderVals(HttpHeaders headers, String privKeyStr, String urlStr, String actor, byte[] bodyBytes,
            String method) {
        try {
            // try to get the key from the cache first
            PrivateKey privKey = apCache.privateKeys.get(privKeyStr);

            // if key not found in cache, generate it, and cache it.
            if (privKey == null) {
                byte[] privKeyBytes = Base64.getDecoder().decode(privKeyStr);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                PKCS8EncodedKeySpec keySpecPKCS8 = new PKCS8EncodedKeySpec(privKeyBytes);
                privKey = kf.generatePrivate(keySpecPKCS8);

                // put the key in the cache now!
                apCache.privateKeys.put(privKeyStr, privKey);
            }

            // WARNING: dateFormat is NOT threasafe. Always create one here.
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            String date = dateFormat.format(new Date());

            // This is basically calculating the HASH of the bodyBytes
            String digestHeader = digestFromBodyBytes(bodyBytes);

            URL url = new URL(urlStr);
            String strToSign = "(request-target): " + method + " " + url.getPath() + //
                    "\nhost: " + url.getHost() + //
                    "\ndate: " + date;

            if (digestHeader != null) {
                strToSign += "\ndigest: " + digestHeader;
            }

            Signature sig = Signature.getInstance(SIGNATURE_ALGO);
            sig.initSign(privKey);
            sig.update(strToSign.getBytes(StandardCharsets.UTF_8));
            byte[] signature = sig.sign();

            /*
             * note: leroma is including content-length in this headers list but we don't. I should probably
             * add it but be sure not to break compatability when doing so.
             */
            String headerSig = headerPair("keyId", actor + "#main-key") + "," + //
                    headerPair("headers", "(request-target) host date" + (digestHeader != null ? " digest" : "")) + "," + //
                    headerPair("algorithm", "rsa-sha256") + "," + //
                    headerPair("signature", Base64.getEncoder().encodeToString(signature));

            headers.add("Host", url.getHost());
            headers.add("Date", date);
            headers.add("Signature", headerSig);

            if (digestHeader != null) {
                headers.add("Digest", digestHeader);
            }
        } catch (Exception e) {
            log.error("loadSignatureHeaderVals failed", e);
            throw new RuntimeException(e);
        }
    }

    // it's NOT an error condition when bodyBytes is null, it just means no body exists.
    private String digestFromBodyBytes(byte[] bytes) {
        try {
            return bytes != null
                    ? DIGEST_ALGO + "=" + Base64.getEncoder().encodeToString(MessageDigest.getInstance(DIGEST_ALGO).digest(bytes))
                    : null;
        } catch (Exception e) {
            throw new RuntimeException("failed making digest.");
        }
    }

    private String headerPair(String key, String val) {
        return key + "=\"" + val + "\"";
    }

    /* Returns true if signaure check is successful */
    public boolean verifySignature(HttpServletRequest httpReq, Object payload, String actorUrl, byte[] bodyBytes,
            Val<String> keyEncoded) {
        PublicKey pubKey = apCrypto.getPubKeyFromActorUrl(null, actorUrl, keyEncoded);
        if (pubKey == null) {
            return false;
        }

        try {
            verifySignature(httpReq, pubKey, bodyBytes);
        } catch (Exception e) {
            log.error("Sig failed.");
            return false;
        }

        return true;
    }
}
