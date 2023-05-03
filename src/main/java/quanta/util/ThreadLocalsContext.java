package quanta.util;

import javax.servlet.http.HttpSession;
import quanta.config.SessionContext;

/**
 * Holder for transferring ThreadLocals values from one thread to another.
 */
public class ThreadLocalsContext {
	public long threadId;
	public HttpSession httpSession;
	public SessionContext sessionContext;
}
