
package quanta.mongo;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.WriteResultChecking;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import quanta.config.AppProp;
import quanta.config.ServiceBase;
import quanta.exception.base.RuntimeEx;
import quanta.util.ExUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Ref: http://mongodb.github.io/mongo-java-driver/3.7/driver/getting-started/quick-start-pojo/
// see also: ServerMonitorListener to detect heartbeats, etc.
/**
 * Spring configuration bean responsible for initializing and setting up MongoDD connection.
 */
@Configuration
@EnableMongoRepositories(basePackages = "quanta.mongo")
public class MongoAppConfig extends AbstractMongoClientConfiguration {
	
	private static Logger log = LoggerFactory.getLogger(MongoAppConfig.class);
	public static final String databaseName = "database";
	private MongoTemplate ops;
	private MongoClient mongoClient;
	private GridFSBucket gridFsBucket;
	private GridFsTemplate grid;
	private SimpleMongoClientDatabaseFactory factory;
	/**
	 * we have this so we can set it to true and know that MongoDb failed and gracefully run in case we
	 * need to run for debugging purposes.
	 */
	public static boolean connectionFailed = false;
	@Autowired
	private AppProp appProp;

	@EventListener
	public void handleContextRefresh(ContextRefreshedEvent event) {
		ServiceBase.init(event.getApplicationContext());
		log.debug("MongoAppConfig. Context refreshed.");
	}

	@Override
	@Bean
	public MongoDatabaseFactory mongoDbFactory() {
		if (connectionFailed) return null;
		if (factory == null) {
			log.debug("create mongoDbFactory");
			try {
				MongoClient mc = mongoClient();
				if (mc != null) {
					factory = new SimpleMongoClientDatabaseFactory(mc, databaseName);
				} else {
					return null;
				}
			} catch (Exception e) {
				connectionFailed = true;
				log.debug("Unable to connect to MongoDb");
				return null;
			}
		}
		ServiceBase.mdbf = factory;
		return factory;
	}

	@Bean
	public GridFSBucket gridFsBucket() {
		if (connectionFailed) return null;
		if (gridFsBucket == null) {
			log.debug("create gridFdBucket");
			MongoDatabaseFactory mdbf = mongoDbFactory();
			if (mdbf != null) {
				MongoDatabase db = mdbf.getMongoDatabase();
				gridFsBucket = GridFSBuckets.create(db);
			}
		}
		return gridFsBucket;
	}

	// DO NOT REMOVE THIS. IT IS REQUIRED FOR THIS BEAN TO WORK.
	@Bean
	public MongoEventListener userCascadingMongoEventListener() {
		return new MongoEventListener();
	}

	@Override
	public MongoClient mongoClient() {
		if (connectionFailed) return null;
		if (mongoClient == null) {
			log.debug("create mongoClient");
			MongoCredential credential = null;
			if (appProp.getMongoSecurity()) {
				log.debug("MongoSecurity enabled.");
				String password = appProp.getMongoPassword();
				credential = MongoCredential.createCredential("root", "admin", password.toCharArray());
			} else {
				log.debug("MongoSecurity disabled.");
			}
			try {
				String mongoHost = appProp.getMongoDbHost();
				Integer mongoPort = appProp.getMongoDbPort();
				if (mongoHost == null) {
					throw new RuntimeEx("mongodb.host property is missing");
				}
				if (mongoPort == null) {
					throw new RuntimeEx("mongodb.port property is missing");
				}
				String uri = "mongodb://" + mongoHost + ":" + String.valueOf(mongoPort);
				log.info("Connecting to MongoDb: " + uri);
				/*
				 * This codec registroy is what allows us to store objects that contain other POJOS, like for
				 * example the way we're storing AccessControl objects in a map inside SubNode
				 */
				CodecRegistry pojoCodecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), fromProviders(PojoCodecProvider.builder().automatic(true).build()));
				MongoClientSettings.Builder builder = MongoClientSettings.builder();
				if (credential != null) {
					builder = builder.credential(credential);
				}
				builder = builder.applyConnectionString(new ConnectionString(uri)); //
				builder = builder.codecRegistry(pojoCodecRegistry); //
				MongoClientSettings settings = builder.build();
				mongoClient = MongoClients.create(settings);
				if (mongoClient != null) {
					if (credential != null) {
						for (String db : mongoClient.listDatabaseNames()) {
							log.debug("MONGO DB NAME: " + db);
						}
					}
					log.info("Connected to Mongo OK.");
				} else {
					connectionFailed = true;
					log.error("Unable to connect MongoClient");
				}
			} catch (Exception e) {
				connectionFailed = true;
				ExUtil.error(log, "********** Unable to connect to MongoDb. **********", e);
				throw e;
			}
		}
		return mongoClient;
	}

	/**
	 * MongoTemplate is thread-safe and can be reused everywhere in all threads.
	 */
	@Override
	@Bean
	public MongoTemplate mongoTemplate(MongoDatabaseFactory databaseFactory, MappingMongoConverter converter) {
		if (ops == null) {
			log.debug("create mongoTemplate");
			ops = super.mongoTemplate(databaseFactory, converter);
			ops.setWriteResultChecking(WriteResultChecking.EXCEPTION);
			ServiceBase.ops = ops;
		}
		return ops;
	}

	@Bean
	public GridFsTemplate gridFsTemplate(MappingMongoConverter converter) throws Exception {
		if (connectionFailed) return null;
		if (grid == null) {
			log.debug("create gridFsTemplate");
			MongoDatabaseFactory mdbf = mongoDbFactory();
			if (mdbf != null) {
				grid = new GridFsTemplate(mdbf, converter);
			} else {
				return null;
			}
		}
		return grid;
	}

	@Override
	protected String getDatabaseName() {
		return databaseName;
	}

	@Override
	protected String getMappingBasePackage() {
		return "quanta.mongo";
	}
}
