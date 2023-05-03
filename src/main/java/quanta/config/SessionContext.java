package quanta.config;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import javax.servlet.http.HttpSession;
import org.bson.types.ObjectId;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import lombok.extern.slf4j.Slf4j;
import quanta.model.UserPreferences;
import quanta.model.client.NodeProp;
import quanta.model.client.PrincipalName;
import quanta.mongo.MongoUtil;
import quanta.mongo.model.SubNode;
import quanta.util.ThreadLocals;
import quanta.util.Util;

/**
 * Session object holding state per user session.
 * 
 * 1: Need to refactor so that the only session-specific data are things that apply to THIS server
 * node instance and wouldn't be something that would make load balancer nodes have issues.
 */
@Component
@Scope("prototype")
@Slf4j 
public class SessionContext extends ServiceBase {
	private HttpSession session;
	private String urlIdFailMsg;
	private String userMsg;
	private String displayUserProfileId;
	private String initialNodeId;
	private String loadNostrId;
	private String loadNostrIdRelays;

	public PublicKey pubSigKey = null;

	// DO NOT DELETE (keep for future ref)
	// implements InitializingBean, DisposableBean {

	public static final String QSC = "QSC";
	private boolean live = true;

	/* Identification of user's account root node. */
	private String rootId;

	/*
	 * When the user does a "Timeline" search we store the path of the node the timeline was done on so
	 * that with a simple substring search, we can detect any time a new node is added that would've
	 * appeared in the timeline and then do a server push to browsers of any new nodes, thereby creating
	 * a realtime view of the timeline, making it become like a "chat room"
	 */
	private String timelinePath;

	private String userName = PrincipalName.ANON.s();
	private ObjectId userNodeId;
	private String pastUserName = userName;
	private String timezone;
	private String timeZoneAbbrev;

	private String allowedFeatures = "";

	// variable not currently being used (due to refactoring)
	private long lastLoginTime;
	private long lastActiveTime;

	private UserPreferences userPreferences;

	public int counter;

	/* Emitter for sending push notifications to the client */
	private SseEmitter pushEmitter = new SseEmitter();

	// this one WILL work with multiple sessions per user
	private static final HashSet<SessionContext> allSessions = new HashSet<>();

	// Full list of active and inactive (dead) sessions.
	public static final HashSet<SessionContext> historicalSessions = new HashSet<>();

	/* keeps track of total calls to each URI */
	public HashMap<String, Integer> actionCounters = new HashMap<>();

	private String captcha;
	private int captchaFails = 0;

	private String userToken;
	private String appGuid;

	private boolean enableIPSM;

	// this gets set to true, to trigger a refresh when needed again.
	private boolean friendsTagsDirty;

	// whenever we know the user is refreshing browser this are the things to reset.
	public void urlAccessReset() {
		urlIdFailMsg = null;
		userMsg = null;
		displayUserProfileId = null;
		initialNodeId = null;	
		loadNostrIdRelays = null;	
	}

	public boolean isEnableIPSM() {
		return enableIPSM;
	}

	public void setEnableIPSM(boolean enableIPSM) {
		this.enableIPSM = enableIPSM;
	}

	/*
	 * When the user is viewing the Node Feed for a specific node, this will be the path of that root
	 * node, and we use this so we can easily do a 'browser push' to any user whenever something new is
	 * created under a that feed. todo-2: we could rename this to "chatNodePath", because it's basically
	 * the chat node when the user is in a chat room.
	 */
	private String watchingPath;

	public SessionContext() {
		// WARNING: This object might be created on a worker thread so do not assume it's 
		// an actual HTTP request thread creating this.
		// log.trace(String.format("SessionContext: object hashCode[%d]", hashCode()));
	}

	public static SessionContext init(ApplicationContext context, HttpSession session) {
		// Get the SessionContext bean off the http session if it exists
		SessionContext scBean = (SessionContext) session.getAttribute(SessionContext.QSC);

		// if we don't have a SessionContext yet or it timed out then create a new one.
		if (scBean == null || !scBean.isLive()) {

			// if we had a bean for this HTTP session, we need to remove it because we're replacing
			// it with a new one now. 
			removeSession(scBean);

			/*
			 * Note: we create SessionContext objects here on some requests that don't need them, but that's ok
			 * because all our code makes the assumption there will be a SessionContext on the thread.
			 * log.debug("Creating new session at req "+httpReq.getRequestURI());
			 */
			scBean = (SessionContext) context.getBean(SessionContext.class);
			session.setAttribute(SessionContext.QSC, scBean);

			synchronized (allSessions) {
				allSessions.add(scBean);
			}
	
			synchronized (historicalSessions) {
				historicalSessions.add(scBean);
			}
		}
		ThreadLocals.setHttpSession(session);
		ThreadLocals.setSC(scBean);
		scBean.setSession(session);
		return scBean;
	}

	public HttpSession getSession() {
		return session;
	}

	public void setSession(HttpSession session) {
		this.session = session;
	}

	/* Extra layer of security to invalidate this session object */
	public void forceAnonymous() {
		userToken = null;
		userName = PrincipalName.ANON.s();
		rootId = null;
		userNodeId = null;
		timelinePath = null;
		watchingPath = null;
	}

	public void addAction(String actionName) {
		Integer count = actionCounters.get(actionName);
		if (count == null) {
			actionCounters.put(actionName, 1);
		} else {
			actionCounters.put(actionName, count.intValue() + 1);
		}
	}

	public String dumpActions(String prefix, int countThreshold) {
		StringBuilder sb = new StringBuilder();
		for (String actionName : actionCounters.keySet()) {
			Integer count = (Integer) actionCounters.get(actionName);
			if (count.intValue() >= countThreshold) {
				sb.append(prefix);
				sb.append(actionName);
				sb.append(" ");
				sb.append(String.valueOf(count));
				sb.append("\n");
			}
		}
		return sb.toString();
	}

	/* This is called only upon successful login of a non-anon user */
	public void setAuthenticated(String userName, ObjectId userNodeId) {
		if (userName.equals(PrincipalName.ANON.s())) {
			throw new RuntimeException("invalid call to setAuthenticated for anon.");
		}

		if (userToken == null) {
			userToken = Util.genStrongToken();
		}

		log.debug("sessionContext authenticated hashCode=" + String.valueOf(hashCode()) + " user: " + userName + " to userToken "
				+ userToken);
		setUserName(userName);

		if (userNodeId == null) {
			SubNode userNode = arun.run(as -> read.getUserNodeByUserName(as, userName));
			// we found user's node.
			if (userNode != null) {
				setUserNodeId(userNode.getId());
			} else {
				throw new RuntimeException("No userNode found for user: " + userName);
			}
		} else {
			setUserNodeId(userNodeId);
		}
	}

	public boolean isAuthenticated() {
		return userToken != null;
	}

	/*
	 * We rely on the secrecy and unguessability of the token here, but eventually this will become JWT
	 * and perhaps use Spring Security
	 */
	public static boolean validToken(String token, String userName) {
		if (token == null)
			return false;

		synchronized (allSessions) {
			for (SessionContext sc : allSessions) {
				if (token.equals(sc.getUserToken())) {
					if (userName != null) {
						return userName.equals(sc.getUserName());
					} else {
						return true;
					}
				}
			}
		}
		return false;
	}

	public static boolean sessionExists(SessionContext sc) {
		synchronized (allSessions) {
			return allSessions.contains(sc);
		}
	}

	public static SessionContext getSCByToken(String token) {
		if (token == null)
			return null;

		synchronized (allSessions) {
			// great candidate for a stream() here.
			for (SessionContext sc : allSessions) {
				if (token.equals(sc.getUserToken())) {
					return sc;
				}
			}
		}
		return null;
	}

	public static void authBearer() {
		SessionContext sc = ThreadLocals.getSC();
		if (sc == null) {
			throw new RuntimeException("Unable to get SessionContext to check token.");
		}
		String bearer = ThreadLocals.getReqBearerToken();

		// otherwise require secure header
		if (bearer == null || !validToken(bearer, sc.getUserName())) {
			throw new RuntimeException("Auth failed. Bad bearer token.");
		}
	}

	public static void authSig() {
		SessionContext sc = ThreadLocals.getSC();
		if (sc == null) {
			throw new RuntimeException("Unable to get SessionContext to check token.");
		}

		if (!sc.prop.isRequireCrypto() || PrincipalName.ANON.s().equals(sc.getUserName())) {
			return;
		}

		String sig = ThreadLocals.getReqSig();
		if (sig == null) {
			throw new RuntimeException("Request failed. No signature.");
		}

		// if pubSigKey not yet saved in SessionContext then generate it
		if (sc.pubSigKey == null) {
			SubNode userNode = arun.run(as -> read.getUserNodeByUserName(as, sc.getUserName()));
			if (userNode == null) {
				throw new RuntimeException("Unknown user: " + sc.getUserName());
			}
			String pubKeyJson = userNode.getStr(NodeProp.USER_PREF_PUBLIC_SIG_KEY);
			if (pubKeyJson == null) {
				throw new RuntimeException("User Account didn't have SIG KEY: userName: " + sc.getUserName());
			}

			sc.pubSigKey = crypto.parseJWK(pubKeyJson, userNode);
			if (sc.pubSigKey == null) {
				throw new RuntimeException("Unable generate USER_PREF_PUBLIC_SIG_KEY for accnt " + userNode.getIdStr());
			}
			// log.debug("Saved User SigKey in SessionContext: " + sc.pubSigKey);
		}

		boolean verified =
				crypto.sigVerify(sc.pubSigKey, Util.hexStringToBytes(sig), sc.getUserName().getBytes(StandardCharsets.UTF_8));
		if (!verified) {
			throw new RuntimeException("Request Sig Failed. Probably wrong signature key in browser for user "+sc.getUserName());
		}
	}

	public String getUserToken() {
		return userToken;
	}

	public static void removeSession(SessionContext sc) {
		if (sc == null) return;
		synchronized (allSessions) {
			allSessions.remove(sc);
		}
	}

	public static int getSessionCount() {
		synchronized (allSessions) {
			return allSessions.size();
		}
	}

	public static List<SessionContext> getAllSessions(boolean requireToken, boolean requireAppGuid) {
		List<SessionContext> ret = new LinkedList<>();
		HashSet<String> tokens = new HashSet<>();
		HashSet<String> guids = new HashSet<>();

		synchronized (allSessions) {
			for (SessionContext sc : allSessions) {
				if (sc.isLive()) {
					if (requireAppGuid) {
						if (sc.getAppGuid() != null) {
							if (!guids.contains(sc.getAppGuid())) {
								ret.add(sc);
								guids.add(sc.getAppGuid());
							}
						}
					} else if (requireToken) {
						if (sc.getUserToken() != null) {
							if (!tokens.contains(sc.getUserToken())) {
								ret.add(sc);
								tokens.add(sc.getUserToken());
							}
						}
					} else {
						ret.add(sc);
					}
				}
			}
		}
		return ret;
	}

	public static List<SessionContext> getHistoricalSessions() {
		synchronized (historicalSessions) {
			return new LinkedList<>(historicalSessions);
		}
	}

	public static List<SessionContext> getSessionsByUserName(String userName) {
		if (userName == null)
			return null;

		List<SessionContext> list = null;
		synchronized (allSessions) {
			for (SessionContext sc : allSessions) {
				if (userName.equals(sc.getUserName())) {
					if (list == null) {
						list = new LinkedList<>();
					}
					list.add(sc);
				}
			}
		}
		return list;
	}

	public void sessionTimeout() {
		log.trace(String.format("Destroying Session object hashCode[%d] of user %s", hashCode(), userName));

		synchronized (allSessions) {
			/*
			 * This "lastActiveTime", should really be called "last message checked time", becaues that's the
			 * purpose it serves, so I think setting this here is undesirable, but we should only reset when the
			 * user is really checking their messages (like in UserFeedService), where this logic was moved to.
			 * usrMgr.updateLastActiveTime(this);
			 */
			allSessions.remove(this);
			setLive(false);
		}
	}

	public boolean isAdmin() {
		return PrincipalName.ADMIN.s().equalsIgnoreCase(userName);
	}

	public boolean isAnonUser() {
		return PrincipalName.ANON.s().equalsIgnoreCase(userName);
	}

	public boolean isTestAccount() {
		return MongoUtil.isTestAccountName(userName);
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		if (userName != null) {
			pastUserName = userName;
		}
		this.userName = userName;
	}

	public String getPastUserName() {
		return pastUserName;
	}

	public void setPastUserName(String pastUserName) {
		this.pastUserName = pastUserName;
	}

	public String getTimezone() {
		return timezone;
	}

	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}

	public String getTimeZoneAbbrev() {
		return timeZoneAbbrev;
	}

	public void setTimeZoneAbbrev(String timeZoneAbbrev) {
		this.timeZoneAbbrev = timeZoneAbbrev;
	}

	public String getRootId() {
		return rootId;
	}

	public void setRootId(String rootId) {
		this.rootId = rootId;
	}

	public UserPreferences getUserPreferences() {
		return userPreferences;
	}

	public void setUserPreferences(UserPreferences userPreferences) {
		this.userPreferences = userPreferences;
	}

	public long getLastLoginTime() {
		return lastLoginTime;
	}

	public void setLastLoginTime(long lastLoginTime) {
		this.lastLoginTime = lastLoginTime;
	}

	public long getLastActiveTime() {
		return lastActiveTime;
	}

	public void setLastActiveTime(long lastActiveTime) {
		this.lastActiveTime = lastActiveTime;
	}

	public SseEmitter getPushEmitter() {
		return pushEmitter;
	}

	public String getCaptcha() {
		return captcha;
	}

	public void setCaptcha(String captcha) {
		this.captcha = captcha;
	}

	public int getCaptchaFails() {
		return captchaFails;
	}

	public void setCaptchaFails(int captchaFails) {
		this.captchaFails = captchaFails;
	}

	public String getTimelinePath() {
		return timelinePath;
	}

	public void setTimelinePath(String timelinePath) {
		this.timelinePath = timelinePath;
	}

	public boolean isLive() {
		return live;
	}

	public void setLive(boolean live) {
		this.live = live;
	}

	// DO NOT DELETE: Keep for future reference
	// // from DisposableBean interface
	// @Override
	// public void destroy() throws Exception {
	// //log.debug("SessionContext destroy hashCode=" + String.valueOf(hashCode()) + ": userName=" +
	// this.userName);
	// }

	// // From InitializingBean interface
	// @Override
	// public void afterPropertiesSet() throws Exception {}

	public String getWatchingPath() {
		return watchingPath;
	}

	public void setWatchingPath(String watchingPath) {
		this.watchingPath = watchingPath;
	}

	public ObjectId getUserNodeId() {
		return userNodeId;
	}

	public void setUserNodeId(ObjectId userNodeId) {
		this.userNodeId = userNodeId;
	}

	public String getAllowedFeatures() {
		return allowedFeatures;
	}

	public void setAllowedFeatures(String allowedFeatures) {
		if (allowedFeatures == null) {
			allowedFeatures = "";
		}
		this.allowedFeatures = allowedFeatures;
	}

	public boolean allowWeb3() {
		// turning on for everyone for now
		return true;
		// return getAllowedFeatures().contains("web3");
	}

	public String getAppGuid() {
		return appGuid;
	}

	public void setAppGuid(String appGuid) {
		this.appGuid = appGuid;
	}

	public boolean isFriendsTagsDirty() {
		return friendsTagsDirty;
	}

	public void setFriendsTagsDirty(boolean friendsTagsDirty) {
		this.friendsTagsDirty = friendsTagsDirty;
	}

	public String getUrlIdFailMsg() {
		return urlIdFailMsg;
	}

	public void setUrlIdFailMsg(String urlIdFailMsg) {
		this.urlIdFailMsg = urlIdFailMsg;
	}

	public String getUserMsg() {
		return userMsg;
	}

	public void setUserMsg(String userMsg) {
		this.userMsg = userMsg;
	}

	public String getDisplayUserProfileId() {
		return displayUserProfileId;
	}

	public void setDisplayUserProfileId(String displayUserProfileId) {
		this.displayUserProfileId = displayUserProfileId;
	}

	public String getInitialNodeId() {
		return initialNodeId;
	}

	public void setInitialNodeId(String initialNodeId) {
		this.initialNodeId = initialNodeId;
	}

	public String getLoadNostrId() {
		return loadNostrId;
	}

	public void setLoadNostrId(String loadNostrId) {
		this.loadNostrId = loadNostrId;
	}

	public String getLoadNostrIdRelays() {
		return loadNostrIdRelays;
	}

	public void setLoadNostrIdRelays(String loadNostrIdRelays) {
		this.loadNostrIdRelays = loadNostrIdRelays;
	}
}
