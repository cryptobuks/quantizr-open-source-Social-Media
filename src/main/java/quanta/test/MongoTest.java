package quanta.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.actpub.model.APList;
import quanta.actpub.model.APObj;
import quanta.config.ServiceBase;
import quanta.exception.NodeAuthFailedException;
import quanta.exception.base.RuntimeEx;
import quanta.model.client.NodeProp;
import quanta.model.client.PrincipalName;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.util.LimitedInputStreamEx;
import quanta.util.ThreadLocals;
import quanta.util.XString;

/**
 * This is actually where I just run various experiments related to MongoDB, and this is not
 * supposed to be any thing like a unit test for the mongo stuff.
 */

@Component("MongoTest")
@Slf4j 
public class MongoTest extends ServiceBase implements TestIntf {
	@Override
	public void test() throws Exception {
		log.debug("*****************************************************************************************");
		log.debug("MongoTest Running!");

		// testDocOrderQuery();

		// testComplexProperties();

		// testDirtyReads();

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
		// node.setProp("testKeyA", "tesetValA");
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

	private void testDocOrderQuery() {
		arun.run(as -> {
			String rootId = "631503fdb6acb76f73971fec";

			SubNode rootNode = read.getNode(as, rootId);

			log.debug("______________________________________");
			// log.debug("START: " + rootNode.getContent());
			// iterate from root first.
			docOrderTest(as, rootId, rootId);

			Iterable<SubNode> iter = read.getSubGraph(as, rootNode, null, -1, false, false, false);

			// this runs a test to iterate down from every possible starting place in the subgraph
			for (SubNode n : iter) {
				log.debug("______________________________________");
				// log.debug("START: " + n.getContent());
				docOrderTest(as, rootId, n.getIdStr());
			}
			return null;
		});
	}

	private void docOrderTest(MongoSession as, String rootId, String nodeId) {
		List<SubNode> nodes = read.genDocList(as, rootId, nodeId, true, null);
		for (SubNode n : nodes) {
			log.debug("CONTENT: " + n.getContent());
		}
	}

	// "name": ":catjam:",
	// "updated": "2020-08-25T14:05:01Z",
	// "icon": {
	// "type": "Image",
	// "mediaType": "image/gif",
	// "url":
	// "https://files.mastodon.social/custom_emojis/images/000/224/097/original/d9c5e447581399a9.gif"
	// }

	// Verify we can read-write these kinds of properties
	public void testComplexProperties() {
		String nodeId = "61bffa8a0e86eb44d1f04dc6";
		MongoSession as = asUser(PrincipalName.ADMIN.s());

		SubNode node = mongoUtil.findByIdNoCache(new ObjectId(nodeId));

		// APObj payload = new APObj().put("tag", new APList().val(new APObj().put("propname", "propval")));
		node.set(NodeProp.ACT_PUB_TAG, new APList().val(new APObj() //
				.put("name", ":catjam:") //
				.put("icon", new APObj() //
						.put("url",
								"https://files.mastodon.social/custom_emojis/images/000/224/097/original/d9c5e447581399a9.gif"))));

		log.debug("Complex Object: " + XString.prettyPrint(node));
		update.saveSession(as);
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
		if (adminNode == null) {
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
		if (adamsRootNode != null) {
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
		verify("/abc/abc".matches(dc));
		verify(!"/abc/def/x".matches(dc));
		verify(!"/abc/def/x/".matches(dc));
		verify(!"/abc/def/".matches(dc));
		verify(!"/abc/def/abc".matches(dc));
		verify(!"/arq/def/abc".matches(dc));
		verify(!"/abc/def/abc/".matches(dc));
		verify(!"/abc/abc/abc/".matches(dc));
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

		log.debug("All REGEX Path tests ok.");
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
			int maxFileSize = user.getMaxUploadSize(ms);
			attach.writeStream(ms, false, "", node,
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
		SubNode userNode = arun.run(as -> read.getUserNodeByUserName(as, userName));
		if (userNode == null) {
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
