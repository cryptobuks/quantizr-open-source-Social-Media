package quanta.test;

import com.mongodb.client.result.DeleteResult;
import java.util.Calendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import quanta.config.ServiceBase;
import quanta.mongo.model.FediverseName;

@Component("MongoFediverseNamesTest")
public class MongoFediverseNamesTest extends ServiceBase implements TestIntf {

    private static Logger log = LoggerFactory.getLogger(MongoFediverseNamesTest.class);

    @Override
    public void test() throws Exception {
        log.debug("*****************************************************************************************");
        log.debug("MongoFediverseNamesTest Running!");
        removeAll();
        ops.indexOps(FediverseName.class).ensureIndex(new Index().on(FediverseName.NAME, Direction.ASC).unique());
        String name = "jameson2@server.com";
        FediverseName fName = new FediverseName();
        fName.setName(name);
        fName.setCreateTime(Calendar.getInstance().getTime());
        ops.save(fName);
        log.debug("Saved: " + name);
        try {
            fName = new FediverseName();
            fName.setName(name);
            update.saveObj(fName);
            throw new RuntimeException("Allowed non duplicate name: " + name);
        } catch (Exception e) {
            log.debug("Successfully rejected duplicate with: " + e.getClass().getName());
        }
        dump();
        log.debug("MongoFediverseNamesTest Ok.");
        log.debug("*****************************************************************************************");
    }

    private void dump() {
        log.debug("Dumping all FediverseNames...");
        Iterable<FediverseName> recs = ops.findAll(FediverseName.class);

        for (FediverseName fName : recs) {
            log.debug(fName.getName());
        }
    }

    private void removeAll() {
        Query q = new Query();
        q.addCriteria(Criteria.where(FediverseName.NAME).ne(null));
        DeleteResult res = ops.remove(q, FediverseName.class);
        log.debug("Objects deleted: " + res.getDeletedCount());
    }
}
