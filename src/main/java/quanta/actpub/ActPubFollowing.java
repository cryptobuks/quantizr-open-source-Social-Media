package quanta.actpub;

import static quanta.actpub.model.AP.apInt;
import static quanta.actpub.model.AP.apObj;
import static quanta.actpub.model.AP.apStr;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.actpub.model.APOAccept;
import quanta.actpub.model.APOActivity;
import quanta.actpub.model.APOActor;
import quanta.actpub.model.APOFollow;
import quanta.actpub.model.APOOrderedCollection;
import quanta.actpub.model.APOOrderedCollectionPage;
import quanta.actpub.model.APOUndo;
import quanta.actpub.model.APObj;
import quanta.config.NodeName;
import quanta.config.ServiceBase;
import quanta.instrument.PerfMon;
import quanta.model.NodeInfo;
import quanta.model.client.ConstantInt;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.model.client.PrincipalName;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.GetFollowingRequest;
import quanta.response.GetFollowingResponse;
import quanta.util.ThreadLocals;
import quanta.util.Util;
import quanta.util.XString;

/**
 * Methods relating to AP following
 */
@Component
@Slf4j 
public class ActPubFollowing extends ServiceBase {
    @Autowired
    private ActPubLog apLog;

    /**
     * Send outbound message to foreign servers to follow/unfollow users
     * 
     * apUserName is full user name like alice@quantizr.com
     */
    public void setFollowing(String followerUserName, String apUserName, boolean following) {
        try {
            apLog.trace("Local Follower User (person doing the following): " + followerUserName + " setFollowing: " + apUserName
                    + "following=" + following);
            // admin doesn't follow/unfollow
            if (PrincipalName.ADMIN.s().equalsIgnoreCase(followerUserName)) {
                return;
            }

            arun.run(as -> {
                // try getting actor url from cache first
                String actorUrlOfUserBeingFollowed = apCache.actorUrlsByUserName.get(apUserName);

                // if not found in cache, get it the harder way.
                if (actorUrlOfUserBeingFollowed == null) {
                    actorUrlOfUserBeingFollowed =
                            apub.getUserProperty(as, followerUserName, apUserName, null, NodeProp.ACT_PUB_ACTOR_URL.s());

                    // if we got the actor url put it in the cache now.
                    if (actorUrlOfUserBeingFollowed != null) {
                        // are there othere places we can take advantage and load this cache, by chance? #todo-optimization
                        // (yes I looked, there's about 20ish other places we can take advantage of having both these and
                        // just cram into cache)
                        apCache.actorUrlsByUserName.put(apUserName, actorUrlOfUserBeingFollowed);
                    }
                }

                String sessionActorUrl = apUtil.makeActorUrlForUserName(followerUserName);

                // generate a bogus id follow id here. We don't need anything more
                APOFollow followAction =
                        new APOFollow(prop.getProtocolHostAndPort() + "/follow/" + String.valueOf(new Date().getTime()),
                                sessionActorUrl, actorUrlOfUserBeingFollowed);
                APObj action = null;

                // send follow action
                if (following) {
                    action = followAction;
                }
                // send unfollow action
                else {
                    action = new APOUndo(prop.getProtocolHostAndPort() + "/unfollow/" + String.valueOf(new Date().getTime()), //
                            sessionActorUrl, //
                            followAction);
                }

                // #todo-optimization: we can call apub.getUserProperty() to get toInbox right?
                APOActor toActor = apUtil.getActorByUrl(as, followerUserName, actorUrlOfUserBeingFollowed);
                if (toActor != null) {
                    String privateKey = apCrypto.getPrivateKey(as, followerUserName);
                    apUtil.securePostEx(apStr(toActor, APObj.inbox), privateKey, sessionActorUrl, action, APConst.MTYPE_LD_JSON_PROF);
                } else {
                    apLog.trace("Unable to get actor to post to: " + actorUrlOfUserBeingFollowed);
                }
                return null;
            });
        } catch (Exception e) {
            log.debug("Set following Failed.");
        }
    }

    /**
     * Follows or Unfollows users
     * 
     * Process inbound 'Follow' actions (comming from foreign servers). This results in the follower an
     * account node in our local DB created if not already existing, and then a FRIEND node under his
     * FRIEND_LIST created to represent the person he's following, if not already existing.
     * 
     * If 'unFollow' is true we actually do an unfollow instead of a follow.
     * 
     * This 'activity' can be either APOUndo or APOFollow
     */
    public void processFollowActivity(APOActivity activity) {
        boolean unFollow = activity instanceof APOUndo;

        Runnable runnable = () -> {
            arun.<APObj>run(as -> {
                try {
                    // #todo-optimization: we can call apub.getUserProperty() to get followerUserName right?
                    APOActor followerActor = apUtil.getActorByUrl(as, null, activity.getActor());
                    if (followerActor == null) {
                        apLog.trace("no followerActor object gettable from actor: " + activity.getActor());
                        return null;
                    }

                    log.debug("getLongUserNameFromActorUrl: " + activity.getActor()); // + "\n" +
                                                                                      // XString.prettyPrint(followerActor));
                    String followerUserName = apUtil.getLongUserNameFromActor(followerActor);

                    // this will lookup the user AND import if it's a non-existant user
                    SubNode followerAccountNode = apub.getAcctNodeByForeignUserName(as, null, followerUserName, false, true);
                    if (followerAccountNode == null) {
                        apLog.trace("unable to import user " + followerUserName);
                        throw new RuntimeException("Unable to get or import user: " + followerUserName);
                    }

                    apub.userEncountered(followerUserName, false);

                    String actorBeingFollowedUrl = null;

                    // Actor being followed (local to our server)
                    // Note: followObj CAN be a String here.
                    Object followObj = apObj(activity, APObj.object);

                    if (followObj == null) {
                        log.debug("Can't get followObj from: " + XString.prettyPrint(activity));
                        throw new RuntimeException("no followObj");
                    }

                    log.debug("followObj.type=" + followObj.getClass().getName());
                    if (followObj instanceof String) {
                        actorBeingFollowedUrl = (String) followObj;
                        log.debug("Got actorBeingFollowedUrl from direct object being a string.");
                    } else {
                        actorBeingFollowedUrl = apStr(followObj, APObj.id);
                        log.debug("Got actorBeingFollowedUrl from object.id");
                    }

                    log.debug("actorBeingFollowedUrl: " + actorBeingFollowedUrl);

                    if (actorBeingFollowedUrl == null) {
                        log.debug("failed to get actorBeingFollowed from this object: " + XString.prettyPrint(activity));
                        return null;
                    }

                    String userToFollow = apUtil.getLocalUserNameFromActorUrl(actorBeingFollowedUrl);
                    if (userToFollow == null) {
                        log.debug("unable to get a user name from actor url: " + actorBeingFollowedUrl);
                        return null;
                    }

                    // get the Friend List of the follower
                    SubNode followerFriendList = read.getUserNodeByType(as, followerUserName, null, null,
                            NodeType.FRIEND_LIST.s(), null, NodeName.FRIENDS);

                    /*
                     * lookup to see if this followerFriendList node already has userToFollow already under it
                     */
                    SubNode friendNode = read.findFriendNode(as, followerFriendList.getOwner(), null, userToFollow);

                    // if we have this node but in some obsolete path delete it. Might be the path of BLOCKED_USERS
                    if (friendNode != null && !mongoUtil.isChildOf(followerFriendList, friendNode)) {
                        delete.delete(as, friendNode);
                        friendNode = null;
                    }

                    if (friendNode == null) {
                        if (!unFollow) {
                            apLog.trace("unable to find user node by name: " + followerUserName + " so creating.");
                            friendNode = edit.createFriendNode(as, followerFriendList, userToFollow);
                            // userFeed.sendServerPushInfo(localUserName,
                            // new NotificationMessage("apReply", null, contentHtml, toUserName));
                        }
                    } else {
                        // if this is an unfollow delete the friend node
                        if (unFollow) {
                            delete.deleteNode(as, friendNode, false, true);
                        }
                    }

                    String _actorBeingFollowedUrl = actorBeingFollowedUrl;

                    // Now we send back to the server the Accept response, asynchronously
                    exec.run(() -> {
                        apLog.trace("Sending Follow Accepted.");
                        String privateKey = apCrypto.getPrivateKey(as, userToFollow);

                        // Try to give the server a bit of time, before sending back the accept/reject
                        Util.sleep(2000);

                        // Must send either Accept or Reject. Currently we auto-accept all.
                        APObj acceptPayload = unFollow ? new APOUndo(null, activity.getActor(), _actorBeingFollowedUrl) : //
                                new APOFollow();

                        /*
                         * todo-2: These parameters are definitely correct for 'Follow', but I need to verify for an 'undo'
                         * unfollow if they are acceptable (do this by letting both Pleroma AND Mastodon unfollow quanta
                         * users and see what the format of the message is sent from those).
                         */
                        acceptPayload.put(APObj.id, activity.getId());
                        acceptPayload.put(APObj.actor, activity.getActor());
                        acceptPayload.put(APObj.object, _actorBeingFollowedUrl);

                        APOAccept accept = new APOAccept(//
                                _actorBeingFollowedUrl, // actor
                                activity.getActor(), // to
                                // for now we generate bogus accepts
                                prop.getProtocolHostAndPort() + "/accepts/" + String.valueOf(new Date().getTime()), // id
                                acceptPayload); // object

                        log.debug("Sending Accept of Follow Request to inbox " + apStr(followerActor, APObj.inbox));

                        apUtil.securePostEx(apStr(followerActor, APObj.inbox), privateKey, _actorBeingFollowedUrl, accept,
                                APConst.MTYPE_LD_JSON_PROF);
                        log.debug("Secure post completed.");
                    });
                } catch (Exception e) {
                    log.error("Failed sending follow reply.", e);
                }
                return null;
            });
        };
        exec.run(runnable);
    }

    /**
     * Generates outbound following data
     */
    @PerfMon(category = "apFollowing")
    public APOOrderedCollection generateFollowing(String userDoingAction, String userName) {
        String url = prop.getProtocolHostAndPort() + APConst.PATH_FOLLOWING + "/" + userName;
        Long totalItems = getFollowingCount(userDoingAction, userName);

        APOOrderedCollection ret = new APOOrderedCollection(url, totalItems, url + "?page=true", //
                url + "?min_id=0&page=true");
        return ret;
    }

    /**
     * Generates one page of results for the outbound 'following' request
     */
    @PerfMon(category = "apFollowing")
    public APOOrderedCollectionPage generateFollowingPage(String userName, String minId) {
        List<String> following = getFollowing(userName, true, true, minId, false, null);

        // this is a self-reference url (id)
        String url = prop.getProtocolHostAndPort() + APConst.PATH_FOLLOWING + "/" + userName + "?page=true";
        if (minId != null) {
            url += "&min_id=" + minId;
        }

        APOOrderedCollectionPage ret = new APOOrderedCollectionPage(url, following,
                prop.getProtocolHostAndPort() + APConst.PATH_FOLLOWING + "/" + userName, following.size());
        return ret;
    }

    /* Calls saveFediverseName for each person who is a 'follower' of actor */
    public int loadRemoteFollowing(MongoSession ms, String userDoingAction, APOActor actor) {
        APObj followings = getFollowing(ms, userDoingAction, apStr(actor, APObj.following));
        if (followings == null) {
            log.debug("Unable to get followings for AP user: " + apStr(actor, APObj.following));
            return 0;
        }

        int ret = apInt(followings, APObj.totalItems);

        apUtil.iterateCollection(ms, userDoingAction, followings, Integer.MAX_VALUE, obj -> {
            try {
                // if (ok(obj )) {
                // log.debug("follower: OBJ=" + XString.prettyPrint(obj));
                // }

                if (obj instanceof String) {
                    String followingActorUrl = (String) obj;
                    apub.saveFediverseName(followingActorUrl);
                } else {
                    log.debug("Unexpected following item class: " + obj.getClass().getName());
                }

            } catch (Exception e) {
                log.error("Failed processing collection item.", e);
            }
            // always iterate all.
            return true;
        });
        return ret;
    }

    public APObj getFollowing(MongoSession ms, String userDoingAction, String url) {
        if (url == null)
            return null;

        APObj outbox = apUtil.getRemoteAP(ms, userDoingAction, url);
        // ActPubService.outboxQueryCount++;
        // ActPubService.cycleOutboxQueryCount++;
        apLog.trace("Following: " + XString.prettyPrint(outbox));
        return outbox;
    }

    /**
     * Returns following for LOCAL users only 'userName'. This doesn't use ActPub or query any remote
     * servers
     * 
     * Returns a list of all the 'actor urls' for all the users that 'userName' is following.
     * 
     * todo-1: do paging. Implement minId.
     */
    public List<String> getFollowing(String userName, boolean foreignUsers, boolean localUsers, String minId,
            boolean queueForRefresh, HashSet<ObjectId> blockedUserIds) {
        final List<String> following = new LinkedList<>();
        // log.debug("getFollowing of user: " + userName);

        arun.run(as -> {
            Iterable<SubNode> iter = findFollowingOfUser(as, userName);

            for (SubNode n : iter) {
                if (queueForRefresh && blockedUserIds != null && !blockedUserIds.contains(n.getId())) {
                    apub.queueUserForRefresh(n.getStr(NodeProp.USER), true);
                }

                // log.debug(" Found Friend Node: " + n.getIdStr());
                // if this Friend node is a foreign one it will have the actor url property
                String remoteActorId = n.getStr(NodeProp.ACT_PUB_ACTOR_ID);

                // this will be non-null if it's a remote account.
                if (remoteActorId != null) {
                    if (foreignUsers) {
                        following.add(remoteActorId);
                    }
                }
                // otherwise, it's a local user, and we know how to build the Actor URL of our own users.
                else {
                    if (localUsers) {
                        // the name on the account that owns the Friend node in his Friends List, is the "Follower"
                        String followingUserName = n.getStr(NodeProp.USER);

                        // sanity check that name doesn't contain '@' making it a foreign user.
                        if (!followingUserName.contains("@")) {
                            following.add(apUtil.makeActorUrlForUserName(followingUserName));
                        }
                    }
                }
            }
            return null;
        });
        return following;
    }

    public Long getFollowingCount(String userDoingAction, String userName) {
        return (Long) arun.run(as -> {
            Long count = countFollowingOfUser(as, userDoingAction, userName, null);
            return count;
        });
    }

    public GetFollowingResponse getFollowing(MongoSession ms, GetFollowingRequest req) {
        GetFollowingResponse res = new GetFollowingResponse();

        return arun.run(as -> {
            Query q = findFollowingOfUser_query(as, req.getTargetUserName());
            if (q == null)
                return null;

            q.limit(ConstantInt.ROWS_PER_PAGE.val());
            q.skip(ConstantInt.ROWS_PER_PAGE.val() * req.getPage());

            Iterable<SubNode> iterable = mongoUtil.find(q);
            List<NodeInfo> searchResults = new LinkedList<>();
            int counter = 0;

            for (SubNode node : iterable) {
                NodeInfo info = convert.convertToNodeInfo(false, ThreadLocals.getSC(), as, node, false, counter + 1, false, false,
                        false, false, false, false, null, false);
                if (info != null) {
                    searchResults.add(info);
                }
            }

            res.setSearchResults(searchResults);
            return res;
        });
    }

    /* Returns FRIEND nodes for every user 'userName' is following */
    public Iterable<SubNode> findFollowingOfUser(MongoSession ms, String userName) {
        Query q = findFollowingOfUser_query(ms, userName);
        if (q == null)
            return null;

        return mongoUtil.find(q);
    }

    public long countFollowingOfUser(MongoSession ms, String userDoingAction, String userName, String actorUrl) {
        // if local user
        if (userName.indexOf("@") == -1) {
            return countFollowingOfLocalUser(ms, userName);
        }
        // if foreign user
        else {
            /* Starting with just actorUrl, lookup the following count */
            int ret = 0;
            if (actorUrl != null) {
                // #todo-optimization: we can call apub.getUserProperty() to get 'following' prop right?
                APOActor actor = apUtil.getActorByUrl(ms, userDoingAction, actorUrl);
                if (actor != null) {
                    APObj following = getFollowing(ms, userDoingAction, apStr(actor, APObj.following));
                    if (following == null) {
                        log.debug("Unable to get followers for AP user: " + apStr(actor, APObj.following));
                    }
                    ret = apInt(following, APObj.totalItems);
                }
            }
            return ret;
        }
    }

    public long countFollowingOfLocalUser(MongoSession ms, String userName) {
        Query q = findFollowingOfUser_query(ms, userName);
        if (q == null)
            return 0;

        return ops.count(q, SubNode.class);
    }

    private Query findFollowingOfUser_query(MongoSession ms, String userName) {
        Query q = new Query();

        // get friends list node
        SubNode friendsListNode =
                read.getUserNodeByType(ms, userName, null, null, NodeType.FRIEND_LIST.s(), null, NodeName.FRIENDS);
        if (friendsListNode == null)
            return null;

        /*
         * query all the direct children under the friendsListNode, that are FRIEND type although they
         * should all be FRIEND types.
         */
        Criteria crit = Criteria.where(//
                SubNode.PATH).regex(mongoUtil.regexDirectChildrenOfPath(friendsListNode.getPath()))//
                .and(SubNode.TYPE).is(NodeType.FRIEND.s());

        q.addCriteria(crit);
        return q;
    }
}
