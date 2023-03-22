package quanta.util;

import org.springframework.boot.SpringApplication;
import lombok.extern.slf4j.Slf4j;

/**
 * DO NOT DELETE
 * 
 * For future reference only, I'm keeping this is as an example of how to call a SpringBoot app as a
 * command line app.
 */
// @SpringBootApplication
// @EnableScheduling
@Slf4j 
public class BackupUtil {
	public static void main(String[] args) {
		SpringApplication.run(BackupUtil.class, args);
		log.debug("App Started, and will shutdown now.");

		try {
			// command line app can run here.
		}
		catch (Exception e) {
			log.error("Backup failed.", e);
		}
		System.exit(0);
	}
}
