package quanta.service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.actpub.ActPubLog;
import quanta.actpub.model.APODID;
import quanta.actpub.model.APOMention;
import quanta.actpub.model.APObj;
import quanta.config.NodeName;
import quanta.config.ServiceBase;
import quanta.config.SessionContext;
import quanta.exception.OutOfSpaceException;
import quanta.exception.base.RuntimeEx;
import quanta.model.UserPreferences;
import quanta.model.UserStats;
import quanta.model.client.Attachment;
import quanta.model.client.Constant;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.model.client.PrincipalName;
import quanta.model.client.PrivilegeType;
import quanta.model.client.UserProfile;
import quanta.model.ipfs.file.IPFSDirStat;
import quanta.mongo.CreateNodeLocation;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.AddFriendRequest;
import quanta.request.BlockUserRequest;
import quanta.request.ChangePasswordRequest;
import quanta.request.CloseAccountRequest;
import quanta.request.GetUserAccountInfoRequest;
import quanta.request.LoginRequest;
import quanta.request.ResetPasswordRequest;
import quanta.request.SavePublicKeyRequest;
import quanta.request.SaveUserPreferencesRequest;
import quanta.request.SaveUserProfileRequest;
import quanta.request.SignupRequest;
import quanta.response.AddFriendResponse;
import quanta.response.BlockUserResponse;
import quanta.response.ChangePasswordResponse;
import quanta.response.CloseAccountResponse;
import quanta.response.DeleteFriendResponse;
import quanta.response.FriendInfo;
import quanta.response.GetPeopleResponse;
import quanta.response.GetUserAccountInfoResponse;
import quanta.response.LoginResponse;
import quanta.response.PushPageMessage;
import quanta.response.ResetPasswordResponse;
import quanta.response.SavePublicKeyResponse;
import quanta.response.SaveUserPreferencesResponse;
import quanta.response.SaveUserProfileResponse;
import quanta.response.SignupResponse;
import quanta.util.Const;
import quanta.util.DateUtil;
import quanta.util.ExUtil;
import quanta.util.ThreadLocals;
import quanta.util.Util;
import quanta.util.XString;
import quanta.util.val.LongVal;
import quanta.util.val.Val;

/**
 * Service methods for processing user management functions. Login, logout, signup, user
 * preferences, and settings persisted per-user
 */
@Component
@Slf4j
public class UserManagerService extends ServiceBase {
	@Autowired
	private ActPubLog apLog;

	@Autowired
	public AuthenticationManager authenticationManager;

	private static final Random rand = new Random();

	/* Private keys of each user by user name as key */
	public static final ConcurrentHashMap<String, String> privateKeysByUserName = new ConcurrentHashMap<>();

	/*
	 * Note that this function does 'succeed' even with ANON user given, and just considers that an
	 * anonymouse user
	 */
	public LoginResponse login(HttpServletRequest httpReq, LoginRequest req) {
		LoginResponse res = new LoginResponse();
		SessionContext sc = ThreadLocals.getSC();
		SubNode userNode = null;
		// log.debug("login: " + XString.prettyPrint(req));

		/* Anonymous user */
		if (req.getUserName() == null || PrincipalName.ANON.s().equals(req.getUserName())) {
			log.debug("Anonymous user login.");
			// just as a precaution update the sc userName to anon values
			sc.setUserName(PrincipalName.ANON.s());
			sc.setUserNodeId(null);
		}
		/* Admin Login */
		else if (PrincipalName.ADMIN.s().equals(req.getUserName())) {
			// log.debug("AdminLogin root=: " + XString.prettyPrint(mongoUtil.getSystemRootNode()));
			// springLogin throws exception if it fails.
			springLogin(req.getUserName(), req.getPassword(), httpReq);
			sc.setAuthenticated(req.getUserName(), null);
		}
		/* User Login */
		else {
			// lookup userNode to get the ACTUAL (case sensitive) userName to put in sesssion.
			userNode = arun.run(as -> read.getUserNodeByUserName(as, req.getUserName()));
			String userName = userNode.getStr(NodeProp.USER);

			String pwdHash = mongoUtil.getHashOfPassword(req.getPassword());
			// springLogin throws exception if it fails.
			springLogin(userName, pwdHash, httpReq);
			sc.setAuthenticated(userName, null);
		}

		// If we reach here we either have ANON user or some authenticated user (password checked)
		ThreadLocals.initMongoSession(sc);

		/*
		 * We have to get timezone information from the user's browser, so that all times on all nodes
		 * always show up in their precise local time!
		 */
		sc.setTimezone(DateUtil.getTimezoneFromOffset(req.getTzOffset()));
		sc.setTimeZoneAbbrev(DateUtil.getUSTimezone(-req.getTzOffset() / 60, req.getDst()));
		res.setAnonUserLandingPageNode(prop.getUserLandingPageNode());

		if (sc.isAuthenticated()) {
			MongoSession ms = ThreadLocals.getMongoSession();
			processLogin(ms, res, userNode, sc.getUserName(), req.getAsymEncKey(), req.getSigKey(), req.getNostrNpub(),
					req.getNostrPubKey());
			log.debug("login: user=" + sc.getUserName());
		} else {
			res.setUserPreferences(getDefaultUserPreferences());
		}

		// note, this is a valid path even for 'anon' user.
		res.setMessage("login ok.");
		res.setSuccess(true);
		return res;
	}

	public void springLogin(String userName, String password, HttpServletRequest httpReq) {
		UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userName, password,
				Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
		authToken.setDetails(new WebAuthenticationDetails(httpReq));
		Authentication authentication = authenticationManager.authenticate(authToken);
		SecurityContextHolder.getContext().setAuthentication(authentication);
		// log.debug("Spring login successful: User=" + userName);
	}

	public void ensureUserHomeNodeExists(MongoSession ms, String userName, String content, String type, String name) {
		SubNode userNode = read.getUserNodeByUserName(ms, userName);
		if (userNode != null) {
			SubNode userHomeNode = read.getNodeByName(ms, userName + ":" + name);
			if (userHomeNode == null) {
				SubNode node = create.createNode(ms, userNode, null, type, 0L, CreateNodeLocation.LAST, null, null, true, true);
				node.setOwner(userNode.getId());
				if (name != null) {
					node.setName(name);
				}
				node.setContent(content);
				node.touch();
				acl.addPrivilege(ms, null, node, PrincipalName.PUBLIC.s(), null, Arrays.asList(PrivilegeType.READ.s()), null);
				update.save(ms, node);
			}
		}
	}

	/*
	 * caller can optionally pass userNode if it's already available, or else it will be looked up using
	 * userName
	 */
	public void processLogin(MongoSession ms, LoginResponse res, SubNode userNode, String userName, String asymEncKey,
			String sigKey, String nostrNpub, String nostrPubKey) {
		SessionContext sc = ThreadLocals.getSC();
		// log.debug("processLogin: " + userName);
		if (userNode == null) {
			userNode = arun.run(as -> read.getUserNodeByUserName(as, userName));
		}

		if (userNode == null) {
			throw new RuntimeEx("User not found: " + userName);
		}

		String id = userNode.getIdStr();
		if (id == null) {
			throw new RuntimeException("userNode id is null for user: " + userName);
		}
		sc.setRootId(id);
		sc.setAllowedFeatures(userNode.getStr(NodeProp.ALLOWED_FEATURES));
		res.setAllowedFeatures(sc.getAllowedFeatures());

		UserPreferences userPreferences = getUserPreferences(userName, userNode);
		sc.setUserPreferences(userPreferences);

		res.setRootNodePath(userNode.getPath());
		res.setAllowFileSystemSearch(prop.isAllowFileSystemSearch());
		res.setUserPreferences(userPreferences);
		res.setAuthToken(sc.getUserToken());

		Date now = new Date();
		sc.setLastLoginTime(now.getTime());
		userNode.set(NodeProp.LAST_LOGIN_TIME, now.getTime());

		String existingNpub = userNode.getStr(NodeProp.NOSTR_USER_NPUB);
		if (existingNpub == null) {
			userNode.set(NodeProp.NOSTR_USER_NPUB, nostrNpub);
		}

		String existingNostrPubKey = userNode.getStr(NodeProp.NOSTR_USER_PUBKEY);
		if (existingNostrPubKey == null) {
			userNode.set(NodeProp.NOSTR_USER_PUBKEY, nostrPubKey);
		}

		/*
		 * NOTE: For user to forcably change their public keys, they have to use the menu option for doing
		 * that, it won't happen here, automatically because their current browser may have different keys
		 * than their current keys. In other words we only set keys if we don't have the key yet (i.e. will
		 * not overwrite existing key here)
		 */
		String pubEncKey = userNode.getStr(NodeProp.USER_PREF_PUBLIC_KEY);
		if (pubEncKey == null) {
			if (userNode.set(NodeProp.USER_PREF_PUBLIC_KEY, asymEncKey)) {
				log.debug("USER_PREF_PUBLIC_KEY changed during login");
			}
		} else {
			if (!pubEncKey.equals(asymEncKey)) {
				res.setUnknownPubEncKey(true);
			}
		}

		// ditto, note just above, same applies to this key
		String pubSigKey = userNode.getStr(NodeProp.USER_PREF_PUBLIC_SIG_KEY);
		if (pubSigKey == null) {
			if (userNode.set(NodeProp.USER_PREF_PUBLIC_SIG_KEY, sigKey)) {
				log.debug("USER_PREF_PUBLIC_SIG_KEY changed during login: " + sigKey);
			}
		} else {
			if (!pubSigKey.equals(sigKey)) {
				res.setUnknownPubSigKey(true);
			}
		}

		res.setUserProfile(user.getUserProfile(userNode.getIdStr(), null, userNode, true));

		ensureValidCryptoKeys(userNode);
		// log.debug("SAVING USER NODE: "+XString.prettyPrint(userNode));
		update.save(ms, userNode);
	}

	/*
	 * Creates crypto key properties if not already existing
	 * 
	 * no longer used.
	 */
	public void ensureValidCryptoKeys(SubNode userNode) {
		try {
			String publicKey = userNode.getStr(NodeProp.CRYPTO_KEY_PUBLIC);
			if (publicKey == null) {
				KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
				kpg.initialize(2048);
				KeyPair pair = kpg.generateKeyPair();

				publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
				String privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());

				userNode.set(NodeProp.CRYPTO_KEY_PUBLIC, publicKey);
				userNode.set(NodeProp.CRYPTO_KEY_PRIVATE, privateKey);
			}
		} catch (Exception e) {
			log.error("failed creating crypto keys", e);
		}
	}

	public CloseAccountResponse closeAccount(CloseAccountRequest req) {
		CloseAccountResponse res = new CloseAccountResponse();
		log.debug("Closing Account: " + ThreadLocals.getSC().getUserName());
		arun.run(as -> {
			String userName = ThreadLocals.getSC().getUserName();

			SubNode ownerNode = read.getUserNodeByUserName(as, userName);
			if (ownerNode != null) {
				delete.delete(as, ownerNode, false);
			}
			return null;
		});
		return res;
	}

	/**
	 * 'userStats' Holds a map of User Root Node (account node) IDs as key mapped to the UserStats for
	 * that user.
	 */
	public void writeUserStats(MongoSession ms, HashMap<ObjectId, UserStats> userStats) {
		userStats.forEach((ObjectId key, UserStats stat) -> {
			SubNode node = read.getNode(ms, key);
			if (node != null) {
				// log.debug("Setting stat.binUsage=" + stat.binUsage);
				node.set(NodeProp.BIN_TOTAL, stat.binUsage);
			} else {
				log.debug("Node not found by key: " + key);
			}
		});
	}

	public long getTotalAttachmentBytes(MongoSession ms, SubNode node) {
		LongVal totalBytes = new LongVal();

		if (node != null && node.getAttachments() != null) {
			node.getAttachments().forEach((String key, Attachment att) -> {
				if (att.getSize() > 0L) {
					totalBytes.add(att.getSize());
				}
			});
		}
		return totalBytes.getVal();
	}

	/*
	 * We have 'sign' so we can use this method to either deduct from or add to the user's total usage
	 * amount
	 */
	public void addBytesToUserNodeBytes(MongoSession ms, long binSize, SubNode userNode) {
		if (userNode == null) {
			userNode = read.getUserNodeByUserName(null, null);
		}

		// get the current binTotal on the user account (max they are allowed to upload)
		Long binTotal = userNode.getInt(NodeProp.BIN_TOTAL);
		if (binTotal == null) {
			binTotal = 0L;
		}

		// log.debug("before binTotal=" + binTotal);
		binTotal += binSize;
		if (binTotal < 0) {
			binTotal = 0L;
		}

		Long userQuota = userNode.getInt(NodeProp.BIN_QUOTA);
		if (!ms.isAdmin() && binTotal > userQuota) {
			throw new OutOfSpaceException();
		}

		// log.debug("after binTotal=" + binTotal);
		userNode.set(NodeProp.BIN_TOTAL, binTotal);
	}

	/*
	 * Processes last step of signup, which is validation of registration code. This means user has
	 * clicked the link they were sent during the signup email verification, and they are sending in a
	 * signupCode that will turn on their account and actually create their account.
	 * 
	 * We return whatever a message would be to the user that just says if the signupCode was accepted
	 * or not and it's displayed on welcome.html only.
	 */
	public String processSignupCode(String signupCode) {
		log.debug("User is trying signupCode: " + signupCode);
		return arun.run(as -> {

			// signupCode is just the new account node id? I guess that's secure, if user
			// has this value it's the only user
			// who could possibly know this unguessable value.
			SubNode node = read.getNode(as, signupCode);

			if (node != null) {
				if (!node.getBool(NodeProp.SIGNUP_PENDING)) {
					return "Signup Complete. You may login now.";
				} else {
					String userName = node.getStr(NodeProp.USER);

					if (PrincipalName.ADMIN.s().equalsIgnoreCase(userName)) {
						return "processSignupCode should not be called for admin user.";
					} else {
						node.delete(NodeProp.SIGNUP_PENDING);
						update.save(as, node);
						return "Signup Successful. You may login now.";
					}
				}
			} else {
				return "Signup Code is invalid.";
			}
		});
	}

	public void initNewUser(MongoSession ms, String userName, String password, String email, boolean automated) {
		SubNode userNode = mongoUtil.createUser(ms, userName, email, password, automated, null, false);
		if (userNode != null) {
			log.debug("Successful signup complete.");
		}
	}

	public List<String> getOwnerNames(SubNode node) {
		Val<List<String>> ret = new Val<List<String>>();
		arun.run(as -> {
			ret.setVal(acl.getOwnerNames(as, node));
			return null;
		});
		return ret.getVal();
	}

	/*
	 * Processes a signup request from a user. We create the user root node in a pending state, and like
	 * all other user accounts all information specific to that user that we currently know is held in
	 * that node (i.e. preferences)
	 */
	public SignupResponse signup(SignupRequest req, boolean automated) {
		SignupResponse res = new SignupResponse();
		arun.run(as -> {
			String userName = req.getUserName().trim();
			String password = req.getPassword().trim();
			String email = req.getEmail();

			log.debug("Signup: userName=" + userName + " email=" + email);
			res.setSuccess(true);

			String userError = validator.checkUserName(userName);
			if (userError != null) {
				res.setUserError(userError);
				res.setSuccess(false);
			}

			String passwordError = validator.checkPassword(password);
			if (passwordError != null) {
				res.setPasswordError(passwordError);
				res.setSuccess(false);
			}

			String emailError = validator.checkEmail(email);
			if (emailError != null) {
				res.setEmailError(emailError);
				res.setSuccess(false);
			}

			if (!automated) {
				if (!ThreadLocals.getSC().getCaptcha().equals(req.getCaptcha())) {
					int captchaFails = ThreadLocals.getSC().getCaptchaFails();

					if (captchaFails > 0) {
						try {
							// this sleep should stop brute forcing, every failed attempt makes the user
							// need to wait an additional 2 seconds each time.
							Thread.sleep(captchaFails * 2000);
						} catch (Exception e) {
						}
					}
					ThreadLocals.getSC().setCaptchaFails(captchaFails + 1);
					res.setCaptchaError("Wrong captcha. Try again.");
					res.setSuccess(false);
				}
			}

			if (!res.isSuccess()) {
				return res;
			}

			if (!automated) {
				initiateSignup(as, userName, password, email);
			} else {
				initNewUser(as, userName, password, email, automated);
			}
			return null;
		});

		return res;
	}

	/*
	 * Adds user to the list of pending accounts and they will stay in pending status until their
	 * signupCode has been used to validate their email address.
	 */
	public void initiateSignup(MongoSession ms, String userName, String password, String email) {

		SubNode ownerNode = read.getUserNodeByUserName(ms, userName);
		if (ownerNode != null) {
			throw new RuntimeEx("User already exists.");
		}

		SubNode newUserNode = mongoUtil.createUser(ms, userName, email, password, false, null, false);

		/*
		 * It's easiest to use the actua new UserNode ID as the 'signup code' to send to the user, because
		 * it's random and tied to this user by definition
		 */
		String signupCode = newUserNode.getIdStr();
		String signupLink = prop.getHttpProtocol() + "://" + prop.getMetaHost() + "?signupCode=" + signupCode;
		String content = null;

		/*
		 * We print this out so we can use it in DEV mode when no email support may be configured
		 */
		log.debug("Signup URL: " + signupLink);

		String brandingAppName = prop.getConfigText("brandingAppName");

		content = "Welcome to " + brandingAppName + ", " + userName + "!" + //
				"<p>\nUse this link to complete the signup: <br>\n" + signupLink;

		if (!StringUtils.isEmpty(prop.getMailHost())) {
			outbox.queueEmail(email, brandingAppName + " - Account Signup", content);
		}
	}

	public void setDefaultUserPreferences(SubNode prefsNode) {
		prefsNode.set(NodeProp.USER_PREF_EDIT_MODE, false);
		prefsNode.set(NodeProp.USER_PREF_RSS_HEADINGS_ONLY, true);
	}

	public SavePublicKeyResponse savePublicKeys(SavePublicKeyRequest req) {
		SavePublicKeyResponse res = new SavePublicKeyResponse();
		String userName = ThreadLocals.getSC().getUserName();

		arun.run(as -> {
			SubNode userNode = read.getUserNodeByUserName(as, userName);

			if (userNode != null) {
				if (req.getAsymEncKey() != null) {
					userNode.set(NodeProp.USER_PREF_PUBLIC_KEY, req.getAsymEncKey());
				}

				if (req.getSigKey() != null) {
					// force pubSigKey to regenerate as needed by setting to null
					ThreadLocals.getSC().pubSigKey = null;
					userNode.set(NodeProp.USER_PREF_PUBLIC_SIG_KEY, req.getSigKey());
				}
				res.setSuccess(true);
			} else {
				log.debug("savePublicKey failed to find userName: " + userName);
			}
			return null;
		});
		return res;
	}

	public GetUserAccountInfoResponse getUserAccountInfo(GetUserAccountInfoRequest req) {
		GetUserAccountInfoResponse res = new GetUserAccountInfoResponse();
		String userName = ThreadLocals.getSC().getUserName();

		arun.run(as -> {
			SubNode userNode = read.getUserNodeByUserName(as, userName);
			if (userNode == null) {
				res.setMessage("unknown user: " + userName);
				res.setSuccess(false);
			}

			try {
				// foreign users won't have these.
				Long binQuota = userNode.getInt(NodeProp.BIN_QUOTA);
				Long binTotal = userNode.getInt(NodeProp.BIN_TOTAL);

				// I really need to convert these props to Integers not Strings
				res.setBinQuota(binQuota == null ? -1 : binQuota.intValue());
				res.setBinTotal(binTotal == null ? -1 : binTotal.intValue());
			} catch (Exception e) {
			}

			res.setSuccess(true);
			return null;
		});
		return res;
	}

	public SaveUserPreferencesResponse saveUserPreferences(SaveUserPreferencesRequest req) {
		SaveUserPreferencesResponse res = new SaveUserPreferencesResponse();

		UserPreferences userPrefs = ThreadLocals.getSC().getUserPreferences();
		// note: This will be null if session has timed out.
		if (userPrefs == null) {
			return res;
		}

		UserPreferences reqUserPrefs = req.getUserPreferences();

		// once triggered it stays on (for now)
		if (reqUserPrefs.isEnableIPSM()) {
			ThreadLocals.getSC().setEnableIPSM(true);
		}

		arun.run(as -> {
			SubNode prefsNode = read.getNode(as, req.getUserNodeId());
			if (prefsNode == null)
				throw new RuntimeException("Unable to update preferences.");

			// Make sure the account node we're about to modify does belong to the current user.
			if (!ThreadLocals.getSC().getUserName().equals(prefsNode.getStr(NodeProp.USER))) {
				throw new RuntimeException("Not your node.");
			}

			/*
			 * Assign preferences as properties on this node,
			 */
			prefsNode.set(NodeProp.USER_PREF_EDIT_MODE, reqUserPrefs.isEditMode());
			prefsNode.set(NodeProp.USER_PREF_SHOW_METADATA, reqUserPrefs.isShowMetaData());
			prefsNode.set(NodeProp.USER_PREF_NSFW, reqUserPrefs.isNsfw());
			prefsNode.set(NodeProp.USER_PREF_SHOW_PROPS, reqUserPrefs.isShowProps());
			prefsNode.set(NodeProp.USER_PREF_AUTO_REFRESH_FEED, reqUserPrefs.isAutoRefreshFeed()); // #add-prop
			prefsNode.set(NodeProp.USER_PREF_SHOW_PARENTS, reqUserPrefs.isShowParents());
			prefsNode.set(NodeProp.USER_PREF_SHOW_REPLIES, reqUserPrefs.isShowReplies());
			prefsNode.set(NodeProp.USER_PREF_RSS_HEADINGS_ONLY, reqUserPrefs.isRssHeadlinesOnly());
			prefsNode.set(NodeProp.USER_PREF_MAIN_PANEL_COLS, reqUserPrefs.getMainPanelCols());

			userPrefs.setEditMode(reqUserPrefs.isEditMode());
			userPrefs.setShowMetaData(reqUserPrefs.isShowMetaData());
			userPrefs.setNsfw(reqUserPrefs.isNsfw());
			userPrefs.setShowProps(reqUserPrefs.isShowProps());
			userPrefs.setShowParents(reqUserPrefs.isShowParents());
			userPrefs.setShowReplies(reqUserPrefs.isShowReplies());
			userPrefs.setRssHeadlinesOnly(reqUserPrefs.isRssHeadlinesOnly());
			userPrefs.setMainPanelCols(reqUserPrefs.getMainPanelCols());

			// log.debug("saveUserPreferences: [hashCode=" + userPrefs.hashCode() + "] " +
			// XString.prettyPrint(userPrefs));
			res.setSuccess(true);
			return null;
		});
		return res;
	}

	public SaveUserProfileResponse saveUserProfile(SaveUserProfileRequest req) {
		SaveUserProfileResponse res = new SaveUserProfileResponse();
		String userName = ThreadLocals.getSC().getUserName();

		arun.run(as -> {
			boolean failed = false;
			SubNode userNode = read.getUserNodeByUserName(as, userName);

			// DO NOT DELETE: This is temporaryly disabled (no ability to edit userName)
			// If userName is changing, validate it first.
			// if (!req.getUserName().equals(userName)) {
			// validator.checkUserName(req.getUserName());

			// SubNode nodeFound = api.getUserNodeByUserName(session, req.getUserName());
			// if (ok(nodeFound )) {
			// res.setMessage("User already exists.");
			// res.setSuccess(false);
			// failed = true;
			// }
			// }

			if (!failed) {
				userNode.set(NodeProp.USER_BIO, req.getUserBio());
				userNode.set(NodeProp.USER_TAGS, req.getUserTags());
				userNode.set(NodeProp.USER_BLOCK_WORDS, processBlockedWords(req.getBlockedWords()));
				userNode.set(NodeProp.USER_RECENT_TYPES, req.getRecentTypes());
				userNode.set(NodeProp.DISPLAY_NAME, req.getDisplayName());
				userNode.set(NodeProp.MFS_ENABLE, req.isMfsEnable());

				// sessionContext.setUserName(req.getUserName());
				update.save(as, userNode);
				res.setSuccess(true);

				if (req.isPublish()) {
					writeProfileToIPNS(ThreadLocals.getSC(), userName, req.getUserBio(), req.getDisplayName());
				}

				edit.processAfterSave(as, userNode, null, true);
			}
			return null;
		});
		return res;
	}

	/* Takes in blockedWords and returns them as a unique and sorted array, each on a separate line */
	public String processBlockedWords(String blockedWords) {
		if (blockedWords == null)
			return null;

		HashSet<String> wordsSet = new HashSet<>();
		StringTokenizer t = new StringTokenizer(blockedWords, " \n\r\t,", false);
		while (t.hasMoreTokens()) {
			wordsSet.add(t.nextToken());
		}
		ArrayList<String> wordsList = new ArrayList<>(wordsSet);
		wordsList.sort((a, b) -> a.compareTo(b));
		return String.join("\n", wordsList);
	}

	public void writeProfileToIPNS(SessionContext sc, String userName, String bio, String displayName) {
		if (!ThreadLocals.getSC().allowWeb3()) {
			return;
		}

		// Note: we need to access the current thread, because the rest of the logic runs in a damon thread.
		String userNodeId = ThreadLocals.getSC().getUserNodeId().toHexString();
		exec.run(() -> {
			arun.run(as -> {
				SubNode userNode = read.getNode(as, userNodeId, false, null);
				String key = userNode.getStr(NodeProp.USER_IPFS_KEY);

				// If we didn't already generate the key for this user, then generate one.
				if (!sc.getRootId().equals(key)) {
					// make sure there is an IPFS key with same name as user's root ID.
					Map<String, Object> keyGenResult = ipfsKey.gen(as, sc.getRootId());
					if (keyGenResult == null) {
						log.debug("Unable to generate IPFS Key for Name " + sc.getRootId());
						// return null;
					} else {
						userNode.set(NodeProp.USER_IPFS_KEY, sc.getRootId());
						log.debug("Key Gen Result: " + XString.prettyPrint(keyGenResult));
					}
				}

				APODID did = new APODID(userName + "@" + prop.getMetaHost());
				did.put("bio", bio);
				did.put("displayName", displayName);

				String didPayload = XString.prettyPrint(did);
				String cid = null;
				log.debug("Writing UserProfile of " + userName + " to IPNS: " + didPayload);

				// make a folder for this user
				String folderName = "/" + userNodeId;

				// put identity file in this folder
				String fileName = folderName + "/identity.json";
				log.debug("identity file: " + fileName);

				// Instead let's wrap in a MFS folder type for now. This is all experimental so far.
				ipfsFiles.addFile(as, fileName, MediaType.APPLICATION_JSON_VALUE, didPayload);

				// Now we have to read the file we just wrote to get it's CID so we can publish it.
				IPFSDirStat pathStat = ipfsFiles.pathStat(folderName);
				if (pathStat == null) {
					push.sendServerPushInfo(sc, new PushPageMessage("Decentralized Identity Publish FAILED", true));
					return null;
				}

				log.debug("Parent Folder PathStat " + folderName + ": " + XString.prettyPrint(pathStat));

				// IPFSDir dir = ipfsFiles.getDir(folderName);
				// if (ok(dir)) {
				// log.debug("Parent Folder Listing " + folderName + ": " + XString.prettyPrint(dir));
				// }
				cid = pathStat.getHash();

				log.debug("Publishing CID (root folder): " + cid);
				Map<String, Object> ret = ipfsName.publish(as, sc.getRootId(), cid);
				log.debug("Publishing complete!");

				userNode.set(NodeProp.USER_DID_IPNS, ret.get("Name"));
				update.save(as, userNode);

				push.sendServerPushInfo(sc, new PushPageMessage("Decentralized Identity Publish Complete.", false));
				return null;
			});
		});
	}

	/* The code pattern here is very similar to addFriendInternal */
	public BlockUserResponse blockUser(MongoSession ms, BlockUserRequest req) {
		BlockUserResponse res = new BlockUserResponse();
		String userName = ThreadLocals.getSC().getUserName();
		ObjectId accntIdDoingBlock = ThreadLocals.getSC().getUserNodeId();

		// get the node that holds all blocked users
		SubNode blockedList =
				read.getUserNodeByType(ms, userName, null, null, NodeType.BLOCKED_USERS.s(), null, NodeName.BLOCKED_USERS);

		SubNode userNode = read.findFriendNode(ms, accntIdDoingBlock, null, req.getUserName());

		// if we have this node but in some obsolete path delete it. Might be the path of FRIENDS_LIST!
		if (userNode != null && !mongoUtil.isChildOf(blockedList, userNode)) {
			delete.delete(ms, userNode);
			userNode = null;
		}

		if (userNode == null) {
			SubNode accntNode = arun.run(s -> read.getUserNodeByUserName(s, req.getUserName()));

			if (accntNode == null)
				throw new RuntimeException("User not found.");

			userNode = edit.createFriendNode(ms, blockedList, req.getUserName());
			if (userNode != null) {
				res.setMessage(
						"Blocked user " + req.getUserName() + ". To manage blocks, go to `Menu -> Friends -> Blocked Users`");
			} else {
				res.setMessage("Unable to block user: " + req.getUserName());
			}

			res.setSuccess(true);
		} else {
			/*
			 * todo-2: for this AND the friend request (similar places), we need to make it where the user can
			 * never get here or click a button if this is redundant. also we don't yet have in the GUI the
			 * indication of "Follows You" and "[You're] Following" when someone views a user, which is part of
			 * what's needed for this.
			 */
			res.setMessage("You already blocked " + req.getUserName());
			res.setSuccess(true);
		}
		return res;
	}

	public DeleteFriendResponse deleteFriend(MongoSession ms, String delUserNodeId, String parentType) {
		// apLog.trace("deleteFriend request: " + XString.prettyPrint(req));
		DeleteFriendResponse res = new DeleteFriendResponse();
		ms = ThreadLocals.ensure(ms);

		Criteria crit = Criteria.where(SubNode.PROPS + "." + NodeProp.USER_NODE_ID.s()).is(delUserNodeId); //
		List<SubNode> friendNodes = getSpecialNodesList(ms, null, parentType, null, false, crit);
		if (friendNodes != null) {

			// we run a for loop but there will only be only up to one friend node in this result set.
			for (SubNode friendNode : friendNodes) {
				// we delete with updateHasChildren=false, because it's more efficient
				delete.delete(ms, friendNode, false);
			}
		}
		res.setSuccess(true);
		return res;
	}

	/*
	 * Adds all the users in 'req.userName' (as a newline elimited list) as new friends of the current
	 * user
	 */
	public AddFriendResponse addFriend(MongoSession ms, AddFriendRequest req) {
		AddFriendResponse res = new AddFriendResponse();
		String userDoingAction = ThreadLocals.getSC().getUserName();

		if (userDoingAction.equals(PrincipalName.ADMIN.s())) {
			throw new RuntimeException("Don't follow from admin account.");
		}

		final List<String> users = XString.tokenize(req.getUserName().trim(), "\n", true);

		// If just following one user do it synchronously and send back the response
		if (users.size() == 1) {
			String ret = addFriend(ms, userDoingAction, null, users.get(0));
			res.setMessage(ret);
		}
		// else if following multiple users run in an async exector thread
		else if (users.size() > 1) {

			// For now we only allow FollowBot to do multiple-user follows
			if (!userDoingAction.equals(PrincipalName.FOLLOW_BOT.s())) {
				throw new RuntimeException("Account not authorized for multi-follows.");
			}

			res.setMessage("Following users is in progress.");
			exec.run(() -> {
				Val<Integer> counter = new Val<>(0);
				users.forEach(u -> {
					counter.setVal(counter.getVal() + 1);
					log.debug("BATCH FOLLOW: " + u + ", " + String.valueOf(counter.getVal()) + "/" + users.size());
					addFriend(ms, userDoingAction, null, u);

					// sleep so the foreign server doesn't start throttling us if these users are
					// very many onthe same server.
					Util.sleep(4000);
				});
				log.debug("BATCH FOLLOW complete.");
			});
		}
		res.setSuccess(true);
		return res;
	}

	/*
	 * Adds 'req.userName' as a friend by creating a FRIEND node under the current user's FRIENDS_LIST
	 * if the user wasn't already a friend
	 */
	public String addFriend(MongoSession ms, String userDoingFollow, ObjectId accntIdDoingFollow, String userBeingFollowed) {
		String _userToFollow = userBeingFollowed;
		_userToFollow = XString.stripIfStartsWith(_userToFollow, "@");

		// duplicate variable because of lambdas below
		String userToFollow = _userToFollow;

		if (userToFollow.equalsIgnoreCase(PrincipalName.ADMIN.s())) {
			return "You can't be friends with the admin.";
		}

		// If we don't know the account id of the person doing the follow, then look it up.
		if (accntIdDoingFollow == null) {
			SubNode followerAcctNode = arun.run(s -> read.getUserNodeByUserName(ms, userDoingFollow, false));

			if (followerAcctNode == null) {
				throw new RuntimeException("Unable to find user: " + userDoingFollow);
			}
			accntIdDoingFollow = followerAcctNode.getId();
		}

		addFriendInternal(ThreadLocals.getMongoSession(), userDoingFollow, accntIdDoingFollow, userToFollow);
		return "Added Friend: " + userToFollow;
	}

	/* The code pattern here is very similar to 'blockUser' */
	private void addFriendInternal(MongoSession ms, String userDoingFollow, ObjectId accntIdDoingFollow, String userToFollow) {
		SubNode followerFriendList =
				read.getUserNodeByType(ms, userDoingFollow, null, null, NodeType.FRIEND_LIST.s(), null, NodeName.FRIENDS);

		if (followerFriendList == null) {
			log.debug("Can't access Friend list for: " + userDoingFollow);
			return;
		}

		/*
		 * lookup to see if this followerFriendList node already has userToFollow already under it.
		 */
		SubNode friendNode = read.findFriendNode(ms, accntIdDoingFollow, null, userToFollow);

		// if we have this node but in some obsolete path delete it. Might be the path of BLOCKED_USERS
		if (friendNode != null && !mongoUtil.isChildOf(followerFriendList, friendNode)) {
			delete.delete(ms, friendNode);
			friendNode = null;
		}

		// if friendNode is non-null here it means we were already following the user.
		if (friendNode != null)
			return;

		if (userToFollow.contains("@")) {
			apub.loadForeignUser(userDoingFollow, userToFollow);
		}

		// the passed in 'ms' may or may not be admin session, but we always DO need this with admin, so we
		// must use arun.
		SubNode userNode = arun.run(s -> read.getUserNodeByUserName(s, userToFollow, false));

		if (userNode == null)
			return;

		// follower bot never blocks people, so we can avoid calling that if follower bot.
		if (!userDoingFollow.equals(PrincipalName.FOLLOW_BOT.s())) {
			// We can't have both a FRIEND and a BLOCK so remove the friend. There's also a unique constraint on
			// the DB enforcing this.
			deleteFriend(ms, userNode.getIdStr(), NodeType.BLOCKED_USERS.s());
		}

		apLog.trace("Creating friendNode for " + userToFollow);
		friendNode = edit.createFriendNode(ms, followerFriendList, userToFollow);

		if (friendNode != null) {
			friendNode.set(NodeProp.USER_NODE_ID, userNode.getIdStr());

			// updates AND sends the friend request out to the foreign server.
			edit.updateSavedFriendNode(userDoingFollow, friendNode);

			// Update our cache, because we now have a new followed user.
			synchronized (apCache.followedUsers) {
				apCache.followedUsers.add(userToFollow);
			}
		}
	}

	/*
	 * Abbreviated flag means don't get ALL the info for the user but an abbreviated object that's
	 * faster to generate like what we need when someone is logging in and the login endpoint needs
	 * their own profile info as fast as possible.
	 * 
	 * caller should pass in 'userNode' if it's available or else userId will be used to get it.
	 */
	public UserProfile getUserProfile(String userId, String nostrPubKey, SubNode _userNode, boolean abbreviated) {
		String sessionUserName = ThreadLocals.getSC().getUserName();

		return (UserProfile) arun.run(as -> {
			UserProfile userProfile = null;
			SubNode userNode = null;
			if (_userNode == null) {
				if (nostrPubKey != null) {
					userNode = read.getUserNodeByUserName(as, "." + nostrPubKey);
				} else if (userId == null) {
					userNode = read.getUserNodeByUserName(as, sessionUserName);
				} else {
					userNode = read.getNode(as, userId, false, null);
				}
			} else {
				userNode = _userNode;
			}

			if (userNode != null) {
				userProfile = new UserProfile();
				String nodeUserName = userNode.getStr(NodeProp.USER);
				String displayName = getFriendlyNameFromNode(userNode);

				userProfile.setUserName(nodeUserName);
				userProfile.setDisplayName(displayName);

				String actorUrl = userNode.getStr(NodeProp.ACT_PUB_ACTOR_URL);
				String actorId = userNode.getStr(NodeProp.ACT_PUB_ACTOR_ID);

				userProfile.setMfsEnable(userNode.getBool(NodeProp.MFS_ENABLE));
				userProfile.setUserBio(userNode.getStr(NodeProp.USER_BIO));
				userProfile.setDidIPNS(userNode.getStr(NodeProp.USER_DID_IPNS));
				userProfile.setUserTags(userNode.getStr(NodeProp.USER_TAGS));
				userProfile.setBlockedWords(userNode.getStr(NodeProp.USER_BLOCK_WORDS));
				userProfile.setRecentTypes(userNode.getStr(NodeProp.USER_RECENT_TYPES));
				userProfile.setRelays(userNode.getStr(NodeProp.NOSTR_RELAYS));
				userProfile.setNostrNpub(userNode.getStr(NodeProp.NOSTR_USER_NPUB));

				Attachment att = userNode.getAttachment(Constant.ATTACHMENT_PRIMARY.s(), false, false);
				if (att != null) {
					userProfile.setAvatarVer(att.getBin());
				}

				Attachment headerAtt = userNode.getAttachment(Constant.ATTACHMENT_HEADER.s(), false, false);
				if (headerAtt != null) {
					userProfile.setHeaderImageVer(headerAtt.getBin());
				}
				userProfile.setUserNodeId(userNode.getIdStr());

				userProfile.setApIconUrl(userNode.getStr(NodeProp.USER_ICON_URL));
				userProfile.setApImageUrl(userNode.getStr(NodeProp.USER_BANNER_URL));
				userProfile.setActorUrl(actorUrl);
				userProfile.setActorId(actorId);

				if (!abbreviated) {
					SubNode userHomeNode = read.getNodeByName(as, nodeUserName + ":" + NodeName.HOME);
					if (userHomeNode != null) {
						userProfile.setHomeNodeId(userHomeNode.getIdStr());
					}

					Long followerCount = apFollower.countFollowersOfUser(as, sessionUserName, userNode, nodeUserName, actorUrl);
					userProfile.setFollowerCount(followerCount.intValue());

					Long followingCount = apFollowing.countFollowingOfUser(as, sessionUserName, nodeUserName, actorUrl);
					userProfile.setFollowingCount(followingCount.intValue());

					if (!ThreadLocals.getSC().isAnonUser()) {
						/*
						 * Only for local users do we attemp to generate followers and following, but theoretically we can
						 * use the ActPub API to query for this for foreign users also.
						 */
						boolean blocked = userIsBlockedByMe(as, userNode, nodeUserName);
						userProfile.setBlocked(blocked);

						boolean following = userIsFollowedByMe(as, userNode, nodeUserName);
						userProfile.setFollowing(following);
					}
				}
			}
			return userProfile;
		});
	}

	public String getFriendlyNameFromNode(SubNode userNode) {
		String displayName = userNode.getStr(NodeProp.DISPLAY_NAME);
		if (StringUtils.isEmpty(displayName)) {
			displayName = userNode.getStr(NodeProp.NOSTR_NAME);
		}
		if (StringUtils.isEmpty(displayName)) {
			displayName = userNode.getStr(NodeProp.NOSTR_USER_NAME);
		}
		return displayName;
	}

	public boolean userIsFollowedByMe(MongoSession ms, SubNode inUserNode, String maybeFollowedUser) {
		String userName = ThreadLocals.getSC().getUserName();
		SubNode friendsList =
				read.getUserNodeByType(ms, userName, null, null, NodeType.FRIEND_LIST.s(), null, NodeName.BLOCKED_USERS);

		// note: findFriend() could work here, but findFriend doesn't tell us IF it's INDEED a Friend or
		// Block.
		// Our FRIEND type is used for both Friends and BLOCKs, which is kind of confusing.
		SubNode userNode = read.findNodeByUserAndType(ms, friendsList, inUserNode, maybeFollowedUser, NodeType.FRIEND.s());
		return userNode != null;
	}

	public boolean userIsBlockedByMe(MongoSession ms, SubNode inUserNode, String maybeBlockedUser) {
		String userName = ThreadLocals.getSC().getUserName();
		SubNode blockedList =
				read.getUserNodeByType(ms, userName, null, null, NodeType.BLOCKED_USERS.s(), null, NodeName.BLOCKED_USERS);

		// note: findFriend() could work here, but findFriend doesn't tell us IF it's INDEED a Friend or
		// Block.
		// Our FRIEND type is used for both Friends and BLOCKs, which is kind of confusing.
		SubNode userNode = read.findNodeByUserAndType(ms, blockedList, inUserNode, maybeBlockedUser, NodeType.FRIEND.s());
		return userNode != null;
	}

	public UserPreferences getDefaultUserPreferences() {
		UserPreferences userPrefs = new UserPreferences();
		userPrefs.setShowMetaData(true);
		userPrefs.setNsfw(false);
		userPrefs.setShowProps(false);
		return userPrefs;
	}

	public UserPreferences getUserPreferences(String userName, SubNode _prefsNode) {
		UserPreferences userPrefs = new UserPreferences();

		arun.run(as -> {
			SubNode prefsNode = _prefsNode;
			if (prefsNode == null) {
				prefsNode = read.getUserNodeByUserName(as, userName);
			}

			userPrefs.setEditMode(prefsNode.getBool(NodeProp.USER_PREF_EDIT_MODE));
			userPrefs.setShowMetaData(prefsNode.getBool(NodeProp.USER_PREF_SHOW_METADATA));
			userPrefs.setNsfw(prefsNode.getBool(NodeProp.USER_PREF_NSFW));
			userPrefs.setShowProps(prefsNode.getBool(NodeProp.USER_PREF_SHOW_PROPS));
			userPrefs.setAutoRefreshFeed(prefsNode.getBool(NodeProp.USER_PREF_AUTO_REFRESH_FEED)); // #add-prop
			userPrefs.setShowParents(prefsNode.getBool(NodeProp.USER_PREF_SHOW_PARENTS));
			userPrefs.setShowReplies(prefsNode.getBool(NodeProp.USER_PREF_SHOW_REPLIES));
			userPrefs.setRssHeadlinesOnly(prefsNode.getBool(NodeProp.USER_PREF_RSS_HEADINGS_ONLY));

			long maxFileSize = prefsNode.getInt(NodeProp.BIN_QUOTA);
			if (maxFileSize == 0) {
				maxFileSize = Const.DEFAULT_USER_QUOTA;
			}
			userPrefs.setMaxUploadFileSize(maxFileSize);

			long mainPanelCols = prefsNode.getInt(NodeProp.USER_PREF_MAIN_PANEL_COLS);
			if (mainPanelCols == 0) {
				mainPanelCols = 6;
			}
			userPrefs.setMainPanelCols(mainPanelCols);

			return null;
		});

		return userPrefs;
	}

	/*
	 * Runs when user is doing the 'change password' or 'reset password'
	 */
	public ChangePasswordResponse changePassword(MongoSession ms, ChangePasswordRequest req) {
		ChangePasswordResponse res = new ChangePasswordResponse();
		ms = ThreadLocals.ensure(ms);

		Val<SubNode> userNode = new Val<>();
		Val<String> userName = new Val<>();

		String passCode = req.getPassCode();
		if (passCode != null) {
			/*
			 * We can run this block as admin, because the codePart below is secret and is checked for a match
			 */
			arun.run(as -> {

				String userNodeId = XString.truncAfterFirst(passCode, "-");

				if (userNodeId == null) {
					throw new RuntimeEx("Unable to find userNodeId: " + userNodeId);
				}
				userNode.setVal(read.getNode(as, userNodeId));

				if (userNode.getVal() == null) {
					throw ExUtil.wrapEx("Invald password reset code.");
				}

				String codePart = XString.parseAfterLast(passCode, "-");

				String nodeCodePart = userNode.getVal().getStr(NodeProp.USER_PREF_PASSWORD_RESET_AUTHCODE);
				if (!codePart.equals(nodeCodePart)) {
					throw ExUtil.wrapEx("Invald password reset code.");
				}

				String password = req.getNewPassword();
				userName.setVal(userNode.getVal().getStr(NodeProp.USER));

				if (PrincipalName.ADMIN.s().equals(userName.getVal())) {
					throw new RuntimeEx("changePassword should not be called fror admin user.");
				}

				userNode.getVal().set(NodeProp.PWD_HASH, mongoUtil.getHashOfPassword(password));
				userNode.getVal().delete(NodeProp.USER_PREF_PASSWORD_RESET_AUTHCODE);

				// note: the adminRunner.run saves the session so we don't do that here.
				return null;
			});
		} else {
			userNode.setVal(read.getUserNodeByUserName(ms, ms.getUserName()));

			if (userNode.getVal() == null) {
				throw ExUtil.wrapEx("changePassword cannot find user.");
			}

			if (PrincipalName.ADMIN.s().equals(userName.getVal())) {
				throw new RuntimeEx("changePassword should not be called fror admin user.");
			}

			String password = req.getNewPassword();
			userName.setVal(userNode.getVal().getStr(NodeProp.USER));
			userNode.getVal().set(NodeProp.PWD_HASH, mongoUtil.getHashOfPassword(password));
			userNode.getVal().delete(NodeProp.USER_PREF_PASSWORD_RESET_AUTHCODE);

			update.save(ms, userNode.getVal());
		}

		res.setUser(userName.getVal());
		res.setSuccess(true);
		return res;
	}

	public boolean isNormalUserName(String userName) {
		userName = userName.trim();
		return !userName.equalsIgnoreCase(PrincipalName.ADMIN.s()) && !userName.equalsIgnoreCase(PrincipalName.ANON.s());
	}

	public ResetPasswordResponse resetPassword(ResetPasswordRequest req) {
		ResetPasswordResponse res = new ResetPasswordResponse();
		arun.run(as -> {
			String user = req.getUser();
			String email = req.getEmail();

			/* make sure username itself is acceptalbe */
			if (!isNormalUserName(user)) {
				res.setMessage("User name is illegal.");
				res.setSuccess(false);
				return null;
			}

			SubNode ownerNode = read.getUserNodeByUserName(as, user);
			if (ownerNode == null) {
				res.setMessage("User does not exist.");
				res.setSuccess(false);
				return null;
			}

			/*
			 * IMPORTANT!
			 *
			 * verify that the email address provides IS A MATCH to the email address for this user!
			 */
			String nodeEmail = ownerNode.getStr(NodeProp.EMAIL);
			if (nodeEmail == null || !nodeEmail.equals(email)) {
				res.setMessage("Wrong user name and/or email.");
				res.setSuccess(false);
				return null;
			}

			/*
			 * if we make it to here the user and email are both correct, and we can initiate the password
			 * reset. We pick some random time between 1 and 2 days from now into the future to serve as the
			 * unguessable auth code AND the expire time for it. Later we can create a deamon processor that
			 * cleans up expired authCodes, but for now we just need to HAVE the auth code.
			 *
			 * User will be emailed this code and we will perform reset when we see it, and the user has entered
			 * new password we can use.
			 */
			int oneDayMillis = 60 * 60 * 1000;
			long authCode = new Date().getTime() + oneDayMillis + rand.nextInt(oneDayMillis);

			ownerNode.set(NodeProp.USER_PREF_PASSWORD_RESET_AUTHCODE, String.valueOf(authCode));
			update.save(as, ownerNode);

			String passCode = ownerNode.getIdStr() + "-" + String.valueOf(authCode);
			String link = prop.getHostAndPort() + "?passCode=" + passCode;

			String brandingAppName = prop.getConfigText("brandingAppName");

			String content = "Password reset was requested on " + brandingAppName + " account: " + user + //
					"<p>\nGo to this link to reset your password: <br>\n" + link;

			outbox.queueEmail(email, brandingAppName + " Password Reset", content);

			res.setMessage("A password reset link has been sent to your email. Check your email in a minute or so.");
			res.setSuccess(true);
			return null;
		});
		return res;
	}

	public GetPeopleResponse getPeopleOnNode(MongoSession ms, String nodeId) {
		GetPeopleResponse res = new GetPeopleResponse();
		SubNode node = read.getNode(ms, nodeId);
		if (node == null) {
			res.setMessage("Unable to find node.");
			res.setSuccess(false);
		}

		String ownerIdStr = node.getOwner().toHexString();

		HashSet<String> idSet = new HashSet<>();
		HashMap<String, APObj> tags = apub.parseTags(node.getContent(), true, false);
		HashMap<String, APObj> nodePropTags = apub.parseTags(node);
		if (nodePropTags != null) {
			tags.putAll(nodePropTags);
		}

		// if we have likes add them into 'tags', because that's what we feed thru the rest of the code.
		if (node.getLikes() != null) {
			node.getLikes().forEach(userName -> {
				String mention = "@" + userName;
				tags.put(mention, new APOMention(null, mention));
			});
		}

		if (tags != null && tags.size() > 0) {
			String userDoingAction = ThreadLocals.getSC().getUserName();
			apub.importUsers(ms, tags, userDoingAction);
		}

		List<FriendInfo> friends = new LinkedList<>();
		arun.run(as -> {

			SubNode ownerAccntNode = read.getNode(as, node.getOwner());
			String ownerName = null;

			if (ownerAccntNode != null) {
				ownerName = ownerAccntNode.getStr(NodeProp.USER);
				FriendInfo ownerInfo = buildPersonInfoFromAccntNode(as, ownerAccntNode);
				if (node.getLikes() != null && node.getLikes().contains(ownerInfo.getUserName())) {
					ownerInfo.setLiked(true);
				}
				res.setNodeOwner(ownerInfo);
			}

			String _ownerName = ownerName;
			tags.forEach((user, tag) -> {
				// ignore if this is something else (like a Hashtag)
				if (!(tag instanceof APOMention))
					return;
				// remove '@' prefix
				user = XString.stripIfStartsWith(user, "@");
				if (user.equals(_ownerName))
					return;

				try {
					SubNode accntNode = read.getUserNodeByUserName(as, user);
					if (accntNode != null) {
						String id = accntNode.getIdStr();
						if (!idSet.contains(id)) {
							FriendInfo fi = buildPersonInfoFromAccntNode(as, accntNode);
							if (fi != null) {
								friends.add(fi);
								idSet.add(id);
							}
						}
					}
				} catch (Exception e) {
					ExUtil.warn(log, "Unable to load user: " + user, e);
				}
			});

			if (node.getAc() != null) {
				/*
				 * Lookup all userNames from the ACL info, to add them all to 'toUserNames'
				 */
				for (String accntId : node.getAc().keySet()) {
					// ignore public, it's not a user.
					if (accntId.equals(ownerIdStr) || idSet.contains(accntId) || PrincipalName.PUBLIC.s().equals(accntId))
						continue;

					SubNode accntNode = apub.cachedGetAccntNodeById(as, accntId, false, null);
					if (accntNode != null) {
						FriendInfo fi = buildPersonInfoFromAccntNode(as, accntNode);
						if (fi != null) {
							friends.add(fi);
							idSet.add(accntId);
						}
					}
				}
				res.setPeople(friends);
			}

			return null;
		});

		if (node.getLikes() != null && friends != null) {
			friends.forEach(fi -> {
				if (node.getLikes().contains(fi.getUserName())) {
					fi.setLiked(true);
				}
			});
		}

		friends.sort((f1, f2) -> f1.getUserName().compareTo(f2.getUserName()));

		res.setSuccess(true);
		return res;
	}

	public FriendInfo buildPersonInfoFromAccntNode(MongoSession ms, SubNode userNode) {
		FriendInfo fi = new FriendInfo();
		String userName = userNode.getStr(NodeProp.USER);
		fi.setUserName(userName);
		fi.setDisplayName(userNode.getStr(NodeProp.DISPLAY_NAME));
		fi.setUserNodeId(userNode.getIdStr());
		fi.setForeignAvatarUrl(userNode.getStr(NodeProp.USER_ICON_URL));

		if (userName.indexOf("@") == -1) {
			Attachment att = userNode.getAttachment(Constant.ATTACHMENT_PRIMARY.s(), false, false);
			if (att != null) {
				fi.setAvatarVer(att.getBin());
			}
		}

		return fi;
	}

	// NOTE: subType = null | "nostr"
	public GetPeopleResponse getPeople(MongoSession ms, String type, String subType) {
		GetPeopleResponse res = new GetPeopleResponse();

		String nodeType = null;
		Criteria moreCriteria = null;
		if ("friends".equals(type)) {
			nodeType = NodeType.FRIEND_LIST.s();
			if ("nostr".equals(subType)) {
				// this regex simly is "Starts with a period"
				moreCriteria = Criteria.where(SubNode.PROPS + "." + NodeProp.USER).regex("^\\.");
			}
		} //
		else if ("blocks".equals(type)) {
			nodeType = NodeType.BLOCKED_USERS.s();
		} else {
			throw new RuntimeException("Invalid type: " + type);
		}
		List<SubNode> friendNodes = getSpecialNodesList(ms, null, nodeType, null, true, moreCriteria);

		if (friendNodes != null) {
			List<FriendInfo> friends = new LinkedList<>();
			for (SubNode friendNode : friendNodes) {
				FriendInfo fi = buildPersonInfoFromFriendNode(ms, friendNode);
				if (fi != null) {
					friends.add(fi);
				}
			}
			res.setPeople(friends);
		}
		res.setSuccess(true);
		return res;
	}

	public FriendInfo buildPersonInfoFromFriendNode(MongoSession ms, SubNode friendNode) {
		String userName = friendNode.getStr(NodeProp.USER);
		FriendInfo fi = null;
		if (userName != null) {
			fi = new FriendInfo();
			fi.setFriendNodeId(friendNode.getIdStr());
			fi.setUserName(userName);
			fi.setTags(friendNode.getTags());
			fi.setForeignAvatarUrl(friendNode.getStr(NodeProp.USER_ICON_URL));

			SubNode userNode = read.getUserNodeByUserName(null, userName);
			if (userNode != null) {
				String displayName = getFriendlyNameFromNode(userNode);
				fi.setDisplayName(displayName);

				fi.setRelays(userNode.getStr(NodeProp.NOSTR_RELAYS));
			}

			String userNodeId = friendNode.getStr(NodeProp.USER_NODE_ID);

			SubNode friendAccountNode = read.getNode(ms, userNodeId, false, null);
			if (friendAccountNode != null) {
				// if a local user use BIN property on node (account node BIN property is the Avatar)
				if (userName.indexOf("@") == -1) {
					Attachment att = friendAccountNode.getAttachment(Constant.ATTACHMENT_PRIMARY.s(), false, false);
					if (att != null) {
						fi.setAvatarVer(att.getBin());
					}
				}
				// Otherwise the avatar will be specified as a remote user's Icon.
				else {
					// set avatar here only if we didn't set it above already
					if (fi.getForeignAvatarUrl() == null) {
						fi.setForeignAvatarUrl(friendAccountNode.getStr(NodeProp.USER_ICON_URL));
					}

				}
			}
			fi.setUserNodeId(userNodeId);
		}
		return fi;
	}

	/**
	 * Looks in the userName's account under their 'underType' type node and returns all the children.
	 * If userName is passed as null, then we use the currently logged in user
	 */
	public List<SubNode> getSpecialNodesList(MongoSession ms, Val<SubNode> parentNodeVal, String underType, String userName,
			boolean sort, Criteria moreCriteria) {
		ms = ThreadLocals.ensure(ms);
		List<SubNode> nodeList = new LinkedList<>();
		SubNode userNode = read.getUserNodeByUserName(ms, userName);
		if (userNode == null)
			return null;

		SubNode parentNode = read.findSubNodeByType(ms, userNode, underType);
		if (parentNode == null)
			return null;

		if (parentNodeVal != null) {
			parentNodeVal.setVal(parentNode);
		}

		for (SubNode node : read.getChildren(ms, parentNode, sort ? Sort.by(Sort.Direction.ASC, SubNode.ORDINAL) : null, null, 0,
				moreCriteria)) {
			nodeList.add(node);
		}
		return nodeList;
	}

	/*
	 * For all foreign servers we remove posts that are older than a certain number of days just to keep
	 * our DB from growing too large.
	 * 
	 * todo-2: Is this a dupliate of "ActPub Maintenance" menu option logic?
	 */
	public void cleanUserAccounts() {
		// not currently used.
		if (true)
			return;

		// adminRunner.run(session -> {
		// final Iterable<SubNode> accountNodes =
		// read.getChildrenUnderParentPath(session, NodePath.ROOT_OF_ALL_USERS, null, null, 0, null, null);

		// for (final SubNode accountNode : accountNodes) {
		// String userName = accountNode.getStrProp(NodeProp.USER);

		// // if account is a 'foreign server' one, then clean it up
		// if (ok(userName )) {
		// log.debug("userName: " + userName);

		// if (userName.contains("@")) {
		// log.debug("Foreign Accnt Kill: " + userName);
		// delete.delete(accountNode);

		// // delete.cleanupOldTempNodesForUser(session, accountNode);
		// }
		// }
		// }

		// apCache.usersPendingRefresh.clear();
		// });
	}

	public String getUserAccountsReport(MongoSession ms) {
		ms = ThreadLocals.ensure(ms);
		int localUserCount = 0;
		int foreignUserCount = 0;

		StringBuilder sb = new StringBuilder();
		Iterable<SubNode> accountNodes = read.getAccountNodes(ms, null, null, null, -1, true, true);

		for (SubNode accountNode : accountNodes) {
			String userName = accountNode.getStr(NodeProp.USER);
			if (userName != null) {
				// if account is a 'foreign server' one, then clean it up
				if (userName.contains("@")) {
					foreignUserCount++;
				} else {
					localUserCount++;
				}
			}
		}
		sb.append("Local User Count: " + localUserCount + "\n");
		sb.append("Foreign User Count: " + foreignUserCount + "\n");
		return sb.toString();
	}

	public void updateLastActiveTime(SessionContext sc) {
		arun.run(as -> {
			SubNode userNode = read.getUserNodeByUserName(as, sc.getUserName());
			if (userNode != null) {
				userNode.set(NodeProp.LAST_ACTIVE_TIME, sc.getLastActiveTime());
				update.save(as, userNode);
			}
			return null;
		});
	}

	public int getMaxUploadSize(MongoSession ms) {
		if (ms.isAnon()) {
			return 0;
		}
		if (ms.isAdmin()) {
			return Integer.MAX_VALUE;
		}

		SubNode userNode = arun.run(as -> read.getUserNodeByUserName(as, ThreadLocals.getSC().getUserName()));
		long ret = userNode.getInt(NodeProp.BIN_QUOTA);
		if (ret == 0) {
			return Const.DEFAULT_USER_QUOTA;
		}
		return (int) ret;
	}
}
