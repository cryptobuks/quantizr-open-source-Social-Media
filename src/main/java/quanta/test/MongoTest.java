package quanta.test;

import static quanta.util.Util.no;
import static quanta.util.Util.ok;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import org.apache.commons.io.FileUtils;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import quanta.exception.NodeAuthFailedException;
import quanta.exception.base.RuntimeEx;
import quanta.model.client.PrincipalName;
import quanta.mongo.MongoAuth;
import quanta.mongo.MongoCreate;
import quanta.mongo.MongoRead;
import quanta.mongo.MongoSession;
import quanta.mongo.MongoUpdate;
import quanta.mongo.MongoUtil;
import quanta.mongo.model.SubNode;
import quanta.service.AttachmentService;
import quanta.service.UserManagerService;
import quanta.util.LimitedInputStreamEx;
import quanta.util.ThreadLocals;

/**
 * This is actually where I just run various experiments related to MongoDB, and this is not
 * supposed to be any thing like a unit test for the mongo stuff.
 */
@Lazy
@Component("MongoTest")
public class MongoTest implements TestIntf {
	private static final Logger log = LoggerFactory.getLogger(MongoTest.class);

	@Autowired
	@Lazy
	private MongoUtil mongoUtil;

	@Autowired
	@Lazy
	private MongoCreate create;

	@Autowired
	@Lazy
	private MongoRead read;

	@Autowired
	@Lazy
	private MongoUpdate update;

	@Autowired
	@Lazy
	private MongoAuth auth;

	@Autowired
	@Lazy
	private AttachmentService attach;

	@Autowired
	@Lazy
	private UserManagerService usrMgr;

	@Override
	public void test() throws Exception {
		log.debug("*****************************************************************************************");
		log.debug("MongoTest Running!");

		testDirtyReads();

		// testPathRegex();

		// authTest();

		// // Verify we can lookup the node we just inserted, by ObjectId
		// SubNode nodeFoundById = read.getNode(adminSession, node.getId());
		// if (no(nodeFoundById )) {
		// throw new RuntimeEx("Unable to find node by id.");
		// }

		// // Verify a lookup by hex string
		// SubNode nodeFoundByStrId = read.getNode(adminSession,
		// node.getIdStr());
		// if (no(nodeFoundByStrId )) {
		// throw new RuntimeEx("Unable to find node by id: " +
		// node.getIdStr());
		// }

		// // Set a property on the node and save the node
		// node.setProp("testKeyA", new SubNodePropVal("tesetValA"));
		// update.save(adminSession, node);
		// log.debug("updated first node.");

		// String newGuyName = "newguy";
		// SubNode stuffOwnerNode = util.createUser(adminSession, newGuyName, "",
		// "passy", true);
		// MongoSession session = MongoSession.createFromNode(stuffOwnerNode);

		// // ----------Verify an attempt to write a duplicate 'path' fails
		// boolean uniqueViolationCaught = false;
		// try {
		// SubNode dupNode = create.createNode(adminSession, "/usrx");
		// update.save(adminSession, dupNode);
		// }
		// catch (Exception e) {
		// uniqueViolationCaught = true;
		// }

		// if (!uniqueViolationCaught) {
		// throw new RuntimeEx("Failed to catch unique constraint violation.");
		// }

		// runBinaryTests(adminSession);

		log.debug("Mongo Test Ok.");
		log.debug("*****************************************************************************************");
	}

	public void testDirtyReads() {
		String nodeId = "61bcdd5b47596e66a7a11ce5";
		MongoSession as = asUser(PrincipalName.ADMIN.s());

		SubNode node1 = mongoUtil.findByIdNoCache(new ObjectId(nodeId));
		node1.setContent("content from MongoTest.testDirtyReads");
		log.debug("node1: hashCode=" + node1.hashCode());

		// This will verify that the MongoEventListener is capable of detecting the dirty read and
		// logging a warning about it.
		SubNode node2 = mongoUtil.findByIdNoCache(new ObjectId(nodeId));
		log.debug("node2: hashCode=" + node2.hashCode());
		// update.saveSession(ms);
	}

	public void authTest() {
		MongoSession as = asUser(PrincipalName.ADMIN.s());

		SubNode adminNode = read.getUserNodeByUserName(as, PrincipalName.ADMIN.s());
		if (no(adminNode)) {
			throw new RuntimeEx("Unable to find admin user node.");
		}

		// root for all testing
		SubNode testingRoot = create.createNode(as, "/r/?");
		testingRoot.setContent("Root for Testing");
		update.save(as, testingRoot);

		// Insert a test node
		SubNode adminsNode = create.createNode(as, testingRoot.getPath() + "/?");
		adminsNode.setContent("admin's test node " + System.currentTimeMillis());
		update.save(as, adminsNode);
		ObjectId insertedId = adminsNode.getId();
		log.debug("admin inserted a node: " + insertedId.toString());

		// login as adam
		MongoSession adamSession = asUser("adam");

		// adam tries to set the path on adminsNode
		try {
			adminsNode.setPath(adminsNode.getPath() + "abc");
			update.save(adamSession, adminsNode);
			throw new RuntimeException("failed to block path alter.");
		} catch (NodeAuthFailedException e) {
			log.debug("Successful path alter blocked.");
		}

		// let adam try and fail to access insertedId
		try {
			SubNode updateNode = read.getNode(adamSession, insertedId);
			throw new RuntimeException("failed to block.");
		} catch (NodeAuthFailedException e) {
			log.debug("Successful auth block.");
		}

		// adam attempts and fails to create a node in a protected area
		try {
			log.debug("Insecure root insert test.");
			SubNode adamsNode = create.createNode(adamSession, testingRoot.getPath() + "/?");
			adamsNode.setContent("adam's test node " + System.currentTimeMillis());
			update.save(as, adamsNode);
			throw new RuntimeException("allowed node in secure area");
		} catch (NodeAuthFailedException e) {
			log.debug("successfully blocked invalid create (in root)");
		}

		// adam attempts and fails to create a node under adminsNode
		try {
			log.debug("Insecure insert test (under adminsNode)");
			SubNode adamsNode = create.createNode(adamSession, adminsNode.getPath() + "/?");
			adamsNode.setContent("adam's test node " + System.currentTimeMillis());
			update.save(as, adamsNode);
			throw new RuntimeException("allowed node in secure area");
		} catch (NodeAuthFailedException e) {
			log.debug("successfully blocked invalid create (in an admin node)");
		}

		// adam successfully inserts node in his root
		SubNode adamsNode = null;
		SubNode adamsRootNode = read.getUserNodeByUserName(adamSession, "adam");
		if (ok(adamsRootNode)) {
			adamsNode = create.createNode(adamSession, adamsRootNode.getPath() + "/?");
			adamsNode.setContent("adam's test node " + System.currentTimeMillis());
			update.save(adamSession, adamsNode);
			insertedId = adamsNode.getId();
			log.debug("adam inserted a node: " + insertedId.toString());
		}

		// admin tries to save a duplicated path
		asSession(as);
		adamsNode.setPath(adminsNode.getPath());
		try {
			ThreadLocals.setMongoSession(as);
			update.save(as, adamsNode);
			throw new RuntimeException("failed to detect keydup.");
		} catch (DuplicateKeyException e) {
			log.debug("Successfully rejected key dup.");
		}

		// adam tries and fails to save adminsNode
		asSession(adamSession);
		try {
			log.debug("Attempting save by wrong user.");
			ThreadLocals.setMongoSession(adamSession);
			update.save(adamSession, adminsNode);
			throw new RuntimeException("failed to block.");
		} catch (NodeAuthFailedException e) {
			log.debug("Successfully blocked save by wrong user.");
		}
	}

	public void testPathRegex() {
		// Direct Children Test
		String dc = mongoUtil.regexDirectChildrenOfPath("/abc");
		verify("/abc/def".matches(dc));
		verify(!"/abc/def/x".matches(dc));
		verify(!"/abcx".matches(dc));

		// Recursive Children Test
		String rc = mongoUtil.regexRecursiveChildrenOfPath("/abc");
		verify("/abc/x".matches(rc));
		verify("/abc/def".matches(rc));
		verify("/abc/def/x".matches(rc));
		verify("/abc/def/xyz/nop".matches(rc));
		verify(!"/abcx".matches(rc));

		// the final slash is built into the query.
		verify(!"/abc".matches(rc));
	}

	public void verify(boolean val) {
		if (!val) {
			throw new RuntimeException("Assertion failed.");
		}
	}

	public void runBinaryTests(MongoSession ms) {
		log.debug("Running binaries tests.");

		try {
			SubNode node = create.createNode(ms, "/binaries");
			update.save(ms, node);
			int maxFileSize = usrMgr.getMaxUploadSize(ms);
			attach.writeStream(ms, "", node,
					new LimitedInputStreamEx(new FileInputStream("/home/clay/test-image.png"), maxFileSize), null, "image/png",
					null);
			update.save(ms, node);

			log.debug("inserted root for binary testing.", null, "image/png", null);

			InputStream inStream = attach.getStream(ms, "", node, true);
			FileUtils.copyInputStreamToFile(inStream, new File("/home/clay/test-image2.png"));
			log.debug("completed reading back the file, and writing out a copy.");
		} catch (Exception e) {
			throw new RuntimeEx(e);
		}
	}

	private MongoSession asUser(String userName) {
		SubNode userNode = read.getUserNodeByUserName(auth.getAdminSession(), userName);
		if (no(userNode)) {
			throw new RuntimeException("UserNode not found for userName " + userName);
		}
		MongoSession ms = new MongoSession(userName, userNode.getId());
		asSession(ms);
		return ms;
	}

	private void asSession(MongoSession ms) {
		ThreadLocals.setMongoSession(ms);
	}
}
