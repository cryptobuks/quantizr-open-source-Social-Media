package quanta.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import quanta.config.NodePath;
import quanta.config.ServiceBase;
import quanta.config.SessionContext;
import quanta.model.Jwk;
import quanta.model.client.Attachment;
import quanta.model.client.NodeProp;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.SignNodesRequest;
import quanta.request.SignSubGraphRequest;
import quanta.response.NodeSigData;
import quanta.response.NodeSigPushInfo;
import quanta.response.PushPageMessage;
import quanta.response.SignNodesResponse;
import quanta.util.Const;
import quanta.util.ExUtil;
import quanta.util.ThreadLocals;
import quanta.util.Util;
import quanta.util.val.BooleanVal;
import quanta.util.val.IntVal;
import quanta.util.val.Val;

@Component
@Slf4j 
public class CryptoService extends ServiceBase {
	public static final ObjectMapper mapper = new ObjectMapper();

	public final ConcurrentHashMap<Integer, NodeSigPushInfo> sigPendingQueue = new ConcurrentHashMap<>();
	private static final Random rand = new Random();

	int SIGN_BLOCK_SIZE = 100;

	// NOTE: This didn't allow unknown properties as expected but putting the
	// following in the JSON classes did:
	// @JsonIgnoreProperties(ignoreUnknown = true)
	{
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	public boolean nodeSigVerify(SubNode node, String sig) {
		if (sig == null || node == null)
			return false;
		PublicKey pubKey = null;

		try {
			// if we didn't get this as admin key we'll be generating the key
			if (pubKey == null) {
				// log.debug("Checking Signature: " + sig + " nodeId: " + node.getIdStr());
				SubNode ownerAccntNode = arun.run(as -> read.getNode(as, node.getOwner()));
				if (ownerAccntNode == null) {
					log.error("sig check failed. Can't find owner of node: " + node.getIdStr());
					return false;
				}

				String pubKeyJson = ownerAccntNode.getStr(NodeProp.USER_PREF_PUBLIC_SIG_KEY);
				if (pubKeyJson == null) {
					log.debug("User Account didn't have SIG KEY: accntNodeId=" + ownerAccntNode.getIdStr() + " They own nodeId="
							+ node.getIdStr());
					return false;
				}

				pubKey = parseJWK(pubKeyJson, ownerAccntNode);
				if (pubKey == null) {
					log.error("Unable generate USER_PREF_PUBLIC_SIG_KEY for accnt " + ownerAccntNode.getIdStr());
					return false;
				}
			}

			String strToSign = getNodeSigData(node);

			boolean verified = sigVerify(pubKey, Util.hexStringToBytes(sig), strToSign.getBytes(StandardCharsets.UTF_8));
			if (!verified) {
				// log.debug("SIG FAIL nodeId: " + node.getIdStr() + "\nsigData: [" + strToSign + "] signature: " + sig);
			}
			return verified;
		} catch (Exception e) {
			ExUtil.error(log, "crypto sig failed on " + node.getIdStr(), e);
		}

		return false;
	}

	public String getNodeSigData(SubNode node) {
		String path = node.getPath();
		if (path.startsWith(NodePath.PENDING_PATH + "/")) {
			path = NodePath.ROOT_PATH + "/" + path.substring(5);
		}

		String strToSign = path + "-" + node.getOwner().toHexString();
		if (StringUtils.isNotEmpty(node.getContent())) {
			strToSign += "-" + node.getContent();
		}

		List<Attachment> atts = node.getOrderedAttachments();
		if (atts != null && atts.size() > 0) {
			for (Attachment att : atts) {
				if (att.getBin() != null) {
					strToSign += "-" + att.getBin();
				}
				if (att.getBinData() != null) {
					strToSign += "-" + att.getBinData();
				}
			}
		}

		return strToSign;
	}

	/*
	 * some day we need to find a cleaner way to parse JWK, but this seems to be the best solution
	 * everyone is using based on my google searches.
	 */
	public PublicKey parseJWK(String jwkJson, SubNode accntNode) {
		PublicKey pubKey = null;
		try {
			// log.debug("parsing: " + pubKeyJson);
			Jwk keyObj = mapper.readValue(jwkJson.getBytes(StandardCharsets.UTF_8), Jwk.class);
			if (keyObj == null) {
				log.error("Unable to parse USER_PREF_PUBLIC_SIG_KEY from accnt " + accntNode.getIdStr());
				return null;
			}

			BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(keyObj.getN()));
			BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(keyObj.getE()));

			pubKey = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
			if (keyObj == null) {
				log.error("Unable generate USER_PREF_PUBLIC_SIG_KEY for accnt " + accntNode.getIdStr());
				return null;
			}
		} catch (Exception e) {
			ExUtil.error(log, "parseJWK failed", e);
		}
		return pubKey;
	}

	public boolean sigVerify(PublicKey pubKey, byte[] sigBytes, byte[] dataBytes) {
		try {
			Signature verifier = Signature.getInstance("SHA256withRSA");
			verifier.initVerify(pubKey);
			verifier.update(dataBytes);
			return verifier.verify(sigBytes);
		} catch (Exception e) {
			ExUtil.error(log, "exception in signature", e);
			throw new RuntimeException(e);
		}
	}

	public void signNodes(MongoSession ms, SignNodesRequest req, SignNodesResponse res) {
		/*
		 * if the signPendingQueue contains the workload we assume it's the same workload, which is fine
		 * because we aren't doing that security right here.
		 */
		if (sigPendingQueue.containsKey(req.getWorkloadId())) {
			BulkOperations bops = null;
			int batchSize = 0;

			for (NodeSigData data : req.getListToSign()) {
				ObjectId id = new ObjectId(data.getNodeId());

				/*
				 * todo-1: we could optimize this and be faster by using an 'in clause' to lookup all the nodes in a
				 * single query instead of doing this 'getNode' on each one.
				 */
				SubNode node = read.getNode(ms, id);

				// if we found the node and this setter DID change it's value, then we save.
				if (node != null && node.set(NodeProp.CRYPTO_SIG, data.getData())) {
					// clean so we won't let this node get persisted, because we're doing the persist in this bulk op
					ThreadLocals.clean(node);

					bops = update.bulkOpSetPropVal(bops, id, SubNode.PROPS, node.getProps());
					if (++batchSize > Const.MAX_BULK_OPS) {
						bops.execute();
						batchSize = 0;
						bops = null;
					}
				}
			}

			if (bops != null) {
				bops.execute();
			}
			sigPendingQueue.remove(req.getWorkloadId());
		} else {
			log.warn("Unknown workload id: " + req.getWorkloadId());
		}
	}

	public void signSubGraph(MongoSession ms, SessionContext sc, SignSubGraphRequest req) {
		SubNode parent = read.getNode(ms, req.getNodeId());
		if (parent == null) {
			return;
		}

		// query all nodes under the path that are owned by 'ms'
		Criteria criteria = Criteria.where(SubNode.PATH).regex(mongoUtil.regexRecursiveChildrenOfPath(parent.getPath())) //
				.and(SubNode.OWNER).is(ms.getUserNodeId());

		Val<NodeSigPushInfo> pushInfo = new Val<>();
		Query query = new Query();
		query.addCriteria(criteria);
		IntVal count = new IntVal();

		// add in root node first
		pushInfo.setVal(new NodeSigPushInfo(Math.abs(rand.nextInt())));
		pushInfo.getVal().setListToSign(new LinkedList<>());
		pushInfo.getVal().getListToSign().add(new NodeSigData(parent.getIdStr(), getNodeSigData(parent)));
		count.inc();

		BooleanVal failed = new BooleanVal();

		ops.stream(query, SubNode.class).forEachRemaining(node -> {
			// make sure session is still alive
			if (failed.getVal() || !sc.isLive()) return;

			// create new push object lazily
			if (pushInfo.getVal() == null) {
				pushInfo.setVal(new NodeSigPushInfo(Math.abs(rand.nextInt())));
				pushInfo.getVal().setListToSign(new LinkedList<>());
			}

			// add this node.
			pushInfo.getVal().getListToSign().add(new NodeSigData(node.getIdStr(), getNodeSigData(node)));
			count.inc();

			// if we have enough to send a block send it.
			if (pushInfo.getVal().getListToSign().size() >= SIGN_BLOCK_SIZE) {
				// log.debug("BLOCK: " + XString.prettyPrint(pushInfo));
				if (!waitForBrowserSentSigs(sc, pushInfo.getVal())) {
					failed.setVal(true);
				}
				// reset the push object.
				pushInfo.setVal(null);
			}
		});

		// make sure session is still alive
		if (failed.getVal() || !sc.isLive()) return;

		// send the accumulated remainder
		if (pushInfo.getVal() != null && pushInfo.getVal().getListToSign().size() > 0) {
			// log.debug("REMAIN: " + XString.prettyPrint(pushInfo));
			if (!waitForBrowserSentSigs(sc, pushInfo.getVal())) {
				failed.setVal(true);
			}

			// make sure session is still alive
			if (failed.getVal() || !sc.isLive()) return;
		}

		push.sendServerPushInfo(sc, new PushPageMessage(
				"SubGraph signatures complete. " + String.valueOf(count.getVal()) + " nodes were signed.", true));
	}

	// This method pushes data down to the browser to be signed and waits for the reply here.
	private boolean waitForBrowserSentSigs(SessionContext sc, NodeSigPushInfo pushInfo) {
		sigPendingQueue.put(pushInfo.getWorkloadId(), pushInfo);
		push.sendServerPushInfo(sc, pushInfo);

		Util.sleep(10);
		long totalTime = 0;
		long sleepTime = 100;

		// we wait for up to 30 seconds for the browser to sign the nodes, before we will give up and 
		// return false;
		while (totalTime < 30000 && sc.isLive() && sigPendingQueue.contains(pushInfo.getWorkloadId())) {
			Util.sleep(sleepTime);
			totalTime += sleepTime;
		}

		return totalTime < 30000;
	}
}
