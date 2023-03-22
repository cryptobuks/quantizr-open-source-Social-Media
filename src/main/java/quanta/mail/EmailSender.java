package quanta.mail;

import java.util.Date;
import java.util.Properties;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.event.TransportEvent;
import javax.mail.event.TransportListener;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.sun.mail.smtp.SMTPTransport;
import lombok.extern.slf4j.Slf4j;
import quanta.config.AppProp;
import quanta.config.ServiceBase;
import quanta.util.ExUtil;
import quanta.util.LimitedInputStream;

/**
 * Component that sends emails
 */
@Component
@Slf4j 
public class EmailSender extends ServiceBase implements TransportListener {
	@Autowired
	private AppProp appProp;

	public static final Object lock = new Object();

	public static final String MIME_HTML = "text/html";
	public int TIMEOUT = 10000; // ten seconds
	public int TIMESLICE = 250; // quarter second

	public boolean debug = true;
	private Properties props;
	private Session mailSession;
	private SMTPTransport transport;

	/*
	 * This method can and should be called before sending mails, close() method should be called after
	 * mail is sent
	 */
	public void init() {
		if (!mailEnabled())
			return;
		log.trace("MailSender.init()");

		String mailHost = appProp.getMailHost();
		String mailUser = appProp.getMailUser();
		String mailPassword = appProp.getMailPassword();
		// log.debug("mailPassword=" + mailPassword);

		if (mailSession == null) {
			props = new Properties();
			props.put("mail.smtps.host", mailHost);
			props.put("mail.smtps.auth", "true");

			mailSession = Session.getInstance(props, null);
			if (mailSession != null) {
				log.trace("Created mailSession");
			}
			mailSession.setDebug(debug);
		}

		try {
			transport = (SMTPTransport) mailSession.getTransport("smtps");
			if (transport != null) {
				log.trace("Created mail transport.");
			}

			transport.addTransportListener(this);

			log.trace("Connecting to mailHost " + mailHost);
			transport.connect(mailHost, mailUser, mailPassword);
			log.trace("connected ok");

		} catch (Exception e) {
			throw ExUtil.wrapEx(e);
		}
	}

	public Session getMailSession() {
		return mailSession;
	}

	public boolean mailEnabled() {
		return !StringUtils.isEmpty(appProp.getMailPassword());
	}

	public static Object getLock() {
		return lock;
	}

	public void close() {
		if (!mailEnabled())
			return;
		if (transport != null) {
			try {
				log.trace("closing transport");
				transport.close();
			} catch (Exception e) {
				throw ExUtil.wrapEx(e);
			} finally {
				transport = null;
			}
		}
	}

	public void sendMail(String sendToAddress, String fromAddress, String content, String subjectLine) {
		if (!mailEnabled())
			return;

		if (fromAddress == null) {
			fromAddress = appProp.getMailFrom();
		}

		if (transport == null) {
			throw ExUtil.wrapEx("Tried to use MailSender after close() call or without initializing.");
		}

		MimeMessage message = new MimeMessage(mailSession);
		try {
			message.setSentDate(new Date());
			message.setSubject(subjectLine);
			message.setFrom(new InternetAddress(fromAddress));
			message.setRecipient(Message.RecipientType.TO, new InternetAddress(sendToAddress));

			// MULTIPART
			// ---------------
			// MimeMultipart multipart = new MimeMultipart("part");
			// BodyPart messageBodyPart = new MimeBodyPart();
			// messageBodyPart.setContent(content, "text/html");
			// multipart.addBodyPart(messageBodyPart);
			// message.setContent(multipart);

			// SIMPLE (no multipart)
			// ---------------
			message.setContent(content, MIME_HTML);

			// can get alreadyconnected exception here ??
			// transport.connect(mailHost, mailUser, mailPassword);

			/*
			 * important: while inside this 'sendMessage' method, the 'messageDelivered' callback will get
			 * called if the send is successful, so we can return the value below, even though we do not set it
			 * in this method
			 */

			transport.sendMessage(message, message.getRecipients(Message.RecipientType.TO));

			// I'm not sure if the callbacks are on this same thread or not. Commenting out
			// pending research into this.
			// log.debug("Response: " + transport.getLastServerResponse() + " Code: " +
			// transport.getLastReturnCode());
		} catch (Exception e) {
			throw ExUtil.wrapEx(e);
		}
	}

	@Override
	public void messageDelivered(TransportEvent arg) {
		log.trace("messageDelivered.");
	}

	@Override
	public void messageNotDelivered(TransportEvent arg) {
		log.trace("messageNotDelivered.");
	}

	@Override
	public void messagePartiallyDelivered(TransportEvent arg) {
		log.trace("messagePartiallyDelivered.");
	}

	// Converts a stream of EML file text to Markdown
	public String convertEmailToMarkdown(LimitedInputStream is) {
		StringBuilder cont = new StringBuilder();
		try {
			MimeMessage message = new MimeMessage(null /* mail.getMailSession() */, is);

			// todo-1: would be better to have a 'type' for emails.
			cont.append("#### " + message.getSubject());
			cont.append("\n");
			cont.append("From: " + message.getFrom()[0]);
			cont.append("\n\n");
			Object obj = message.getContent();
			if (obj instanceof MimeMultipart) {
				MimeMultipart mm = (MimeMultipart) obj;
				for (int i = 0; i < mm.getCount(); i++) {
					BodyPart part = mm.getBodyPart(i);
					if (part.getContentType().startsWith("text/plain;")) {
						cont.append(part.getContent().toString());
						cont.append("\n\n");
					}
				}
			}
		} catch (Exception e) {
			log.error("Failed to upload", e);
		} 
		return cont.toString();
	}
}
