package quanta.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import lombok.extern.slf4j.Slf4j;
import quanta.CallProcessor;
import quanta.actpub.ActPubCache;
import quanta.actpub.ActPubCrypto;
import quanta.actpub.ActPubFactory;
import quanta.actpub.ActPubFollower;
import quanta.actpub.ActPubFollowing;
import quanta.actpub.ActPubOutbox;
import quanta.actpub.ActPubReplies;
import quanta.actpub.ActPubService;
import quanta.actpub.ActPubUtil;
import quanta.lucene.FileIndexer;
import quanta.mail.EmailSender;
import quanta.mail.EmailSenderDaemon;
import quanta.mail.OutboxMgr;
import quanta.mongo.AdminRun;
import quanta.mongo.MongoAppConfig;
import quanta.mongo.MongoAuth;
import quanta.mongo.MongoCreate;
import quanta.mongo.MongoDelete;
import quanta.mongo.MongoRead;
import quanta.mongo.MongoRepository;
import quanta.mongo.MongoUpdate;
import quanta.mongo.MongoUtil;
import quanta.service.AclService;
import quanta.service.AttachmentService;
import quanta.service.CryptoService;
import quanta.service.GraphNodesService;
import quanta.service.LuceneService;
import quanta.service.OpenGraphService;
import quanta.service.PushService;
import quanta.service.RSSFeedService;
import quanta.service.SchemaOrgService;
import quanta.service.SystemService;
import quanta.service.UserFeedService;
import quanta.service.UserManagerService;
import quanta.service.imports.ImportBookService;
import quanta.service.imports.ImportService;
import quanta.service.ipfs.IPFSCat;
import quanta.service.ipfs.IPFSConfig;
import quanta.service.ipfs.IPFSDag;
import quanta.service.ipfs.IPFSFiles;
import quanta.service.ipfs.IPFSKey;
import quanta.service.ipfs.IPFSName;
import quanta.service.ipfs.IPFSObj;
import quanta.service.ipfs.IPFSPin;
import quanta.service.ipfs.IPFSPubSub;
import quanta.service.ipfs.IPFSRepo;
import quanta.service.ipfs.IPFSService;
import quanta.service.ipfs.IPFSSwarm;
import quanta.service.node.NodeEditService;
import quanta.service.node.NodeMoveService;
import quanta.service.node.NodeRenderService;
import quanta.service.node.NodeSearchService;
import quanta.service.nostr.NostrService;
import quanta.types.BookmarkType;
import quanta.types.FriendType;
import quanta.types.NostrEncryptedDMType;
import quanta.types.RoomType;
import quanta.types.RssFeedType;
import quanta.types.TypePluginMgr;
import quanta.util.AsyncExec;
import quanta.util.Convert;
import quanta.util.EnglishDictionary;
import quanta.util.FileUtils;
import quanta.util.MimeUtil;
import quanta.util.SubNodeUtil;
import quanta.util.Validator;

/**
 * We have lots of circular references in our services, and since SpringBoot has decided it doesn't
 * support that without setting a flag to disable checking, I solved this problem in this monolithic
 * way, because I don't consider circular references among beans to be a bad thing, nor am I going
 * to refactor a million line of code to eliminate them because of this flawed presumptuious
 * opinionated decision made by Spring Boot developers.
 * 
 * To make all services able to access other services we break convention here and use inheritance
 * in a non "is-a" way, which is normally bad practice. However the benefit to this small design
 * choice is huge in numbers of saved lines of code. The fact that it creates a monolith of all
 * these services is fine too, because none of this code shares to other projects. Also since
 * Services are a type of class that really never need to derive from other base classes, it's
 * perfectly acceptable to use the same base class across all of them so they can all reference each
 * other using a simple "object.property" syntax.
 * 
 * Another reason this class is good is that it gives every object a way to call methods on itself
 * AND have AOP methods (like @PerfMon-driven ones) be capable of working. If you don't call a
 * method thru it's proxy object then Spring AOP does NOT work, and this is a problem because in
 * Spring normally when you call a method in the same object you're calling from Spring WILL NOT go
 * thru the proxy. As an example if we called a method in UserFeedService from inside that same
 * service, then unless we call like this: userFeed.myMethod(), then the proxy-based AOP stuff will
 * not execute, becuase it won't ge called thru a proxy.
 */
@Slf4j
public class ServiceBase {
	@Autowired
	public AppProp prop;

	@Autowired
	public ApplicationContext context;

	public static UserFeedService userFeed;
	public static Convert convert;
	public static TypePluginMgr typePluginMgr;
	public static MongoCreate create;
	public static MongoRead read;
	public static MongoUpdate update;
	public static MongoDelete delete;
	public static MongoAuth auth;
	public static MongoUtil mongoUtil;
	public static SubNodeUtil snUtil;
	public static AclService acl;
	public static UserManagerService user;
	public static AdminRun arun;
	public static AttachmentService attach;
	public static ActPubService apub;
	public static NodeRenderService render;
	public static NodeEditService edit;
	public static ActPubCache apCache;
	public static Validator validator;
	public static OutboxMgr outbox;
	public static EmailSenderDaemon notify;
	public static EmailSender mail;
	public static PushService push;
	public static ActPubUtil apUtil;
	public static ActPubFollower apFollower;
	public static ActPubFollowing apFollowing;
	public static ActPubReplies apReplies;
	public static GraphNodesService graphNodes;
	public static ActPubOutbox apOutbox;
	public static EnglishDictionary english;
	public static AsyncExec exec;
	public static NodeSearchService search;
	public static CallProcessor callProc;
	public static NodeMoveService move;
	public static ImportBookService importBookService;
	public static ActPubCrypto apCrypto;
	public static ImportService importService;
	public static LuceneService lucene;
	public static FileIndexer fileIndexer;
	public static SystemService system;
	public static RSSFeedService rssFeed;
	public static OpenGraphService openGraph;
	public static ActPubFactory apFactory;
	public static FileUtils fileUtil;
	public static MimeUtil mimeUtil;
	public static MongoAppConfig mac;
	public static BookmarkType bookmarkType;
	public static NostrEncryptedDMType nostrEncryptedDMType;
	public static FriendType friendType;
	public static RoomType roomType;
	public static RssFeedType rssType;
	public static MongoTemplate ops;
	public static MongoRepository mongoRepo;
	public static SimpleMongoClientDatabaseFactory mdbf;
	public static CryptoService crypto;
	public static SchemaOrgService schema;
	public static NostrService nostr;

	public static IPFSService ipfs;
	public static IPFSCat ipfsCat;
	public static IPFSFiles ipfsFiles;
	public static IPFSPin ipfsPin;
	public static IPFSObj ipfsObj;
	public static IPFSDag ipfsDag;
	public static IPFSName ipfsName;
	public static IPFSKey ipfsKey;
	public static IPFSRepo ipfsRepo;
	public static IPFSSwarm ipfsSwarm;
	public static IPFSConfig ipfsConfig;
	public static IPFSPubSub ipfsPubSub;

	public static boolean initComplete = false;
	public static final Object initLock = new Object();

	public ServiceBase() {
		// log.debug("ServiceBase(a): " + getClass().getName());
	}

	/*
	 * Note: All` @EventListener public void handleContextRefresh(ContextRefreshedEvent event)` should
	 * call this method immediately before doing anything else, and this is fine because nothing happens
	 * on subsequent runs. The reason is because we cannot predict WHICH @EventListener will be called
	 * first, so we must allow any sequence that Spring happens to run with, in a non-deterministic way.
	 */
	public static void init(ApplicationContext ctx) {
		synchronized (initLock) {
			if (initComplete) {
				return;
			}
			log.debug("Setting ServiceBase Proxy Instances...");

			userFeed = getBean(ctx, UserFeedService.class);
			convert = getBean(ctx, Convert.class);
			typePluginMgr = getBean(ctx, TypePluginMgr.class);
			create = getBean(ctx, MongoCreate.class);
			read = getBean(ctx, MongoRead.class);
			update = getBean(ctx, MongoUpdate.class);
			delete = getBean(ctx, MongoDelete.class);
			auth = getBean(ctx, MongoAuth.class);
			mongoUtil = getBean(ctx, MongoUtil.class);
			snUtil = getBean(ctx, SubNodeUtil.class);
			acl = getBean(ctx, AclService.class);
			user = getBean(ctx, UserManagerService.class);
			arun = getBean(ctx, AdminRun.class);
			attach = getBean(ctx, AttachmentService.class);
			apub = getBean(ctx, ActPubService.class);
			render = getBean(ctx, NodeRenderService.class);
			edit = getBean(ctx, NodeEditService.class);
			apCache = getBean(ctx, ActPubCache.class);
			validator = getBean(ctx, Validator.class);
			outbox = getBean(ctx, OutboxMgr.class);
			notify = getBean(ctx, EmailSenderDaemon.class);
			mail = getBean(ctx, EmailSender.class);
			push = getBean(ctx, PushService.class);
			apUtil = getBean(ctx, ActPubUtil.class);
			apFollower = getBean(ctx, ActPubFollower.class);
			apFollowing = getBean(ctx, ActPubFollowing.class);
			apReplies = getBean(ctx, ActPubReplies.class);
			graphNodes = getBean(ctx, GraphNodesService.class);
			apOutbox = getBean(ctx, ActPubOutbox.class);
			english = getBean(ctx, EnglishDictionary.class);
			exec = getBean(ctx, AsyncExec.class);
			search = getBean(ctx, NodeSearchService.class);
			callProc = getBean(ctx, CallProcessor.class);
			move = getBean(ctx, NodeMoveService.class);
			importBookService = getBean(ctx, ImportBookService.class);
			apCrypto = getBean(ctx, ActPubCrypto.class);
			importService = getBean(ctx, ImportService.class);
			lucene = getBean(ctx, LuceneService.class);
			fileIndexer = getBean(ctx, FileIndexer.class);
			system = getBean(ctx, SystemService.class);
			rssFeed = getBean(ctx, RSSFeedService.class);
			openGraph = getBean(ctx, OpenGraphService.class);
			apFactory = getBean(ctx, ActPubFactory.class);
			fileUtil = getBean(ctx, FileUtils.class);
			mimeUtil = getBean(ctx, MimeUtil.class);
			bookmarkType = getBean(ctx, BookmarkType.class);
			nostrEncryptedDMType = getBean(ctx, NostrEncryptedDMType.class);
			friendType = getBean(ctx, FriendType.class);
			roomType = getBean(ctx, RoomType.class);
			rssType = getBean(ctx, RssFeedType.class);
			mongoRepo = getBean(ctx, MongoRepository.class);
			crypto = getBean(ctx, CryptoService.class);
			schema = getBean(ctx, SchemaOrgService.class);
			nostr = getBean(ctx, NostrService.class);

			ipfs = getBean(ctx, IPFSService.class);
			ipfsCat = getBean(ctx, IPFSCat.class);
			ipfsFiles = getBean(ctx, IPFSFiles.class);
			ipfsPin = getBean(ctx, IPFSPin.class);
			ipfsObj = getBean(ctx, IPFSObj.class);
			ipfsDag = getBean(ctx, IPFSDag.class);
			ipfsName = getBean(ctx, IPFSName.class);
			ipfsKey = getBean(ctx, IPFSKey.class);
			ipfsRepo = getBean(ctx, IPFSRepo.class);
			ipfsSwarm = getBean(ctx, IPFSSwarm.class);
			ipfsConfig = getBean(ctx, IPFSConfig.class);
			ipfsPubSub = getBean(ctx, IPFSPubSub.class);

			initComplete = true;
		}
	}

	static <T> T getBean(ApplicationContext ctx, Class<T> requiredType) throws BeansException {
		log.debug("getBean: " + requiredType.getSimpleName());
		return ctx.getBean(requiredType);
	}

	public void checkIpfs() {
		if (!prop.ipfsEnabled()) {
			throw new RuntimeException("IPFS Not Enabled");
		}
	}
}
