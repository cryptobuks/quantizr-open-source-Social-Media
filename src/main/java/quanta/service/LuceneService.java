package quanta.service;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;
import quanta.response.LuceneIndexResponse;
import quanta.response.LuceneSearchResponse;

/**
 * Service for processing Lucene-related functions.
 */
@Component
@Slf4j 
public class LuceneService extends ServiceBase  {
	public LuceneIndexResponse reindex(MongoSession ms, String nodeId, String searchFolder) {
		LuceneIndexResponse res = new LuceneIndexResponse();
		String ret = null;
		SubNode node = read.getNode(ms, nodeId, true, null);
		if (node != null) {
			/*
			 * Remember 'searchFolder' will have to be visible to the VM and therefore this might require adding
			 * a new mapping parameter to the startup shell script for docker. Docker can't see the entire
			 * folder structure on the host machine, but can only see what has specifically been shared to it.
			 * 
			 * NOTE: We're using the nodeId as the subdirectory in the lucene data folder to keep the index of
			 * this node garanteed to be separate but determined by this node (i.e. unique to this node)
			 */
			fileIndexer.index(searchFolder /* "/tmp/search" */, nodeId, "sh,md,txt,pdf,zip,tar,json,gz,tgz,xz", true);
			ret = fileIndexer.getSummaryReport();
			fileIndexer.close();
		}

		res.setSuccess(true);
		res.setMessage(ret);
		return res;
	}

	public LuceneSearchResponse search(MongoSession ms, String nodeId, String searchText) {
		LuceneSearchResponse res = new LuceneSearchResponse();
		String ret = null;
		// disabled for now.
		// SubNode node = read.getNode(session, nodeId, true);
		// if (ok(node )) {
		// ret = searcher.search(nodeId, searchText);
		// }

		res.setSuccess(true);
		res.setMessage(ret);
		return res;
	}
}
