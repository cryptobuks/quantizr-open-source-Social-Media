
package quanta.test;

import org.springframework.stereotype.Component;
import quanta.config.ServiceBase;
import quanta.util.XString;

@Component("LangTest")
public class LangTest extends ServiceBase implements TestIntf {
	
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LangTest.class);

	@Override
	public void test() throws Exception {
		// both true
		log.debug("Contains Asian: " + XString.containsChinese("xxx已下架xxx"));
		log.debug("Contains Russian: " + XString.containsRussian("xxкиилxxx"));
		log.debug("Contains nonEnglish: " + XString.containsNonEnglish("なるほど，これはむずいわ．どうしようかなー"));
		// both false
		// log.debug("Contains Asian: " + XString.containsChinese("xxкиилxxx"));
		// log.debug("Contains Russian: " + XString.containsRussian("xxx已下架xxx"));
	}
}
