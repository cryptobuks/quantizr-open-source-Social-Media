
package quanta.test;

import org.springframework.stereotype.Component;
import quanta.config.ServiceBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("UtilsTest")
public class UtilsTest extends ServiceBase implements TestIntf {
	
	private static Logger log = LoggerFactory.getLogger(UtilsTest.class);

	@Override
	public void test() throws Exception {
	}
}
