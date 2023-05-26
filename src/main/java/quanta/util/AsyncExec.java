package quanta.util;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.config.ServiceBase;

/*
 * Wraps execution of a Runnable by the spring executor service. Warning: Don't try to refactor to
 * use
 * 
 * @Async annotation. That approach is dangerous and won't work in all scenarios
 */
@Component
@Slf4j
public class AsyncExec extends ServiceBase {
    @Autowired
    @Qualifier("threadPoolTaskExecutor")
    public Executor executor;

    // Reflects the true concurrently count, and should represent the current number of running threads
    // at all times.
    int execCounter = 0;
    int maxExecCounter = 0; // max value for execCounter ever

    public void run(Runnable runnable) {
        run(new ThreadLocalsContext(), runnable);
    }

    /*
     * todo-1: when something throws an exception in this method we can't tell WHERE it truly came from
     * so we need to pass a callstack thread into this or a name or something that lets us know what
     * operation it's running.
     */
    private void run(ThreadLocalsContext tlc, Runnable runnable) {
        executor.execute(new Runnable() {
            public void run() {
                try {
                    execCounter++;
                    if (execCounter > maxExecCounter) {
                        maxExecCounter = execCounter;
                    }
                    if (tlc != null) {
                        tlc.setValsIntoThread();
                    }
                    runnable.run();
                } catch (Exception e) {
                    ExUtil.error(log, "exception in AsyncExec", e);
                } finally {
                    ThreadLocals.removeAll();
                    execCounter--;
                    // log.debug("Finished thread: " + Thread.currentThread().getName() + " execCounter="
                    // + String.valueOf(execCounter) + " maxConcurrency=" + String.valueOf(maxExecCounter));
                }
            }
        });
    }
}
