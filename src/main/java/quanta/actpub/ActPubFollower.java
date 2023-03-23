package quanta.actpub;

import static quanta.actpub.model.AP.apInt;
import static quanta.actpub.model.AP.apStr;
import java.util.LinkedList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.actpub.model.APOActor;
import quanta.actpub.model.APOOrderedCollection;
import quanta.actpub.model.APOOrderedCollectionPage;
import quanta.actpub.model.APObj;
import quanta.config.NodePath;
import quanta.config.ServiceBase;
import quanta.instrument.PerfMon;
import quanta.model.NodeInfo;
import quanta.model.client.ConstantInt;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.GetFollowersRequest;
import quanta.response.GetFollowersResponse;
import quanta.util.ThreadLocals;
import quanta.util.XString;

/**
 * Methods related to AP Follower
 */
@Component
@Slf4j 
public class ActPubFollower extends ServiceBase {
    @Autowired
    private ActPubLog apLog;

    /**
     * Generates outbound followers data
     */
    @PerfMon(category = "apFollower")
    public APOOrderedCollection generateFollowers(String userMakingRequest, String userName) {
        String url = prop.getProtocolHostAndPort() + APConst.PATH_FOLLOWERS + "/" + userName;
        Long totalItems = getFollowersCount(userMakingRequest, userName);

        APOOrderedCollection ret = new APOOrderedCollection(url, totalItems, url + "?page=true", //
                url + "?min_id=0&page=true");
        return ret;
    }

    /* Calls saveFediverseName for each person who is a 'follower' of actor */
    public int loadRemoteFollowers(MongoSession ms, String userMakingRequest, APOActor actor) {

        APObj followers = getRemoteFollowers(ms, userMakingRequest, apStr(actor, APObj.followers));
        if (followers == null) {
            log.debug("Unable to get followers for AP user: " + apStr(actor, APObj.followers));
            return 0;
        }

        int ret = apInt(followers, APObj.totalItems);

        apUtil.iterateCollection(ms, userMakingRequest, followers, Integer.MAX_VALUE, obj -> {
            try {
                // if (ok(obj )) {
                // log.debug("follower: OBJ=" + XString.prettyPrint(obj));
                // }

                if (obj instanceof String) {
                    String followerActorUrl = (String) obj;

                    // for now just add the url for future crawling. todo-1: later we can do something more meaningful
                    // with each actor url.
                    if (apub.saveFediverseName(followerActorUrl)) {
                        // log.debug("follower: " + followerActorUrl);
                    }
                } else {
                    log.debug("Unexpected follower item class: " + obj.getClass().getName());
                }

            } catch (Exception e) {
                log.error("Failed processing collection item.", e);
            }
            // always iterate all.
            return true;
        });
        return ret;
    }

    public APObj getRemoteFollowers(MongoSession ms, String userMakingRequest, String url) {
        if (url == null)
            return null;

        APObj outbox = apUtil.getRemoteAP(ms, userMakingRequest, url);
        // ActPubService.outboxQueryCount++;
        // ActPubService.cycleOutboxQueryCount++;
        apLog.trace("Followers: " + XString.prettyPrint(outbox));
        return outbox;
    }

    /**
     * Returns followers for LOCAL users only following 'userName'. This doesn't use ActPub or query any
     * remote servers
     * 
     * Returns a list of all the 'actor urls' for all the users that are following user 'userName'
     */
    public List<String> getFollowersPage(String userName, String minId) {
        List<String> followers = new LinkedList<>();
        log.debug("getFollowers of " + userName + " minId=" + minId);

        arun.run(as -> {
            /*
             * Gets nodes of type 'sn:friend' who are targeting this 'userName' (i.e. friend nodes, i.e.
             * representing followers of this user)
             */
            Iterable<SubNode> iter = getPeopleByUserName(as, userName);

            for (SubNode n : iter) {
                // the owner of the friend node is the "Follower".
                SubNode ownerOfFriendNode = read.getNode(as, n.getOwner());

                if (ownerOfFriendNode != null) {
                    // log.debug(" owner (follower): " + ownerOfFriendNode.getIdStr());
                    // fyi: we had ACT_PUB_ACTOR_URL here before, which was a bug.
                    String remoteActorUrl = ownerOfFriendNode.getStr(NodeProp.ACT_PUB_ACTOR_ID);

                    // this will be non-null if it's a remote account.
                    if (remoteActorUrl != null) {
                        followers.add(remoteActorUrl);
                    }
                    // otherwise, it's a local user, and we know how to build the Actor URL of our own users.
                    else {
                        // the name on the account that owns the Friend node in his Friends List, is the "Follower"
                        String followerUserName = ownerOfFriendNode.getStr(NodeProp.USER);

                        // sanity check that name doesn't contain '@' making it a foreign user.
                        if (!followerUserName.contains("@")) {
                            followers.add(apUtil.makeActorUrlForUserName(followerUserName));
                        }
                    }
                }
            }
            return null;
        });

        return followers;
    }

    public Long getFollowersCount(String userMakingRequest, String userName) {
        return (Long) arun.run(as -> {
            Long count = countFollowersOfUser(as, userMakingRequest, null, userName, null);
            return count;
        });
    }

    @PerfMon(category = "apFollower")
    public APOOrderedCollectionPage generateFollowersPage(String userName, String minId) {
        List<String> followers = getFollowersPage(userName, minId);

        // this is a self-reference url (id)
        String url = prop.getProtocolHostAndPort() + APConst.PATH_FOLLOWERS + "/" + userName + "?page=true";
        if (minId != null) {
            url += "&min_id=" + minId;
        }

        APOOrderedCollectionPage ret = new APOOrderedCollectionPage(url, followers,
                prop.getProtocolHostAndPort() + APConst.PATH_FOLLOWERS + "/" + userName, followers.size());
        return ret;
    }

    public Iterable<SubNode> getPeopleByUserName(MongoSession ms, String userName) {
        Query q = getPeopleByUserName_query(ms, null, userName);
        if (q == null)
            return null;
        return mongoUtil.find(q);
    }

    public GetFollowersResponse getFollowers(MongoSession ms, GetFollowersRequest req) {
        GetFollowersResponse res = new GetFollowersResponse();

        return arun.run(as -> {
            Query q = getPeopleByUserName_query(as, null, req.getTargetUserName());
            if (q == null)
                return null;

            q.limit(ConstantInt.ROWS_PER_PAGE.val());
            q.skip(ConstantInt.ROWS_PER_PAGE.val() * req.getPage());

            Iterable<SubNode> iterable = mongoUtil.find(q);
            List<NodeInfo> searchResults = new LinkedList<NodeInfo>();
            int counter = 0;

            for (SubNode node : iterable) {
                NodeInfo info = convert.convertToNodeInfo(false, ThreadLocals.getSC(), as, node, false, counter + 1, false,
                        false, false, true, false, false, null, false);
                if (info != null) {
                    searchResults.add(info);
                }
            }

            res.setSearchResults(searchResults);
            return res;
        });
    }

    public long countFollowersOfUser(MongoSession ms, String userMakingRequest, SubNode userNode, String userName,
            String actorUrl) {
        // if local user
        if (userName.indexOf("@") == -1) {
            return countFollowersOfLocalUser(ms, userNode, userName);
        }
        // if foreign user
        else {
            /* Starting with just actorUrl, lookup the follower count */
            int ret = 0;
            if (actorUrl != null) {
                // #todo-optimization: we can call apub.getUserProperty() to get followersUrl right?
                APOActor actor = apUtil.getActorByUrl(ms, userMakingRequest, actorUrl);
                if (actor != null) {
                    String followersUrl = apStr(actor, APObj.followers);
                    APObj followers = getRemoteFollowers(ms, userMakingRequest, followersUrl);
                    if (followers == null) {
                        log.debug("Unable to get followers for AP user: " + followersUrl);
                    }
                    ret = apInt(followers, APObj.totalItems);
                }
            }
            return ret;
        }
    }

    public long countFollowersOfLocalUser(MongoSession ms, SubNode userNode, String userName) {
        Query q = getPeopleByUserName_query(ms, userNode, userName);
        if (q == null)
            return 0L;
        return ops.count(q, SubNode.class);
    }

    /* caller can pass userName only or else pass userNode if it's already available */
    public Query getPeopleByUserName_query(MongoSession ms, SubNode userNode, String userName) {
        Query q = new Query();

        if (userNode == null) {
            userNode = read.getUserNodeByUserName(ms, userName, false);
            if (userNode == null) {
                return null;
            }
        }

        Criteria crit = Criteria.where(SubNode.PATH).regex(mongoUtil.regexRecursiveChildrenOfPath(NodePath.USERS_PATH))
                .and(SubNode.PROPS + "." + NodeProp.USER_NODE_ID.s()).is(userNode.getIdStr()) //
                .and(SubNode.TYPE).is(NodeType.FRIEND.s());

        q.addCriteria(crit);
        return q;
    }
}
