package quanta.service;

import static quanta.util.Util.no;
import static quanta.util.Util.ok;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.mongodb.client.MongoDatabase;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import quanta.AppController;
import quanta.config.AppProp;
import quanta.config.AppSessionListener;
import quanta.config.ServiceBase;
import quanta.config.SessionContext;
import quanta.filter.HitFilter;
import quanta.model.UserStats;
import quanta.model.client.NodeProp;
import quanta.model.ipfs.file.IPFSObjectStat;
import quanta.mongo.MongoAppConfig;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.util.Const;
import quanta.util.ExUtil;
import quanta.util.ThreadLocals;
import quanta.util.XString;

/**
 * Service methods for System related functions. Admin functions.
 */

@Component
public class SystemService extends ServiceBase {
	private static final Logger log = LoggerFactory.getLogger(SystemService.class);

	@Autowired
	private AppProp prop;

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
	 * create orphans
	 */
	public String deleteLeavingOrphans(MongoSession ms, String nodeId) {
		SubNode node = read.getNode(ms, nodeId);
		delete.delete(node);
		return "Success.";
	}

	public String compactDb() {
		delete.deleteNodeOrphans(null);
		// do not delete.
		// usrMgr.cleanUserAccounts();

		/*
		 * Create map to hold all user account storage statistics which gets updated by the various
		 * processing in here and then written out in 'writeUserStats' below
		 */
		final HashMap<ObjectId, UserStats> statsMap = new HashMap<>();

		attach.gridMaintenanceScan(statsMap);
		String ret = ipfsGarbageCollect(statsMap);

		arun.run(ms -> {
			user.writeUserStats(ms, statsMap);
			return null;
		});

		ret += runMongoDbCommand(new Document("compact", "nodes"));
		return ret;
	}

	public String ipfsGarbageCollect(HashMap<ObjectId, UserStats> statsMap) {
		String ret = ipfs.getRepoGC();
		ret += update.releaseOrphanIPFSPins(statsMap);
		return ret;
	}

	public String validateDb() {
		// https://docs.mongodb.com/manual/reference/command/validate/
		String ret = runMongoDbCommand(new Document("validate", "nodes").append("full", true));
		ret += ipfs.repoVerify();
		ret += ipfs.pinVerify();
		return ret;
	}

	public String runMongoDbCommand(Document doc) {
		MongoDatabase database = mdbf.getMongoDatabase(MongoAppConfig.databaseName);
		Document result = database.runCommand(doc);

		StringBuilder ret = new StringBuilder();
		ret.append("Results:\n");
		for (Map.Entry<String, Object> set : result.entrySet()) {
			ret.append(String.format("%s: %s\n", set.getKey(), set.getValue()));
		}
		return ret.toString();
	}

	public static void logMemory() {
		// Runtime runtime = Runtime.getRuntime();
		// long freeMem = runtime.freeMemory() / ONE_MB;
		// long maxMem = runtime.maxMemory() / ONE_MB;
		// log.info(String.format("GC Cycle. FreeMem=%dMB, MaxMem=%dMB", freeMem,
		// maxMem));
	}

	public String getJson(MongoSession ms, String nodeId) {
		SubNode node = read.getNode(ms, nodeId, true);
		if (ok(node)) {
			String ret = XString.prettyPrint(node);

			String ipfsLink = node.getStr(NodeProp.IPFS_LINK);
			if (ok(ipfsLink)) {
				IPFSObjectStat fullStat = ipfs.objectStat(ipfsLink, false);
				if (ok(fullStat)) {
					ret += "\n\nIPFS Object Stats:\n" + XString.prettyPrint(fullStat);
				}
			}

			return ret;
		} else {
			return "node not found!";
		}
	}

	public String getSystemInfo() {
		StringBuilder sb = new StringBuilder();
		sb.append("Daemons Enabed: " + String.valueOf(prop.isDaemonsEnabled()) + "\n");
		Runtime runtime = Runtime.getRuntime();
		runtime.gc();
		long freeMem = runtime.freeMemory() / Const.ONE_MB;
		sb.append(String.format("Server Free Mem: %dMB\n", freeMem));
		sb.append(String.format("Sessions: %d\n", AppSessionListener.getSessionCounter()));
		sb.append(getIpReport());
		sb.append("Node Count: " + read.getNodeCount(null) + "\n");
		sb.append("Attachment Count: " + attach.getGridItemCount() + "\n");
		sb.append(user.getUserAccountsReport(null));

		sb.append(apub.getStatsReport());

		if (!StringUtils.isEmpty(prop.getIPFSApiHostAndPort())) {
			sb.append(ipfs.getRepoStat());
		}

		RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
		List<String> arguments = runtimeMxBean.getInputArguments();
		sb.append("\nJava VM args:\n");
		for (String arg : arguments) {
			sb.append(arg + "\n");
		}

		// Run command inside container
		// sb.append(runBashCommand("DISK STORAGE (Docker Container)", "df -h"));
		return sb.toString();
	}

	public String getSessionActivity() {
		StringBuilder sb = new StringBuilder();

		List<SessionContext> sessions = SessionContext.getHistoricalSessions();
		sessions.sort((s1, s2) -> {
			String s1key = s1.getUserName() + "/" + (no(s1.getIp()) ? "?" : s1.getIp());
			String s2key = s2.getUserName() + "/" + (no(s2.getIp()) ? "?" : s2.getIp());
			return s1key.compareTo(s2key);
		});

		sb.append("Live Sessions:\n");
		for (SessionContext s : sessions) {
			if (s.isLive()) {
				sb.append("User: ");
				sb.append(s.getUserName() + "/" + (no(s.getIp()) ? "?" : s.getIp()));
				sb.append("\n");
				sb.append(s.dumpActions("      ", 3));
			}
		}

		sb.append("\nPast Sessions:\n");
		for (SessionContext s : sessions) {
			if (!s.isLive()) {
				sb.append("User: ");
				sb.append(s.getPastUserName() + "/" + (no(s.getIp()) ? "?" : s.getIp()));
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
			while (ok(s = stdout.readLine())) {
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
	private static String getIpReport() {
		return "Unique IPs: " + HitFilter.getUniqueIpHits().size() + "\n" + "Unique USER IPs: " //
				+ AppController.uniqueUserIpHits.size() + "\n";

		// StringBuilder sb = new StringBuilder();
		// sb.append("Unique IPs During Run<br>");
		// int count = 0;
		// HashMap<String, Integer> map = HitFilter.getUniqueIpHits();
		// synchronized (map) {
		// for (String key : map.keySet()) {
		// int hits = map.get(key);
		// sb.append("IP=" + key + " hits=" + hits);
		// sb.append("<br>");
		// count++;
		// }
		// }
		// sb.append("count=" + count + "<br>");
		// return sb.toString();
	}
}
