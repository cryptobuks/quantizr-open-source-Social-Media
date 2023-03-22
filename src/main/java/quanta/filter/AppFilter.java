package quanta.filter;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;
import quanta.config.SessionContext;
import quanta.exception.NotLoggedInException;
import quanta.model.client.PrincipalName;
import quanta.util.Const;
import quanta.util.ExUtil;
import quanta.util.ThreadLocals;
import quanta.util.Util;
import quanta.util.XString;

/**
 * This is the filter that executes FIRST (higheds @Order is the reason) This is Web Filter for
 * processing AppController.API_PATH endpoints(path configured in AppConfiguration.java)
 */
@Component
@Order(5)
public class AppFilter extends GenericFilterBean {
	private static final Logger log = LoggerFactory.getLogger(AppFilter.class);

	@Autowired
	private ApplicationContext context;

	private static int reqId = 0;
	private static boolean logRequests = false;
	private static boolean logResponses = false;

	/*
	 * if non-zero this is used to put a millisecond delay (determined by its value) onto every request
	 * that comes thru as an API call.
	 */
	private static int simulateSlowServer = 0;

	@Override
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
		if (Const.debugRequests) {
			log.debug("AppFilter.doFilter()");
		}
		if (!Util.gracefulReadyCheck(res))
			return;

		HttpServletResponse httpRes = null;
		try {
			int thisReqId = ++reqId;

			HttpServletRequest httpReq = null;

			if (req instanceof HttpServletRequest) {
				// log.debug("***** SC: User: " + sc.getUserName());

				httpReq = (HttpServletRequest) req;
				httpRes = (HttpServletResponse) res;

				log.trace(httpReq.getRequestURI() + " -> " + httpReq.getQueryString());

				// Get SessionContext from the 'token' parameter if we can.
				SessionContext sc = null;
				String token = httpReq.getParameter("token");
				if (token != null) {
					sc = SessionContext.getSCByToken(token);
					if (sc != null) {
						ThreadLocals.setSC(sc);
					}
				} else {
					sc = ThreadLocals.getSC();
				}

				if (sc == null || !SessionContext.sessionExists(sc)) {
					if (Util.allowInsecureUrl(httpReq.getRequestURI())) {
						HttpSession session = httpReq.getSession(true);
						sc = SessionContext.init(context, session);
					} else {
						throw new NotLoggedInException();
					}
				}

				sc.addAction(httpReq.getRequestURI());
				String bearer = httpReq.getHeader("Bearer");
				ThreadLocals.setReqSig(httpReq.getHeader("Sig"));

				/*
				 * if auth token is privided and doesn't exist that's a timed out session so send user back to
				 * landing page. Should also blow away all browser memory. New browser page load.
				 */
				if (!StringUtils.isEmpty(bearer) && !SessionContext.validToken(bearer, null)) {
					// just ignore an invalid token like it was not there.
					log.trace("Ignoring bad bearer token: " + bearer);
					bearer = null;
					sc.setUserName(PrincipalName.ANON.s());
				}

				// if no bearer is given, and no userName is set, then set to ANON
				if (bearer == null && sc.getUserName() == null) {
					sc.setUserName(PrincipalName.ANON.s());
				}

				ThreadLocals.setReqBearerToken(bearer);

				if (simulateSlowServer > 0) {
					Util.sleep(simulateSlowServer);
				}

				if (logRequests) {
					String url = "REQ[" + String.valueOf(thisReqId) + "]: URI=" + httpReq.getRequestURI() + "  QueryString="
							+ httpReq.getQueryString();
					log.debug(url + "\nParameters: " + XString.prettyPrint(httpReq.getParameterMap()));
				}
			} else {
				// log.debug("******* req class: "+req.getClass().getName());
			}

			if (res instanceof HttpServletResponse) {
				ThreadLocals.setServletResponse((HttpServletResponse) res);
			}

			try {
				chain.doFilter(req, res);
				if (logResponses) {
					log.debug("    RES: [" + String.valueOf(thisReqId) + "]" /* +httpRes.getStatus() */
							+ HttpStatus.valueOf(httpRes.getStatus()));
				}
			} catch (RuntimeException ex) {
				log.error("Failed", ex);
				throw ex;
			}
		} catch (NotLoggedInException e) {
			ExUtil.warn("Unauthorized. Not logged in.");
			httpRes.sendError(HttpServletResponse.SC_UNAUTHORIZED);
		} catch (Exception e) {
			/*
			 * we send back the error here, for AJAX calls so we don't bubble up to the default handling and
			 * cause it to send back the html error page.
			 */
			httpRes.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}
}
