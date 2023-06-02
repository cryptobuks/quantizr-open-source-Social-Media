
package quanta.test;

import org.springframework.stereotype.Component;
import quanta.config.ServiceBase;

@Component("UtilsTest")
public class UtilsTest extends ServiceBase implements TestIntf {
	
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UtilsTest.class);

	@Override
	public void test() throws Exception {
	}
}
