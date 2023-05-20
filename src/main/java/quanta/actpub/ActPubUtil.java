package quanta.actpub;

import static quanta.actpub.model.AP.apHasProps;
import static quanta.actpub.model.AP.apList;
import static quanta.actpub.model.AP.apObj;
import static quanta.actpub.model.AP.apStr;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import quanta.actpub.model.AP;
import quanta.actpub.model.APOActor;
import quanta.actpub.model.APOWebFinger;
import quanta.actpub.model.APObj;
import quanta.actpub.model.APType;
import quanta.config.NodeName;
import quanta.config.NodePath;
import quanta.config.ServiceBase;
import quanta.instrument.PerfMon;
import quanta.model.NodeInfo;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.model.client.PrincipalName;
import quanta.model.client.PrivilegeType;
import quanta.mongo.MongoDeleteEvent;
import quanta.mongo.MongoRepository;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.response.GetThreadViewResponse;
import quanta.util.Convert;
import quanta.util.ThreadLocals;
import quanta.util.Util;
import quanta.util.XString;
import quanta.util.val.Val;

/**
 * AP-related utilities
 */
@Component
@Slf4j
public class ActPubUtil extends ServiceBase {
    @Autowired
    private ActPubLog apLog;

    private static final int MAX_THREAD_NODES = 200;

    /*
     * RestTemplate is thread-safe and reusable, and has no state, so we need only one final static
     * instance ever
     */
    private static final RestTemplate restTemplate = new RestTemplate(Util.getClientHttpRequestFactory(10000));
    public static final ObjectMapper mapper = new ObjectMapper();

    // NOTE: This didn't allow unknown properties as expected but putting the
    // following in the JSON classes did:
    // @JsonIgnoreProperties(ignoreUnknown = true)
    {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public APObj buildObj(byte[] bytes) {
        try {
            APObj payload = ActPubUtil.mapper.readValue(bytes, APObj.class);
            return AP.typeFromFactory(payload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * input: clay@server.com
     * 
     * output: server.com
     */
    public String getHostFromUserName(String userName) {
        int atIdx = userName.indexOf("@");
        if (atIdx == -1)
            return null;
        return userName.substring(atIdx + 1);
    }

    /*
     * input: clay@server.com
     * 
     * output: clay
     * 
     * todo-1: make this still work even if input is (@clay@server.com) and also make sure this fix
     * won't simultaneously break something else.
     */
    public String stripHostFromUserName(String userName) {
        int atIdx = userName.indexOf("@");
        if (atIdx == -1)
            return userName;
        return userName.substring(0, atIdx);
    }

    /* Builds an actor url for a LOCAL userName */
    public String makeActorUrlForUserName(String userName) {
        return prop.getProtocolHostAndPort() + APConst.ACTOR_PATH + "/" + userName;
    }

    /*
     * Builds the unique set of hosts from a list of userNames (not used currently)
     * 
     * Looks like this isn't being used.
     */
    public HashSet<String> getHostsFromUserNames(List<String> userNames) {
        String host = prop.getMetaHost();
        HashSet<String> hosts = new HashSet<>();

        for (String toUserName : userNames) {
            // Ignore userNames that are not foreign server names
            if (!toUserName.contains("@")) {
                continue;
            }

            // Ignore userNames that are for our own host
            String userHost = getHostFromUserName(toUserName);
            if (userHost.equals(host)) {
                continue;
            }

            hosts.add(userHost);
        }
        return hosts;
    }

    public String getActorUrlFromWebFingerObj(Object webFinger) {
        if (webFinger == null)
            return null;
        Object self = getLinkByRel(webFinger, "self");
        // log.debug("Self Link: " + XString.prettyPrint(self));

        String actorUrl = null;
        if (self != null) {
            actorUrl = apStr(self, APObj.href);
        }
        return actorUrl;
    }

    /*
     * Searches thru the 'links' array property on webFinger and returns the links array object that has
     * a 'rel' property that matches the value in the rel param string
     */
    public Object getLinkByRel(Object webFinger, String rel) {
        List<?> linksList = apList(webFinger, APObj.links, false);

        if (linksList == null)
            return null;

        for (Object link : linksList) {
            if (rel.equals(apStr(link, APObj.rel))) {
                return link;
            }
        }
        return null;
    }

    public APObj getRemoteAP(MongoSession ms, String userDoingGet, String url) {
        return getJson(ms, APObj.class, userDoingGet, url, APConst.MTYPE_ACT_JSON);
    }

    /*
     * Does the get with userDoingGet if exists, or else falls back to either the supplied ms, or the
     * admin ms.
     */
    public APObj getJson(MongoSession ms, Class<?> clazz, String userDoingGet, String url, MediaType mediaType) {

        if (PrincipalName.ANON.s().equals(userDoingGet)) {
            userDoingGet = null;
        }
        String _userDoingGet = userDoingGet;

        if (_userDoingGet != null) {
            return (APObj) arun.run(as -> {
                String actor = apUtil.makeActorUrlForUserName(_userDoingGet);

                /* if private key not sent then get it using the session */
                String privateKey = apCrypto.getPrivateKey(as, _userDoingGet);
                if (privateKey == null) {
                    throw new RuntimeException("Unable to get private key for user.");
                }
                return secureGet(url, clazz, privateKey, actor, mediaType);
            });
        } else if (ms != null) {
            String actor = apUtil.makeActorUrlForUserName(ms.getUserName());

            /* if private key not sent then get it using the session */
            String privateKey = apCrypto.getPrivateKey(ms, ms.getUserName());
            if (privateKey == null) {
                throw new RuntimeException("Unable to get private key for user.");
            }
            return secureGet(url, clazz, privateKey, actor, mediaType);
        } else {
            return (APObj) arun.run(as -> {
                String actor = apUtil.makeActorUrlForUserName(as.getUserName());

                /* if private key not sent then get it using the session */
                String privateKey = apCrypto.getPrivateKey(as, as.getUserName());
                if (privateKey == null) {
                    throw new RuntimeException("Unable to get private key for user.");
                }
                return secureGet(url, clazz, privateKey, actor, mediaType);
            });
        }
    }

    /**
     * Headers can be optionally passed in, preloaded with security properties, or else null is
     * acceptable too. 'clazz' is optional and tells which APObj-derived class to marshall into.
     */
    @PerfMon(category = "apUtil")
    public APObj getJson(String url, Class<?> clazz, MediaType mediaType, HttpHeaders headers) {
        // log.debug("getJson: " + url);
        APObj ret = null;
        int responseCode = 0;
        try {
            if (headers == null) {
                headers = new HttpHeaders();
            }

            if (mediaType != null) {
                List<MediaType> acceptableMediaTypes = new LinkedList<>();
                acceptableMediaTypes.add(mediaType);
                headers.setAccept(acceptableMediaTypes);
            }

            MultiValueMap<String, Object> bodyMap = new LinkedMultiValueMap<>();
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(bodyMap, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            if (response != null) {
                responseCode = response.getStatusCodeValue();
                // DO NOT DELETE: Example of how to query with completely unknown class.
                // mapper.readValue(response.getBody(), new TypeReference<>() {});
                ret = (APObj) mapper.readValue(response.getBody(), clazz);
            }
            // log.debug("REQ: " + url + "\nRES: " + XString.prettyPrint(ret));
        } catch (HttpClientErrorException.Gone goneEx) {
            log.debug("http says Gone: " + url);
            return null;
        } catch (HttpClientErrorException.Forbidden forbiddenEx) {
            log.debug("http says Forbidden: " + url);
            return null;
        } catch (Exception e) {
            log.debug("failed getting json: " + url + " -> " + e.getMessage() + " ex.class=" + e.getClass().getName()
                    + " respCode=" + responseCode);
            return null;
        }
        return ret;
    }

    public APObj secureGet(String url, Class<?> clazz, String privateKey, String actor, MediaType mediaType) {
        HttpHeaders headers = new HttpHeaders();
        apCrypto.loadSignatureHeaderVals(headers, privateKey, url, actor, null, "get");
        return getJson(url, clazz, mediaType, headers);
    }

    /* Posts to all inboxes */
    public void securePostEx(HashSet<String> inboxes, String fromActor, String privateKey, String actor, APObj message,
            MediaType postType) {
        if (inboxes == null)
            return;
        for (String inbox : inboxes) {
            try {
                apUtil.securePostEx(inbox, privateKey, fromActor, message, APConst.MTYPE_LD_JSON_PROF);
            }
            // catch error from any server, and ignore, go to next server to send to.
            catch (Exception e) {
                apLog.trace("failed to post to: " + inbox);
            }
        }
    }

    public void securePostEx(String url, String privateKey, String actor, APObj message, MediaType postType) {
        try {
            apLog.trace("Secure post to " + url);

            String body = XString.prettyPrint(message);
            apLog.trace("Posting Object:\n" + body);
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

            HttpHeaders headers = new HttpHeaders();
            apCrypto.loadSignatureHeaderVals(headers, privateKey, url, actor, bodyBytes, "post");
            postJson(url, body, headers, postType);
        } catch (Exception e) {
            // don't log, the postJson will have logged any exception.
            throw new RuntimeException(e);
        }
    }

    /*
     * Effeciently gets the Actor by using a cache to ensure we never get the same Actor twice until the
     * app restarts at least.
     * 
     * #todo-optimization: look for places we call this to get data we HAVE or should have locally, for
     * example to get: 1) followers 2) inbox (which we alread have a direct entry in apCache for inbox)
     * ...so we can definitely do a little optimization here around this
     */
    @PerfMon(category = "apUtil")
    public APOActor getActorByUrl(MongoSession ms, String userDoingAction, String url) {
        if (url == null)
            return null;

        apub.saveFediverseName(url);

        // first try to return from cache.
        APOActor actor = apCache.actorsByUrl.get(url);
        if (actor != null) {
            return actor;
        }

        try {
            actor = apUtil.getActor(ms, userDoingAction, url);
        } catch (Exception e) {
            log.error("Unable to get actor from url: " + url);
        }

        if (actor != null) {
            String userName = getLongUserNameFromActor(actor);
            apCache.actorsByUrl.put(url, actor);
            apCache.actorsByUserName.put(userName, actor);
        }
        // log.debug("Actor: " + XString.prettyPrint(actor));
        return actor;
    }

    public String getActorUrlFromForeignUserName(String userDoingAction, String userName) {
        String actorUrl = null;

        SubNode userNode = arun.run(as -> apub.getAcctNodeByForeignUserName(as, userDoingAction, userName, false, true));
        if (userNode != null) {
            actorUrl = userNode.getStr(NodeProp.ACT_PUB_ACTOR_ID);
        }

        // DO NOT DELETE: this is the other way to get the actorUrl without reading or creating the user
        // in our DB but we're not doing this.
        // String actorUrl = null;
        // APObj webFinger = apUtil.getWebFinger(userName);
        // if (ok(webFinger )) {
        // actorUrl = apUtil.getActorUrlFromWebFingerObj(webFinger);
        // }
        return actorUrl;
    }

    /*
     * https://server.org/.well-known/webfinger?resource=acct:someuser@server.org
     * 
     * Get WebFinger from foreign server
     * 
     * 'resource' examples:
     * 
     * someuser@server.org (normal Fediverse, no port)
     * 
     * someuser@ip:port (special testing mode, insecure)
     * 
     * #todo-optimization: check for any calls to this where we could've gotten the needed data locally
     */
    public APObj getWebFinger(MongoSession ms, String userDoingAction, String resource) {
        apub.saveFediverseName(resource);

        return getWebFingerSec(ms, userDoingAction, resource, true);
        // need to re-enable this again if we plan on doing localhost fediverse testing (todo-2)
        // // For non-secure domains, they're required to have a port in their name,
        // // so this is users like bob@q1:8184 (for example), and that port is expected
        // // also to NOT be https 443 port.
        // if (resource.contains(":")) {
        // return getWebFingerSec(resource, false);
        // }

        // try {
        // return getWebFingerSec(resource, true);
        // } catch (Exception e) {
        // return getWebFingerSec(resource, false);
        // }
    }

    /**
     * Sec suffix means 'security' option (https vs http)
     */
    public APObj getWebFingerSec(MongoSession ms, String userDoingAction, String userName, boolean secure) {
        if (userName.startsWith("@")) {
            userName = userName.substring(1);
        }
        String host = (secure ? "https://" : "http://") + getHostFromUserName(userName);

        if (apCache.webFingerFailsByUserName.contains(userName)) {
            return null;
        }

        // return from cache if we have this cached
        APObj finger = apCache.webFingerCacheByUserName.get(userName);
        if (finger != null) {
            return finger;
        }

        String url = host + APConst.PATH_WEBFINGER + "?resource=acct:" + userName;
        finger = getJson(ms, APObj.class, userDoingAction, url, APConst.MTYPE_JRD_JSON);

        if (finger != null) {
            // log.debug("Caching WebFinger: " + XString.prettyPrint(finger));
            apCache.webFingerCacheByUserName.put(userName, finger);
        } else {
            apCache.webFingerFailsByUserName.add(userName);
        }
        return finger;
    }

    public APObj postJson(String url, String body, HttpHeaders headers, MediaType postType) {
        APObj ret = null;
        try {
            // log.debug("postJson to: " + url);

            if (headers == null) {
                headers = new HttpHeaders();
            }
            headers.setAccept(List.of(APConst.MTYPE_ACT_JSON, APConst.MTYPE_JSON));

            String appName = prop.getConfigText("brandingAppName");
            if (appName == null)
                appName = "Quanta";

            // NOTE: I'm not sure this is ever necessary. Noticed Pleroma doing it and copied it.
            headers.add("user-agent", appName + "; https://" + prop.getMetaHost() + " <fake@email.com>");

            headers.setContentType(postType);

            HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            log.trace("POST: " + body + "\nTO: " + url + " RESULT: " + response.getStatusCode() + " response="
                    + response.getBody());
        } catch (Exception e) {
            log.error("postJson failed: " + url, e);
            throw new RuntimeException(e);
        }
        return ret;
    }

    /*
     * Generate webfinger response from our server
     */
    @PerfMon(category = "apUtil")
    public APObj generateWebFinger(String resource) {
        try {
            if (StringUtils.isNotEmpty(resource) && resource.startsWith("acct:")) {
                // split into username and host parts
                String[] parts = resource.substring(5).split("@", 2);

                if (parts.length == 2) {
                    String fullHost = parts[1];

                    // strip the port number off if exists
                    String host = XString.truncAfterFirst(fullHost, ":");

                    if (host.equals(prop.getMetaHost())) {
                        String username = parts[0];

                        SubNode userNode = read.getUserNodeByUserName(null, username);
                        if (userNode != null) {
                            return new APOWebFinger(username + "@" + fullHost, makeActorUrlForUserName(username));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("webfinger failed", e);
            throw new RuntimeException(e);
        }
        return null;
    }

    // #todo-optimization: we can call apub.getUserProperty() to get the value right? or is there a
    // direct cache entry for this?
    public String getLongUserNameFromActorUrl(MongoSession ms, String userDoingAction, String actorUrl) {
        if (actorUrl == null) {
            return null;
        }

        /*
         * Detect if this actorUrl points to our local server, and get the long name the easy way if so
         */
        if (isLocalActorUrl(actorUrl)) {
            String shortUserName = getLocalUserNameFromActorUrl(actorUrl);
            String longUserName = shortUserName + "@" + prop.getMetaHost();
            return longUserName;
        }

        APOActor actor = getActorByUrl(ms, userDoingAction, actorUrl);
        if (actor == null) {
            return null;
        }
        // log.debug("getLongUserNameFromActorUrl: " + actorUrl + "\n" +
        // XString.prettyPrint(actor));
        return getLongUserNameFromActor(actor);
    }

    /**
     * Uses the 'preferredUsername' in the 'actor' object to build the long name of the user like
     * preferredUserName@host.com
     */
    public String getLongUserNameFromActor(APOActor actor) {
        try {
            URL url = new URL(apStr(actor, APObj.inbox));
            String host = url.getHost();

            // get port number (normally not set and thus '-1')
            int port = url.getPort();

            /*
             * Be sure the port name is on the long name of non-standard ports. This is hacking the protocol to
             * support our localhost peer-to-peer scenario (servers q1, q2, etc)
             */
            if (port != -1 && port != 80 && port != 443) {
                host += ":" + String.valueOf(port);
            }

            // log.debug("long user name: " + shortUserName + "@" + host);
            return apStr(actor, APObj.preferredUsername) + "@" + host;
        } catch (Exception e) {
            log.error("failed building toUserName", e);
        }
        return null;
    }

    public boolean isLocalActorUrl(String actorUrl) {
        return actorUrl.startsWith(prop.getProtocolHostAndPort() + APConst.ACTOR_PATH + "/");
    }

    public String fullFediNameOfThreadUser() {
        return ThreadLocals.getSC().getUserName() + "@" + prop.getMetaHost();
    }

    /*
     * we know our own actor layout is this: https://ourserver.com/ap/u/userName, so this method just
     * strips the user name by taking what's after the rightmost slash
     */
    public String getLocalUserNameFromActorUrl(String actorUrl) {
        if (!isLocalActorUrl(actorUrl)) {
            log.debug("Invalid local actor Url: " + actorUrl);
            return null;
        }

        int lastIdx = actorUrl.lastIndexOf("/");
        String ret = null;
        if (lastIdx == -1) {
            log.debug("unable to get a user name from actor url: " + actorUrl);
            return null;
        }
        ret = actorUrl.substring(lastIdx + 1);
        return ret;
    }

    public void validateRequestTime(String date) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            long unixtime = dateFormat.parse(date).getTime();
            long now = System.currentTimeMillis();
            long diff = now - unixtime;
            if (diff > 30000L)
                throw new IllegalArgumentException("Date is too far in the future (difference: " + diff + "ms)");
            if (diff < -30000L)
                throw new IllegalArgumentException("Date is too far in the past (difference: " + diff + "ms)");
        } catch (Exception e) {
            throw new RuntimeException("Failed checking time on http request.");
        }
    }

    public boolean isPublicAddressed(String address) {
        return address.endsWith("#Public") || address.equals("Public") || address.equals("as:Public");
    }

    public boolean isLocalUrl(String url) {
        return url != null && url.startsWith(prop.getHttpProtocol() + "://" + prop.getMetaHost());
    }

    public void iterateCollection(MongoSession ms, String userDoingAction, Object collectionObj, int maxCount,
            ActPubObserver observer) {
        if (collectionObj == null)
            return;
        /*
         * To reduce load for our purposes we can limit to just getting 2 pages of results to update a user,
         * and really just one page would be ideal if not for the fact that some servers return an empty
         * first page and put the results in the 'last' page
         */
        int maxPageQueries = 5;
        int pageQueries = 0;

        // log.debug("interateOrderedCollection(): " + XString.prettyPrint(collectionObj));
        int count = 0;
        /*
         * We user apIdSet to avoid processing any dupliates, because the AP spec calls on us to do this and
         * doesn't guarantee it's own dedupliation
         */
        HashSet<String> apIdSet = new HashSet<>();

        /*
         * The collection object itself is allowed to have items/orderedItems, which if present we process,
         * in addition to the paging, although normally when the collection has the items it means it won't
         * have any paging
         */
        List<?> items = apList(collectionObj, APObj.orderedItems, false);

        // try as unordered next (we handle both)
        if (items == null) {
            items = apList(collectionObj, APObj.items, false);
        }

        if (items != null) {
            // log.debug("orderedItems(a): " + XString.prettyPrint(orderedItems));
            /*
             * Commonly this will just be an array strings (like in a 'followers' collection on Mastodon)
             */
            for (Object obj : items) {
                if (!observer.item(obj)) {
                    return;
                }
                if (++count >= maxCount)
                    return;
            }
        }

        /*
         * Warning: There are times when even with only two items in the outbox Mastodon might send back an
         * empty array in the "first" page and the two items in the "last" page, which makes no sense, but
         * it just means we have to read and deduplicate all the items from all pages to be sure we don't
         * end up with a empty array even when there ARE some
         */
        Object firstPage = apObj(collectionObj, APObj.first);

        if (firstPage != null) {
            // log.debug("First Page Url: " + XString.prettyPrint(firstPage));
            if (++pageQueries > maxPageQueries)
                return;

            Object ocPage = null;

            // if firstPage contained a String consider it a URL to the page and get it.
            if (firstPage instanceof String) {
                ocPage = getRemoteAP(ms, userDoingAction, (String) firstPage);
                // log.debug("ocPage(a): " + XString.prettyPrint(ocPage));
            }
            // else consider firstPage to be the ACTUAL first page object
            else {
                ocPage = firstPage;
            }

            while (ocPage != null) {
                items = apList(ocPage, APObj.orderedItems, false);

                if (items == null) {
                    items = apList(ocPage, APObj.items, false);
                }

                if (items != null) {
                    // log.debug("orderedItems(b): " + XString.prettyPrint(orderedItems));

                    for (Object item : items) {
                        // if item is an object (map)
                        if (apHasProps(item)) {
                            String apId = apStr(item, APObj.id);
                            // if no apId that's fine, just process item.
                            if (apId == null) {
                                if (!observer.item(item))
                                    return;
                            }
                            // if no apId that's fine, just process item.
                            else if (!apIdSet.contains(apId)) {
                                // log.debug("Iterate Collection Item: " + apId);
                                if (!observer.item(item))
                                    return;
                                apIdSet.add(apId);
                            }
                        }
                        // otherwise item is probably a 'String' but whatever it is we call 'item' on
                        // it.
                        else {
                            if (!observer.item(item))
                                return;
                        }
                        if (++count >= maxCount)
                            return;
                    }
                }

                Object nextPage = apObj(ocPage, APObj.next);

                if (nextPage != null) {
                    // log.debug("nextPage: " + XString.prettyPrint(nextPage));
                    if (++pageQueries > maxPageQueries)
                        return;

                    // if nextPage is a string consider that a reference to the URL of the page and get it
                    if (nextPage instanceof String) {
                        ocPage = getRemoteAP(ms, userDoingAction, (String) nextPage);
                        // log.debug("ocPage(d): " + XString.prettyPrint(ocPage));
                    } else {
                        ocPage = nextPage;
                    }
                } else {
                    break;
                }
            }
        }

        Object lastPage = apObj(collectionObj, APObj.last);
        if (lastPage != null) {
            // log.debug("Last Page Url: " + lastPage);
            if (++pageQueries > maxPageQueries)
                return;

            Object ocPage = null;

            // if lastPage is a string it's the url
            if (lastPage instanceof String) {
                ocPage = getRemoteAP(ms, userDoingAction, (String) lastPage);
                // log.debug("ocPage(c): " + XString.prettyPrint(ocPage));
            }
            // else it's the page object
            else {
                ocPage = lastPage;
            }
            if (ocPage != null) {
                items = apList(ocPage, APObj.orderedItems, false);

                if (items == null) {
                    items = apList(ocPage, APObj.items, false);
                }

                if (items != null) {
                    // log.debug("orderedItems(c): " + XString.prettyPrint(orderedItems));
                    for (Object item : items) {
                        // if item is an object (map)
                        if (apHasProps(item)) {
                            String apId = apStr(item, APObj.id);
                            // if no apId that's fine, just process item.
                            if (apId == null) {
                                if (!observer.item(item))
                                    return;
                            }
                            // else process it with apId
                            else if (!apIdSet.contains(apId)) {
                                // log.debug("Iterate Collection Item: " + apId);
                                if (!observer.item(item))
                                    return;
                                apIdSet.add(apId);
                            }
                        }
                        // otherwise item is probably a 'String' but whatever it is we call 'item' on
                        // it.
                        else {
                            if (!observer.item(item))
                                return;
                        }
                        if (++count >= maxCount)
                            return;
                    }
                }
            }
        }
    }

    /* Try to generate the best 'inReplyTo' that TARGETS this node */
    public String buildUrlForReplyTo(MongoSession ms, SubNode node) {
        if (node == null)
            return null;

        // try this property first.
        String replyTo = node.getStr(NodeProp.OBJECT_ID);

        // fall back to this...
        if (replyTo == null) {
            replyTo = node.getStr(NodeProp.ACT_PUB_OBJ_URL);
        }

        // or finally reference pointing to our own server node, if it's not private
        if (replyTo == null) {
            replyTo = snUtil.getIdBasedUrl(node);
        }

        return replyTo;
    }

    /*
     * Every node getting deleted will call into here (via a hook in MongoEventListener), so we can do
     * whatever we need to in this hook, which for now is just used to manage unfollowing a Friend if a
     * friend is deleted
     */
    public void deleteNodeNotify(ObjectId nodeId) {
        if (!MongoRepository.fullInit)
            return;

        arun.run(as -> {
            SubNode node = read.getNode(as, nodeId);
            if (node != null && node.isType(NodeType.FRIEND)) {
                String friendUserName = node.getStr(NodeProp.USER);
                if (friendUserName != null) {
                    // if a foreign user, update thru ActivityPub
                    if (friendUserName.contains("@") && ThreadLocals.getSC() != null && !ThreadLocals.getSC().isAdmin()) {
                        String followerUser = ThreadLocals.getSC().getUserName();
                        apFollowing.setFollowing(followerUser, friendUserName, false);
                    }
                }
            }
            return null;
        });
    }

    @EventListener
    public void onApplicationEvent(MongoDeleteEvent event) {
        deleteNodeNotify((ObjectId) event.getSource());
    }

    // todo-1: method is part of a work in progress and is not complete
    public void readForeignReplies(MongoSession ms, SubNode node, LinkedList<NodeInfo> replyNodes) {
        String apId = node.getStr(NodeProp.OBJECT_ID);
        if (apId == null) {
            // if no apId exists this isn't a foreign node, nothing to do here.
            return;
        }

        Map<String, Object> repliesObj = node.getObj(NodeProp.ACT_PUB_REPLIES.s(), Map.class);
        if (repliesObj == null)
            return;

        String type = apStr(repliesObj, APObj.type);
        if (!APType.Collection.equals(type) && !APType.OrderedCollection.equals(type)) {
            return;
        }

        String userDoingAction = ThreadLocals.getSC().getUserName();

        apUtil.iterateCollection(ms, userDoingAction, repliesObj, 100, obj -> {
            // log.debug("REPLY: " + XString.prettyPrint(obj));

            // If a reply is the string assume that's the URL to the object
            if (obj instanceof String) {
                NodeInfo replyNodeInfo = apUtil.loadObjectNodeInfo(ms, userDoingAction, (String) obj);
                if (replyNodeInfo != null) {
                    // log.debug("reply (BY URL): " + XString.prettyPrint(replyNodeInfo));
                    replyNodes.add(replyNodeInfo);
                }
            }
            // else we try as a data object
            else if (obj instanceof Map) {
                APObj apObj = new APObj((Map) obj);
                NodeInfo replyNodeInfo = apUtil.loadObjectNodeInfoFromObj(ms, userDoingAction, apObj);
                if (replyNodeInfo != null) {
                    // log.debug("reply (BY OBJ): " + XString.prettyPrint(replyNodeInfo));
                    replyNodes.add(replyNodeInfo);
                }
            } else {
                log.debug("Unhandled Reply Type: " + obj.getClass().getName());
            }
            return true;
        });
    }

    public GetThreadViewResponse getNodeReplies(MongoSession ms, String nodeId) {
        GetThreadViewResponse res = new GetThreadViewResponse();
        LinkedList<NodeInfo> nodes = new LinkedList<>();

        // get node that's going to have it's ancestors gathered
        SubNode node = read.getNode(ms, nodeId);
        if (node == null)
            return res;

        NodeInfo info = convert.convertToNodeInfo(false, ThreadLocals.getSC(), ms, node, false, //
                Convert.LOGICAL_ORDINAL_IGNORE, false, false, false, //
                false, true, true, null, false);
        nodes.add(info);

        String apReplies = node.getStr(NodeProp.ACT_PUB_REPLIES);
        if (apReplies != null) {
            readForeignReplies(ms, node, nodes);
        }

        if (nodes.size() > 1) {
            res.setSuccess(true);
            res.setNodes(nodes);
        }
        return res;
    }

    /*
     * Gets the "[Conversation] Thread" for 'nodeId' which is kind of the equivalent of the walk up
     * towards the root of the tree.
     * 
     * NOTE: If nostrNodeIds is provided (non-null) we use it to completely determine the thread
     * content, rather than looking at tree parents or IN_REPLY_TO.
     */
    public GetThreadViewResponse getNodeThreadView(MongoSession ms, String nodeId, boolean loadOthers) {
        boolean debug = true;

        GetThreadViewResponse res = new GetThreadViewResponse();
        LinkedList<NodeInfo> nodes = new LinkedList<>();

        if (debug) {
            log.debug("getNodeThreadView() " + nodeId);
        }

        // get node that's going to have it's ancestors gathered
        SubNode node = read.getNode(ms, nodeId);
        boolean topReached = false;
        ObjectId lastNodeId = null;

        // todo-1: This is an unfinished work in progress. I was unable to find any foreign posts
        // that put any messages in their 'replies' collection, or at least when I query collections
        // I get back an empty array of items for whatever reason.
        // if (ok(node)) {
        // readForeignReplies(ms, node);
        // }

        // iterate up the parent chain or chain of inReplyTo for ActivityPub
        while (node != null && (nodes.size() < MAX_THREAD_NODES)) {
            try {
                NodeInfo info = null;

                /*
                 * note topNode doesn't necessarily mean we're done iterating because it's 'inReplyTo' still may
                 * point to further places 'logically above' (in this conversation thread)
                 */
                boolean topNode =
                        node.isType(NodeType.POSTS) || node.isType(NodeType.ACT_PUB_POSTS) || node.isType(NodeType.ACCOUNT);

                if (!topNode) {
                    info = convert.convertToNodeInfo(false, ThreadLocals.getSC(), ms, node, //
                            false, Convert.LOGICAL_ORDINAL_IGNORE, false, false, false, //
                            false, true, true, null, //
                            true);

                    // we only collect children at this level if it's not an account top level post
                    if (loadOthers) {
                        Iterable<SubNode> iter =
                                read.getChildren(ms, node, Sort.by(Sort.Direction.DESC, SubNode.CREATE_TIME), 20, 0);
                        HashSet<String> childIds = new HashSet<>();
                        List<NodeInfo> children = new LinkedList<>();

                        for (SubNode child : iter) {
                            if (!child.getId().equals(lastNodeId)) {
                                childIds.add(child.getIdStr());
                                children.add(convert.convertToNodeInfo(false, ThreadLocals.getSC(), ms, //
                                        child, false, Convert.LOGICAL_ORDINAL_IGNORE, false, false, false, false, true, true,
                                        null, false));
                            }
                        }

                        /*
                         * if this node has a NodeProp.OBJECT_ID property, we also add in all nodes that have a
                         * NodeProp.INREPLYTO pointing to it, because they are also replies
                         */
                        String replyTargetId = node.getStr(NodeProp.OBJECT_ID);

                        // if node was an ActivityPub one we will have a replyTargetId here that's non null,
                        // and it will be a full URL to the replyTo, BUT if not then this node might point to
                        // a replyTo that's a plain SubNode nodeId (instead of full path, so we search on that
                        // instead)
                        if (replyTargetId == null) {
                            replyTargetId = node.getIdStr();
                        }

                        // REGEX path expression to find both /r/usr/L and /r/usr/R as an *or* inside the actual REGEX
                        // which will combine similar to /r/usr/(L | R), but I'm not sure the syntax yet.
                        iter = read.findNodesByProp(ms, NodePath.USERS_PATH + //
                                "/(" + NodePath.LOCAL + "|" + NodePath.REMOTE + ")", NodeProp.INREPLYTO.s(), replyTargetId);
                        for (SubNode child : iter) {
                            // if we didn't already add above, add now
                            if (!childIds.contains(child.getIdStr())) {
                                children.add(convert.convertToNodeInfo(false, ThreadLocals.getSC(), ms, child, false,
                                        Convert.LOGICAL_ORDINAL_IGNORE, false, false, false, false, true, true, null, false));
                            }
                        }

                        if (children.size() > 0) {
                            info.setChildren(children);
                        }
                    }
                }

                if (info != null) {
                    nodes.addFirst(info);
                    lastNodeId = node.getId();
                }

                // if topNode, set parent to null, to trigger the only path up to have to
                // go thru an inReplyTo, rather than be based on tree structure.
                // SubNode parent = topNode ? null : read.getParent(ms, node);
                SubNode parent = null;
                if (topNode) {
                    // leave parent == null;
                } else {
                    parent = read.getParent(ms, node);
                }
                boolean top = parent != null && (parent.isType(NodeType.POSTS) || parent.isType(NodeType.ACT_PUB_POSTS));

                // if we didn't get a usable (non root) parent from the tree structure, try using the 'inReplyTo'
                // value
                if (parent == null || top) {
                    String inReplyTo = node.getStr(NodeProp.INREPLYTO);

                    // if node has an inReplyTo...
                    if (inReplyTo != null) {

                        // we distinguish a URL from a nodeId by the fact that only URLs can contain ":"
                        if (inReplyTo.contains(":")) {
                            // then loadObject will get it from DB or else resort to getting it from network
                            parent = apUtil.loadObject(ms, ThreadLocals.getSC().getUserName(), inReplyTo);
                        }
                        // if inReplyTo not a URL, treat it as a nodeId
                        else {
                            parent = read.getNode(ms, inReplyTo);
                        }
                    }
                    // else try to get the node being replied to as if this is a NostrNode
                    else {
                        Val<Boolean> nodeMissing = new Val<Boolean>(false);
                        parent = nostr.getNodeBeingRepliedTo(ms, node, nodeMissing);
                        if (nodeMissing.getVal()) {
                            res.setNostrDeadEnd(true);
                        }

                        if (debug) {
                            if (parent != null) {
                                log.debug("NOSTR REPLY PARENT of " + node.getIdStr() + "=" + XString.prettyPrint(parent));
                            } else {
                                log.debug("NOSTR couldn't find reply parent.");
                            }
                        }
                    }
                }

                node = parent;
                if (node == null) {
                    topReached = true;
                }
            } catch (Exception e) {
                node = null;
                topReached = true;
                /*
                 * ignore this. Every user will eventually end up at some non-root node they don't own, even if it's
                 * the one above their account, this represents how far up the user is able to read towards the root
                 * of the tree based on sharing setting of nodes encountered along the way to the root.
                 */
            }
        }
        if (node == null) {
            topReached = true;
        }

        res.setTopReached(topReached);
        res.setNodes(nodes);
        if (nodes.size() > 1) {
            // sort the array
            nodes.sort((n1, n2) -> (int) n1.getLastModified().compareTo(n2.getLastModified()));

            // sort all children also
            for (NodeInfo n : nodes) {
                if (n.getChildren() != null) {
                    n.getChildren().sort((n1, n2) -> (int) n1.getLastModified().compareTo(n2.getLastModified()));
                }
            }

            res.setSuccess(true);
        }

        if (debug) {
            log.debug("getNodeThreadView() RESP: " + XString.prettyPrint(res));
        }
        return res;
    }

    public NodeInfo loadObjectNodeInfo(MongoSession ms, String userDoingAction, String url) {
        SubNode node = loadObject(ms, userDoingAction, url);
        NodeInfo info = convert.convertToNodeInfo(false, ThreadLocals.getSC(), ms, node, //
                false, Convert.LOGICAL_ORDINAL_IGNORE, false, false, //
                true, false, true, true, null, false);
        return info;
    }

    public NodeInfo loadObjectNodeInfoFromObj(MongoSession ms, String userDoingAction, APObj obj) {
        SubNode node = loadObjectFromObj(ms, userDoingAction, obj);
        NodeInfo info = convert.convertToNodeInfo(false, ThreadLocals.getSC(), ms, node, //
                false, Convert.LOGICAL_ORDINAL_IGNORE, false, false, //
                true, false, true, true, null, false);
        return info;
    }

    /*
     * Loads the foreign object into Quanta under the foreign account representing that user, and
     * returns it. Returns existing node if found instead. If there's no account created yet for the
     * user we create the account
     * 
     * if allowFiltering==false that means allow foreign languages, profanity, etc.
     */
    public SubNode loadObject(MongoSession ms, String userDoingAction, String url) {
        // log.debug("loadObject: url=" + url + " userDoingAction: " + userDoingAction);
        if (url == null)
            return null;

        if (apUtil.isLocalUrl(url)) {
            int lastIdx = url.lastIndexOf("=");
            if (lastIdx != -1) {
                String nodeId = url.substring(lastIdx + 1);
                return read.getNode(ms, nodeId);
            }
        }

        // Try to look up the node first from the DB.
        SubNode nodeFound = read.findNodeByProp(ms, NodeProp.OBJECT_ID.s(), url);
        if (nodeFound != null) {
            log.debug("loadObject(): Node found by ID: " + url);
            return nodeFound;
        }

        // node not found in DB yet, so we have to get it from off the web from scratch
        APObj obj = apUtil.getRemoteAP(ms, userDoingAction, url);
        if (obj == null) {
            log.debug("unable to get json: " + url);
            return null;
        }

        return loadObjectFromObj(ms, userDoingAction, obj);
    }

    public SubNode loadObjectFromObj(MongoSession ms, String userDoingAction, APObj obj) {
        String id = apStr(obj, APObj.id);

        SubNode nodeFound = read.findNodeByProp(ms, NodeProp.OBJECT_ID.s(), id);
        if (nodeFound != null) {
            log.debug("loadObjectFromObj(): Node found by ID: " + id);
            return nodeFound;
        }

        // todo-1: we only support "Note" for now.
        String type = apStr(obj, APObj.type);
        switch (type) {
            // todo-1: I know we don't support type "Question" or type "Video" yet so I need to at least
            // send back a visible message to the user saying that this type of node is not yet supported by
            // Quanta and so the history cannot be displayed.
            case APType.Note:
                String ownerActorUrl = apStr(obj, APObj.attributedTo);
                if (ownerActorUrl != null) {
                    return (SubNode) arun.run(as -> {
                        SubNode node = null;
                        SubNode accountNode = apub.getAcctNodeByActorUrl(as, userDoingAction, ownerActorUrl);
                        if (accountNode != null) {
                            String apUserName = accountNode.getStr(NodeProp.USER);
                            SubNode outboxNode =
                                    read.getUserNodeByType(as, apUserName, accountNode, "### Posts", NodeType.ACT_PUB_POSTS.s(),
                                            Arrays.asList(PrivilegeType.READ.s(), PrivilegeType.WRITE.s()), NodeName.POSTS);
                            if (outboxNode == null) {
                                log.debug("no outbox for user: " + apUserName);
                                return null;
                            }

                            node = apub.saveInboundForeignObj(as, userDoingAction, accountNode, outboxNode, obj, APType.Create,
                                    null, null, false, null);
                        }
                        return node;
                    });
                }
                break;
            default:
                // todo-1: in the ThreadViewer logic we need to somehow show to the user this happened
                log.debug("Unhandled type in loadObject: " + type);
                break;
        }
        return null;
    }

    /*
     * Updates all the ActPub properties from actor object onto the node, and returns true only of
     * something was indeed changed so that the DB will only get updated if something DID change
     */
    public boolean updateNodeFromActorObject(SubNode node, APOActor actor) {
        boolean changed = false;
        Object icon = apObj(actor, APObj.icon);

        // todo-1: need to also support icon being an array here, to be compatable with spec:
        // "icon": [
        // "https://kenzoishii.example.com/image/165987aklre4"
        // ]

        if (icon != null) {
            String iconUrl = apStr(icon, APObj.url);
            if (iconUrl != null) {
                String curIconUrl = node.getStr(NodeProp.USER_ICON_URL);
                if (!iconUrl.equals(curIconUrl)) {
                    if (node.set(NodeProp.USER_ICON_URL, iconUrl)) {
                        changed = true;
                    }
                }
            }
        }

        Object endpoints = apObj(actor, APObj.endpoints);
        if (endpoints != null) {
            String sharedInbox = apStr(endpoints, APObj.sharedInbox);
            if (sharedInbox != null) {
                String curSharedInbox = node.getStr(NodeProp.ACT_PUB_SHARED_INBOX);
                if (!sharedInbox.equals(curSharedInbox)) {
                    if (node.set(NodeProp.ACT_PUB_SHARED_INBOX, sharedInbox)) {
                        changed = true;
                    }
                }
            }
        }

        Object image = apObj(actor, APObj.image);
        if (image != null) {
            String imageUrl = apStr(image, APObj.url);
            if (imageUrl != null) {
                String curImageUrl = node.getStr(NodeProp.USER_BANNER_URL);
                if (!imageUrl.equals(curImageUrl)) {
                    if (node.set(NodeProp.USER_BANNER_URL, imageUrl)) {
                        changed = true;
                    }
                }
            }
        }

        if (node.set(NodeProp.USER_BIO, apStr(actor, APObj.summary)))
            changed = true;

        if (node.set(NodeProp.DISPLAY_NAME, apStr(actor, APObj.name)))
            changed = true;

        String actorId = apStr(actor, APObj.id);
        if (actorId == null) {
            log.debug("no actorId on object: " + XString.prettyPrint(actor));
        }

        // this is the URL of the Actor JSON object
        if (node.set(NodeProp.ACT_PUB_ACTOR_ID, actorId))
            changed = true;

        // update cache just because we can
        apCache.inboxesByUserName.put(node.getStr(NodeProp.USER), apStr(actor, APObj.inbox));

        if (node.set(NodeProp.ACT_PUB_ACTOR_INBOX, apStr(actor, APObj.inbox)))
            changed = true;

        // this is the URL of the HTML of the actor.
        if (node.set(NodeProp.ACT_PUB_ACTOR_URL, apStr(actor, APObj.url)))
            changed = true;

        // get the pubKey so we can save into our account node
        String pubKey = apCrypto.getEncodedPubKeyFromActorObj(actor);

        // this is the PublicKey.pubKeyPem, of the user
        if (node.set(NodeProp.ACT_PUB_KEYPEM, pubKey))
            changed = true;

        return changed;
    }

    public APOActor getActor(MongoSession ms, String userDoingGet, String url) {
        return (APOActor) apUtil.getJson(ms, APOActor.class, userDoingGet, url, APConst.MTYPE_ACT_JSON);
    }
}
