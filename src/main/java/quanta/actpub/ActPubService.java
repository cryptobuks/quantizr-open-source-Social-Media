package quanta.actpub;

import static quanta.actpub.model.AP.apAPObj;
import static quanta.actpub.model.AP.apBool;
import static quanta.actpub.model.AP.apDate;
import static quanta.actpub.model.AP.apList;
import static quanta.actpub.model.AP.apObj;
import static quanta.actpub.model.AP.apParseList;
import static quanta.actpub.model.AP.apStr;
import java.net.URL;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import quanta.actpub.model.AP;
import quanta.actpub.model.APList;
import quanta.actpub.model.APOAccept;
import quanta.actpub.model.APOActivity;
import quanta.actpub.model.APOActor;
import quanta.actpub.model.APOAnnounce;
import quanta.actpub.model.APODelete;
import quanta.actpub.model.APOHashtag;
import quanta.actpub.model.APOLike;
import quanta.actpub.model.APOMention;
import quanta.actpub.model.APOPerson;
import quanta.actpub.model.APOUndo;
import quanta.actpub.model.APObj;
import quanta.actpub.model.APType;
import quanta.config.NodeName;
import quanta.config.ServiceBase;
import quanta.exception.NodeAuthFailedException;
import quanta.instrument.PerfMon;
import quanta.model.client.APTag;
import quanta.model.client.Attachment;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.model.client.PrincipalName;
import quanta.model.client.PrivilegeType;
import quanta.mongo.CreateNodeLocation;
import quanta.mongo.MongoRepository;
import quanta.mongo.MongoSession;
import quanta.mongo.model.AccessControl;
import quanta.mongo.model.FediverseName;
import quanta.mongo.model.SubNode;
import quanta.util.Convert;
import quanta.util.DateUtil;
import quanta.util.ExUtil;
import quanta.util.ThreadLocals;
import quanta.util.Util;
import quanta.util.XString;
import quanta.util.val.Val;

/**
 * General AP functions
 */
@Component
@Slf4j
public class ActPubService extends ServiceBase {
    @Autowired
    private ActPubLog apLog;

    public static final boolean ENGLISH_LANGUAGE_CHECK = false;
    public static final int MAX_MESSAGES = 10;
    public static final int MAX_FOLLOWERS = 20;
    public static int outboxQueryCount = 0;
    public static int cycleOutboxQueryCount = 0;
    public static int newPostsInCycle = 0;
    public static int accountsRefreshed = 0;
    public static int refreshForeignUsersCycles = 0;
    public static int refreshForeignUsersQueuedCount = 0;
    public static String lastRefreshForeignUsersCycleTime = "n/a";
    public static int inboxCount = 0;
    public static boolean userRefresh = false;
    public static boolean refreshingForeignUsers = false;
    public static boolean scanningForeignUsers = false;
    public static int NUM_CURATED_ACCOUNTS = 1500;

    public void sendLikeMessage(MongoSession ms, String userDoingLike, SubNode node) {
        exec.run(() -> {
            SubNode likedAccount = read.getNode(ms, node.getOwner());

            String inbox = likedAccount.getStr(NodeProp.ACT_PUB_ACTOR_INBOX);
            if (inbox == null) {
                throw new RuntimeException("No inbox for owner of node: " + node.getIdStr());
            }

            // update cache just because we can
            apCache.inboxesByUserName.put(likedAccount.getStr(NodeProp.USER), inbox);

            // foreign ID of node being liked
            String apId = node.getStr(NodeProp.OBJECT_ID);

            // the like needs to go out at least to this actorId
            String toActorId = node.getStr(NodeProp.ACT_PUB_OBJ_ATTRIBUTED_TO);

            String fromActor = apUtil.makeActorUrlForUserName(userDoingLike);

            List<String> toUserNames = new LinkedList<String>();
            toUserNames.add(toActorId);

            APOLike message = apFactory.newLike(node.getIdStr() + "-like", apId, fromActor, toUserNames, null);
            // log.debug("Sending Like message: "+XString.prettyPrint(message));

            String privateKey = apCrypto.getPrivateKey(ms, userDoingLike);
            apUtil.securePostEx(inbox, privateKey, fromActor, message, APConst.MTYPE_LD_JSON_PROF);
        });
    }

    /*
     * When 'node' has been created under 'parent' (by the sessionContext user) this will send a
     * notification to foreign servers. This call returns immediately and delegates the actual
     * proccessing to a daemon thread.
     * 
     * For concurrency reasons, note that we pass in the nodeId to this method rather than the node even
     * if we do have the node, because we want to make sure there's no concurrent access.
     * 
     * IMPORTANT: This method ONLY sends notifications to users who ARE in the 'acl' which means these
     * can only be users ALREADY imported into the system, however this is ok, because we will have
     * called saveMentionsToNodeACL() right before calling this method so the 'acl' should completely
     * contain all the mentions that exist in the text of the message.
     */
    public void sendObjOutbound(MongoSession ms, SubNode parent, SubNode node, boolean forceSendToPublic) {
        // log.debug("sendObjOutbound: " + XString.prettyPrint(node));

        exec.run(() -> {
            try {
                boolean isAccnt = node.isType(NodeType.ACCOUNT);
                String inReplyTo = !isAccnt ? apFactory.makeForeignInReplyTo(ms, node.getStr(NodeProp.INREPLYTO), parent) : null;
                APList attachments = !isAccnt ? apub.createAttachmentsList(node) : null;
                String boostTarget = node.getStr(NodeProp.BOOST);

                // toUserNames will hold ALL usernames in the ACL list (both local and foreign user names)
                HashSet<String> toUserNames = new HashSet<>();
                boolean privateMessage = true;

                if (forceSendToPublic) {
                    privateMessage = false;
                } else {
                    if (node.getAc() != null) {
                        /*
                         * Lookup all userNames from the ACL info, to add them all to 'toUserNames'
                         */
                        for (String accntId : node.getAc().keySet()) {
                            if (PrincipalName.PUBLIC.s().equals(accntId)) {
                                privateMessage = false;
                            } else {
                                SubNode accntNode = cachedGetAccntNodeById(ms, accntId, false, null);

                                // get username off this node and add to 'toUserNames'
                                if (accntNode != null) {
                                    toUserNames.add(accntNode.getStr(NodeProp.USER));
                                }
                            }
                        }
                    }
                }

                // String apId = parent.getStringProp(NodeProp.OBJECT_ID.s());
                String fromUser = ThreadLocals.getSC().getUserName();
                String fromActor = apUtil.makeActorUrlForUserName(fromUser);
                String privateKey = apCrypto.getPrivateKey(ms, fromUser);
                String objUrl = snUtil.getIdBasedUrl(node);

                APObj message = null;

                if (node.isType(NodeType.ACCOUNT)) {
                    // construct the Update-type wrapper around teh Person object, and send
                    message = apFactory.newUpdateForPerson(fromUser, toUserNames, fromActor, privateMessage, node);
                    log.debug("Sending updated Person outbound: " + XString.prettyPrint(message));

                } else {
                    /*
                     * if this node has a boostTarget, we know it's an Announce so we send out the announce
                     * 
                     * todo-2: we should probably rely on if there's an ActPub TYPE itself that's "Announce" (we save
                     * that right?) UPDATE: Yes we do like this: on our node: "p" : {"ap:objType" : "Announce",
                     */
                    if (!StringUtils.isEmpty(boostTarget)) {
                        SubNode boostTargetNode = read.getNode(ms, boostTarget);
                        if (boostTargetNode != null) {
                            String boostedId = boostTargetNode.getStr(NodeProp.OBJECT_ID);

                            // What we're doing here is if this is not a foreign node being boosted we return
                            // a URL pointing to our local node.
                            if (boostedId == null) {
                                boostedId = prop.getProtocolHostAndPort() + "?id=" + boostTarget;
                            }

                            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
                            message = apFactory.newAnnounce(fromUser, fromActor, objUrl, toUserNames, boostedId, now,
                                    privateMessage);

                            // log.debug("Outbound Announce: " + XString.prettyPrint(message));
                        }
                    }
                    // else send out as a note.
                    else {
                        String content = Convert.replaceTagsWithHtml(node, false);
                        if (content == null) {
                            content = node.getContent();
                        }

                        String repliesUrl = prop.getProtocolHostAndPort() + APConst.PATH_REPLIES + "/" + node.getIdStr();
                        message = apFactory.newCreateForNote(fromUser, toUserNames, fromActor, inReplyTo, content, objUrl,
                                repliesUrl, privateMessage, attachments);

                        // log.debug("Outbound Note: " + XString.prettyPrint(message));
                    }
                }

                // for users that don't have a sharedInbox we collect their inboxes here to send to them
                // individually
                HashSet<String> userInboxes = new HashSet<>();

                // When posting a public message we send out to all unique sharedInboxes here
                if (!privateMessage) {
                    HashSet<String> sharedInboxes = new HashSet<>();

                    // loads ONLY foreign user's inboxes into the two sets.
                    getSharedInboxesOfFollowers(fromUser, sharedInboxes, userInboxes);

                    // merge both sets of inboxes into allInboxes and send to them
                    HashSet<String> allInboxes = new HashSet<>(userInboxes);
                    allInboxes.addAll(sharedInboxes);
                    apUtil.securePostEx(allInboxes, fromActor, privateKey, fromActor, message, APConst.MTYPE_LD_JSON_PROF);
                }

                // Post message to all foreign usernames found in 'toUserNames', but skip all in userInboxes because
                // we just sent to those above.
                if (toUserNames.size() > 0) {
                    sendMessageToUsers(ms, toUserNames, fromUser, message, privateMessage, userInboxes);
                }
            } //
            catch (Exception e) {
                log.error("sendNote failed", e);
                throw new RuntimeException(e);
            }
        });
    }

    public SubNode cachedGetAccntNodeById(MongoSession ms, String id, boolean allowAuth, Val<SubNode> accntNode) {
        // try to get the node from the cache first
        SubNode node = apCache.acctNodesById.get(id);

        // if not in cache find the node from the DB and ADD to the cache.
        if (node == null) {
            node = read.getNode(ms, id, allowAuth, accntNode);
            if (node != null) {
                apCache.acctNodesById.put(id, node);
            }
        }
        return node;
    }

    public void sendNodeDelete(MongoSession ms, String actPubId, HashMap<String, AccessControl> acl) {
        // if no sharing bail out.
        if (acl == null || acl.isEmpty())
            return;

        exec.run(() -> {
            try {
                HashSet<String> toUserNames = new HashSet<>();
                boolean privateMessage = true;

                /*
                 * Lookup all userNames from the ACL info, to add them all to 'toUserNames'
                 */
                for (String accntId : acl.keySet()) {
                    if (PrincipalName.PUBLIC.s().equals(accntId)) {
                        privateMessage = false;
                    } else {
                        // try to get account node from cache
                        SubNode accntNode = cachedGetAccntNodeById(ms, accntId, true, null);

                        // get username off this node and add to 'toUserNames'
                        if (accntNode != null) {
                            String userName = accntNode.getStr(NodeProp.USER);
                            toUserNames.add(userName);
                        }
                    }
                }

                // String apId = parent.getStringProp(NodeProp.OBJECT_ID.s());
                String fromUser = ThreadLocals.getSC().getUserName();
                String fromActor = apUtil.makeActorUrlForUserName(fromUser);
                String privateKey = apCrypto.getPrivateKey(ms, fromUser);
                APObj message = apFactory.newDeleteForNote(actPubId, fromActor);

                // for users that don't have a sharedInbox we collect their inboxes here to send to them
                // individually
                HashSet<String> userInboxes = new HashSet<>();

                // When posting a public message we send out to all unique sharedInboxes here
                if (!privateMessage) {
                    HashSet<String> sharedInboxes = new HashSet<>();
                    getSharedInboxesOfFollowers(fromUser, sharedInboxes, userInboxes);

                    // merge both sets of inboxes into allInboxes and send to them
                    HashSet<String> allInboxes = new HashSet<>(userInboxes);
                    allInboxes.addAll(sharedInboxes);
                    apUtil.securePostEx(allInboxes, fromActor, privateKey, fromActor, message, APConst.MTYPE_LD_JSON_PROF);
                }

                // Post message to all foreign usernames found in 'toUserNames'
                if (toUserNames.size() > 0) {
                    sendMessageToUsers(ms, toUserNames, fromUser, message, privateMessage, userInboxes);
                }
            } //
            catch (Exception e) {
                log.error("sendDelete failed", e);
                throw new RuntimeException(e);
            }
        });
    }

    /*
     * Finds all the foreign server followers of userName, and returns the unique set of sharedInboxes
     * of them all.
     */
    public void getSharedInboxesOfFollowers(String userName, HashSet<String> sharedInboxes, HashSet<String> userInboxes) {
        // This query gets the FRIEND nodes that specify userName on them
        Query q = arun.run(as -> apFollower.getPeopleByUserName_query(as, null, userName));
        if (q == null)
            return;

        Iterable<SubNode> iterator = mongoUtil.find(q);
        for (SubNode node : iterator) {
            // log.debug("follower: " + XString.prettyPrint(node));
            /*
             * Note: The OWNER of this FRIEND node is the person doing the follow, so we look up their account
             * node which is in node.ownerId
             */
            SubNode followerAccount = arun.run(as -> read.getNode(as, node.getOwner()));
            if (followerAccount != null) {
                String followerUserName = followerAccount.getStr(NodeProp.USER);

                // if this is a foreign user...
                if (followerUserName.contains("@")) {
                    String sharedInbox = followerAccount.getStr(NodeProp.ACT_PUB_SHARED_INBOX);
                    if (sharedInbox != null) {
                        // log.debug("SharedInbox: " + sharedInbox);
                        sharedInboxes.add(sharedInbox);
                    }
                    // not all users have a shared inbox, and the ones that don't we collect here...
                    else {
                        String inbox = followerAccount.getStr(NodeProp.ACT_PUB_ACTOR_INBOX);
                        if (inbox != null) {
                            userInboxes.add(inbox);

                            // update cache just because we can
                            apCache.inboxesByUserName.put(followerAccount.getStr(NodeProp.USER), inbox);
                        }
                    }
                }
            }
        }
    }

    public APList createAttachmentsList(SubNode node) {
        APList attachments = null;

        List<Attachment> atts = node.getOrderedAttachments();
        if (atts != null) {
            attachments = new APList();

            for (Attachment att : atts) {
                if (att.getBin() != null && att.getMime() != null) {
                    attachments.val(new APObj() //
                            .put(APObj.type, APType.Document) //
                            .put(APObj.mediaType, att.getMime()) //
                            /*
                             * NOTE: The /f/id endpoint is intentionally wide open, but only for nodes that have at least some
                             * sharing meaning they can be visible to at least someone other than it's owner.
                             */
                            .put(APObj.url, prop.getProtocolHostAndPort() + "/f/id/" + node.getIdStr() + "?att=" + att.getKey()));
                }
            }
        }
        return attachments;
    }

    /*
     * Gets the property from the userName's account node or else resorts to calling webFinger+actorObj
     * from web if not found locally. You must supply the actorObject property (apProp) as well as the
     * SubNode property val name (nodeProp) to retrieve the value, because we might get it from either
     * place.
     * 
     * For special case of retrieving actorUrl we pass apProp=null, and
     * nodeProp=NodeProp.ACT_PUB_ACTOR_URL.s()
     */
    public String getUserProperty(MongoSession ms, String userDoingAction, String userName, String apProp, String nodeProp) {
        // First try to get inbox for toUserName by looking for it the DB (with allowImport=false, to NOT
        // import)
        String val = (String) arun.run(as -> {
            String ival = null;
            SubNode accntNode = apub.getAcctNodeByForeignUserName(as, userDoingAction, userName, true, false);
            if (accntNode != null) {
                ival = accntNode.getStr(nodeProp);
                if (ival != null) {
                    log.debug("FOUND " + nodeProp + " IN DB: " + ival);
                }
            }
            return ival;
        });

        /*
         * if this inbox is null here, it just means we haven't imported the user into Quanta and that is
         * fine, and we don't want to import them now either when all that's happening is they're being sent
         * a message, so in this case access the user from scratch by getting webFinger, actorObject, etc.
         */
        if (val == null) {
            // Ignore userNames that are for our own host
            String userHost = apUtil.getHostFromUserName(userName);
            if (userHost.equals(prop.getMetaHost())) {
                throw new RuntimeException("not foreign user.");
            }

            // log.debug("to Foreign User: " + toUserName);
            APObj webFinger = apUtil.getWebFinger(ms, userDoingAction, userName);
            if (webFinger == null) {
                apLog.trace("Unable to get webfinger for " + userName);
                throw new RuntimeException("unable to get webFinger");
            }

            String toActorUrl = apUtil.getActorUrlFromWebFingerObj(webFinger);

            // special case if we need only ActorUrl return now.
            if (nodeProp.equals(NodeProp.ACT_PUB_ACTOR_URL.s())) {
                return toActorUrl;
            }

            APOActor toActorObj = apUtil.getActorByUrl(ms, userDoingAction, toActorUrl);
            if (toActorObj != null) {
                // log.debug(" actor: " + toActorUrl);
                val = apStr(toActorObj, apProp);
            }
        }
        return val;
    }

    /*
     * Sends message outbound to other inboxes, for all inboxes corresponding to all 'toUserNames'
     * IMPORTANT: This method doesn't require or expect 'toUserNames' to have been 'imported' into
     * Quanta. That is, there may not even BE a "user node" for any of these users, or there may be. We
     * don't know or care in here, because we go straight to the WebFinger and build up their outbox
     * "live" from the WebFinger in realtime.
     * 
     * skipInboxes is a way to know which inboxes we've already sent to, and to not send again
     */
    public void sendMessageToUsers(MongoSession ms, HashSet<String> toUserNames, String fromUser, APObj message,
            boolean privateMessage, HashSet<String> skipInboxes) {
        if (toUserNames == null)
            return;
        String fromActor = null;

        /*
         * Post the same message to all the inboxes that need to see it
         */
        for (String toUserName : toUserNames) {
            // Ignore userNames that are not foreign server names
            if (!toUserName.contains("@")) {
                continue;
            }

            String inbox = apCache.inboxesByUserName.get(toUserName);

            if (inbox == null) {
                try {
                    inbox = getUserProperty(ms, fromUser, toUserName, APObj.inbox, NodeProp.ACT_PUB_ACTOR_INBOX.s());
                }
                // by design, we continue here. Yes this is correct.
                catch (Exception e) {
                    continue;
                }
            }

            if (!StringUtils.isEmpty(inbox)) {
                /*
                 * regardless of how we ended up getting 'inbox' here we cache it by userName, so that future calls
                 * to this method to send them more messages will be lightning fast (from memory)
                 */
                apCache.inboxesByUserName.put(toUserName, inbox);

                // send post if inbox not in skipInboxes
                if (!skipInboxes.contains(inbox)) {
                    /* lazy create fromActor here */
                    if (fromActor == null) {
                        fromActor = apUtil.makeActorUrlForUserName(fromUser);
                    }

                    String userDoingPost = ThreadLocals.getSC().getUserName();
                    // log.debug("Posting object:\n" + XString.prettyPrint(message) + "\n to inbox: " + inbox);
                    String privateKey = apCrypto.getPrivateKey(ms, userDoingPost);
                    apUtil.securePostEx(inbox, privateKey, fromActor, message, APConst.MTYPE_LD_JSON_PROF);
                }
            }
        }
    }

    /**
     * Gets the account SubNode representing foreign user apUserName (like someuser@fosstodon.org), by
     * first checking the 'acctNodesByUserName' cache, or else by reading in the user from, the database
     * (if preferDbNode==true) or else the we read from the Fediverse, and updating the cache.
     */
    public SubNode getAcctNodeByForeignUserName(MongoSession ms, String userDoingAction, String apUserName, boolean preferDbNode,
            boolean allowImport) {
        apUserName = XString.stripIfStartsWith(apUserName, "@");
        if (!apUserName.contains("@")) {
            log.debug("Invalid foreign user name: " + apUserName);
            return null;
        }
        saveFediverseName(apUserName);

        // return from cache if we already have the value cached
        SubNode acctNode = apCache.acctNodesByUserName.get(apUserName);
        if (acctNode != null) {
            return acctNode;
        }

        if (preferDbNode) {
            acctNode = read.getUserNodeByUserName(ms, apUserName, false);
        }

        if (acctNode == null && allowImport) {
            /* First try to get a cached actor APObj */
            APOActor actor = apCache.actorsByUserName.get(apUserName);

            // if we have actor object skip the step of getting it and import using it.
            if (actor != null) {
                acctNode = importActor(ms, null, actor);
            }
        }

        /*
         * if we were unable to get the acctNode, then we need to read it from scratch meaning starting at
         * the very beginning which is to get webFinger first and load from there
         */
        if (acctNode == null && allowImport) {
            log.debug("Load:" + apUserName);
            APObj webFinger = apUtil.getWebFinger(ms, userDoingAction, apUserName);

            if (webFinger != null) {
                String actorUrl = apUtil.getActorUrlFromWebFingerObj(webFinger);
                if (actorUrl != null) {
                    acctNode = getAcctNodeByActorUrl(ms, userDoingAction, actorUrl);
                }
            }
        }

        // if we got the SubNode, cache it before returning it.
        if (acctNode != null) {
            // Any time we have an account node being cached we should cache it by it's ID too right away.
            apCache.acctNodesById.put(acctNode.getIdStr(), acctNode);
            apCache.acctNodesByUserName.put(apUserName, acctNode);
        } else {
            // it's only an error if we were allowing an import, otherwise a null return is *not* an error, but
            // a normal flow.
            if (allowImport) {
                log.error("Unable to load user: " + apUserName);
            }
        }
        return acctNode;
    }

    /**
     * Gets foreign account SubNode using actorUrl, using the cached copy of found.
     */
    @PerfMon(category = "apub")
    public SubNode getAcctNodeByActorUrl(MongoSession ms, String userDoingAction, String actorUrl) {
        saveFediverseName(actorUrl);

        /* return node from cache if already cached */
        SubNode acctNode = apCache.acctNodesByActorUrl.get(actorUrl);
        if (acctNode != null) {
            return acctNode;
        }

        APOActor actor = apUtil.getActorByUrl(ms, userDoingAction, actorUrl);

        // if webfinger was successful, ensure the user is imported into our system.
        if (actor != null) {
            acctNode = importActor(ms, null, actor);
            if (acctNode != null) {
                // Any time we have an account node being cached we should cache it by it's ID too right away.
                apCache.acctNodesById.put(acctNode.getIdStr(), acctNode);
                apCache.acctNodesByActorUrl.put(actorUrl, acctNode);
            }
        }
        return acctNode;
    }

    /*
     * Returns account node of the user, creating one if not already existing. If the userNode already
     * exists and the caller happens to have it we can pass in as non-null userNode and it will get
     * used.
     */
    @PerfMon(category = "apub")
    public SubNode importActor(MongoSession ms, SubNode userNode, APOActor actor) {

        // if userNode unknown then get and/or create one. May be creating a brand new one even.
        if (userNode == null) {
            String apUserName = apUtil.getLongUserNameFromActor(actor).trim();

            // This checks for both the non-port and has-port versions of the host (host may or may not have
            // port)
            if (apUserName.endsWith("@" + prop.getMetaHost().toLowerCase())
                    || apUserName.contains("@" + prop.getMetaHost().toLowerCase() + ":")) {
                log.debug("Can't import a user that's not from a foreign server.");
                return null;
            }
            apLog.trace("importing Actor: " + apUserName);

            saveFediverseName(apUserName);

            // Try to get the userNode for this actor
            userNode = read.getUserNodeByUserName(ms, apUserName);

            /*
             * If we don't have this user in our system, create them.
             */
            if (userNode == null) {
                userNode = mongoUtil.createUser(ms, apUserName, null, null, true, null, false);
            }
        }

        if (apUtil.updateNodeFromActorObject(userNode, actor)) {
            update.save(ms, userNode, false);
        }

        /* cache the account node id for this user by the actor url */
        String selfRef = apStr(actor, APObj.id); // actor url of 'actor' object, is the same as the 'id'
        apCache.acctIdByActorUrl.put(selfRef, userNode.getIdStr());
        return userNode;
    }

    /*
     * Processes incoming INBOX requests for (Follow, Undo Follow), to be called by foreign servers to
     * follow a user on this server
     */
    @PerfMon(category = "apub")
    public void processInboxPost(HttpServletRequest httpReq, byte[] body) {
        APObj payload = apUtil.buildObj(body);
        apLog.trace("INBOX: " + XString.prettyPrint(payload));
        if (payload.getType() == null)
            return;
        /*
         * todo-1: verify that for follow types the actorUrl can be obtained also from payload, rather than
         * only payload.actor===payload.obj.actor, but I think they should be the SAME for a follow action
         */
        if (payload.getActor() == null) {
            log.error("no 'actor' found on payload: " + XString.prettyPrint(payload));
            throw new RuntimeException("No actor on payload");
        }

        Val<String> keyEncoded = new Val<>();
        if (!apCrypto.verifySignature(httpReq, payload, payload.getActor(), body, keyEncoded)) {
            throw new RuntimeException("Signature check fail: " + XString.prettyPrint(payload));
        }

        /* IMPORTANT: The ActivityPub calls these Activities */
        switch (payload.getType()) {
            case APType.Create:
            case APType.Update:
                /*
                 * todo-1: I'm waiting for a way to test what the inbound call looks like for an Update, before
                 * coding the outbound call but don't know of any live instances that support it yet.
                 */
                processCreateOrUpdateActivity(httpReq, (APOActivity) payload, body, keyEncoded);
                break;

            case APType.Follow:
                apFollowing.processFollowActivity((APOActivity) payload);
                break;

            case APType.Undo:
                processUndoActivity(httpReq, (APOUndo) payload, body);
                break;

            case APType.Delete:
                processDeleteActivity(httpReq, (APODelete) payload, body, keyEncoded);
                break;

            case APType.Accept:
                processAcceptActivity((APOAccept) payload);
                break;

            case APType.Like:
                processLikeActivity(httpReq, (APOLike) payload, body);
                break;

            case APType.Announce:
                processAnnounceActivity((APOAnnounce) payload, body);
                break;

            default:
                log.debug("Unsupported inbox type:" + XString.prettyPrint(payload));
                break;
        }
    }

    /* Process inbound undo actions (coming from foreign servers) */
    @PerfMon(category = "apub")
    public void processUndoActivity(HttpServletRequest httpReq, APOUndo activity, byte[] bodyBytes) {
        APObj obj = apAPObj(activity, APObj.object);
        apLog.trace("Undo Type: " + obj.getType());
        switch (obj.getType()) {
            case APType.Follow:
                apFollowing.processFollowActivity(activity);
                break;

            case APType.Like:
                processLikeActivity(httpReq, activity, bodyBytes);
                break;

            case APType.Announce:
                processAnnounceActivity(activity, bodyBytes);
                break;

            default:
                log.debug("Unsupported Undo payload object type:" + XString.prettyPrint(obj));
                break;
        }
    }

    @PerfMon(category = "apub")
    public void processAcceptActivity(APOAccept activity) {
        APObj obj = activity.getAPObj();
        apLog.trace("Accept Type: " + obj.getType());
        switch (obj.getType()) {
            case APType.Follow:
                apLog.trace("Nothing to do for Follow Acceptance. no op.");
                break;

            default:
                log.debug("Unsupported payload object type:" + XString.prettyPrint(obj));
                break;
        }
    }

    /* action will be APType.Create or APType.Update */
    @PerfMon(category = "apub")
    public void processCreateOrUpdateActivity(HttpServletRequest httpReq, APOActivity activity, byte[] bodyBytes,
            Val<String> keyEncoded) {
        arun.run(as -> {
            apLog.trace("processCreateOrUpdateAction");

            APObj object = activity.getAPObj();
            apLog.trace("create type: " + object.getType());

            switch (object.getType()) {
                case APType.Video:
                case APType.Note:
                    createOrUpdateObj(as, activity, keyEncoded.getVal());
                    break;

                case APType.Person:
                    // we can safely cast to APOActor here because getAPObj returns the proper type object.
                    processUpdatePerson(as, (APOActor) object, keyEncoded.getVal());
                    break;

                default:
                    // this captures videos? and other things (todo-1: add more support)
                    // not showing quesitons. they eat up too much log space.
                    if (!"Question".equals(object.getType())) {
                        log.debug("Unhandled Action: " + activity.getType() + "  type=" + object.getType() + "\n"
                                + XString.prettyPrint(activity));
                    }
                    break;
            }
            return null;
        });
    }

    @PerfMon(category = "apub")
    public void processLikeActivity(HttpServletRequest httpReq, APOActivity activity, byte[] bodyBytes) {
        boolean unlike = activity instanceof APOUndo;
        arun.<Object>run(as -> {
            apLog.trace("process " + (unlike ? "unlike" : "like"));

            String objectIdUrl = apStr(activity, APObj.object);

            if (objectIdUrl == null) {
                log.debug("Unable to get object from payload: " + XString.prettyPrint(activity));
                return null;
            }

            // For now we don't maintain likes on nodes that aren't native to Quanta.
            if (!objectIdUrl.startsWith(prop.getProtocolHostAndPort())) {
                log.debug("Ignoring 'like' on foreign node: " + objectIdUrl);
                return null;
            }

            String userDoingAction = ThreadLocals.getSC().getUserName();
            SubNode actorAccntNode = apub.getAcctNodeByActorUrl(as, userDoingAction, activity.getActor());
            if (actorAccntNode == null) {
                throw new RuntimeException("actor not found.");
            }
            String actorUserName = actorAccntNode.getStr(NodeProp.USER);

            // Our objects are identified like this: "https://quanta.wiki?id=6277120c1363dc5d1fb426b5"
            // So by chopping after last '=' we can get the ID part.
            String nodeId = XString.parseAfterLast(objectIdUrl, "=");
            SubNode node = read.getNode(as, nodeId);
            if (node == null) {
                throw new RuntimeException("Unable to find node: " + nodeId);
            }
            if (node.getLikes() == null) {
                node.setLikes(new HashSet<>());
            }

            if (unlike) {
                node.removeLike(actorUserName);
            } else {
                node.addLike(actorUserName);
            }
            return null;
        });
    }

    @PerfMon(category = "apub")
    public void processAnnounceActivity(APOActivity activity, byte[] bodyBytes) {
        boolean undo = activity instanceof APOUndo;
        arun.<Object>run(as -> {
            apLog.trace("process " + (undo ? "unannounce" : "announce") + " Payload=" + XString.prettyPrint(activity));

            // if this is an undo operation we just delete the node and we're done.
            if (undo) {
                delete.deleteByPropVal(as, NodeProp.OBJECT_ID.s(), activity.getId());
                return null;
            }

            // get the url of the thing being boosted
            String objectIdUrl = apStr(activity, APObj.object);
            if (objectIdUrl == null) {
                log.debug("Unable to get object from payload: " + XString.prettyPrint(activity));
                return null;
            }

            // find or create an actual node that will hold the target (i.e. thing being boosted)
            SubNode boostedNode = apUtil.loadObject(as, null, objectIdUrl);
            if (boostedNode != null) {
                // log.debug("BOOSTING: " + XString.prettyPrint(boostedNode));

                // get account node for person doing the boosting
                SubNode actorAccountNode = getAcctNodeByActorUrl(as, null, activity.getActor());
                if (actorAccountNode != null) {
                    String userName = actorAccountNode.getStr(NodeProp.USER);

                    // get posts node which will be parent we save boost into
                    SubNode postsNode = read.getUserNodeByType(as, userName, actorAccountNode, "### Posts",
                            NodeType.ACT_PUB_POSTS.s(), Arrays.asList(PrivilegeType.READ.s()), NodeName.POSTS);

                    saveInboundForeignObj(as, null, actorAccountNode, postsNode, activity, APType.Announce,
                            boostedNode.getIdStr(), null, true, null);
                }
            } else {
                log.debug("Unable to get node being boosted.");
            }

            return null;
        });
    }

    @PerfMon(category = "apub")
    public void processDeleteActivity(HttpServletRequest httpReq, APODelete activity, byte[] bodyBytes, Val<String> keyEncoded) {
        arun.<Object>run(as -> {
            apLog.trace("processDeleteAction");

            Object object = activity.getObject();
            String id = null;

            // if the object to be deleted is specified as a string, assume it's the ID.
            if (object instanceof String) {
                id = (String) object;
            }
            // otherwise we assume it's an object, with an ID in it.
            else {
                id = apStr(object, APObj.id);
            }

            if (id == null) {
                log.debug("Unable to get actorId for use in processDeleteAction: payload=" + XString.prettyPrint(activity));
                return null;
            }

            // find node user wants to delete
            SubNode delNode = read.findNodeByProp(as, NodeProp.OBJECT_ID.s(), id);
            if (delNode == null)
                return null;

            // verify the user doing the delete is the owner of the node, before deleting.
            if (apCrypto.ownerHasKey(as, delNode, keyEncoded.getVal())) {
                // run subgraph delete asynchronously
                exec.run(() -> {
                    delete.deleteSubGraphChildren(as, delNode, true);
                });
            } else {
                log.debug("key match fail. rejecting attempt to delete node: " + XString.prettyPrint(delNode)
                        + "   \ninbound payload: " + XString.prettyPrint(activity));
            }
            return null;
        });
    }

    @PerfMon(category = "apub")
    public void processUpdatePerson(MongoSession as, APOActor actor, String encodedKey) {
        apLog.trace("processUpdatePerson");
        if (!as.isAdmin())
            throw new NodeAuthFailedException();

        SubNode actorAccnt = read.findNodeByProp(as, NodeProp.ACT_PUB_ACTOR_ID.s(), actor.getId());
        if (actorAccnt == null) {
            log.debug("user not found: " + actor.getId());
            return;
        }
        log.debug("got Actor: " + actorAccnt.getIdStr());

        if (!encodedKey.equals(actorAccnt.getStr(NodeProp.ACT_PUB_KEYPEM))) {
            throw new RuntimeException("wrong public key");
        }

        // make sure node is for a foreign user account
        if (!actorAccnt.getStr(NodeProp.USER).contains("@")) {
            throw new RuntimeException("Denied modify of Person (for non-remote account).");
        }

        // just paranoia here. check if this node is admin owned.
        if (actorAccnt.getOwner().equals(as.getUserNodeId())) {
            throw new RuntimeException("Access denied.");
        }

        // and finally if we reach here, put all the properties onto the SubNode and save it.
        if (apUtil.updateNodeFromActorObject(actorAccnt, actor)) {
            update.save(as, actorAccnt, false);
            log.debug("Updated Person from ActPub: NodeId=" + actorAccnt.getIdStr());
        }
    }

    @PerfMon(category = "apub")
    public void createOrUpdateObj(MongoSession as, APOActivity activity, String encodedKey) {
        if (!as.isAdmin())
            throw new NodeAuthFailedException();
        apLog.trace("createOrUpdateObj");

        // obj is the 'Note' or 'Video' object, or other payload type.
        APObj obj = activity.getAPObj();

        /*
         * If this is a 'reply' post then parse the ID out of this, and if we can find that node by that id
         * then insert the reply under that, instead of the default without this id which is to put in
         * 'inbox'
         */
        String inReplyTo = apStr(obj, APObj.inReplyTo);
        String replyToId = null;

        /* This will say null unless inReplyTo is used to get an id to lookup */
        SubNode nodeBeingRepliedTo = null;

        /*
         * Detect if inReplyTo is formatted like this: 'https://quanta.wiki?id=xxxxx' (proprietary URL
         * format for this server) and if so lookup the nodeBeingRepliedTo by using that nodeId
         */
        if (apUtil.isLocalUrl(inReplyTo)) {
            // todo-1: need a cleaner way to parse "id" out of this url.
            int lastIdx = inReplyTo.lastIndexOf("=");
            if (lastIdx != -1) {
                replyToId = inReplyTo.substring(lastIdx + 1);

                // DO NOT DELETE (THIS MAY COME BACK)
                // by removing this line, we change the design to where inbound replies always go into user's POSTS
                // node, rather than underneath the node they're replying to. I'm leaving this line here in case
                // there
                // are scenarios in the future where we might want this old functionality back where replies go in
                // to
                // subnodes under the thing bring replied to,.
                // nodeBeingRepliedTo = read.getNode(as, replyToId, false, null);
            }
        }

        /*
         * If a foreign user is replying to a specific node, we put the reply under that node
         * 
         * NOTE: yes this is code path is intentionally disabled by commenting out the setting of
         * nodeBeingRepliedTo above (see note above: do not delete this dead block of code)
         */
        if (nodeBeingRepliedTo != null) {
            apLog.trace("foreign actor replying to a quanta node.");
            saveInboundForeignObj(as, null, null, nodeBeingRepliedTo, obj, activity.getType(), null, encodedKey, true, replyToId);
        }
        /*
         * Otherwise the node is not a reply so we put it under POSTS node inside the foreign account node
         * on our server, and then we add 'sharing' to it for each person in the 'to/cc' so that this new
         * node will show up in those people's FEEDs
         */
        else {
            apLog.trace("not reply to existing Quanta node.");
            // get actor's account node from their actorUrl
            SubNode actorAccountNode = getAcctNodeByActorUrl(as, null, activity.getActor());
            if (actorAccountNode != null) {
                String userName = actorAccountNode.getStr(NodeProp.USER);
                SubNode postsNode = read.getUserNodeByType(as, userName, actorAccountNode, "### Posts",
                        NodeType.ACT_PUB_POSTS.s(), Arrays.asList(PrivilegeType.READ.s()), NodeName.POSTS);
                saveInboundForeignObj(as, null, actorAccountNode, postsNode, obj, activity.getType(), null, encodedKey, true,
                        replyToId);
            }
        }
    }

    /*
     * Saves inbound note (or CharMessage) comming from other foreign servers
     * 
     * todo-1: when importing users in bulk (like at startup or the admin menu), some of these queries
     * in here will be redundant. Look for ways to optimize.
     * 
     * temp = true, means we are loading an outbox of a user and not recieving a message specifically to
     * a local user so the node should be considered 'temporary' and can be deleted after a week or so
     * to clean the Db.
     * 
     * action will be APType.Create, APType.Update, or APType.Announce
     */
    @PerfMon(category = "apub")
    public SubNode saveInboundForeignObj(MongoSession ms, String userDoingAction, SubNode toAccountNode, SubNode parentNode,
            APObj obj, String action, String boostTargetId, String encodedKey, boolean allowFiltering, String inReplyTo) {
        apLog.trace("saveObject [" + action + "]" + XString.prettyPrint(obj));

        /*
         * First look to see if there is a target node already existing for this so we don't add a duplicate
         * 
         * note: partial index "unique-apid", is what makes this lookup fast.
         */
        SubNode dupNode = read.findNodeByProp(ms, parentNode, NodeProp.OBJECT_ID.s(), obj.getId());

        if (dupNode != null) {
            // If we found this node by ID and we aren't going to be updating it, return it as is.
            if (!action.equals(APType.Update)) {
                apLog.trace("duplicate post ignored: ActPubId: " + obj.getId() + " nodeId=" + dupNode.getIdStr());
                return dupNode;
            }
            // if we're updating the node, need to validate they encodedKey owns it.
            else {
                if (!apCrypto.ownerHasKey(ms, dupNode, encodedKey)) {
                    log.warn("unauthorized key [" + encodedKey + "] tried action " + action + " on node " + dupNode.getIdStr()
                            + " with object " + XString.prettyPrint(obj));
                    throw new RuntimeException("unauthorized key");
                }
            }
        }

        Date published = apDate(obj, APObj.published);

        // NOTE: If this is an inbound reply to a local node, normally we can expect inReplyTo to be set
        // here and
        // it will be the nodeId of the node being replied to.
        if (inReplyTo == null) {
            inReplyTo = apStr(obj, APObj.inReplyTo);
        }
        String contentHtml = APType.Announce.equals(action) ? "" : apStr(obj, APObj.content);

        String objUrl = apStr(obj, APObj.url);
        List<?> objUrls = null;
        if (objUrl == null) {
            objUrls = apList(obj, APObj.url, false);
        }

        List<?> icons = apList(obj, APObj.icon, true);
        String name = apStr(obj, APObj.name);

        String objAttributedTo = getSingleAttributedTo(obj, true);

        Boolean sensitive = apBool(obj, APObj.sensitive);
        List<?> tagArray = (List<?>) apList(obj, APObj.tag, false);
        APObj replies = apAPObj(obj, APObj.replies);

        // Ignore non-english for now (later we can make this a user-defined language selection)
        String lang = "0";
        Object context = apObj(obj, APObj.context);
        if (context != null) {
            String language = null;

            // if context is a list we try to dig the language out of one of it's objects
            if (context instanceof List) {
                Object langObj = apParseList((List) context, APObj.language);
                if (langObj instanceof String) {
                    language = (String) langObj;
                }
            }

            // if we didn't get a language that way try the simpler way
            if (language == null) {
                language = apStr(context, APObj.language, false);
            }

            if (language != null) {
                lang = language;
                // let's try allowing 'und' and see if get get a bunch of foreign stuff or not. If so we can go back
                // to requiring a 'en' only value.
                if (allowFiltering && !"en".equalsIgnoreCase(language) && !"und".equalsIgnoreCase(language)) {
                    log.debug("Ignore Non-English: " + language);
                    return null;
                }
            }
        }

        if (allowFiltering) {
            if (ENGLISH_LANGUAGE_CHECK) {
                if (lang.equals("0")) {
                    if (!english.isEnglish(contentHtml)) {
                        log.debug("Ignored Foreign: " + XString.prettyPrint(obj));
                        return null;
                    } else {
                        // this was an arbitrary meaningless value used to detect/test for correct program flow.
                        lang = "en-ck3";
                    }
                }
            }
        }

        // foreign account will own this node, this may be passed if it's known or null can be passed in.
        if (toAccountNode == null) {
            toAccountNode = getAcctNodeByActorUrl(ms, userDoingAction, objAttributedTo);
        }
        SubNode newNode = create.createNode(ms, parentNode, null, NodeType.COMMENT.s(), 0L, CreateNodeLocation.LAST, null,
                toAccountNode.getId(), true, true);

        // If we're updating a node, find what the ID should be and we can just put that ID value into
        // newNode
        if (action.equals(APType.Update)) {
            // if we didn't find what to update throw error.
            if (dupNode == null) {
                throw new RuntimeException("Unable to find node to update.");
            }

            // If wrong user is updating this object throw error.
            String dupNodeAttributedTo = dupNode.getStr(NodeProp.ACT_PUB_OBJ_ATTRIBUTED_TO);
            if (dupNodeAttributedTo == null || !dupNodeAttributedTo.equals(objAttributedTo)) {
                throw new RuntimeException("Wrong person to update object.");
            }

            // remove dupNode from memory cache so it can't be written out
            ThreadLocals.clean(dupNode);

            newNode.setId(dupNode.getId());
            ThreadLocals.clean(newNode);
        }

        newNode.setContent(contentHtml);

        // this block needs to be moved into ActPub utils and named 'parseTagArray'
        HashMap<String, APObj> tags = null;
        if (tagArray != null) {
            // If we have a tagArray object save it on the node properties.
            newNode.set(NodeProp.ACT_PUB_TAG, tagArray);
            tags = parseTagArray(tagArray);

            // DO NOT DELETE
            // NOTE: LEAVE HERE, in case we ever need to parse contentHtml into the tagArray
            // HashMap<String, APObj> tags = apub.parseTags(contentHtml, true, false);

            if (tags != null) {
                importUsers(ms, tags, null);
                for (String mentionName : tags.keySet()) {
                    if (mentionName.startsWith("@")) {
                        saveFediverseName(mentionName);
                    }
                }
            }
        }

        newNode.setModifyTime(published);
        newNode.setCreateTime(published);

        if (sensitive != null && sensitive.booleanValue()) {
            newNode.set(NodeProp.ACT_PUB_SENSITIVE, "y");
        }

        newNode.set(NodeProp.OBJECT_ID, obj.getId());

        if (objUrl != null) {
            newNode.set(NodeProp.ACT_PUB_OBJ_URL, objUrl);
        } else if (objUrls != null) {
            apLog.trace("Got muli urls: " + XString.prettyPrint(objUrls));
            newNode.set(NodeProp.ACT_PUB_OBJ_URLS, objUrls);
        }

        if (icons != null) {
            newNode.set(NodeProp.ACT_PUB_OBJ_ICONS, icons);
        }

        if (name != null) {
            newNode.set(NodeProp.ACT_PUB_OBJ_NAME, name);
        }

        if (replies != null) {
            newNode.set(NodeProp.ACT_PUB_REPLIES, replies);
        }

        newNode.set(NodeProp.INREPLYTO, inReplyTo);
        newNode.set(NodeProp.ACT_PUB_OBJ_TYPE, obj.getType());
        newNode.set(NodeProp.ACT_PUB_OBJ_ATTRIBUTED_TO, objAttributedTo);

        if (boostTargetId != null) {
            newNode.set(NodeProp.BOOST, boostTargetId);
        }

        // part of troubleshooting the non-english language detection
        // newNode.setProp("lang", lang);

        shareToAllObjectRecipients(ms, userDoingAction, newNode, obj, APObj.to);

        // note: I was temporarily think this was doing too much sharing, but
        // I now think we do need it, especially because sometimes the only place
        // some posts put their 'public' designation is in the CC, so let's process
        // all ccs. The risk (I thought I had was that a 'reply to node', will cause these CCs
        // to be included when they shouldn't). We'll see.
        shareToAllObjectRecipients(ms, userDoingAction, newNode, obj, APObj.cc);

        addAttachments(ms, newNode, obj);
        update.save(ms, newNode, false);

        try {
            push.pushNodeUpdateToBrowsers(ms, null, newNode);
        } catch (Exception e) {
            log.error("pushNodeUpdateToBrowsers failed (ignoring error)", e);
        }

        apLog.trace("newAPNode: " + XString.prettyPrint(newNode));
        return newNode;
    }

    /*
     * If we can get a plai string attributedTo property off the obj we return that, but if the
     * attributedTo is an array then we search it for a Person type object in the array and then return
     * the 'id' property of said person object.
     */
    private String getSingleAttributedTo(APObj obj, boolean warnIfMissing) {
        Val<Object> val = null;

        if ((val = AP.getFromMap(obj, APObj.attributedTo)) != null) {
            if (val.getVal() == null) {
                return null;
            }
            // if we have a plain string prop return it.
            else if (val.getVal() instanceof String) {
                apLog.trace("attributed to found as string: " + (String) val.getVal());
                return (String) val.getVal();
            }
            // else if this is a list we scan it.
            else if (val.getVal() instanceof List) {
                List<?> attribsList = (List<?>) val.getVal();
                for (Object attribItem : attribsList) {

                    // get a concrete class instance from the factory for 'attribItem'
                    attribItem = AP.typeFromFactory(attribItem);

                    // once we find a person in the obj, we consider that out attributedTo string
                    // and return that.
                    if (attribItem instanceof APOPerson) {
                        String ret = apStr(attribItem, APObj.id);
                        apLog.trace("attributed to found as id on Person: " + ret);
                        return ret;
                    }
                }
            } else {
                if (warnIfMissing) {
                    ExUtil.warn("unhandled type on getSingleAttributedTo() return val: "
                            + (val.getVal() != null ? val.getVal().getClass().getName() : "null on object")
                            + "\nUnable to get property " + APObj.attributedTo + " from obj " + XString.prettyPrint(obj));
                }
                return null;
            }
        }

        if (warnIfMissing) {
            ExUtil.warn("unhandled type on getSingleAttributedTo(): " + (obj != null ? obj.getClass().getName() : "null")
                    + "\nUnable to get property " + APObj.attributedTo + " from obj " + XString.prettyPrint(obj));
        }
        return null;
    }

    private HashMap<String, APObj> parseTagArray(List<?> tagArray) {
        HashMap<String, APObj> tags = new HashMap<>();

        // now build our own stronger typed APObj-derived objects for the array into tags
        for (Object tag : tagArray) {
            String type = apStr(tag, APObj.type);
            String name = apStr(tag, APObj.name);
            String href = apStr(tag, APObj.href);

            if ("Mention".equalsIgnoreCase(type)) {
                tags.put(name, new APOMention(href, name));
            } //
            else if ("Hashtag".equalsIgnoreCase(type)) {
                tags.put(name, new APOHashtag(href, name));
            }
        }
        return tags;
    }

    // imports the list of foreign users into the system, and performs the side-effect, of ensuring
    // both local users and foreign users have the 'href' set on the APOTag
    public void importUsers(MongoSession ms, HashMap<String, APObj> users, String userDoingAction) {
        if (users == null)
            return;

        users.forEach((user, tag) -> {
            // ignore of this is something else like a Hashtag
            if (!(tag instanceof APOMention))
                return;

            try {
                // remove '@' prefix
                user = XString.stripIfStartsWith(user, "@");

                // if username contains @ (after sripping off any first char) making it a foreign user.
                if (user.contains("@")) {
                    if (prop.isActPubEnabled()) {
                        SubNode userNode = getAcctNodeByForeignUserName(ms, userDoingAction, user, true, true);
                        if (userNode == null) {
                            log.debug("Unable to import user: " + user);
                            return;
                        }

                        String actorUrl = userNode.getStr(NodeProp.ACT_PUB_ACTOR_ID);
                        if (actorUrl != null) {
                            tag.put(APObj.href, actorUrl);
                        }
                    }
                }
                // else if this is a local user, still need actor ID
                else {
                    String actorUrl = apUtil.makeActorUrlForUserName(user);
                    tag.put(APObj.href, actorUrl);
                }
            } catch (Exception e) {
                log.error("unable to get href for user: " + user, e);
            }
        });
    }

    /*
     * Adds node sharing (ACL) entries for all recipients (i.e. propName==to | cc)
     * 
     * The node save is expected to be done external to this function after this function runs.
     */
    @PerfMon(category = "apub")
    private void shareToAllObjectRecipients(MongoSession ms, String userDoingAction, SubNode node, Object obj, String propName) {
        List<?> list = apList(obj, propName, true);
        if (list != null) {
            /* Build up all the access controls */
            for (Object to : list) {
                if (to instanceof String) {
                    /* The spec allows either a 'followers' URL here or an 'actor' URL here */
                    shareToUsersForUrl(ms, userDoingAction, node, (String) to);
                } else {
                    apLog.trace("to list entry not supported: " + to.getClass().getName());
                }
            }
        } else {
            apLog.trace("No addressing to " + propName);
        }
    }

    /*
     * Reads the object from 'url' to determine if it's a 'followers' URL or an 'actor' URL, and then
     * shares the node to either all the followers or the specific actor
     */
    private void shareToUsersForUrl(MongoSession ms, String userDoingAction, SubNode node, String url) {
        apLog.trace("shareToUsersForUrl: " + url);

        if (apUtil.isPublicAddressed(url)) {
            acl.makePublicAppendable(ms, node);
            return;
        }

        /*
         * if url does not contain "/followers" then the best first try is to assume it's an actor url and
         * try that first
         */
        if (!url.contains("/followers")) {
            shareNodeToActorByUrl(ms, userDoingAction, node, url);
        }
        /*
         * else assume this is a 'followers' url. Sharing normally will include a 'followers' and run this
         * code path when some foreign user has a mention of our local user and is also a public post, and
         * the foreign mastodon will then encode a 'followers' url into the 'to' or 'cc' of the incomming
         * node designating that it's shared to all the followers (I think even 'private' messages to all
         * followers will have this as well)
         */
        else {
            /*
             * I'm decided to disable this code, but leave it in place for future referece, but for now this
             * platform doesn't support the concept of sharing only to followers. Everything is either shared to
             * public, or shared explicitly to specific users.
             */
            boolean allow = false;
            if (allow) {
                APObj followersObj = apUtil.getRemoteAP(ms, userDoingAction, url);
                if (followersObj != null) {
                    // note/warning: the ActPubFollower.java class also has code to read followers.
                    apUtil.iterateCollection(ms, userDoingAction, followersObj, MAX_FOLLOWERS, obj -> {
                        /*
                         * Mastodon seems to have the followers items as strings, which are the actor urls of the followers.
                         */
                        if (obj instanceof String) {
                            String followerActorUrl = (String) obj;
                            shareNodeToActorByUrl(ms, userDoingAction, node, followerActorUrl);
                        }
                        return true;
                    });
                }
            }
        }
    }

    /*
     * Shares this node to the designated user using their actorUrl and is expected to work even if the
     * actorUrl points to a local user
     */
    @PerfMon(category = "apub")
    private void shareNodeToActorByUrl(MongoSession ms, String userDoingAction, SubNode node, String actorUrl) {
        apLog.trace("Sharing node to actorUrl: " + actorUrl);
        /*
         * Yes we tolerate for this to execute with the 'public' designation in place of an actorUrl here
         */
        if (actorUrl.endsWith("#Public")) {
            acl.makePublicAppendable(ms, node);
            return;
        }

        saveFediverseName(actorUrl);

        /* try to get account id from cache first */
        String acctId = apCache.acctIdByActorUrl.get(actorUrl);

        /*
         * if acctId not found in cache load foreign user (will cause it to also get cached)
         */
        if (acctId == null) {
            SubNode acctNode = null;

            if (apUtil.isLocalActorUrl(actorUrl)) {
                String longUserName = apUtil.getLongUserNameFromActorUrl(ms, userDoingAction, actorUrl);
                acctNode = read.getUserNodeByUserName(ms, longUserName);
            } else {
                /*
                 * todo-1: this is contributing to our [currently] unwanted FEDIVERSE CRAWLER effect chain reaction.
                 * The rule here should be either don't load foreign users whose outboxes you don't plan to load or
                 * else have some property on the node that designates if we need to read the actual outbox or if
                 * you DO want to add a user and not load their outbox.
                 */
                // acctNode = loadForeignUserByActorUrl(ms, actorUrl);
                saveFediverseName(actorUrl);
            }

            if (acctNode != null) {
                acctId = acctNode.getIdStr();
            }
        }

        if (acctId != null) {
            apLog.trace("node shared to UserNodeId: " + acctId);
            acl.setKeylessPriv(ms, node, acctId, APConst.RDWR);
        } else {
            apLog.trace("not sharing to this user.");
        }
    }

    private void addAttachments(MongoSession ms, SubNode node, Object obj) {
        List<?> attachments = apList(obj, APObj.attachment, false);
        if (attachments == null) {
            // log.debug("no attachments.");
            return;
        }

        for (Object att : attachments) {
            String mediaType = apStr(att, APObj.mediaType);
            String url = apStr(att, APObj.url);

            if (mediaType != null && url != null) {
                attach.readFromUrl(ms, url, node, node.getIdStr(), mediaType, mediaType, -1, false);
            }
        }
    }

    @PerfMon(category = "apub")
    public APOPerson generatePersonObj(String userName) {
        return arun.<APOPerson>run(as -> {
            // we get the usernode without authorizing because the APOPerson is guaranteed to only contain
            // public info.
            SubNode userNode = read.getUserNodeByUserName(as, userName, false);
            if (userNode != null) {
                return apFactory.generatePersonObj(userNode);
            }
            return null;
        });
    }

    /*
     * Be careful, because any user you queue into here will have their outbox loaded into Quanta, and
     * it's easy to set off a chain reaction where more users keep comming in like a FediCrawler
     */
    public void userEncountered(String apUserName, boolean force) {
        saveFediverseName(apUserName);

        if (force) {
            queueUserForRefresh(apUserName, force);
        }
    }

    /*
     * For now name can be an actual username or an actor URL. I just want to see how rapidly this
     * explodes in order to decide what to do next.
     * 
     * Returns true if the name was added, or false if already existed
     */
    public boolean saveFediverseName(String name) {
        if (name == null)
            return false;
        name = name.trim();

        // lazy job of detecting garbage names
        if (name.indexOf("\n") != -1 || name.indexOf("\r") != -1 || name.indexOf("\t") != -1)
            return false;

        if (!apCache.allUserNames.contains(name)) {
            apCache.allUserNames.put(name, false);
            return true;
        }
        return false;
    }

    public void queueUserForRefresh(String apUserName, boolean force) {
        // log.debug("queueForRefresh: " + apUserName);

        // if not on production we don't run ActivityPub stuff. (todo-1: need to make it optional)
        if (!prop.isActPubEnabled()) {
            return;
        }

        if (apUserName == null || !apUserName.contains("@") || apUserName.toLowerCase().endsWith("@" + prop.getMetaHost()))
            return;

        saveFediverseName(apUserName);

        // unless force is true, don't add this apUserName to pending list
        if (!force && apCache.usersPendingRefresh.contains(apUserName)) {
            return;
        }

        // add as 'false' meaning the refresh is not yet done
        apCache.usersPendingRefresh.put(apUserName, false);
    }

    /* every 90 minutes read all the outboxes of all users */
    @Scheduled(fixedDelay = 90 * DateUtil.MINUTE_MILLIS)
    public void bigRefresh() {
        if (!prop.isDaemonsEnabled() || !MongoRepository.fullInit)
            return;

        // refreshForeignUsers();
        // refreshFollowedUsers();
    }

    /*
     * Run every few seconds
     */
    @Scheduled(fixedDelay = 3 * 1000)
    public void userRefresh() {
        if (userRefresh || !prop.isActPubEnabled() || !prop.isDaemonsEnabled() || !MongoRepository.fullInit)
            return;

        try {
            userRefresh = true;
            try {
                saveUserNames();
            } catch (Exception e) {
                // log and ignore.
                log.error("saveUserNames", e);
            }

            refreshUsers();
        } catch (Exception e) {
            // log and ignore.
            log.error("refresh outboxes", e);
        } finally {
            userRefresh = false;
        }
    }

    private void refreshUsers() {
        if (!prop.isDaemonsEnabled())
            return;

        List<String> names = new ArrayList<>();
        for (String userName : apCache.usersPendingRefresh.keySet()) {
            names.add(userName);
        }

        /*
         * shuffle just to reduce likelyhood we might hit the same server too many times, and yes I realize
         * keySet wasn't even sorted to begin with but nor do I want to count on it's randomness.
         */
        Collections.shuffle(names);

        String lastServer = null;
        for (String userName : names) {
            if (!prop.isDaemonsEnabled())
                break;

            // never hit the same server twice here, which is good to not do too much traffic on any one
            String server = apUtil.getHostFromUserName(userName);
            if (server != null && server.equals(lastServer)) {
                continue;
            }

            try {
                Boolean done = apCache.usersPendingRefresh.get(userName);
                if (done)
                    continue;

                /*
                 * To limit load on our server we do a sleep here, making sure to do a 4s sleep if we're accessing
                 * the same server twice in a row or a 1s sleep if it's a different server. Accessing the same
                 * server too fast without any delays can make them start blocking/throttling us.
                 * 
                 * upate: I was seeing a performance lag, so I'm setting to 4000ms for each cycle for now
                 * regardless.
                 */
                Thread.sleep(server.equals(lastServer) ? 4000 : 2000);
                lastServer = server;

                // flag as done (even if it fails we still want it flagged as done. no retries will be done).
                apCache.usersPendingRefresh.put(userName, true);

                loadForeignUser(null, userName);
            } catch (Exception e) {
                log.debug("Unable to load user: " + userName);
            }
        }
    }

    /*
     * This insures everything about all users is as up-to-date as possible by doing the equivalent an
     * importActor, but non-destructively to keep all existing nodes and posts
     * 
     * There are several kinds of ways these can fail like this: (so we need to be able to assign
     * statuses to accounts for these cases or at least a PASS/FAIL so the admin can optionally clean up
     * old/dead accounts)
     * 
     * get webFinger: Alice5401@mastodon.online 2021-08-20 17:33:48,996 DEBUG quanta.actpub.ActPubUtil
     * [threadPoolTaskExecutor-1] failed getting json: https://mastodon.online/users/Alice5401 -> 410
     * Gone: [{"error":"Gone"}]
     * 
     * and ths... get webFinger: reddit@societal.co 2021-08-20 17:33:46,824 DEBUG
     * quanta.actpub.ActPubUtil [threadPoolTaskExecutor-1] failed getting json:
     * https://societal.co/users/reddit -> Unexpected character ('<' (code 60)): expected a valid value
     * (JSON String, Number, Array, Object or token 'null', 'true' or 'false') at [Source:
     * (StringReader); line: 1, column: 2]
     * 
     * and this... DEBUG quanta.actpub.ActPubUtil [threadPoolTaskExecutor-1] failed getting json:
     * https://high.cat/users/archillect -> 401 Unauthorized: [Request not signed] 2021-08-20
     * 17:33:33,061
     */
    public void refreshActorPropsForAllUsers() {
        Runnable runnable = () -> {
            accountsRefreshed = 0;
            arun.run(as -> {
                // Query to pull all user accounts
                Iterable<SubNode> accountNodes = read.getAccountNodes(as, null, null, null, -1, true, true);

                for (SubNode acctNode : accountNodes) {
                    // get userName, and skip over any that aren't foreign accounts
                    String userName = acctNode.getStr(NodeProp.USER);
                    if (userName == null || !userName.contains("@"))
                        continue;

                    log.debug("rePullActor [" + accountsRefreshed + "]: " + userName);
                    String url = acctNode.getStr(NodeProp.ACT_PUB_ACTOR_ID);

                    try {
                        if (url != null) {
                            APOActor actor = apUtil.getActor(as, null, url);

                            if (actor != null) {
                                // we could double check userName, and bail if wrong, but this is not needed.
                                // String userName = getLongUserNameFromActor(actor);
                                apCache.actorsByUrl.put(url, actor);
                                apCache.actorsByUserName.put(userName, actor);

                                // since we're passing in the account node this importActor will basically just update the
                                // properties on it and save it.
                                importActor(as, acctNode, actor);
                                log.debug("import ok");
                                accountsRefreshed++;
                            } else {
                                log.debug("Unable to get actor obj for url: " + url);
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Failed getting actor: " + url);
                    }

                    /*
                     * we don't want out VPS to think anything nefarious is happening (like we're doing a DDOS or
                     * something) so we sleep a full second between each user
                     */
                    Util.sleep(1000);
                }
                log.debug("Finished refreshActorPropsForAllUsers");
                return null;
            });
        };
        exec.run(runnable);
    }

    public String readOutbox(String userName) {
        loadForeignUser("FollowBot", userName);
        return "Outbox requested";
    }

    public void loadForeignUser(String userMakingRequest, String userName) {
        arun.run(as -> {
            log.debug("Reading outbox: " + userName);
            SubNode userNode = getAcctNodeByForeignUserName(as, userMakingRequest, userName, false, true);
            if (userNode == null) {
                log.debug("Unable to getAccount Node for userName: " + userName);
                return null;
            }

            String actorUrl = userNode.getStr(NodeProp.ACT_PUB_ACTOR_ID);
            APOActor actor = apUtil.getActorByUrl(as, userMakingRequest, actorUrl);
            if (actor != null) {
                // if their outbox fails just, stop processing and don't bother trying to get followers or
                // following,.
                if (!apOutbox.loadForeignOutbox(as, userMakingRequest, actor, userNode, userName)) {
                    return null;
                }

                /*
                 * I was going to load followerCounts into userNode, but I decided to just query them live when
                 * needed on the UserPreferences dialog
                 */
                // todo-1: need a flag to enable these to allow for agressive collection of usernames, but for now
                // we have more than enough users
                // so I'm disabling this.
                // int followerCount = apFollower.loadRemoteFollowers(ms, userMakingRequest, actor);
                // int followingCount = apFollowing.loadRemoteFollowing(ms, userMakingRequest, actor);
            } else {
                log.debug("Unable to get actor from url: " + actorUrl);
            }
            return null;
        });
    }

    /* Saves all the pending new FediverseName objects we've accumulated */
    private void saveUserNames() {
        List<String> names = new LinkedList<>(apCache.allUserNames.keySet());

        for (String name : names) {
            Boolean done = apCache.allUserNames.get(name);
            if (done)
                continue;

            apCache.allUserNames.put(name, true);

            FediverseName fName = new FediverseName();
            fName.setName(name);
            fName.setCreateTime(Calendar.getInstance().getTime());

            try {
                // log.debug("Saving Name: " + fName.getName());
                ops.save(fName);
                // Thread.sleep(500);
            } catch (Exception e) {
                // this will happen for every duplicate. so A LOT!
            }
        }
    }

    /**
     * Returns number of userNamesPendingMessageRefresh that map to 'false' values
     */
    public int queuedUserCount() {
        int count = 0;
        for (String apUserName : apCache.usersPendingRefresh.keySet()) {
            Boolean done = apCache.usersPendingRefresh.get(apUserName);
            if (!done) {
                count++;
            }
        }
        return count;
    }

    /*
     * Generates the list of all users being followed into 'apCache.followedUsers'
     */
    public void identifyFollowedAccounts(boolean queueForRefresh, HashSet<ObjectId> blockedUserIds) {
        if (!prop.isDaemonsEnabled() || !prop.isActPubEnabled() || scanningForeignUsers)
            return;

        arun.run(as -> {
            if (scanningForeignUsers)
                return null;

            try {
                scanningForeignUsers = true;

                Iterable<SubNode> accountNodes = read.getAccountNodes(as, null, null, null, -1, true, true);

                for (SubNode node : accountNodes) {
                    if (!prop.isDaemonsEnabled())
                        break;

                    String userName = node.getStr(NodeProp.USER);

                    // if a foreign account we skip it.
                    if (userName == null || userName.contains("@"))
                        continue;

                    // we query for only the foreign users this local user is following
                    List<String> following =
                            apFollowing.getFollowing(userName, true, false, null, queueForRefresh, blockedUserIds);

                    // log.debug("FOLLOW_COUNT: " + userName + " = " + following.size());
                    synchronized (apCache.followedUsers) {
                        apCache.followedUsers.addAll(following);
                    }
                }

                StringBuilder sb = new StringBuilder();
                synchronized (apCache.followedUsers) {
                    apCache.followedUsers.forEach(user -> {
                        sb.append(user + "\n");
                    });
                }
                // need a 'server info' query that can dump these out for the admin user to see in browser.
                // log.debug("FOLLOWED USERS: " + sb.toString());
            } finally {
                scanningForeignUsers = false;
            }
            return null;
        });
    }

    public void refreshFollowedUsers() {
        log.debug("refreshFollowedUsers()");

        // get list of admin blocked users
        HashSet<ObjectId> blockedUserIds = new HashSet<>();
        userFeed.getBlockedUserIds(blockedUserIds, PrincipalName.ADMIN.s());

        // queue all followed users for refresh
        identifyFollowedAccounts(true, blockedUserIds);
    }

    /*
     * This code was needed for curating content into our database before we had the FollowBot for this
     * purpose. This code grabs some users from the DB (not necessarily followed by any user) and
     * queries their outboxes. However now that we have FollowBot, we can depend on other foreign
     * servers posting to us all the content we need to populate our curated fediverse feed.
     * 
     * I'm leaving this code here, and the hooks to call it, rather than deleting it, in case we have a
     * need for this kind of processing in the future.
     */
    public void refreshForeignUsers() {
        log.debug("refreshForeignUsers()");

        if (!prop.isDaemonsEnabled() || !prop.isActPubEnabled() || refreshingForeignUsers)
            return;

        arun.run(as -> {
            if (refreshingForeignUsers)
                return null;

            try {
                refreshingForeignUsers = true;

                lastRefreshForeignUsersCycleTime = DateUtil.getFormattedDate(new Date().getTime());
                refreshForeignUsersCycles++;
                refreshForeignUsersQueuedCount = 0;
                cycleOutboxQueryCount = 0;
                newPostsInCycle = 0;

                HashSet<ObjectId> blockedUserIds = new HashSet<>();
                userFeed.getBlockedUserIds(blockedUserIds, PrincipalName.ADMIN.s());

                Iterable<SubNode> accountNodes = read.getAccountNodes(as, null, //
                        Sort.by(Sort.Direction.ASC, SubNode.CREATE_TIME), NUM_CURATED_ACCOUNTS, -1, true, true);
                StringBuilder usersToFollow = new StringBuilder();

                // process each account in the system
                for (SubNode node : accountNodes) {
                    if (!prop.isDaemonsEnabled())
                        break;

                    // if this user is blocked by admin, skip them.
                    if (blockedUserIds.contains(node.getId()))
                        continue;

                    String userName = node.getStr(NodeProp.USER);

                    // if not a forgien account ignore
                    if (userName == null || !userName.contains("@"))
                        continue;

                    refreshForeignUsersQueuedCount++;
                    queueUserForRefresh(userName, true);
                    usersToFollow.append(userName + "\n");
                }

                log.debug("usersToFollow: " + usersToFollow.toString());
            } finally {
                refreshingForeignUsers = false;
            }
            return null;
        });
    }

    /* This is just to pull in arbitary new users so our Fediverse feed is populated */
    public String crawlNewUsers() {
        if (!prop.isActPubEnabled())
            return "ActivityPub not enabled";

        return arun.run(as -> {
            Iterable<SubNode> accountNodes = read.getAccountNodes(as, null, null, null, -1, true, true);

            // Load the list of all known users
            HashSet<String> knownUsers = new HashSet<>();
            for (SubNode node : accountNodes) {
                String userName = node.getStr(NodeProp.USER);
                if (userName == null)
                    continue;
                knownUsers.add(userName);
            }

            Iterable<FediverseName> recs = ops.findAll(FediverseName.class);
            int numLoaded = 0;
            for (FediverseName fName : recs) {
                try {
                    String userName = fName.getName();
                    log.debug("crawled user: " + userName);

                    // This userName may be an actor url, and if so we convert it to an actual username.
                    if (userName.startsWith("https://")) {
                        userName = apUtil.getLongUserNameFromActorUrl(as, null, userName);
                        // log.debug("Converted to: " + userName);
                    }

                    if (knownUsers.contains(userName))
                        continue;

                    queueUserForRefresh(userName, true);
                    apCache.allUserNames.remove(userName);

                    if (++numLoaded > 250) {
                        break;
                    }
                } catch (Exception e) {
                    log.error("queueing FediverseName failed.", e);
                }
            }
            return "Queued some new users to crawl.";
        });
    }

    public String maintainActPubUsers() {
        if (!prop.isActPubEnabled())
            return "ActivityPub not enabled";

        return arun.run(as -> {
            log.debug("Starting ActPub cleanup...");
            long delCount = delete.deleteOldActPubPosts(4, as);
            String message = "AP Maintenance Complete. Deleted " + String.valueOf(delCount) + " old posts.";
            log.debug(message);
            return message;
        });
    }

    public String maintainNostrUsers() {
        return arun.run(as -> {
            log.debug("Starting Nostr cleanup...");
            long delCount = delete.deleteOldNostrPosts(4, as);
            String message = "Nostr Maintenance Complete. Deleted " + String.valueOf(delCount) + " old posts.";
            log.debug(message);
            return message;
        });
    }

    public String getStatsReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\nActivityPub Stats:\n");
        sb.append("Accounts Refreshed: " + accountsRefreshed + "\n");
        sb.append("Refresh in progress: " + (userRefresh ? "true" : "false") + "\n");
        sb.append("Cached Usernames: " + apCache.allUserNames.size() + "\n");
        sb.append("Users Currently Queued (for refresh): " + queuedUserCount() + "\n");
        sb.append("Refresh Foreign Users Cycles: " + refreshForeignUsersCycles + "\n");
        sb.append("Last Foreign Users Refresh Time: " + lastRefreshForeignUsersCycleTime + "\n");
        sb.append("Number of Users Queued at last Cycle: " + refreshForeignUsersQueuedCount + "\n");
        sb.append("Cycle Foreign Outbox Queries: " + cycleOutboxQueryCount + "\n");
        sb.append("Total Foreign Outbox Queries: " + outboxQueryCount + "\n");
        sb.append("New Incomming Posts last cycle: " + newPostsInCycle + "\n");
        sb.append("Inbox Post count: " + inboxCount + "\n");
        return sb.toString();
    }

    public String dumpFediverseUsers() {
        ThreadLocals.requireAdmin();
        StringBuilder sb = new StringBuilder();
        Iterable<FediverseName> recs = ops.findAll(FediverseName.class);
        int count = 0;
        for (FediverseName fName : recs) {
            sb.append(fName.getName());
            sb.append("\n");
            count++;
        }
        return "Fediverse Users: " + String.valueOf(count) + "\n\n" + sb.toString();
    }

    public List<String> getUserNamesFromNodeAcl(MongoSession ms, SubNode node) {
        // if we have no ACL return null
        if (node == null || node.getAc() == null)
            return null;

        List<String> userNames = new LinkedList<>();

        /*
         * Lookup all userNames from the ACL info, to add them all to 'toUserNames'
         */
        for (String accntId : node.getAc().keySet()) {
            if (PrincipalName.PUBLIC.s().equals(accntId)) {
                continue;
            }

            try {
                SubNode accntNode = cachedGetAccntNodeById(ms, accntId, false, null);

                // get username off this node and add to 'toUserNames'
                if (accntNode != null) {
                    String userName = accntNode.getStr(NodeProp.USER);
                    userNames.add(userName);
                }
            } catch (Exception e) {
                log.error("Failed getting userName from accountId: " + accntId);
            }
        }
        return userNames;
    }

    public APList getTagListFromUserNames(String userDoingAction, List<String> userNames) {
        if (userNames == null || userNames.isEmpty())
            return null;

        APList tagList = new APList();
        for (String userName : userNames) {
            try {
                String actorUrl = null;

                // if this is a local username
                if (!userName.contains("@")) {
                    actorUrl = apUtil.makeActorUrlForUserName(userName);
                }
                // else foreign userName
                else {
                    actorUrl = apUtil.getActorUrlFromForeignUserName(userDoingAction, userName);
                }

                if (actorUrl == null)
                    continue;

                // prepend character to make it like '@user@server.com'
                tagList.val(new APOMention(actorUrl, "@" + userName));
            }
            // log and continue if any loop (user) fails here.
            catch (Exception e) {
                log.debug("failed adding user to message: " + userName + " -> " + e.getMessage());
            }
        }
        return tagList;
    }

    /* We're expected to have a nodeId here --or-- a url, but not both */
    public String getRemoteJson(MongoSession ms, String nodeId, String objUrl) {

        // if we have a nodeId try to use it to get the objUrl from and ignore objUrl param, otherwise
        // we'll just end up using the passed objUrl
        // if (ok(nodeId)) {
        // SubNode node = read.getNode(ms, nodeId);
        // if (no(node)) {
        // return "Node not found.";
        // }
        // objUrl = node.getStr(NodeProp.ACT_PUB_OBJ_URL);
        // if (no(objUrl)) {
        // return "Node has no ActivityPub URL";
        // }
        // }

        String userDoingAction = ThreadLocals.getSC().getUserName();
        try {
            APObj obj = apUtil.getRemoteAP(ms, userDoingAction, objUrl);
            if (obj != null) {
                return "URL: " + objUrl + "\n\n" + XString.prettyPrint(obj);
            } else {
                return "Unable to load data.";
            }
        } catch (Exception e) {
            log.error("Unable to get JSON from url: " + objUrl);
            return "Error: " + e.getMessage();
        }
    }

    /*
     * Returns map of of APOHashtag and/or APOMention objects, where the keys are the hashtag or mention
     * including the # or @ as first char of the key.
     */
    public HashMap<String, APObj> parseTags(String content, boolean parseMentions, boolean parseHashtags) {
        HashMap<String, APObj> tags = new HashMap<>();
        if (content == null)
            return tags;

        StringTokenizer t = new StringTokenizer(content, APConst.TAGS_TOKENIZER, false);

        while (t.hasMoreTokens()) {
            String tok = t.nextToken();

            if (tok.length() > 1) {
                // MENTION (@name@server.com or @name)
                int atMatches = StringUtils.countMatches(tok, "@");
                if (parseMentions && tok.startsWith("@") && atMatches <= 2) {
                    String actor = null;

                    String userName = tok;
                    boolean isLocalUserName = userName.endsWith("@" + prop.getMetaHost().toLowerCase());
                    if (isLocalUserName) {
                        userName = XString.stripIfStartsWith(userName, "@");
                        userName = apUtil.stripHostFromUserName(userName);
                        actor = apUtil.makeActorUrlForUserName(userName);
                    }
                    // foreign userName
                    else if (atMatches == 2) {
                        String userDoingAction = ThreadLocals.getSC().getUserName();
                        actor = apUtil.getActorUrlFromForeignUserName(userDoingAction, tok);
                    }
                    tags.put(tok, new APOMention(actor, tok));
                }
                // HASHTAG
                else if (parseHashtags && tok.startsWith("#") && StringUtils.countMatches(tok, "#") == 1) {
                    String shortTok = XString.stripIfStartsWith(tok, "#");
                    tags.put(tok, new APOHashtag(prop.getProtocolHostAndPort() + "?view=feed&tagSearch=" + shortTok, tok));
                }
            }
        }

        return tags;
    }

    /**
     * Parses Mentions+Hashtags from ACT_PUB_TAG property of the node.
     */
    public HashMap<String, APObj> parseTags(SubNode node) {
        HashMap<String, APObj> tagMap = new HashMap<>();
        if (node == null)
            return tagMap;

        List<APTag> tags = node.getTypedObj(NodeProp.ACT_PUB_TAG.s(), new TypeReference<List<APTag>>() {});
        if (tags == null)
            return tagMap;

        for (APTag tag : tags) {
            try {
                // ActPub spec originally didn't have Hashtag here, so default to that if no type
                if (tag.getType() == null) {
                    tag.setType("Hashtag");
                }

                if ("Hashtag".equalsIgnoreCase(tag.getType())) {
                    tagMap.put(tag.getName(), new APOHashtag(tag.getHref(), tag.getName()));
                }
                // Process Mention
                else if ("Mention".equalsIgnoreCase(tag.getType())) {
                    APOMention tagObj = new APOMention(tag.getHref(), tag.getName());

                    // add a string like host@username
                    URL hrefUrl = new URL(tag.getHref());

                    // sometimes the name is ALREADY containing the host, so be sure not to append it again in that case
                    // or else we end up with "user@server.com@server.com"
                    String longName = tag.getName();

                    // if 'longName' is like "@user" with no domain, then add the domain.
                    if (StringUtils.countMatches(longName, "@") < 2) {
                        longName += "@" + hrefUrl.getHost();
                    }

                    // I'm just adding this as a sanity check but it should be unnecessary
                    if (!longName.startsWith("@")) {
                        longName = "@" + longName;
                    }

                    // one more sanity check to be sure everything is ok with the name
                    if (StringUtils.countMatches(longName, "@") > 2) {
                        continue;
                    }

                    // build this name without host part if it's a local user, otherwise full fediverse name
                    String user = prop.getMetaHost().equals(hrefUrl.getHost()) ? tag.getName() : longName;

                    // add the name if it's not the current user. No need to self-mention in a reply?
                    if (!user.equals("@" + apUtil.fullFediNameOfThreadUser())) {
                        tagMap.put(user, tagObj);
                    }
                }
            } catch (Exception e) {
                log.error("Unable to process tag.", e);
                // ignore errors on any tag and continue to next tag
            }
        }

        return tagMap;
    }
}
