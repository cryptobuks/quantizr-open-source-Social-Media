package quanta.test;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;

@Component("UtilsTest")
@Slf4j
public class UtilsTest extends ServiceBase implements TestIntf {

	@Override
	public void test() throws Exception {
	
	}
}
