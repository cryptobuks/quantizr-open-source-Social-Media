
package quanta.filter;

import java.io.IOException;
import java.util.HashMap;
import javax.annotation.PostConstruct;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;
import quanta.config.AppProp;
import quanta.model.IPInfo;
import quanta.util.Const;
import quanta.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ServletFilter for throttling access
 */
@Component
@Order(3)
public class ThrottleFilter extends GenericFilterBean {
	
	private static Logger log = LoggerFactory.getLogger(ThrottleFilter.class);
	@Autowired
	private AppProp appProp;
	private static boolean THROTTLE_ENABLED = false;
	private static int THROTTLE_INTERVAL = 500;
	/*
	 * For debugging we can turn this flag on and disable the server from processing multiple requests
	 * simultenaously this is every helpful for debugging.
	 */
	private static boolean singleThreadDebugging = false;
	private static final HashMap<String, IPInfo> ipInfo = new HashMap<>();

	@PostConstruct
	public void postConstruct() {
		THROTTLE_INTERVAL = appProp.getThrottleTime();
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		if (Const.debugRequests) {
			log.debug("ThrottleFilter.doFilter()");
		}
		if (!Util.gracefulReadyCheck(response)) return;
		HttpServletRequest sreq = null;
		IPInfo info = null;
		if (request instanceof HttpServletRequest) {
			sreq = (HttpServletRequest) request;
			HttpSession session = sreq.getSession(false);
			if (session != null) {
				String ip = session.getId();
				synchronized (ipInfo) {
					info = ipInfo.get(ip);
					if (info == null) {
						ipInfo.put(ip, info = new IPInfo());
					}
				}
				throttleRequest(sreq, info);
			}
		}
		/*
		 * singleThreadDebugging creates one lock per IP so that each machine calling our server gets single
		 * threaded, but other servers can call in parallel
		 */
		if (singleThreadDebugging && info != null) {
			synchronized (info.getLock()) {
				chain.doFilter(request, response);
			}
		} else {
			chain.doFilter(request, response);
		}
	}

	private void throttleRequest(HttpServletRequest httpReq, IPInfo info) {
		if (httpReq == null) return;
		long curTime = System.currentTimeMillis();
		if (info.getLastRequestTime() == 0) {
			info.setLastRequestTime(curTime);
			return;
		}
		// log.debug("check:" + httpReq.getRequestURI());
		if ( //
		//
		//
		//
		//
		//
		//
		httpReq.getRequestURI().endsWith("/checkMessages") || httpReq.getRequestURI().endsWith("/getUserProfile") || httpReq.getRequestURI().endsWith("/getConfig") || httpReq.getRequestURI().endsWith("/getBookmarks") || httpReq.getRequestURI().endsWith("/login") || httpReq.getRequestURI().endsWith("/proxyGet") || httpReq.getRequestURI().endsWith("/serverPush") || httpReq.getRequestURI().endsWith("/anonPageLoad") ||  //
		httpReq.getRequestURI().endsWith("/getOpenGraph")) {
		} else {
			if (THROTTLE_ENABLED) {
				long wait = THROTTLE_INTERVAL - (curTime - info.getLastRequestTime());
				if (wait > 0) {
					log.debug("throt: " + httpReq.getRequestURI() + " " + String.valueOf(wait));
					try {
						Thread.sleep(wait);
					} catch (Exception e) {
					}
				}
			}
		}
		info.setLastRequestTime(System.currentTimeMillis());
	}

	public void destroy() {
	}
}
