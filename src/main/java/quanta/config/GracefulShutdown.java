package quanta.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/* see: https://dzone.com/articles/graceful-shutdown-spring-boot-applications */

/**
 * Handles Tomcat shutdown
 * 
 * NOTE: This is a spring bean instantiated with @Bean elsewhere.
 */
@Component
@Slf4j 
public class GracefulShutdown implements TomcatConnectorCustomizer, ApplicationListener<ContextClosedEvent> {
    private volatile Connector connector;

    @Autowired
    private ApplicationContext appContext;

    @Override
    public void customize(Connector connector) {
        this.connector = connector;
    }

    /*
     * Invoke with `0` to indicate no error or different code to indicate abnormal exit. es:
     * shutdownManager.initiateShutdown(0);
     **/
    public void initiateShutdown(int returnCode) {
        SpringApplication.exit(appContext, () -> returnCode);
    }

    // WARNING: Using @ApplicationEvent here doesn't work for some reason, so don't try that.
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.debug("GracefulShudown: ContextClosedEvent");
        this.connector.pause();
        Executor executor = this.connector.getProtocolHandler().getExecutor();
        if (executor instanceof ThreadPoolExecutor) {
            try {
                ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;

                log.debug("GracefulShutdown closing executor with hashCode=" + executor.hashCode() + " class="
                        + executor.getClass().getName());
                threadPoolExecutor.shutdown();
                if (!threadPoolExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn(
                            "Tomcat thread pool did not shut down gracefully within 30 seconds. Proceeding with forceful shutdown");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        } else {
            if (executor != null) {
                log.debug("Unexpected executor: " + executor.getClass().getName());
            }
        }

        AppConfiguration.shutdown();
        log.debug("GracefulShudown: complete");
    }
}
