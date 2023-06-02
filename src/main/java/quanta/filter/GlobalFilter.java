
package quanta.filter;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;
import quanta.actpub.APConst;
import quanta.config.SessionContext;
import quanta.util.Const;
import quanta.util.ThreadLocals;
import quanta.util.Util;

/**
 * Global Servlet filter for cross-cutting concerns across all endpoints
 */
@Component
@Order(2)
public class GlobalFilter extends GenericFilterBean {
	
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalFilter.class);
	@Autowired
	private ApplicationContext context;

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		if (Const.debugRequests) {
			log.debug("GlobalFilter.doFilter()");
		}
		if (!Util.gracefulReadyCheck(response)) return;
		try {
			ThreadLocals.removeAll();
			HttpServletRequest sreq = null;
			if (request instanceof HttpServletRequest) {
				sreq = (HttpServletRequest) request;
				String uri = sreq.getRequestURI();
				boolean createSession = "/".equals(uri) || uri.isEmpty();
				// Special checks for Cache-Controls
				if ( //
				//
				// JS bundle file
				sreq.getRequestURI().contains("/images/") || sreq.getRequestURI().contains("/fonts/") || sreq.getRequestURI().contains("/dist/main.") || sreq.getRequestURI().endsWith("/images/favicon.ico") ||  //
				sreq.getRequestURI().contains("?v=")) {
					((HttpServletResponse) response).setHeader("Cache-Control", "public, must-revalidate, max-age=31536000");
				}
				// Special check for CORS
				if (sreq.getRequestURI().contains(APConst.PATH_WEBFINGER) ||  //
				sreq.getRequestURI().contains(APConst.PATH_AP + "/")) {
					((HttpServletResponse) response).setHeader("Access-Control-Allow-Origin", "*");
				}
				HttpSession session = sreq.getSession(createSession);
				if (session != null) {
					SessionContext.init(context, session);
				}
			}
			chain.doFilter(request, response);
		} finally {
			/* Set thread back to clean slate, for it's next cycle time in threadpool */
			ThreadLocals.removeAll();
		}
	}

	public void destroy() {
	}
}
