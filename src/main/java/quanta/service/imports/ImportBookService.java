package quanta.service.imports;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.request.InsertBookRequest;
import quanta.response.InsertBookResponse;
import quanta.util.ImportWarAndPeace;
import quanta.util.ThreadLocals;
import quanta.util.XString;

/**
 * Special-purpose code for importing the book War and Peace which ships with SubNode, and is used
 * for demonstration purposes to show how browsing, searching, etc. works, and for testing with a
 * reasonable sized chunk of data (i.e. the entire book)
 */
@Component
@Slf4j 
public class ImportBookService extends ServiceBase {
	public InsertBookResponse insertBook(MongoSession ms, InsertBookRequest req) {
		InsertBookResponse res = new InsertBookResponse();
		ms = ThreadLocals.ensure(ms);
		ThreadLocals.requireAdmin();

		String nodeId = req.getNodeId();
		SubNode node = read.getNode(ms, nodeId);
		auth.ownerAuth(node);
		log.debug("Insert Root: " + XString.prettyPrint(node));

		/*
		 * for now we don't check book name. Only one book exists: War and Peace
		 */
		ImportWarAndPeace iwap = context.getBean(ImportWarAndPeace.class);
		iwap.importBook(ms, "classpath:public/data/war-and-peace.txt", node,
				safeBooleanVal(req.getTruncated()) ? 2 : Integer.MAX_VALUE);

		update.saveSession(ms);
		res.setSuccess(true);
		return res;
	}

	public static boolean safeBooleanVal(Boolean val) {
		return val != null && val.booleanValue();
	}
}
