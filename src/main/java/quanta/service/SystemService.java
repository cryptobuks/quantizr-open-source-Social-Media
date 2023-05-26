package quanta.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import javax.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoDatabase;
import lombok.extern.slf4j.Slf4j;
import quanta.actpub.APConst;
import quanta.config.AppSessionListener;
import quanta.config.ServiceBase;
import quanta.config.SessionContext;
import quanta.filter.AuditFilter;
import quanta.filter.HitFilter;
import quanta.model.UserStats;
import quanta.model.client.Attachment;
import quanta.model.client.Constant;
import quanta.model.client.NodeProp;
import quanta.model.client.NostrEvent;
import quanta.model.client.NostrEventEx;
import quanta.model.client.NostrQuery;
import quanta.model.client.PrincipalName;
import quanta.model.ipfs.file.IPFSObjectStat;
import quanta.mongo.MongoAppConfig;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.response.FriendInfo;
import quanta.response.GetPeopleResponse;
import quanta.response.PushPageMessage;
import quanta.util.Const;
import quanta.util.DateUtil;
import quanta.util.ExUtil;
import quanta.util.ThreadLocals;
import quanta.util.Util;
import quanta.util.XString;
import quanta.util.val.IntVal;

/**
 * Service methods for System related functions. Admin functions.
 */

@Component
@Slf4j
public class SystemService extends ServiceBase {

	private static final RestTemplate restTemplate = new RestTemplate(Util.getClientHttpRequestFactory(10000));
	public static final ObjectMapper mapper = new ObjectMapper();
	{
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
		} //
		finally {
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
		}
		//
		finally {
			prop.setDaemonsEnabled(true);
		}
		return ret;
	}

	public String ipfsGarbageCollect(HashMap<ObjectId, UserStats> statsMap) {
		if (!prop.ipfsEnabled())
			return "IPFS Disabled.";
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
		String ret = "validate: " + runMongoDbCommand(MongoAppConfig.databaseName, //
				new Document("validate", "nodes")//
						.append("full", true));

		ret += "\n\ndbStats: " + runMongoDbCommand(MongoAppConfig.databaseName, //
				new Document("dbStats", 1).append("scale", 1024));

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
		sb.append("AuditFilter Enabed: " + String.valueOf(AuditFilter.enabled) + "\n");
		sb.append("Daemons Enabed: " + String.valueOf(prop.isDaemonsEnabled()) + "\n");
		Runtime runtime = Runtime.getRuntime();
		runtime.gc();
		long freeMem = runtime.freeMemory() / Const.ONE_MB;
		sb.append(String.format("Server Free Mem: %dMB\n", freeMem));
		sb.append(String.format("Sessions: %d\n", AppSessionListener.getSessionCounter()));
		sb.append(getSessionReport());
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

		sb.append("\nNostr Query TServer:\n" + nostrQueryUpdate() + "\n");

		// Run command inside container
		// sb.append(runBashCommand("DISK STORAGE (Docker Container)", "df -h"));
		return sb.toString();
	}

	// tserver-tag
	@Scheduled(fixedDelay = 30 * DateUtil.MINUTE_MILLIS)
	public String nostrQueryUpdate() {
		if (!prop.isNostrDaemonEnabled()) {
			return "nostrDeamon not enabled";
		}

		HashMap<String, Object> message = new HashMap<>();

		SubNode root = read.getDbRoot();
		String relays = root.getStr(NodeProp.NOSTR_RELAYS);
		List<String> relayList = XString.tokenize(relays, "\n\r", true);
		message.put("relays", relayList);

		log.debug("nostrQueryUpdate: relays: " + XString.prettyPrint(relayList));

		HashSet<String> authorsSet = new HashSet<>();

		GetPeopleResponse adminFriends = arun.run(as -> {
			GetPeopleResponse ret1 = user.getPeople(as, PrincipalName.ADMIN.s(), "friends", Constant.NETWORK_NOSTR.s());
			return ret1;
		});

		for (FriendInfo fi : adminFriends.getPeople()) {
			authorsSet.add(fi.getUserName().substring(1));
		}

		GetPeopleResponse userFriends = arun.run(as -> {
			// todo-0: getting from user "clay" is a temporary hack until I have a way to populate admin account
			// with enough Nostr people for a decent curated feed.
			GetPeopleResponse ret1 = user.getPeople(as, "clay", "friends", Constant.NETWORK_NOSTR.s());
			return ret1;
		});

		for (FriendInfo fi : userFriends.getPeople()) {
			authorsSet.add(fi.getUserName().substring(1));
		}

		List<String> authors = new LinkedList<>(authorsSet);

		message.put("authors", authors);
		List<Integer> kinds = new LinkedList<>();
		kinds.add(1);
		NostrQuery query = new NostrQuery();
		query.setAuthors(authors);
		query.setKinds(kinds);
		query.setLimit(200);
		message.put("query", query);

		// tserver-tag (put TSERVER_API_KEY in secrets file)
		message.put("apiKey", prop.getTServerApiKey());

		String body = XString.prettyPrint(message);

		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(APConst.MTYPE_JSON));
		headers.setContentType(APConst.MTYPE_JSON);

		HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);
		String url = "http://tserver-host:" + prop.getTServerPort() + "/nostr-query";

		ResponseEntity<List<NostrEventEx>> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity,
				new ParameterizedTypeReference<List<NostrEventEx>>() {});

		IntVal saveCount = new IntVal(0);
		HashSet<String> accountNodeIds = new HashSet<>();
		List<String> eventNodeIds = new ArrayList<>();
		int eventCount = response.getBody().size();
		arun.run(as -> {
			for (NostrEventEx event : response.getBody()) {
				log.debug("SAVE NostrEvent from TServer: " + XString.prettyPrint(event));

				NostrEvent ev = new NostrEvent();
				ev.setId(event.getId());
				ev.setSig(event.getSig());
				ev.setKind(event.getKind());
				ev.setPk(event.getPubkey());
				ev.setTimestamp(event.getCreatedAt());
				ev.setTags(event.getTags());
				ev.setContent(event.getContent());

				nostr.saveEvent(as, ev, accountNodeIds, eventNodeIds, saveCount);
			}
			return null;
		});

		return "NostrQueryUpdate: relays=" + relayList.size() + " people=" + authors.size() + " eventCount=" + eventCount
				+ " newCount=" + saveCount.getVal();
	}

	// For now this is for server restart notify, but will eventually be a general broadcast messenger.
	// work in progress.
	public String sendAdminNote() {
		int sessionCount = 0;
		for (SessionContext sc : SessionContext.getAllSessions(false, true)) {
			HttpSession httpSess = ThreadLocals.getHttpSession();
			log.debug("Send admin note to: " + sc.getUserName() + " sessId: " + httpSess.getId());
			// need custom messages support pushed by admin
			push.sendServerPushInfo(sc,
					new PushPageMessage("Server " + prop.getMetaHost()
							+ "  will restart for maintenance soon.<p><p>When you get an error, just refresh your browser.",
							true));
			sessionCount++;
		}

		return String.valueOf(sessionCount) + " sessions notified.";
	}

	public String getSessionActivity() {
		StringBuilder sb = new StringBuilder();

		List<SessionContext> sessions = SessionContext.getHistoricalSessions();
		sessions.sort((s1, s2) -> s1.getUserName().compareTo(s2.getUserName()));

		sb.append("Live Sessions:\n");
		for (SessionContext s : sessions) {
			if (s.isLive()) {
				sb.append("User: ");
				sb.append(s.getUserName());
				sb.append("\n");
				sb.append(s.dumpActions("      ", 3));
			}
		}

		sb.append("\nPast Sessions:\n");
		for (SessionContext s : sessions) {
			if (!s.isLive()) {
				sb.append("User: ");
				sb.append(s.getPastUserName());
				sb.append("\n");
				sb.append(s.dumpActions("      ", 3));
			}
		}
		return sb.toString();
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

			// output.append("Exit value: " + p.waitFor());
			// p.getInputStream().close();
			// p.getOutputStream().close();
			// p.getErrorStream().close();
		} catch (Exception e) {
			ExUtil.error(log, "Unable to run script", e);
		}
		output.append("\n\n");
		return output.toString();
	}

	/*
	 * uniqueIps are all IPs even comming from foreign FediverseServers, but uniqueUserIps are the ones
	 * that represent actual users accessing thru their browsers
	 */
	private static String getSessionReport() {
		StringBuilder sb = new StringBuilder();
		sb.append("All Sessions (over 20 hits)\n");
		HashMap<String, Integer> map = HitFilter.getHits();
		synchronized (map) {
			for (String key : map.keySet()) {
				int hits = map.get(key);
				if (hits > 20) {
					sb.append("    " + key + " hits=" + hits + "\n");
				}
			}
		}

		sb.append("Live Sessions:\n");
		for (SessionContext sc : SessionContext.getAllSessions(false, true)) {
			if (sc.isLive() && sc.getUserName() != null) {
				Integer hits = map.get(sc.getSession().getId());
				sb.append("    " + sc.getUserName() + " hits=" + (hits != null ? String.valueOf(hits) : "?"));
				sb.append("\n");
			}
		}
		sb.append("\n");

		return sb.toString();
	}
}
