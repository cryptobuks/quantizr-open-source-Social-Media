package quanta.test;

import java.util.Date;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.util.DateUtil;
import quanta.util.ExUtil;

@Component("UtilsTest")
@Slf4j 
public class UtilsTest extends ServiceBase implements TestIntf {
	@Override
	public void test() throws Exception {
		asyncExecTest();
	}

	private void asyncExecTest() {
		for (int i = 0; i < 100; i++) {
			final int _i = i;
			exec.run(() -> {
				log.debug("Running asyncExecTest");
				try {
					log.debug("Sleep " + _i);
					Thread.sleep(_i * 1000);
				} catch (Exception e) {
					ExUtil.error(log, "exception AsyncExec", e);
				}
			});
		}
	}

	public void timesTest() throws Exception {
		log.debug("*****************************************************************************************");
		log.debug("UtilsTest Running!");

		Date d1 = DateUtil.parseISOTime("2011-12-03T10:15:30Z");
		log.debug("parsed d1: " + d1);
		Date d2 = DateUtil.parseISOTime("2021-05-25T15:43:28+00:00");
		log.debug("parsed d2: " + d2);
	}
}
