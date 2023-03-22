package quanta.actpub;

import java.security.PrivateKey;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bson.types.ObjectId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.actpub.model.APOActor;
import quanta.actpub.model.APObj;
import quanta.config.ServiceBase;
import quanta.mongo.model.SubNode;
import quanta.util.DateUtil;

/**
 * Holds all the global caches related to AP.
 * 
 * #todo-optimization: need timer that cleans each of these out once every 8hrs.
 */
@Component
@Slf4j 
public class ActPubCache extends ServiceBase {
    /*
     * Holds users for which messages need refreshing (false value) but sets value to 'true' once
     * completed
     */
    public final ConcurrentHashMap<String, Boolean> usersPendingRefresh = new ConcurrentHashMap<>();

    public final ConcurrentHashMap<String, Boolean> allUserNames = new ConcurrentHashMap<>();

    /* Cache Actor objects by UserName in memory only for now */
    public final ConcurrentHashMap<String, APOActor> actorsByUserName = new ConcurrentHashMap<>();

    /* Cache Actor objects by URL in memory only for now */
    public final ConcurrentHashMap<String, APOActor> actorsByUrl = new ConcurrentHashMap<>();

    /* Cache Actor URLS by UserName in memory only for now */
    public final ConcurrentHashMap<String, String> actorUrlsByUserName = new ConcurrentHashMap<>();

    /* Cache inboxes by UserName in memory only for now */
    public final ConcurrentHashMap<String, String> inboxesByUserName = new ConcurrentHashMap<>();

    /* Cache of user account node Ids by actor url */
    public final ConcurrentHashMap<String, String> acctIdByActorUrl = new ConcurrentHashMap<>();

    /* Account Node by actor Url */
    public final ConcurrentHashMap<String, SubNode> acctNodesByActorUrl = new ConcurrentHashMap<>();

    /* Account Node by User Name */
    public final ConcurrentHashMap<String, SubNode> acctNodesByUserName = new ConcurrentHashMap<>();

    /* Account Node by node ID */
    public final ConcurrentHashMap<String, SubNode> acctNodesById = new ConcurrentHashMap<>();

    /* Cache WebFinger objects by UserName in memory only for now */
    public final ConcurrentHashMap<String, APObj> webFingerCacheByUserName = new ConcurrentHashMap<>();

    /* Maps the string representation of a key to the PrivateKey object */
    public final ConcurrentHashMap<String, PrivateKey> privateKeys = new ConcurrentHashMap<>();

    /* Cache WebFinger fails, so we don't try them again */
    public final Set<String> webFingerFailsByUserName = Collections.synchronizedSet(new HashSet<String>());

    /*
     * This holds the set of all users followed by any user in Quanta (which we might need to change to
     * just the users that are followed by FollowBot?) so that when we're doing the WebCrawl to pull
     * down outbox content we can potentially skip this these users with the assumption they will be
     * SENDING inbound posts live as they're created, meaning we don't need to CRAWL (pull) them.
     * 
     * BUT we need to keep track of WHICH users are sending inbound to us these posts and ONLY THEN
     * would we assume we don't want to actually try to CRAWL their outbox to get their data (in the
     * theoretically possible case where their instance for some reason isn't *honoring* our follow, or
     * the user rejected the follow, or for whatever other reason it could be the case that the *follow*
     * isn't getting us the data but a "crawl" (outbox pull) might work to get the user's data.
     */
    public final HashSet<String> followedUsers = new HashSet<>();

    @Scheduled(fixedDelay = 180 * DateUtil.MINUTE_MILLIS)
    public void cacheClean() {
        log.debug("ActPubCache: cacheClean 3hr");
        actorsByUserName.clear();
        actorsByUrl.clear();
        actorUrlsByUserName.clear();
        inboxesByUserName.clear();
        acctNodesByActorUrl.clear();
        acctNodesByUserName.clear();
        webFingerCacheByUserName.clear();
    }

    /*
     * Update cache, when nodes change. Yes, this is a bit of a tight-coupling to be calling this from
     * the MongoEventListener, when we could be using more of a pub-sub mode.
     */
    public void saveNotify(SubNode node) {
        final ObjectId objId = node.getId();

        acctNodesByActorUrl.forEach((k, v) -> {
            if (v.getId().equals(objId)) {
                acctNodesByActorUrl.put(k, node);
            }
        });

        acctNodesByUserName.forEach((k, v) -> {
            if (v.getId().equals(objId)) {
                acctNodesByUserName.put(k, node);
            }
        });

        acctNodesById.forEach((k, v) -> {
            if (v.getId().equals(objId)) {
                acctNodesById.put(k, node);
            }
        });
    }
}
