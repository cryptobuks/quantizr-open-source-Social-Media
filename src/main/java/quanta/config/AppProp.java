package quanta.config;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import quanta.actpub.APConst;
import quanta.mongo.MongoRepository;
import quanta.util.ExUtil;
import quanta.util.StreamUtil;
import quanta.util.XString;

/**
 * Wrapper to access application properties.
 * 
 * WARNING: Don't put this in ServiceBase with other singletons, because this one needs to be
 * accessible immediately to be used by other beans before all are fully initialized.
 */
@Component
@Slf4j
public class AppProp {
	@Autowired
	private Environment env;

	@Autowired
	private ApplicationContext context;

	// if false this disables all backgrouind processing.
	private boolean daemonsEnabled = true;

	private String protocolHostAndPort = null;

	public static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
	HashMap<String, Object> configMap = null;
	private static final Object configLock = new Object();

	public HashMap<String, Object> getConfig() {
		synchronized (configLock) {
			if (configMap != null) {
				return configMap;
			}

			synchronized (yamlMapper) {
				try {
					configMap = readYamlExternal("config-text.yaml");

					// if we found the external config file in [deploy]/config/ folder then use it's contents
					if (configMap == null) {
						// otherwise use the internal version (internal to JAR)
						configMap = readYamlInternal("config-text.yaml");
					}

					setPropertyOrdinals();
				} catch (Exception e) {
					ExUtil.error(log, "failed to load help-text.yaml", e);
				}

				if (configMap == null) {
					configMap = new HashMap<>();
				}
				configMap.put("ipfsEnabled", ipfsEnabled());
				return configMap;
			}
		}
	}

	private void setPropertyOrdinals() {
		LinkedHashMap<String, LinkedHashMap> classes = (LinkedHashMap) configMap.get("props");
		for (LinkedHashMap<String, LinkedHashMap> clazz : classes.values()) {
			int attIdx = 1;
			for (LinkedHashMap<String, Object> prop : clazz.values()) {
				prop.put("ord", attIdx++);
			}
		}
	}

	/* Gets config text from external file if found, and it not gets property from internal */
	public String getConfigText(String prop) {
		return (String) getConfig().get(prop);
	}

	/*
	 * Reads a yaml file into a map from internal file at "classname:[fileName]
	 */
	private HashMap<String, Object> readYamlInternal(String fileName) {
		synchronized (yamlMapper) {
			InputStream is = null;
			HashMap<String, Object> map = null;

			try {
				log.debug("Loading config from internal classpath: " + fileName);
				Resource resource = context.getResource("classpath:" + fileName);
				is = resource.getInputStream();

				map = yamlMapper.readValue(is, new TypeReference<HashMap<String, Object>>() {});
				if (map == null) {
					map = new HashMap<>();
				}

			} catch (Exception e) {
				ExUtil.error(log, "failed to load help-text.yaml", e);
			} finally {
				StreamUtil.close(is);
			}
			return map;
		}
	}

	private HashMap<String, Object> readYamlExternal(String fileName) {
		synchronized (yamlMapper) {
			HashMap<String, Object> map = null;
			try {
				File file = new File("/config/" + fileName);

				// if an external config file is found use it.
				if (file.isFile()) {
					log.debug("Loading config from file system: " + fileName);
					map = yamlMapper.readValue(file, new TypeReference<HashMap<String, Object>>() {});
				}
			} catch (Exception e) {
				ExUtil.error(log, "failed to load help-text.yaml", e);
			}
			return map;
		}
	}

	public String getHostAndPort() {
		return getHttpProtocol() + "://" + getMetaHost() + ":" + getServerPort();
	}

	public String getLuceneDir() {
		return "/subnode-lucene"; // todo-2: get this from prop
	}

	public String getStringProp(String propName) {
		return env.getProperty(propName);
	}

	public String getMetaHost() {
		return env.getProperty("metaHost");
	}

	public String getHttpProtocol() {
		return env.getProperty("httpProtocol");
	}

	public String getProtocolHostAndPort() {
		if (protocolHostAndPort != null)
			return protocolHostAndPort;

		protocolHostAndPort = getHttpProtocol() + "://" + getMetaHost();

		// If port is needed (not default) then add it.
		if (!(getHttpProtocol().equals("https") && getServerPort().equals("443"))
				&& !(getHttpProtocol().equals("http") && getServerPort().equals("80"))) {
			protocolHostAndPort += ":" + getServerPort();
		}

		return protocolHostAndPort;
	}

	public Integer getSessionTimeoutMinutes() {
		String timeout = env.getProperty("server.servlet.session.timeout");
		Integer timeoutVal = Integer.parseInt(timeout.replace("m", ""));
		return timeoutVal;
	}

	public List<String> getRunTests() {
		List<String> ret = null;
		String runTests = env.getProperty("runTests");
		if (!StringUtils.isEmpty(runTests)) {
			ret = XString.tokenize(runTests, ",", true);
		} else {
			ret = new LinkedList<>();
		}
		return ret;
	}

	public String getServerPort() {
		return env.getProperty("server.port");
	}

	public String getTServerPort() {
		return env.getProperty("TSERVER_PORT");
	}

	public String getTServerApiKey() {
		return env.getProperty("TSERVER_API_KEY");
	}

	public String getProfileName() {
		return env.getProperty("profileName");
	}

	public String getInstanceId() {
		return env.getProperty("instanceId");
	}

	public boolean isActPubEnabled() {
		return APConst.TRUE.equals(env.getProperty("actPubEnabled"));
	}

	public boolean isNostrDaemonEnabled() {
		return APConst.TRUE.equals(env.getProperty("nostrDaemonEnabled"));
	}

	public boolean ipfsEnabled() {
		return APConst.TRUE.equals(env.getProperty("ipfsEnabled"));
	}

	public String getIPFSApiHostAndPort() {
		return env.getProperty("ipfs.host") + ":" + env.getProperty("ipfs.apiPort");
	}

	public String getIPFSApiBase() {
		return getIPFSApiHostAndPort() + "/api/v0";
	}

	public String getIPFSGatewayHostAndPort() {
		return env.getProperty("ipfs.host") + ":" + env.getProperty("ipfs.gatewayPort");
	}

	public String getMongoDbHost() {
		return env.getProperty("mongodb.host");
	}

	public Integer getMongoDbPort() {
		return Integer.parseInt(env.getProperty("mongodb.port"));
	}

	public String getAdminDataFolder() {
		return getPathProperty("adminDataFolder");
	}

	public String getRsaKeyFolder() {
		return getPathProperty("rsaKeyFolder");
	}

	public String getTestUserAccounts() {
		return env.getProperty("testUserAccounts");
	}

	public String getNostrCurationAccounts() {
		return env.getProperty("nostrCurationAccounts");
	}

	public String getMongoAdminUserName() {
		return env.getProperty("mongoAdminUserName");
	}

	/* Should be set to true if mongo security is turned on requiring credentials */
	public boolean getMongoSecurity() {
		return APConst.TRUE.equals(env.getProperty("mongoSecurity"));
	}

	public String getMongoPassword() {
		return env.getProperty("mongoPassword");
	}

	public String getAdminPassword() {
		return env.getProperty("adminPassword");
	}

	public boolean isRequireCrypto() {
		return APConst.TRUE.equals(env.getProperty("requireCrypto"));
	}

	public String getTestPassword() {
		return env.getProperty("testPassword");
	}

	public String getUserLandingPageNode() {
		return env.getProperty("anonUserLandingPageNode");
	}

	public int getThrottleTime() {
		return Integer.parseInt(env.getProperty("throttleTime"));
	}

	public String getMailHost() {
		return env.getProperty("mail.host");
	}

	public String getMailFrom() {
		return env.getProperty("mail.from");
	}

	public String getMailPort() {
		return env.getProperty("mail.port");
	}

	public String getMailUser() {
		return env.getProperty("mail.user");
	}

	public String getMailPassword() {
		return env.getProperty("mail.password");
	}

	public boolean isAllowFileSystemSearch() {
		return getBooleanProp("allowFileSystemSearch");
	}

	/* considers property 'true' if it starts with letter 't', 'y' (yes), or 1 */
	public boolean getBooleanProp(String propName) {
		String val = env.getProperty(propName);
		if (val == null)
			return false;
		val = val.toLowerCase();
		return val.startsWith("t") || val.startsWith("y") || val.startsWith("1");
	}

	public String getProp(String propName) {
		return env.getProperty(propName);
	}

	public String getPathProperty(String propName) {
		return translateDirs(env.getProperty(propName));
	}

	public String translateDirs(String folder) {
		if (folder == null)
			return folder;
		String userDir = System.getProperty("user.dir");
		return folder.replace("{user.dir}", userDir);
	}

	public boolean isDaemonsEnabled() {
		return daemonsEnabled && MongoRepository.fullInit;
	}

	public void setDaemonsEnabled(boolean daemonsEnabled) {
		this.daemonsEnabled = daemonsEnabled;
		log.debug("setDaemonsEnabled: " + String.valueOf(daemonsEnabled));
	}
}
