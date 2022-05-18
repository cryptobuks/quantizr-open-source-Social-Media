package quanta.service;

import static quanta.util.Util.no;
import static quanta.util.Util.ok;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import quanta.actpub.ActPubLog;
import quanta.actpub.model.APODID;
import quanta.config.NodeName;
import quanta.config.ServiceBase;
import quanta.config.SessionContext;
import quanta.exception.OutOfSpaceException;
import quanta.exception.base.RuntimeEx;
import quanta.model.UserPreferences;
import quanta.model.UserStats;
import quanta.model.client.NodeProp;
import quanta.model.client.NodeType;
import quanta.model.client.PrincipalName;
import quanta.model.client.PrivilegeType;
import quanta.model.client.UserProfile;
import quanta.model.ipfs.file.IPFSDirStat;
import quanta.mongo.CreateNodeLocation;
import quanta.mongo.MongoSession;
import quanta.mongo.MongoUtil;
import quanta.mongo.model.SubNode;
import quanta.request.AddFriendRequest;
import quanta.request.BlockUserRequest;
import quanta.request.ChangePasswordRequest;
import quanta.request.CloseAccountRequest;
import quanta.request.GetUserAccountInfoRequest;
import quanta.request.GetUserProfileRequest;
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
import quanta.response.GetFriendsResponse;
import quanta.response.GetUserAccountInfoResponse;
import quanta.response.GetUserProfileResponse;
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
import quanta.util.Val;
import quanta.util.XString;

/**
 * Service methods for processing user management functions. Login, logout, signup, user
 * preferences, and settings persisted per-user
 */
@Component
public class UserManagerService extends ServiceBase {
	private static final Logger log = LoggerFactory.getLogger(UserManagerService.class);

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
		// log.debug("login: " + XString.prettyPrint(req));

		/* Anonymous user */
		if (no(req.getUserName()) || PrincipalName.ANON.s().equals(req.getUserName())) {
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
			SubNode userNode = read.getUserNodeByUserName(auth.getAdminSession(), req.getUserName());
			String userName = userNode.getStr(NodeProp.USER.s());

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
		log.debug("Processing Login: urlId=" + (ok(sc.getUrlId()) ? sc.getUrlId() : "null"));

		if (ok(sc.getUrlId())) {
			// log.debug("setHomeNodeOverride (from session urlId): " + sc.getUrlId());
			res.setHomeNodeOverride(sc.getUrlId());
		}

		if (sc.isAuthenticated()) {
			MongoSession ms = ThreadLocals.getMongoSession();
			processLogin(ms, res, sc.getUserName());
			log.debug("login: user=" + sc.getUserName());

			// ensure we've pre-created this node.
			SubNode postsNode = read.getUserNodeByType(ms, sc.getUserName(), null, "### Posts", NodeType.POSTS.s(),
					Arrays.asList(PrivilegeType.READ.s()), NodeName.POSTS);

			ensureUserHomeNodeExists(ms, sc.getUserName(), "### " + sc.getUserName() + "'s Node", NodeType.NONE.s(),
					NodeName.HOME);
		} else {
			res.setUserPreferences(getDefaultUserPreferences());
		}

		res.setUserName(sc.getUserName());
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
		if (ok(userNode)) {
			SubNode userHomeNode = read.getNodeByName(ms, userName + ":" + name);
			if (no(userHomeNode)) {
				SubNode node = create.createNode(ms, userNode, null, type, 0L, CreateNodeLocation.LAST, null, null, true);
				node.setOwner(userNode.getId());
				if (ok(name)) {
					node.setName(name);
				}
				node.setContent(content);
				node.touch();
				acl.addPrivilege(ms, null, node, PrincipalName.PUBLIC.s(), Arrays.asList(PrivilegeType.READ.s()), null);
				update.save(ms, node);
			}
		}
	}

	public void processLogin(MongoSession ms, LoginResponse res, String userName) {
		SessionContext sc = ThreadLocals.getSC();
		// log.debug("processLogin: " + userName);
		SubNode userNode = read.getUserNodeByUserName(auth.getAdminSession(), userName);

		if (no(userNode)) {
			throw new RuntimeEx("User not found: " + userName);
		}

		String id = userNode.getIdStr();
		if (no(id)) {
			throw new RuntimeException("userNode id is null for user: " + userName);
		}
		sc.setRootId(id);
		sc.setAllowedFeatures(userNode.getStr(NodeProp.ALLOWED_FEATURES));
		res.setAllowedFeatures(sc.getAllowedFeatures());

		UserPreferences userPreferences = getUserPreferences(userName, userNode);
		sc.setUserPreferences(userPreferences);

		res.setRootNode(id);
		res.setRootNodePath(userNode.getPath());

		// be sure to get userName off node so case sensitivity is exact.
		res.setUserName(userNode.getStr(NodeProp.USER));
		res.setDisplayName(userNode.getStr(NodeProp.DISPLAY_NAME));

		res.setAllowFileSystemSearch(prop.isAllowFileSystemSearch());
		res.setUserPreferences(userPreferences);
		res.setAuthToken(sc.getUserToken());

		Date now = new Date();
		sc.setLastLoginTime(now.getTime());
		userNode.set(NodeProp.LAST_LOGIN_TIME.s(), now.getTime());

		ensureValidCryptoKeys(userNode);
		update.save(ms, userNode);
	}

	/*
	 * Creates crypto key properties if not already existing
	 * 
	 * no longer used.
	 */
	public void ensureValidCryptoKeys(SubNode userNode) {
		try {
			String publicKey = userNode.getStr(NodeProp.CRYPTO_KEY_PUBLIC.s());
			if (no(publicKey)) {
				KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
				kpg.initialize(2048);
				KeyPair pair = kpg.generateKeyPair();

				publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
				String privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());

				userNode.set(NodeProp.CRYPTO_KEY_PUBLIC.s(), publicKey);
				userNode.set(NodeProp.CRYPTO_KEY_PRIVATE.s(), privateKey);
			}
		} catch (Exception e) {
			log.error("failed creating crypto keys", e);
		}
	}

	public CloseAccountResponse closeAccount(CloseAccountRequest req) {
		CloseAccountResponse res = new CloseAccountResponse();
		log.debug("Closing Account: " + ThreadLocals.getSC().getUserName());
		arun.run(ms -> {
			String userName = ThreadLocals.getSC().getUserName();

			SubNode ownerNode = read.getUserNodeByUserName(ms, userName);
			if (ok(ownerNode)) {
				delete.delete(ms, ownerNode, false);
			}
			return null;
		});
		return res;
	}

	/**
	 * @param ms
	 * @param userStats Holds a map of User Root Node (account node) IDs as key mapped to the UserStats
	 *        for that user.
	 */
	public void writeUserStats(MongoSession ms, HashMap<ObjectId, UserStats> userStats) {
		userStats.forEach((ObjectId key, UserStats stat) -> {
			SubNode node = read.getNode(ms, key);
			if (ok(node)) {
				// log.debug("Setting stat.binUsage=" + stat.binUsage);
				node.set(NodeProp.BIN_TOTAL.s(), stat.binUsage);
			} else {
				log.debug("Node not found by key: " + key);
			}
		});
	}

	/**
	 * increments the userNode usasage bytes by adding the bytes the attachment uses on 'node'
	 * 
	 * @param node
	 * @param userNode
	 * @param sign Controls if this is a subtract or an add (should be always 1 or -1)
	 */
	public void addNodeBytesToUserNodeBytes(MongoSession ms, SubNode node, SubNode userNode, int sign) {
		if (no(node)) {
			return;
		}

		// get the size of the attachment on this node
		long binSize = node.getInt(NodeProp.BIN_SIZE);
		if (binSize > 0L) {
			// log.debug("Will +/- amt: " + binSize);

			if (no(userNode)) {
				userNode = read.getUserNodeByUserName(null, null);
			}

			addBytesToUserNodeBytes(ms, binSize, userNode, sign);
		}
	}

	/*
	 * We have 'sign' so we can use this method to either deduct from or add to the user's total usage
	 * amount
	 */
	public void addBytesToUserNodeBytes(MongoSession ms, long binSize, SubNode userNode, int sign) {
		if (no(userNode)) {
			userNode = read.getUserNodeByUserName(null, null);
		}

		// get the current binTotal on the user account (max they are allowed to upload)
		Long binTotal = userNode.getInt(NodeProp.BIN_TOTAL);
		if (no(binTotal)) {
			binTotal = 0L;
		}

		// log.debug("before binTotal=" + binTotal);
		binTotal += sign * binSize;
		if (binTotal < 0) {
			binTotal = 0L;
		}

		Long userQuota = userNode.getInt(NodeProp.BIN_QUOTA);
		if (!ms.isAdmin() && binTotal > userQuota) {
			throw new OutOfSpaceException();
		}

		// log.debug("after binTotal=" + binTotal);
		userNode.set(NodeProp.BIN_TOTAL.s(), binTotal);
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
		return arun.run(ms -> {

			// signupCode is just the new account node id? I guess that's secure, if user
			// has this value it's the only user
			// who could possibly know this unguessable value.
			SubNode node = read.getNode(ms, signupCode);

			if (ok(node)) {
				if (!node.getBool(NodeProp.SIGNUP_PENDING)) {
					return "Signup Complete. You may login now.";
				} else {
					String userName = node.getStr(NodeProp.USER.s());

					if (PrincipalName.ADMIN.s().equalsIgnoreCase(userName)) {
						return "processSignupCode should not be called for admin user.";
					} else {
						node.delete(NodeProp.SIGNUP_PENDING.s());
						update.save(ms, node);
						return "Signup Successful. You may login now.";
					}
				}
			} else {
				return "Signup Code is invalid.";
			}
		});
	}

	public void initNewUser(MongoSession ms, String userName, String password, String email, boolean automated) {
		SubNode userNode = mongoUtil.createUser(ms, userName, email, password, automated);
		if (ok(userNode)) {
			log.debug("Successful signup complete.");
		}
	}

	public List<String> getOwnerNames(SubNode node) {
		Val<List<String>> ret = new Val<List<String>>();
		arun.run(ms -> {
			ret.setVal(acl.getOwnerNames(ms, node));
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
		arun.run(ms -> {
			String userName = req.getUserName().trim();
			String password = req.getPassword().trim();
			String email = req.getEmail();

			log.debug("Signup: userName=" + userName + " email=" + email);
			res.setSuccess(true);

			/* throw exceptions of the username or password are not valid */
			String userError = validator.checkUserName(userName);
			if (ok(userError)) {
				res.setUserError(userError);
				res.setSuccess(false);
			}

			String passwordError = validator.checkPassword(password);
			if (ok(passwordError)) {
				res.setPasswordError(passwordError);
				res.setSuccess(false);
			}

			String emailError = validator.checkEmail(email);
			if (ok(emailError)) {
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
				initiateSignup(ms, userName, password, email);
			} else {
				initNewUser(ms, userName, password, email, automated);
			}
			return null;
		});

		res.setMessage("success");
		return res;
	}

	/*
	 * Adds user to the list of pending accounts and they will stay in pending status until their
	 * signupCode has been used to validate their email address.
	 */
	public void initiateSignup(MongoSession ms, String userName, String password, String email) {

		SubNode ownerNode = read.getUserNodeByUserName(ms, userName);
		if (ok(ownerNode)) {
			throw new RuntimeEx("User already exists.");
		}

		SubNode newUserNode = mongoUtil.createUser(ms, userName, email, password, false);

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
		prefsNode.set(NodeProp.USER_PREF_EDIT_MODE.s(), false);
		prefsNode.set(NodeProp.USER_PREF_RSS_HEADINGS_ONLY.s(), true);
	}

	public SavePublicKeyResponse savePublicKey(SavePublicKeyRequest req) {
		SavePublicKeyResponse res = new SavePublicKeyResponse();
		String userName = ThreadLocals.getSC().getUserName();

		arun.run(ms -> {
			SubNode userNode = read.getUserNodeByUserName(ms, userName);

			if (ok(userNode)) {
				userNode.set(NodeProp.USER_PREF_PUBLIC_KEY.s(), req.getKeyJson());
			} else {
				log.debug("savePublicKey failed to find userName: " + userName);
			}
			// oops this is coming up when I don't want to see it, when the user logs in,
			// so we need to be sure to somehow only show the message when the user has
			// CLICKED
			// the actual publish keys menu
			// res.setMessage("Successfully saved public key.");
			res.setSuccess(true);
			return null;
		});
		return res;
	}

	public GetUserAccountInfoResponse getUserAccountInfo(GetUserAccountInfoRequest req) {
		GetUserAccountInfoResponse res = new GetUserAccountInfoResponse();
		String userName = ThreadLocals.getSC().getUserName();

		arun.run(ms -> {
			SubNode userNode = read.getUserNodeByUserName(ms, userName);
			if (no(userNode)) {
				res.setMessage("unknown user: " + userName);
				res.setSuccess(false);
			}

			try {
				// foreign users won't have these.
				Long binQuota = userNode.getInt(NodeProp.BIN_QUOTA);
				Long binTotal = userNode.getInt(NodeProp.BIN_TOTAL);

				// I really need to convert these props to Integers not Strings
				res.setBinQuota(no(binQuota) ? -1 : binQuota.intValue());
				res.setBinTotal(no(binTotal) ? -1 : binTotal.intValue());
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
		if (no(userPrefs)) {
			return res;
		}

		UserPreferences reqUserPrefs = req.getUserPreferences();

		// once triggered it stays on (for now)
		if (reqUserPrefs.isEnableIPSM()) {
			ThreadLocals.getSC().setEnableIPSM(true);
		}

		arun.run(ms -> {
			SubNode prefsNode = read.getNode(ms, req.getUserNodeId());
			if (no(prefsNode))
				throw new RuntimeException("Unable to update preferences.");

			// Make sure the account node we're about to modify does belong to the current user.
			if (!ThreadLocals.getSC().getUserName().equals(prefsNode.getStr(NodeProp.USER.s()))) {
				throw new RuntimeException("Not your node.");
			}

			/*
			 * Assign preferences as properties on this node,
			 */
			prefsNode.set(NodeProp.USER_PREF_EDIT_MODE.s(), reqUserPrefs.isEditMode());
			prefsNode.set(NodeProp.USER_PREF_SHOW_METADATA.s(), reqUserPrefs.isShowMetaData());
			prefsNode.set(NodeProp.USER_PREF_NSFW.s(), reqUserPrefs.isNsfw());
			prefsNode.set(NodeProp.USER_PREF_SHOW_PARENTS.s(), reqUserPrefs.isShowParents());
			prefsNode.set(NodeProp.USER_PREF_RSS_HEADINGS_ONLY.s(), reqUserPrefs.isRssHeadlinesOnly());
			prefsNode.set(NodeProp.USER_PREF_MAIN_PANEL_COLS.s(), reqUserPrefs.getMainPanelCols());

			userPrefs.setEditMode(reqUserPrefs.isEditMode());
			userPrefs.setShowMetaData(reqUserPrefs.isShowMetaData());
			userPrefs.setNsfw(reqUserPrefs.isNsfw());
			userPrefs.setShowParents(reqUserPrefs.isShowParents());
			userPrefs.setRssHeadlinesOnly(reqUserPrefs.isRssHeadlinesOnly());
			userPrefs.setMainPanelCols(reqUserPrefs.getMainPanelCols());

			// log.debug("saveUserPreferences: " + XString.prettyPrint(prefsNode));
			res.setSuccess(true);
			return null;
		});
		return res;
	}

	public SaveUserProfileResponse saveUserProfile(SaveUserProfileRequest req) {
		SaveUserProfileResponse res = new SaveUserProfileResponse();
		String userName = ThreadLocals.getSC().getUserName();

		arun.run(ms -> {
			boolean failed = false;
			SubNode userNode = read.getUserNodeByUserName(ms, userName);

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
				// userNode.setProp(NodeProp.USER.s(), req.getUserName());
				userNode.set(NodeProp.USER_BIO.s(), req.getUserBio());
				userNode.set(NodeProp.USER_TAGS.s(), req.getUserTags());
				userNode.set(NodeProp.DISPLAY_NAME.s(), req.getDisplayName());
				userNode.set(NodeProp.MFS_ENABLE.s(), req.isMfsEnable());

				// sessionContext.setUserName(req.getUserName());
				update.save(ms, userNode);
				res.setSuccess(true);

				if (req.isPublish()) {
					writeProfileToIPNS(ThreadLocals.getSC(), userName, req.getUserBio(), req.getDisplayName());
				}
			}
			return null;
		});
		return res;
	}

	public void writeProfileToIPNS(SessionContext sc, String userName, String bio, String displayName) {
		if (!ThreadLocals.getSC().allowWeb3()) {
			return;
		}

		// Note: we need to access the current thread, because the rest of the logic runs in a damon thread.
		String userNodeId = ThreadLocals.getSC().getUserNodeId().toHexString();
		exec.run(() -> {
			arun.run(ms -> {
				SubNode userNode = read.getNode(ms, userNodeId, false);
				String key = userNode.getStr(NodeProp.USER_IPFS_KEY);

				// If we didn't already generate the key for this user, then generate one.
				if (!sc.getRootId().equals(key)) {
					// make sure there is an IPFS key with same name as user's root ID.
					Map<String, Object> keyGenResult = ipfsKey.gen(ms, sc.getRootId());
					if (no(keyGenResult)) {
						log.debug("Unable to generate IPFS Key for Name " + sc.getRootId());
						// return null;
					} else {
						userNode.set(NodeProp.USER_IPFS_KEY.s(), sc.getRootId());
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
				ipfsFiles.addFile(ms, fileName, MediaType.APPLICATION_JSON_VALUE, didPayload);

				// Now we have to read the file we just wrote to get it's CID so we can publish it.
				IPFSDirStat pathStat = ipfsFiles.pathStat(folderName);
				if (no(pathStat)) {
					push.sendServerPushInfo(sc, new PushPageMessage("Decentralized Identity Publish FAILED"));
					return null;
				}

				log.debug("Parent Folder PathStat " + folderName + ": " + XString.prettyPrint(pathStat));

				// IPFSDir dir = ipfsFiles.getDir(folderName);
				// if (ok(dir)) {
				// log.debug("Parent Folder Listing " + folderName + ": " + XString.prettyPrint(dir));
				// }
				cid = pathStat.getHash();

				log.debug("Publishing CID (root folder): " + cid);
				Map<String, Object> ret = ipfsName.publish(ms, sc.getRootId(), cid);
				log.debug("Publishing complete!");

				userNode.set(NodeProp.USER_DID_IPNS.s(), ret.get("Name"));
				update.save(ms, userNode);

				push.sendServerPushInfo(sc, new PushPageMessage("Decentralized Identity Publish Complete."));
				return null;
			});
		});
	}

	public BlockUserResponse blockUser(MongoSession ms, BlockUserRequest req) {
		BlockUserResponse res = new BlockUserResponse();
		String userName = ThreadLocals.getSC().getUserName();

		// get the node that holds all blocked users
		SubNode blockedList =
				read.getUserNodeByType(ms, userName, null, null, NodeType.BLOCKED_USERS.s(), null, NodeName.BLOCKED_USERS);

		/*
		 * lookup to see if this will be a duplicate
		 */
		SubNode userNode = read.findNodeByUserAndType(ms, blockedList, req.getUserName(), NodeType.FRIEND.s());
		if (no(userNode)) {
			SubNode accntNode = arun.run(s -> {
				return read.getUserNodeByUserName(s, req.getUserName());
			});

			if (no(accntNode))
				throw new RuntimeException("User not found.");

			// We can't have both a FRIEND and a BLOCK so remove the friend. There's also a unique constring on
			// the DB enforcing this.
			deleteFriend(ms, accntNode.getIdStr(), NodeType.FRIEND_LIST.s());
			update.saveSession(ms);

			userNode = edit.createFriendNode(ms, blockedList, req.getUserName());
			if (ok(userNode)) {
				res.setMessage(
						"Blocked user " + req.getUserName() + ". To manage blocks, go to `Menu -> Friends -> Blocked Users`");
			} else {
				res.setMessage("Unable to block user: " + req.getUserName());
			}

			edit.updateSavedFriendNode(userName, userNode);
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

		// This loop over friendNodes could be done all in a single delete query command, but for now let's
		// just do the delete this way using our existing methods.
		List<SubNode> friendNodes = getSpecialNodesList(ms, parentType, null, true);
		if (ok(friendNodes)) {
			for (SubNode friendNode : friendNodes) {
				// the USER_NODE_ID property on friends nodes contains the actual account ID of this friend.
				String userNodeId = friendNode.getStr(NodeProp.USER_NODE_ID);
				if (delUserNodeId.equals(userNodeId)) {
					delete.delete(ms, friendNode, false);
				}
			}
		}
		res.setSuccess(true);
		return res;
	}

	/*
	 * Adds 'req.userName' as a friend by creating a FRIEND node under the current user's FRIENDS_LIST
	 * if the user wasn't already a friend
	 */
	public AddFriendResponse addFriend(MongoSession ms, AddFriendRequest req) {
		// apLog.trace("addFriend request: " + XString.prettyPrint(req));
		AddFriendResponse res = new AddFriendResponse();
		String userName = ThreadLocals.getSC().getUserName();

		String _newUserName = req.getUserName().trim();
		_newUserName = XString.stripIfStartsWith(_newUserName, "@");
		String newUserName = _newUserName;

		if (newUserName.equalsIgnoreCase(PrincipalName.ADMIN.s())) {
			res.setMessage("You can't be friends with the admin.");
			res.setSuccess(false);
			return res;
		}

		// This is concurrency safe because by the time we get to this async exec, we're done processing in
		// this request thread.
		exec.run(() -> {
			MongoSession mst = ThreadLocals.getMongoSession();

			// get the Friend List of the follower
			SubNode followerFriendList =
					read.getUserNodeByType(mst, userName, null, null, NodeType.FRIEND_LIST.s(), null, NodeName.FRIENDS);

			/*
			 * lookup to see if this followerFriendList node already has userToFollow already under it
			 */
			SubNode friendNode = read.findNodeByUserAndType(mst, followerFriendList, newUserName, NodeType.FRIEND.s());

			// if friendNode was non-null here it means we were already following the user.
			if (no(friendNode)) {
				apLog.trace("loadForeignUser: " + newUserName);
				apub.loadForeignUser(userName, newUserName);

				SubNode userNode = arun.run(s -> {
					return read.getUserNodeByUserName(s, newUserName);
				});

				if (no(userNode))
					return;

				// We can't have both a FRIEND and a BLOCK so remove the friend. There's also a unique constraint on
				// the DB enforcing this.
				deleteFriend(mst, userNode.getIdStr(), NodeType.BLOCKED_USERS.s());

				apLog.trace("Creating friendNode for " + newUserName);
				friendNode = edit.createFriendNode(mst, followerFriendList, newUserName);

				if (ok(friendNode)) {
					friendNode.set(NodeProp.USER_NODE_ID.s(), userNode.getIdStr());
					edit.updateSavedFriendNode(userName, friendNode);

					// todo-2: eventually we can have a design that pushes these results back to the browser async
					// instead of optimistically saying 'Added friend'
					// res.setMessage("Added Friend: " + newUserName);
				} else {

					// res.setMessage("Unable to add Friend: " + newUserName);
				}
			}
		});
		res.setMessage("Added Friend: " + newUserName);
		res.setSuccess(true);
		return res;
	}

	public GetUserProfileResponse getUserProfile(GetUserProfileRequest req) {
		GetUserProfileResponse res = new GetUserProfileResponse();
		String sessionUserName = ThreadLocals.getSC().getUserName();

		arun.run(ms -> {
			SubNode userNode = null;

			if (no(req.getUserId())) {
				userNode = read.getUserNodeByUserName(ms, sessionUserName);
			} else {
				userNode = read.getNode(ms, req.getUserId(), false);
			}

			if (ok(userNode)) {
				UserProfile userProfile = new UserProfile();
				String nodeUserName = userNode.getStr(NodeProp.USER.s());
				String displayName = userNode.getStr(NodeProp.DISPLAY_NAME.s());
				SubNode userHomeNode = read.getNodeByName(ms, nodeUserName + ":" + NodeName.HOME);

				res.setUserProfile(userProfile);
				userProfile.setUserName(nodeUserName);
				userProfile.setDisplayName(displayName);

				if (ok(userHomeNode)) {
					userProfile.setHomeNodeId(userHomeNode.getIdStr());
				}

				String actorUrl = userNode.getStr(NodeProp.ACT_PUB_ACTOR_URL);

				userProfile.setMfsEnable(userNode.getBool(NodeProp.MFS_ENABLE));
				userProfile.setUserBio(userNode.getStr(NodeProp.USER_BIO.s()));
				userProfile.setDidIPNS(userNode.getStr(NodeProp.USER_DID_IPNS));
				userProfile.setUserTags(userNode.getStr(NodeProp.USER_TAGS.s()));
				userProfile.setAvatarVer(userNode.getStr(NodeProp.BIN.s()));
				userProfile.setHeaderImageVer(userNode.getStr(NodeProp.BIN.s() + "Header"));
				userProfile.setUserNodeId(userNode.getIdStr());
				userProfile.setApIconUrl(userNode.getStr(NodeProp.ACT_PUB_USER_ICON_URL));
				userProfile.setApImageUrl(userNode.getStr(NodeProp.ACT_PUB_USER_IMAGE_URL));
				userProfile.setActorUrl(actorUrl);

				Long followerCount = apFollower.countFollowersOfUser(ms, sessionUserName, nodeUserName, actorUrl);
				userProfile.setFollowerCount(followerCount.intValue());

				Long followingCount = apFollowing.countFollowingOfUser(ms, sessionUserName, nodeUserName, actorUrl);
				userProfile.setFollowingCount(followingCount.intValue());

				if (!ThreadLocals.getSC().isAnonUser()) {
					/*
					 * Only for local users do we attemp to generate followers and following, but theoretically we can
					 * use the ActPub API to query for this for foreign users also.
					 */
					boolean blocked = userIsBlockedByMe(ms, nodeUserName);
					userProfile.setBlocked(blocked);

					boolean following = userIsFollowedByMe(ms, nodeUserName);
					userProfile.setFollowing(following);
				}

				// todo-2: add ability to know "follows you" state (for display on UserProfileDlg)
				res.setSuccess(true);
			}
			return null;
		});
		return res;
	}

	public boolean userIsFollowedByMe(MongoSession ms, String maybeFollowedUser) {
		String userName = ThreadLocals.getSC().getUserName();
		SubNode friendsList =
				read.getUserNodeByType(ms, userName, null, null, NodeType.FRIEND_LIST.s(), null, NodeName.BLOCKED_USERS);
		SubNode userNode = read.findNodeByUserAndType(ms, friendsList, maybeFollowedUser, NodeType.FRIEND.s());
		return ok(userNode);
	}

	public boolean userIsBlockedByMe(MongoSession ms, String maybeBlockedUser) {
		String userName = ThreadLocals.getSC().getUserName();
		SubNode blockedList =
				read.getUserNodeByType(ms, userName, null, null, NodeType.BLOCKED_USERS.s(), null, NodeName.BLOCKED_USERS);
		SubNode userNode = read.findNodeByUserAndType(ms, blockedList, maybeBlockedUser, NodeType.FRIEND.s());
		return ok(userNode);
	}

	public UserPreferences getDefaultUserPreferences() {
		UserPreferences userPrefs = new UserPreferences();
		userPrefs.setShowMetaData(true);
		userPrefs.setNsfw(false);
		return userPrefs;
	}

	public UserPreferences getUserPreferences(String userName, SubNode _prefsNode) {
		UserPreferences userPrefs = new UserPreferences();

		arun.run(ms -> {
			SubNode prefsNode = _prefsNode;
			if (no(prefsNode)) {
				prefsNode = read.getUserNodeByUserName(ms, userName);
			}

			userPrefs.setEditMode(prefsNode.getBool(NodeProp.USER_PREF_EDIT_MODE));
			userPrefs.setShowMetaData(prefsNode.getBool(NodeProp.USER_PREF_SHOW_METADATA));
			userPrefs.setNsfw(prefsNode.getBool(NodeProp.USER_PREF_NSFW));
			userPrefs.setShowParents(prefsNode.getBool(NodeProp.USER_PREF_SHOW_PARENTS));
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
		if (ok(passCode)) {
			/*
			 * We can run this block as admin, because the codePart below is secret and is checked for a match
			 */
			arun.run(as -> {

				String userNodeId = XString.truncateAfterFirst(passCode, "-");

				if (no(userNodeId)) {
					throw new RuntimeEx("Unable to find userNodeId: " + userNodeId);
				}
				userNode.setVal(read.getNode(as, userNodeId));

				if (no(userNode.getVal())) {
					throw ExUtil.wrapEx("Invald password reset code.");
				}

				String codePart = XString.parseAfterLast(passCode, "-");

				String nodeCodePart = userNode.getVal().getStr(NodeProp.USER_PREF_PASSWORD_RESET_AUTHCODE.s());
				if (!codePart.equals(nodeCodePart)) {
					throw ExUtil.wrapEx("Invald password reset code.");
				}

				String password = req.getNewPassword();
				userName.setVal(userNode.getVal().getStr(NodeProp.USER.s()));

				if (PrincipalName.ADMIN.s().equals(userName.getVal())) {
					throw new RuntimeEx("changePassword should not be called fror admin user.");
				}

				userNode.getVal().set(NodeProp.PWD_HASH.s(), mongoUtil.getHashOfPassword(password));
				userNode.getVal().delete(NodeProp.USER_PREF_PASSWORD_RESET_AUTHCODE.s());

				// note: the adminRunner.run saves the session so we don't do that here.
				return null;
			});
		} else {
			userNode.setVal(read.getUserNodeByUserName(ms, ms.getUserName()));

			if (no(userNode.getVal())) {
				throw ExUtil.wrapEx("changePassword cannot find user.");
			}

			if (PrincipalName.ADMIN.s().equals(userName.getVal())) {
				throw new RuntimeEx("changePassword should not be called fror admin user.");
			}

			String password = req.getNewPassword();
			userName.setVal(userNode.getVal().getStr(NodeProp.USER.s()));
			userNode.getVal().set(NodeProp.PWD_HASH.s(), mongoUtil.getHashOfPassword(password));
			userNode.getVal().delete(NodeProp.USER_PREF_PASSWORD_RESET_AUTHCODE.s());

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
		arun.run(ms -> {

			String user = req.getUser();
			String email = req.getEmail();

			/* make sure username itself is acceptalbe */
			if (!isNormalUserName(user)) {
				res.setMessage("User name is illegal.");
				res.setSuccess(false);
				return null;
			}

			SubNode ownerNode = read.getUserNodeByUserName(ms, user);
			if (no(ownerNode)) {
				res.setMessage("User does not exist.");
				res.setSuccess(false);
				return null;
			}

			/*
			 * IMPORTANT!
			 *
			 * verify that the email address provides IS A MATCH to the email address for this user!
			 */
			String nodeEmail = ownerNode.getStr(NodeProp.EMAIL.s());
			if (no(nodeEmail) || !nodeEmail.equals(email)) {
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

			ownerNode.set(NodeProp.USER_PREF_PASSWORD_RESET_AUTHCODE.s(), String.valueOf(authCode));
			update.save(ms, ownerNode);

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

	public GetFriendsResponse getFriends(MongoSession ms) {
		GetFriendsResponse res = new GetFriendsResponse();

		List<SubNode> friendNodes = getSpecialNodesList(ms, NodeType.FRIEND_LIST.s(), null, true);

		if (ok(friendNodes)) {
			List<FriendInfo> friends = new LinkedList<>();

			for (SubNode friendNode : friendNodes) {
				String userName = friendNode.getStr(NodeProp.USER.s());
				if (ok(userName)) {
					FriendInfo fi = new FriendInfo();
					fi.setUserName(userName);

					SubNode userNode = read.getUserNodeByUserName(null, userName);
					if (ok(userNode)) {
						fi.setDisplayName(userNode.getStr(NodeProp.DISPLAY_NAME.s()));
					}

					String userNodeId = friendNode.getStr(NodeProp.USER_NODE_ID);

					SubNode friendAccountNode = read.getNode(ms, userNodeId, false);
					if (ok(friendAccountNode)) {
						// if a local user use BIN property on node (account node BIN property is the Avatar)
						if (userName.indexOf("@") == -1) {
							fi.setAvatarVer(friendAccountNode.getStr(NodeProp.BIN));
						}
						// Otherwise the avatar will be specified as a remote user's Icon.
						else {
							fi.setForeignAvatarUrl(friendAccountNode.getStr(NodeProp.ACT_PUB_USER_ICON_URL));
						}
					}
					fi.setUserNodeId(userNodeId);
					friends.add(fi);
				}
			}
			res.setFriends(friends);
		}
		res.setSuccess(true);
		return res;
	}

	/**
	 * Looks in the userName's account under their 'underType' type node and returns all the children.
	 * If userName is passed as null, then we use the currently logged in user
	 */
	public List<SubNode> getSpecialNodesList(MongoSession ms, String underType, String userName, boolean sort) {
		ms = ThreadLocals.ensure(ms);
		List<SubNode> nodeList = new LinkedList<>();
		SubNode userNode = read.getUserNodeByUserName(ms, userName);
		if (no(userNode))
			return null;

		SubNode parentNode = read.findSubNodeByType(ms, userNode, underType);
		if (no(parentNode))
			return null;

		for (SubNode friendNode : read.getChildren(ms, parentNode, sort ? Sort.by(Sort.Direction.ASC, SubNode.ORDINAL) : null,
				null, 0)) {
			nodeList.add(friendNode);
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
		Iterable<SubNode> accountNodes = read.getChildren(ms, MongoUtil.allUsersRootNode.getId(), null, null, 0, null, null);

		for (SubNode accountNode : accountNodes) {
			String userName = accountNode.getStr(NodeProp.USER);
			if (ok(userName)) {
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
		MongoSession ms = auth.getAdminSession();
		SubNode userNode = read.getUserNodeByUserName(ms, sc.getUserName());
		if (ok(userNode)) {
			userNode.set(NodeProp.LAST_ACTIVE_TIME.s(), sc.getLastActiveTime());
			update.save(ms, userNode);
		}
	}

	public int getMaxUploadSize(MongoSession ms) {
		if (ms.isAnon()) {
			return 0;
		}
		if (ms.isAdmin()) {
			return Integer.MAX_VALUE;
		}

		SubNode userNode = read.getUserNodeByUserName(auth.getAdminSession(), ThreadLocals.getSC().getUserName());
		long ret = userNode.getInt(NodeProp.BIN_QUOTA);
		if (ret == 0) {
			return Const.DEFAULT_USER_QUOTA;
		}
		return (int) ret;
	}
}
