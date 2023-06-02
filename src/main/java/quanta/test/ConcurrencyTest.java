
package quanta.test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConcurrencyTest {
    
    private static Logger log = LoggerFactory.getLogger(ConcurrencyTest.class);

    public void test() {
        log.debug("Concurrency tester for LockEx is currently commented out. Uncomment the next two lines to enable it.");
        //DeadlockDetectorTest ddt = new DeadlockDetectorTest();
        //ddt.run();
    }
}
