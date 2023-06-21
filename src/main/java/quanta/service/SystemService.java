package quanta.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoDatabase;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import quanta.actpub.APConst;
import quanta.config.AppSessionListener;
import quanta.config.ServiceBase;
import quanta.config.SessionContext;
import quanta.model.UserStats;
import quanta.model.client.Attachment;
import quanta.model.client.Constant;
import quanta.model.client.NodeProp;
import quanta.model.client.NostrEvent;
import quanta.model.client.NostrEventWrapper;
import quanta.model.client.NostrQuery;
import quanta.model.ipfs.file.IPFSObjectStat;
import quanta.mongo.MongoAppConfig;
import quanta.mongo.MongoRepository;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.response.FriendInfo;
import quanta.response.GetPeopleResponse;
import quanta.util.Const;
import quanta.util.DateUtil;
import quanta.util.ExUtil;
import quanta.util.ThreadLocals;
import quanta.util.TreeNode;
import quanta.util.Util;
import quanta.util.XString;
import quanta.util.val.IntVal;

/**
 * Service methods for System related functions. Admin functions.
 */
@Component
public class SystemService extends ServiceBase {

    private static Logger log = LoggerFactory.getLogger(SystemService.class);
    private static final Random rand = new Random();
    private static final int replicaId = rand.nextInt(Integer.MAX_VALUE);

    long lastNostrQueryTime = 0L;
    private static final RestTemplate restTemplate = new RestTemplate(Util.getClientHttpRequestFactory(10000));
    public static final ObjectMapper mapper = new ObjectMapper();

    // These two cache collections are for the purpose of being sure that almost all browsing
    // of the admin content (landing page, website) can be done without any DB querying at all and
    // come directly from server memory.
    public static final Object adminNodesCacheLock = new Object();
    public TreeNode adminNodesCache;
    public HashMap<String, TreeNode> adminNodesCacheMap;

    {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        ServiceBase.init(event.getApplicationContext());
    }

    public void cacheAdminNodes() {
        arun.run(as -> {
            String id = prop.getUserLandingPageNode();
            SubNode node = read.getNode(as, id, false);
            if (node != null) {
                synchronized (adminNodesCacheLock) {
                    adminNodesCacheMap = new HashMap<String, TreeNode>();
                    adminNodesCache = read.getSubGraphTree(as, node.getIdStr(), null, adminNodesCacheMap);
                }
            }
            return null;
        });
    }

    public String rebuildIndexes() {
        ThreadLocals.requireAdmin();
        arun.run(as -> {
            mongoUtil.rebuildIndexes(as);
            return null;
        });
        return "success.";
    }

    /*
     * This was created to make it easier to test the orphan handling functions, so we can intentionally
     * create orphans by deleting a node and expecting all it's orphans to stay there and we can test if
     * our orphan deleter can delete them.
     */
    public String deleteLeavingOrphans(MongoSession ms, String nodeId) {
        SubNode node = read.getNode(ms, nodeId);
        delete.delete(ms, node);
        return "Success.";
    }

    public String runConversion() {
        String ret = "";
        try {
            prop.setDaemonsEnabled(false);
            arun.run(as -> {
                // different types of database conversions can be put here as needed
                // mongoUtil.fixSharing(ms);
                return null;
            });
            ret = "Completed ok.";
        } finally {
            //
            prop.setDaemonsEnabled(true);
        }
        return ret;
    }

    public String compactDb() {
        String ret = "";
        try {
            prop.setDaemonsEnabled(false);
            delete.deleteNodeOrphans();
            // do not delete.
            // usrMgr.cleanUserAccounts();
            /*
             * Create map to hold all user account storage statistics which gets updated by the various
             * processing in here and then written out in 'writeUserStats' below
             */
            final HashMap<ObjectId, UserStats> statsMap = new HashMap<>();
            attach.gridMaintenanceScan(statsMap);
            if (prop.ipfsEnabled()) {
                ret = ipfsGarbageCollect(statsMap);
            }
            arun.run(as -> {
                user.writeUserStats(as, statsMap);
                return null;
            });
            ret += runMongoDbCommand(MongoAppConfig.databaseName, new Document("compact", "nodes"));
            ret += "\n\nRemember to Rebuild Indexes next. Or else the system can be slow.";
        } finally {
            //
            prop.setDaemonsEnabled(true);
        }
        return ret;
    }

    public String ipfsGarbageCollect(HashMap<ObjectId, UserStats> statsMap) {
        if (!prop.ipfsEnabled()) return "IPFS Disabled.";
        String ret = ipfsRepo.gc();
        ret += update.releaseOrphanIPFSPins(statsMap);
        return ret;
    }

    // https://docs.mongodb.com/manual/reference/command/validate/
    // db.runCommand(
    // {
    // validate: <string>, // Collection name
    // full: <boolean>, // Optional
    // repair: <boolean>, // Optional, added in MongoDB 5.0
    // metadata: <boolean> // Optional, added in MongoDB 5.0.4
    // })
    public String validateDb() {
        String ret =
            "validate: " +
            runMongoDbCommand(
                MongoAppConfig.databaseName, //
                //
                new Document("validate", "nodes").append("full", true)
            );
        ret +=
            "\n\ndbStats: " +
            runMongoDbCommand(
                MongoAppConfig.databaseName, //
                new Document("dbStats", 1).append("scale", 1024)
            );
        ret += "\n\nusersInfo: " + runMongoDbCommand("admin", new Document("usersInfo", 1));
        if (prop.ipfsEnabled()) {
            ret += ipfsRepo.verify();
            ret += ipfsPin.verify();
        }
        return ret;
    }

    public String repairDb() {
        update.runRepairs();
        return "Repair completed ok.";
    }

    public String runMongoDbCommand(String dbName, Document doc) {
        // NOTE: Use "admin" as databse name to run admin commands like changeUserPassword
        MongoDatabase database = mdbf.getMongoDatabase(dbName);
        Document result = database.runCommand(doc);
        return XString.prettyPrint(result);
    }

    public static void logMemory() {
        // Runtime runtime = Runtime.getRuntime();
        // long freeMem = runtime.freeMemory() / ONE_MB;
        // long maxMem = runtime.maxMemory() / ONE_MB;
        // log.info(String.format("GC Cycle. FreeMem=%dMB, MaxMem=%dMB", freeMem,
        // maxMem));
    }

    public String getJson(MongoSession ms, String nodeId) {
        SubNode node = read.getNode(ms, nodeId, true, null);
        if (node != null) {
            String ret = XString.prettyPrint(node);
            List<Attachment> atts = node.getOrderedAttachments();
            if (atts != null) {
                for (Attachment att : atts) {
                    if (att.getIpfsLink() != null) {
                        IPFSObjectStat fullStat = ipfsObj.objectStat(att.getIpfsLink(), false);
                        if (fullStat != null) {
                            ret += "\n\nIPFS Object Stats:\n" + XString.prettyPrint(fullStat);
                        }
                    }
                }
            }
            if (ms.isAdmin()) {
                ret += "\n\n";
                ret += "English: " + (english.isEnglish(node.getContent()) ? "Yes" : "No") + "\n";
                ret += "Profanity: " + (english.hasBadWords(node.getContent()) ? "Yes" : "No") + "\n";
            }
            return ret;
        } else {
            return "node not found!";
        }
    }

    public String getSystemInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Swarm Replica ID: " + SystemService.replicaId + "\n");
        sb.append("AuditFilter Enabed: " + String.valueOf(AppFilter.audit) + "\n");
        sb.append("Daemons Enabed: " + String.valueOf(prop.isDaemonsEnabled()) + "\n");

        sb.append(getRedisReport());
        sb.append("HttpSessions: " + AppSessionListener.sessionCounter);

        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long freeMem = runtime.freeMemory() / Const.ONE_MB;
        sb.append(String.format("Server Free Mem: %dMB\n", freeMem));

        sb.append("Node Count: " + read.getNodeCount() + "\n");
        sb.append("Attachment Count: " + attach.getGridItemCount() + "\n");
        sb.append(user.getUserAccountsReport(null));
        sb.append(apub.getStatsReport());
        if (!StringUtils.isEmpty(prop.getIPFSApiHostAndPort())) {
            sb.append(ipfsConfig.getStat());
        }
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        List<String> arguments = runtimeMxBean.getInputArguments();
        sb.append("\nJava VM args:\n");
        for (String arg : arguments) {
            sb.append(arg + "\n");
        }

        sb.append("\nEnvironment Vars:\n");
        sb.append(getEnvironment());

        // Run command inside container
        // sb.append(runBashCommand("DISK STORAGE (Docker Container)", "df -h"));
        return sb.toString();
    }

    public String getRedisReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("User Sessions (Redis): \n");
        List<SessionContext> list = user.redisQuery("*");
        for (SessionContext sc : list) {
            sb.append("    " + sc.getUserName() + " " + sc.getUserToken() + "\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    public String getEnvironment() {
        Map<String, String> env = System.getenv();
        LinkedList<String> envList = new LinkedList<String>();
        env.forEach((k, v) -> {
            if (k.toLowerCase().contains("pass")) return;
            envList.add(k + ":" + v + "\n");
        });
        envList.sort(null);
        StringBuilder sb = new StringBuilder();
        envList.forEach(v -> sb.append(v));
        return sb.toString();
    }

    // tserver-tag
    @Scheduled(fixedDelay = 20 * DateUtil.MINUTE_MILLIS)
    public String nostrQueryUpdate() {
        if (!MongoRepository.fullInit) return "App not yet ready";

        if (!prop.isNostrDaemonEnabled()) {
            return "nostrDaemon not enabled";
        }
        HashMap<String, Object> message = new HashMap<>();
        SubNode root = read.getDbRoot();
        String relays = root.getStr(NodeProp.NOSTR_RELAYS);
        List<String> relayList = XString.tokenize(relays, "\n\r", true);
        message.put("relays", relayList);
        log.debug("nostrQueryUpdate: relays: " + XString.prettyPrint(relayList));
        HashSet<String> authorsSet = new HashSet<>();
        arun.run(as -> {
            // For all nostr curation users gather their nostr friends' pubkeys into authorsSet
            final List<String> curationUsers = XString.tokenize(prop.getNostrCurationAccounts(), ",", true);
            log.debug("curationUsers=" + XString.prettyPrint(curationUsers));
            if (curationUsers != null) {
                for (String cuser : curationUsers) {
                    GetPeopleResponse adminFriends = user.getPeople(as, cuser, "friends", Constant.NETWORK_NOSTR.s());
                    if (adminFriends != null && adminFriends.getPeople() != null) {
                        for (FriendInfo fi : adminFriends.getPeople()) {
                            authorsSet.add(fi.getUserName().substring(1));
                        }
                    }
                }
            }
            return null;
        });
        if (authorsSet.size() == 0) {
            return "No friends on admin account to query for";
        }
        List<String> authors = new LinkedList<>(authorsSet);
        message.put("authors", authors);
        List<Integer> kinds = new LinkedList<>();
        kinds.add(1);
        NostrQuery query = new NostrQuery();
        query.setAuthors(authors);
        query.setKinds(kinds);
        query.setLimit(100);
        if (lastNostrQueryTime != 0L) {
            query.setSince(lastNostrQueryTime / 1000);
        }
        lastNostrQueryTime = new Date().getTime();
        message.put("query", query);
        // tserver-tag (put TSERVER_API_KEY in secrets file)
        message.put("apiKey", prop.getTServerApiKey());
        String body = XString.prettyPrint(message);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(APConst.MTYPE_JSON));
        headers.setContentType(APConst.MTYPE_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);
        String url = "http://tserver-host:" + prop.getTServerPort() + "/nostr-query";
        ResponseEntity<List<NostrEvent>> response = restTemplate.exchange(
            url,
            HttpMethod.POST,
            requestEntity,
            new ParameterizedTypeReference<List<NostrEvent>>() {}
        );
        IntVal saveCount = new IntVal(0);
        HashSet<String> accountNodeIds = new HashSet<>();
        List<String> eventNodeIds = new ArrayList<>();
        int eventCount = response.getBody().size();
        arun.run(as -> {
            for (NostrEvent event : response.getBody()) {
                // log.debug("SAVE NostrEvent from TServer: " + XString.prettyPrint(event));
                NostrEventWrapper ne = new NostrEventWrapper();
                ne.setEvent(event);
                nostr.saveEvent(as, ne, accountNodeIds, eventNodeIds, saveCount);
            }
            return null;
        });
        return (
            "NostrQueryUpdate: relays=" +
            relayList.size() +
            " people=" +
            authors.size() +
            " eventCount=" +
            eventCount +
            " newCount=" +
            saveCount.getVal()
        );
    }

    private static String runBashCommand(String title, String command) {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command("bash", "-c", command);
        // pb.directory(new File(dir));
        // pb.redirectErrorStream(true);
        StringBuilder output = new StringBuilder();
        output.append("\n\n");
        output.append(title);
        output.append("\n");
        try {
            Process p = pb.start();
            String s;
            BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));

            while ((s = stdout.readLine()) != null) {
                output.append(s);
                output.append("\n");
            }
        } catch (
            // output.append("Exit value: " + p.waitFor());
            // p.getInputStream().close();
            // p.getOutputStream().close();
            // p.getErrorStream().close();
            Exception e
        ) {
            ExUtil.error(log, "Unable to run script", e);
        }
        output.append("\n\n");
        return output.toString();
    }
}
