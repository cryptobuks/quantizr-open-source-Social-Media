package quanta.test;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;
import quanta.util.XString;

@Component("LangTest")
@Slf4j
public class LangTest extends ServiceBase implements TestIntf {
	@Override
	public void test() throws Exception {
		// both true
		log.debug("Contains Asian: " + XString.containsChinese("xxx已下架xxx"));
		log.debug("Contains Russian: " + XString.containsRussian("xxкиилxxx"));

		log.debug("Contains nonEnglish: "+XString.containsNonEnglish("なるほど，これはむずいわ．どうしようかなー"));

		// both false
		// log.debug("Contains Asian: " + XString.containsChinese("xxкиилxxx"));
		// log.debug("Contains Russian: " + XString.containsRussian("xxx已下架xxx"));
	}
}
