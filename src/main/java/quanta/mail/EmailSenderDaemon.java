package quanta.mail;

import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import quanta.AppServer;
import quanta.config.ServiceBase;
import quanta.model.client.NodeProp;
import quanta.mongo.MongoRepository;
import quanta.mongo.MongoSession;
import quanta.mongo.model.SubNode;

/**
 * Deamon for sending emails.
 */
@Component
@Slf4j 
public class EmailSenderDaemon extends ServiceBase {
	private int runCounter = 0;
	public static final int INTERVAL_SECONDS = 10;
	private int runCountdown = INTERVAL_SECONDS;
	static boolean run = false;

	/*
	 * Note: Spring does correctly protect against concurrent runs. It will always wait until the last
	 * run of this function is completed before running again. So we can always assume only one
	 * thread/deamon of this class is running at at time, because this is a singleton class.
	 * 
	 * see also: @EnableScheduling (in this project)
	 * 
	 * @Scheduled value is in milliseconds.
	 * 
	 * Runs immediately at startup, and then every 10 seconds
	 */
	@Scheduled(fixedDelay = 10000)
	public void run() {
		if (run || !MongoRepository.fullInit)
			return;
		try {
			run = true;
			if (AppServer.isShuttingDown() || !AppServer.isEnableScheduling()) {
				log.debug("ignoring NotificationDeamon schedule cycle");
				return;
			}

			runCounter++;

			/* fail fast if no mail host is configured. */
			if (StringUtils.isEmpty(prop.getMailHost())) {
				if (runCounter < 3) {
					log.debug("NotificationDaemon is disabled, because no mail server is configured.");
				}
				return;
			}

			if (--runCountdown <= 0) {
				runCountdown = INTERVAL_SECONDS;

				arun.run((MongoSession ms) -> {
					Iterable<SubNode> mailNodes = outbox.getMailNodes(ms);
					if (mailNodes != null) {
						sendAllMail(ms, mailNodes);
					}
					return null;
				});
			}
		} catch (Exception e) {
			log.error("notification deamo cycle fail", e);
		} finally {
			run = false;
		}
	}

	/* Triggers the next cycle to not wait, but process immediately */
	public void setOutboxDirty() {
		runCountdown = 0;
	}

	private void sendAllMail(MongoSession ms, Iterable<SubNode> nodes) {
		synchronized (EmailSender.getLock()) {
			try {
				mail.init();
				for (SubNode node : nodes) {
					log.debug("Iterating node to email. nodeId:" + node.getIdStr());

					String email = node.getStr(NodeProp.EMAIL_RECIP);
					String subject = node.getStr(NodeProp.EMAIL_SUBJECT);
					String content = node.getStr(NodeProp.EMAIL_CONTENT);

					if (!StringUtils.isEmpty(email) && !StringUtils.isEmpty(subject) && !StringUtils.isEmpty(content)) {

						log.debug("Found mail to send to: " + email);
						if (delete.delete(ms, node, false) > 0) {
							// only send mail if we were able to delete the node, because other wise something is wrong
							// without ability to delete and so we'd go into a loop sending this item multiple times.
							mail.sendMail(email, null, content, subject);
						} else {
							log.debug("Unable to delete queued mail node: " + node.getIdStr());
						}
					} else {
						log.debug("not sending email. Missing some properties. email or subject or content");
					}
				}
			} finally {
				mail.close();
			}
		}
	}
}
