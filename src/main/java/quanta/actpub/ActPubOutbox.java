package quanta.actpub;

import static quanta.actpub.model.AP.apAPObj;
import static quanta.actpub.model.AP.apStr;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.actpub.model.APList;
import quanta.actpub.model.APOActor;
import quanta.actpub.model.APOAnnounce;
import quanta.actpub.model.APOOrderedCollection;
import quanta.actpub.model.APOOrderedCollectionPage;
import quanta.actpub.model.APObj;
import quanta.actpub.model.APType;
import quanta.config.NodeName;
import quanta.config.ServiceBase;
import quanta.exception.NodeAuthFailedException;
import quanta.instrument.PerfMon;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.model.client.PrincipalName;
import quanta.model.client.PrivilegeType;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.service.AclService;
import quanta.util.DateUtil;
import quanta.util.XString;
import quanta.util.val.Val;

/**
 * AP Outbox
 */
@Component
@Slf4j 
public class ActPubOutbox extends ServiceBase {
    @Autowired
    private ActPubLog apLog;

    // For actual WEB CRAWLER we'd set this larger but for now
    // we just collest some from each outbox, to update the feed
    static final int MAX_OUTBOX_READ = 10;

    /**
     * Caller can pass in userNode if it's already available, but if not just pass null and the
     * apUserName will be used to look up the userNode.
     * 
     * Returns true only if everything successful
     */
    public boolean loadForeignOutbox(MongoSession ms, String userDoingAction, APOActor actor, SubNode userNode,
            String apUserName) {
        Val<Boolean> success = new Val<>(true);
        try {
            // try to read outboxUrl first and if we can't we just return false
            // NOTE: There was a bug where we were getting from APObj.inbox here by accident
            APObj outbox = getOutbox(ms, userDoingAction, apStr(actor, APObj.outbox));
            if (outbox == null) {
                log.debug("outbox read fail: " + apUserName);
                return false;
            }

            if (userNode == null) {
                userNode = read.getUserNodeByUserName(ms, apUserName);
            }

            SubNode outboxNode = read.getUserNodeByType(ms, apUserName, userNode, "### Posts", NodeType.ACT_PUB_POSTS.s(),
                    Arrays.asList(PrivilegeType.READ.s(), PrivilegeType.WRITE.s()), NodeName.POSTS);
            if (outboxNode == null) {
                log.debug("no posts node for user: " + apUserName);
                return false;
            }

            /*
             * Query all existing known outbox items we have already saved for this foreign user
             */
            Iterable<SubNode> outboxItems =
                    read.getChildren(ms, outboxNode, Sort.by(Sort.Direction.DESC, SubNode.CREATE_TIME), MAX_OUTBOX_READ, 0);

            /*
             * Generate a list of known AP IDs so we can ignore them and load only the unknown ones from the
             * foreign server
             */
            HashSet<String> apIdSet = new HashSet<>();
            for (SubNode n : outboxItems) {
                String apId = n.getStr(NodeProp.OBJECT_ID);
                if (apId != null) {
                    apIdSet.add(apId);
                }
            }

            Val<Integer> count = new Val<>(0);
            final SubNode _userNode = userNode;

            // log.debug("scanning outbox orderedCollection");
            apUtil.iterateCollection(ms, userDoingAction, outbox, MAX_OUTBOX_READ, obj -> {
                try {
                    // if (ok(obj)) {
                    //     log.debug("orderedCollection Item: OBJ=" + XString.prettyPrint(obj));
                    // }
                    String apId = apStr(obj, APObj.id);

                    // If this is a new post our server hasn't yet injested.
                    if (!apIdSet.contains(apId)) {
                        APObj object = apAPObj(obj, APObj.object);
                        if (object != null) {
                            String type = apStr(object, APObj.type);
                            // if (object instanceof String) {
                            // // todo-1: handle boosts.
                            // //
                            // // log.debug("Not Handled: Object was a string: " + object + " in outbox item: "
                            // // + XString.prettyPrint(obj));
                            // // Example of what needs to be handled here is when 'obj' contains a 'boost' (retweet)
                            // // {
                            // // "id" : "https://dobbs.town/users/onan/statuses/105613730170001141/activity",
                            // // AP.type : "Announce",
                            // // AP.actor : "https://dobbs.town/users/onan",
                            // // AP.published : "2021-01-25T01:20:30Z",
                            // // AP.to : [ "https://www.w3.org/ns/activitystreams#Public" ],
                            // // "cc" : [ "https://mastodon.sdf.org/users/stunder", "https://dobbs.town/users/onan/followers"
                            // ],
                            // // AP.object : "https://mastodon.sdf.org/users/stunder/statuses/105612925260202844"
                            // // }
                            // }
                            // // todo-1: need to handle "Boosts" and other types here too.
                            // else
                            if (APType.Note.equals(type)) {
                                try {
                                    ActPubService.newPostsInCycle++;
                                    apub.saveInboundForeignObj(ms, userDoingAction, _userNode, outboxNode, object, APType.Create, null,
                                            null, true, null);
                                    count.setVal(count.getVal() + 1);
                                } catch (DuplicateKeyException dke) {
                                    log.debug("Record already existed: " + dke.getMessage());
                                } catch (Exception e) {
                                    // log and ignore.
                                    log.error("error in saveNode()", e);
                                }
                            } else {
                                // this captures videos? and other things (todo-1: add more support)
                                // log.debug("Object type not supported: " + XString.prettyPrint(obj));
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed processing collection item.", e);
                    success.setVal(false);
                }
                return (count.getVal() < ActPubService.MAX_MESSAGES);
            });
        } catch (Exception e) {
            log.error("Error reading outbox of: " + apUserName, e);
            success.setVal(false);
        }
        return success.getVal();
    }

    public APObj getOutbox(MongoSession ms, String userDoingAction, String url) {
        if (url == null)
            return null;

        APObj outbox = apUtil.getRemoteAP(ms, userDoingAction, url);
        ActPubService.outboxQueryCount++;
        ActPubService.cycleOutboxQueryCount++;
        apLog.trace("Outbox [" + url + "]\n" + XString.prettyPrint(outbox));
        return outbox;
    }

    public APOOrderedCollection generateOutbox(String userName) {
        // log.debug("Generate outbox for userName: " + userName);
        String url = prop.getProtocolHostAndPort() + APConst.PATH_OUTBOX + "/" + userName;
        Long totalItems = getOutboxItemCount(userName, PrincipalName.PUBLIC.s());

        APOOrderedCollection ret = new APOOrderedCollection(url, totalItems, url + "?page=true", //
                url + "?min_id=0&page=true");
        return ret;
    }

    /*
     * userName represents the person whose outbox is being QUERIED, and the identity of the user DOING
     * the querying will come from the http header:
     */
    public Long getOutboxItemCount(String userName, String sharedTo) {
        Long totalItems = arun.run(as -> {
            long count = 0;
            SubNode userNode = read.getUserNodeByUserName(null, userName);
            if (userNode != null) {
                List<String> sharedToList = new LinkedList<>();
                sharedToList.add(sharedTo);
                count = auth.countSubGraphByAclUser(as, null, sharedToList, userNode.getOwner());
            }
            return Long.valueOf(count);
        });
        return totalItems;
    }

    /*
     * if minId=="0" that means "last page", and if minId==null it means first page
     */
    @PerfMon(category = "apOutbox")
    public APOOrderedCollectionPage generateOutboxPage(HttpServletRequest httpReq, String userName, String minId) {
        APList items = getOutboxItems(httpReq, userName, minId);

        // this is a self-reference url (id)
        String url = prop.getProtocolHostAndPort() + APConst.PATH_OUTBOX + "/" + userName + "?min_id=" + minId + "&page=true";

        APOOrderedCollectionPage ret = new APOOrderedCollectionPage(url, items,
                prop.getProtocolHostAndPort() + APConst.PATH_OUTBOX + "/" + userName, items.size());
        return ret;
    }

    public APList getOutboxItems(HttpServletRequest httpReq, String userName, String minId) {
        log.debug("getOutboxItems for " + userName);

        /*
         * For now we only support retrieving public nodes here but we need to do the proper thing here
         * eventually to adhere to the ActivityPub spec regarding authenticating what user is calling this
         */
        String sharedTo = PrincipalName.PUBLIC.s();

        String host = prop.getProtocolHostAndPort();
        APList retItems = null;
        String nodeIdBase = host + "?id=";

        /*
         * todo-2: I'm trying to be able to return content based on if the user accessing this has some
         * private nodes shared to them then we can honor that sharing here, rather than ONLY returning
         * PUBLIC nodes, but this is a work in progress
         */
        boolean experimental = false;
        if (experimental) {
            Val<String> keyId = new Val<>();
            Val<String> signature = new Val<>();
            Val<List<String>> headers = new Val<>();

            apCrypto.parseHttpHeaderSig(httpReq, keyId, signature, false, headers);

            if (keyId.getVal() != null) {
                log.debug("keyId=" + keyId.getVal());

                // keyId should be like this:
                // actorId + "#main-key"
                String actorId = keyId.getVal().replace("#main-key", "");

                SubNode actorAccnt = arun.run(as -> read.findNodeByProp(as, NodeProp.ACT_PUB_ACTOR_ID.s(), actorId));
                if (actorAccnt != null) {
                    log.debug("got Actor: " + actorAccnt.getIdStr());

                    // create a MongoSession representing the user account we just looked up
                    // MongoSession userSess = MongoSession(actorAccnt.getStr(NodeProp.USER), actorAccnt.getId());

                    // now verify they have access to this node
                    // for now all we do is show results in log file until we prove this works.
                    try {
                        // auth.auth(userSess, node, PrivilegeType.READ);
                        // log.debug("auth granted.");

                        /*
                         * final step is just validate the signature.
                         * 
                         * todo-1: in all cases where we grab a public key off our own DB, and use it, we need to have a
                         * fallback logic where we check by reading the actual actor object again off the original host and
                         * if the publickey has changed for whatever reason we need to update it in our own db.
                         */
                        PublicKey pubKey = apCrypto.getPublicKeyFromEncoding(actorAccnt.getStr(NodeProp.ACT_PUB_KEYPEM));
                        if (pubKey != null) {
                            try {
                                log.debug("Checking with pubKey.");
                                apCrypto.verifySignature(httpReq, pubKey, null);
                                log.debug("Sig ok.");

                                // this will remain PUBLIC until we set it here.
                                // by commenting out this settor of sharedTo, we can leave this 'experimental' block harmlessly
                                // turned on
                                // just so we collect the logging to tell us if my assumptions are correct.
                                // sharedTo = actorAccnt.getIdStr();
                            } catch (Exception e) {
                                log.error("Sig failed: getOutboxItems user=" + userName);
                                return null;
                            }
                        }
                    } catch (Exception e) {
                        log.debug("access DENIED.");
                    }
                }
            }
        }

        try {
            SubNode userNode = read.getUserNodeByUserName(null, userName);
            if (userNode == null) {
                return null;
            }

            final String _sharedTo = sharedTo;
            retItems = (APList) arun.run(as -> {
                APList items = new APList();
                int MAX_PER_PAGE = 25;
                boolean collecting = false;

                // todo-1: is this just unfinished code or a bug? Either way this paging is broken.
                if (minId == null) {
                    collecting = true;
                }

                // log.debug("collecting = " + collecting);
                List<String> sharedToList = new LinkedList<String>();
                sharedToList.add(_sharedTo);

                for (SubNode child : auth.searchSubGraphByAclUser(as, null, sharedToList,
                        Sort.by(Sort.Direction.DESC, SubNode.MODIFY_TIME), MAX_PER_PAGE, userNode.getOwner())) {

                    // log.debug("OUTBOX ID: " + child.getIdStr());

                    // as a general security rule never send back any admin nodes.
                    if (child.getOwner().equals(as.getUserNodeId())) {
                        continue;
                    }

                    if (items.size() >= MAX_PER_PAGE) {
                        // ocPage.setPrev(outboxBase + "?page=" + String.valueOf(pgNo - 1));
                        // ocPage.setNext(outboxBase + "?page=" + String.valueOf(pgNo + 1));
                        break;
                    }

                    if (collecting) {
                        String boostTarget = child.getStr(NodeProp.BOOST);

                        APObj ret = null;

                        /*
                         * This branch of code is not yet tested (not sure how to get foreign server to run this), but
                         * similar code is working. There is one other similar block of code elsewhere in the app where
                         * boosts are published.
                         */
                        if (!StringUtils.isEmpty(boostTarget)) {
                            // log.debug("processing boostTarget: " + boostTarget + " in outbox gen for " + userName);
                            String published = DateUtil.isoStringFromDate(child.getModifyTime());
                            String actor = apUtil.makeActorUrlForUserName(userName);
                            String id = nodeIdBase + child.getIdStr();

                            // To create an Announce that other instances can consume we go to the boosted Node ID,
                            // and get it's ACT_PUB_OBJ_URL property and use that for the object being boosted
                            SubNode boostTargetNode = read.getNode(as, boostTarget);
                            if (boostTargetNode != null) {
                                String boostedId = boostTargetNode.getStr(NodeProp.OBJECT_ID);

                                if (boostedId == null) {
                                    boostedId = host + "?id=" + boostTarget;
                                }

                                ret = new APOAnnounce(actor, id, published, boostedId);
                                // log.debug("Outbound Announce (from outbox): " + XString.prettyPrint(ret));
                            }
                        } else {
                            // log.debug("not a boost.");
                            ret = apFactory.makeAPOCreateNote(as, userName, nodeIdBase, child);
                        }

                        if (ret != null) {
                            items.add(ret);
                        }
                    }
                }

                return items;
            });

        } catch (Exception e) {
            log.error("failed generating outbox page: ", e);
            throw new RuntimeException(e);
        }
        return retItems;
    }

    /* Gets the object identified by nodeId as an ActPub object */
    public APObj getResource(HttpServletRequest httpReq, String nodeId) {
        if (nodeId == null)
            return null;

        // this is breaking whenever a CURL command is used other scenarios
        // where no signature is in the header.
        boolean experimental = false;
        log.debug("getResource: " + nodeId);

        return (APObj) arun.run(as -> {
            SubNode node = read.getNode(as, nodeId);
            if (!(node != null)) {
                throw new RuntimeException("Node not found: " + nodeId);
            }

            acl.failIfAdminOwned(node);
            boolean authSuccess = false;

            // leaving this turned on, for now just to collect info into the logs about how things work.
            if (experimental) {
                /*
                 * todo-2: A basic 'search' for an object by URL in mastodon calls into here with a generic
                 * non-user-specific keyId so I currently don't know how to exercise this code 'in the wild', but
                 * want to keep it for now.
                 * 
                 * Actually the implementation of this needs to be a refactored version which probably uses
                 * subGraphByAclUser_query since that's a way to get ONLY nodes authorized for specific users in
                 * query....and after writing this I now realize the above 'outbox query' way of doing this even
                 * when querying a single node (like we do here) is more efficient? maybe or maybe not. investigate.
                 */
                Val<String> keyId = new Val<>();
                Val<String> signature = new Val<>();
                Val<List<String>> headers = new Val<>();

                apCrypto.parseHttpHeaderSig(httpReq, keyId, signature, false, headers);

                if (keyId.getVal() != null) {
                    // keyId should be like this:
                    // actorId + "#main-key"
                    String actorId = keyId.getVal().replace("#main-key", "");

                    SubNode actorAccnt = read.findNodeByProp(as, NodeProp.ACT_PUB_ACTOR_ID.s(), actorId);
                    if (actorAccnt != null) {
                        log.debug("got Actor: " + actorAccnt.getIdStr());

                        // create a MongoSession representing the user account we just looked up
                        MongoSession userSess = new MongoSession(actorAccnt.getStr(NodeProp.USER), actorAccnt.getId());

                        // now verify they have access to this node
                        // for now all we do is show results in log file until we prove this works.
                        try {
                            auth.auth(userSess, node, PrivilegeType.READ);
                            log.debug("auth granted.");

                            // final step is just validate the signature.
                            PublicKey pubKey = apCrypto.getPublicKeyFromEncoding(actorAccnt.getStr(NodeProp.ACT_PUB_KEYPEM));
                            if (pubKey != null) {
                                try {
                                    log.debug("Checking with pubKey.");
                                    apCrypto.verifySignature(httpReq, pubKey, null);
                                    authSuccess = true;
                                    log.debug("Sig ok.");
                                } catch (Exception e) {
                                    log.error("Sig failed. getResource: nodeId=" + nodeId);
                                    return null;
                                }
                            }
                        } catch (Exception e) {
                            log.debug("access DENIED.");
                        }
                    }
                }
            }

            // if not authorized and not a public node fail now.
            if (!authSuccess && !AclService.isPublic(as, node)) {
                log.debug("getResource failed on non-public node: " + node.getIdStr());
                throw new NodeAuthFailedException();
            }

            /*
             * todo-1: We should be able to get an object as whatever actual type it is based on the type (not
             * the Quanta Type, but the ActPub type if there is one), rather than always returning a note here.
             */
            APObj ret = apFactory.makeAPONote(as, node, null);
            if (ret != null) {
                apLog.trace("Reply with Object: " + XString.prettyPrint(ret));
            }
            return ret;
        });
    }
}
