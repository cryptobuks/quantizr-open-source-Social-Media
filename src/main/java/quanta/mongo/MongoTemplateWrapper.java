
package quanta.mongo;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import com.mongodb.client.result.DeleteResult;
import quanta.config.ServiceBase;
import quanta.mongo.model.SubNode;
import quanta.util.DateUtil;
import quanta.util.ThreadLocals;

/**
 * This is a level of indirection around MongoTemplate so we can do various cross-cutting concerns,
 * like logging, security, etc.
 */
@Component
public class MongoTemplateWrapper extends ServiceBase {
	// todo-1: make this able to be enabled by Admin panel button
	private static boolean logging = false;
	private static Logger log = LoggerFactory.getLogger(MongoTemplateWrapper.class);

	public DeleteResult remove(MongoSession ms, Query query, Class<?> entityClass) {
		long start = 0L;
		secure(ms, query);
		if (logging) {
			log("remove", ms, query);
			start = System.currentTimeMillis();
		}

		DeleteResult res = ops.remove(query, entityClass);
		if (logging) {
			log.debug("removed=" + res.getDeletedCount() + "time="
					+ DateUtil.formatDurationMillis(System.currentTimeMillis() - start, true));
		}
		return res;
	}

	public long count(MongoSession ms, Query query, Class<?> entityClass) {
		long start = 0L;
		secure(ms, query);
		if (logging) {
			log("count", ms, query);
			start = System.currentTimeMillis();
		}

		long count = ops.count(query, entityClass);
		if (logging) {
			log.debug("count=" + count + "t=" + DateUtil.formatDurationMillis(System.currentTimeMillis() - start, true));
		}
		return count;
	}

	public boolean exists(MongoSession ms, Query query, Class<?> entityClass) {
		long start = 0L;
		secure(ms, query);
		if (logging) {
			log("exists", ms, query);
			start = System.currentTimeMillis();
		}

		boolean exists = ops.exists(query, entityClass);
		if (logging) {
			log.debug("exists: " + exists + "t=" + DateUtil.formatDurationMillis(System.currentTimeMillis() - start, true));
		}
		return exists;
	}

	public <T> List<T> find(MongoSession ms, Query query, Class<T> entityClass) {
		long start = 0L;
		secure(ms, query);
		if (logging) {
			log("find", ms, query);
			start = System.currentTimeMillis();
		}

		List<T> res = ops.find(query, entityClass);
		long count = res != null ? res.size() : 0;
		if (logging) {
			log.debug("count: " + count + "t=" + DateUtil.formatDurationMillis(System.currentTimeMillis() - start, true));
		}
		return res;
	}

	public <T> T findOne(MongoSession ms, Query query, Class<T> entityClass) {
		long start = 0L;
		secure(ms, query);
		if (logging) {
			log("findOne", ms, query);
			start = System.currentTimeMillis();
		}

		T obj = ops.findOne(query, entityClass);
		if (logging) {
			log.debug((obj != null ? "found " : "not found ") + "t="
					+ DateUtil.formatDurationMillis(System.currentTimeMillis() - start, true));
		}
		return obj;
	}

	public DeleteResult remove(MongoSession ms, Query query) {
		long start = 0L;
		secure(ms, query);
		if (logging) {
			log("remove", ms, query);
			start = System.currentTimeMillis();
		}

		DeleteResult res = ops.remove(query, SubNode.class);
		if (logging) {
			log.debug("removed: " + res.getDeletedCount() + "t="
					+ DateUtil.formatDurationMillis(System.currentTimeMillis() - start, true));
		}
		return res;
	}

	public long count(MongoSession ms, Query query) {
		long start = 0L;
		secure(ms, query);
		if (logging) {
			log("count", ms, query);
			start = System.currentTimeMillis();
		}

		long count = ops.count(query, SubNode.class);
		if (logging) {
			log.debug("count: " + count + " t=" + DateUtil.formatDurationMillis(System.currentTimeMillis() - start, true));
		}
		return count;
	}

	public boolean exists(MongoSession ms, Query query) {
		long start = 0L;
		secure(ms, query);
		if (logging) {
			log("exists", ms, query);
			start = System.currentTimeMillis();
		}

		boolean exists = ops.exists(query, SubNode.class);
		if (logging) {
			log.debug("exists: " + exists + " t=" + DateUtil.formatDurationMillis(System.currentTimeMillis() - start, true));
		}
		return exists;
	}

	public List<SubNode> find(MongoSession ms, Query query) {
		long start = 0L;
		secure(ms, query);
		if (logging) {
			log("find", ms, query);
			start = System.currentTimeMillis();
		}

		List<SubNode> res = ops.find(query, SubNode.class);
		long count = res != null ? res.size() : 0;
		if (logging) {
			log.debug("count: " + count + " t=" + DateUtil.formatDurationMillis(System.currentTimeMillis() - start, true));
		}
		return res;
	}

	public SubNode findOne(MongoSession ms, Query query) {
		long start = 0L;
		secure(ms, query);
		if (logging) {
			log("findOne", ms, query);
			start = System.currentTimeMillis();
		}

		SubNode obj = ops.findOne(query, SubNode.class);
		if (logging) {
			log.debug((obj != null ? ("found: " + obj.getIdStr()) : "not found") + " t="
					+ DateUtil.formatDurationMillis(System.currentTimeMillis() - start, true));
		}
		return obj;
	}

	private void log(String name, MongoSession ms, Query query) {
		log.debug("MQ: cmd:" + //
				(ThreadLocals.getSC() != null && ThreadLocals.getSC().getCommand() != null ? ThreadLocals.getSC().getCommand()
						: "?")
				+ " u:" + (ms == null || ms.getUserName() == null ? "null" : ms.getUserName()) + " q:" + name + " "
				+ query.toString());
	}

	private void secure(MongoSession ms, Query query) {
		if (ms != null && !ms.isAdmin()) {
			query.addCriteria(auth.getSecurity(ms));
		}
	}
}
