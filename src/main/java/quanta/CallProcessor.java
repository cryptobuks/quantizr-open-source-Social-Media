package quanta;

import java.util.Date;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;
import quanta.config.ServiceBase;
import quanta.config.SessionContext;
import quanta.exception.NotLoggedInException;
import quanta.exception.OutOfSpaceException;
import quanta.instrument.Instrument;
import quanta.instrument.PerfMonEvent;
import quanta.model.client.ErrorType;
import quanta.mongo.MongoSession;
import quanta.request.LogoutRequest;
import quanta.request.base.RequestBase;
import quanta.response.base.ResponseBase;
import quanta.util.ExUtil;
import quanta.util.LockEx;
import quanta.util.MongoRunnableEx;
import quanta.util.ThreadLocals;
import quanta.util.XString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component
public class CallProcessor extends ServiceBase {
	
	private static Logger log = LoggerFactory.getLogger(CallProcessor.class);

	/*
	 * Wraps the processing of any command by using whatever info is on the session and/or the request
	 * to perform the login if the user is not logged in, and then call the function to be processed
	 */
	public Object run(String command, boolean authBearer, boolean authSig, RequestBase req, HttpSession httpSession, MongoRunnableEx<Object> runner) {
		if (AppServer.isShuttingDown()) {
			throw ExUtil.wrapEx("Server not available.");
		}
		SessionContext sc = ThreadLocals.getSC();
		if (sc == null || !SessionContext.sessionExists(sc)) {
			throw new RuntimeException("Unable to get SessionContext to check token.");
		}
		if (authBearer) {
			SessionContext.authBearer();
		}
		/*
		 * #sig: this works fine, but I'm disabling for now (except for admin) until there's a better way to
		 * inform the user that this can happen when their key on their browser is different than expected,
		 * which CAN even happen simply from using a different browser that hasn't had the signature key
		 * imported into it. And also all the flow around how this can be encountered during login/logout
		 * needs to be tested and more well thought out.
		 */
		if (authSig && sc.isAdmin()) {
			SessionContext.authSig();
		}
		logRequest(command, req, httpSession);
		/*
		 * Instantiating this, runs its constructor and ensures our threadlocal at least has response object
		 * on it, but most (not all) implementations of methods end up instantiating their own which
		 * overwrites this
		 */
		new ResponseBase();
		boolean useLock = true;
		/*
		 * #push-locks: do this cleaner. There should be a way to accomplish this without disabling the
		 * mutex here.
		 */
		switch (command) {
		case "serverPush": 
		case "signNodes": 
			useLock = false;
		default: 
			break;
		}
		Object ret = null;
		LockEx mutex = (LockEx) WebUtils.getSessionMutex(ThreadLocals.getHttpSession());
		if (mutex == null) {
			log.error("Session mutex lock is null.");
		}
		long startTime = 0;
		String userName = null;
		try {
			if (useLock && mutex != null) {
				mutex.lockEx();
			}
			if (req instanceof LogoutRequest) {
				// Note: all this run will be doing in this case is a session invalidate.
				ret = runner.run(null);
			} else {
				// mutexCounter++;
				// log.debug("Enter: mutexCounter: "+String.valueOf(mutexCounter));
				Date now = new Date();
				sc.setLastActiveTime(startTime = now.getTime());
				userName = sc.getUserName();
				MongoSession ms = ThreadLocals.getMongoSession();
				ret = runner.run(ms);
				update.saveSession(ms);
			}
		} catch (NotLoggedInException e1) {
			HttpServletResponse res = ThreadLocals.getServletResponse();
			try {
				if (res != null) {
					ExUtil.warn("Unauthorized. Not logged in.");
					res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
				}
			} catch (Exception e) {
				ExUtil.error(log, "exception in call processor", e);
			}
		} catch (Exception e) {
			ExUtil.error(log, "exception in call processor", e);
			ret = ThreadLocals.getResponse();
			if (ret instanceof ResponseBase) {
				ResponseBase orb = (ResponseBase) ret;
				orb.setSuccess(false);
				setErrorType(orb, e);
				/* only set a message if one is not already set */
				if (StringUtils.isEmpty(orb.getMessage())) {
					/*
					 * for now, we can just send back the actual exception message
					 */
					if (e.getMessage() != null) {
						orb.setMessage("Failed: " + e.getMessage());
					} else {
						orb.setMessage("Failed.");
					}
				}
				orb.setStackTrace(ExceptionUtils.getStackTrace(e));
			}
		} finally {
			int duration = (int) (System.currentTimeMillis() - startTime);
			if (duration > Instrument.CAPTURE_THRESHOLD) {
				new PerfMonEvent(duration, "callProc." + command, userName);
			}
			if (useLock && mutex != null) {
				mutex.unlockEx();
			}
			// mutexCounter--;
			// log.debug("Exit: mutexCounter: "+String.valueOf(mutexCounter));
			nostr.pushNostrInfoToClient();
		}
		logResponse(ret);
		return ret;
	}

	private void setErrorType(ResponseBase res, Exception ex) {
		if (ex instanceof OutOfSpaceException) {
			res.setErrorType(ErrorType.OUT_OF_SPACE.s());
		}
	}

	private static void logRequest(String url, Object req, HttpSession httpSession) {
		log.trace("REQ=" + url + " " + (req == null ? "none" : XString.prettyPrint(req)));
	}

	private static void logResponse(Object res) {
		log.trace("RES=" + (res == null ? "none" : XString.prettyPrint(res)));
	}
}
