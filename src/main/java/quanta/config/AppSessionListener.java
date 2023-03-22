package quanta.config;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;
import lombok.extern.slf4j.Slf4j;
import quanta.util.LockEx;

/**
 * For keeping track of sessions.
 */
@Component
@Slf4j 
public class AppSessionListener implements HttpSessionListener {
	@Autowired private AppProp appProp;
	private static int sessionCounter = 0;

	@Override
	public void sessionCreated(HttpSessionEvent se) {
		HttpSession session = se.getSession();

		// log.debug("sessionCreated: sessionId=" + se.getSession().getId());

		// multiply by 60 to convert minutes to seconds.
		session.setMaxInactiveInterval(appProp.getSessionTimeoutMinutes() * 60);

		/*
		 * I'm not sure if certain parts of 'Spring API' are gonna see this LockEx and just synchronize on
		 * it using synchronize keyword and treating it just as a plain Object would be used for a lock, but
		 * for our own API use of this lock we call lockEx() and unlockEx() on this object to use its' built
		 * in ability to detect and forcably break deadlocks when they happen!
		 */
		session.setAttribute(WebUtils.SESSION_MUTEX_ATTRIBUTE, new LockEx("SESSION-LockEx:" + session.getId(), true, 180000, 1));
		sessionCounter++;
	}

	@Override
	public void sessionDestroyed(HttpSessionEvent se) {
		HttpSession session = se.getSession();
		SessionContext sc = (SessionContext) session.getAttribute(SessionContext.QSC);
		if (sc != null) {
			sc.sessionTimeout();
			session.removeAttribute(SessionContext.QSC);

			// this should trigger the removal of SessionContext.allSessions entry too, however we also
			// try to remove it manually just to be sure, instead of trusting server HTTP layer
			SessionContext.removeSession(sc);
		}
		session.removeAttribute(WebUtils.SESSION_MUTEX_ATTRIBUTE);
		sessionCounter--;

		// log.debug("sessionDestroyed: sessionId=" + se.getSession().getId() + " sessionCount=" + SessionContext.getSessionCount());
	}

	public static int getSessionCounter() {
		return sessionCounter;
	}
}
