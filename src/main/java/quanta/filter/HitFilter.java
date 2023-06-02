
package quanta.filter;

import java.io.IOException;
import java.util.HashMap;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;
import quanta.util.Const;
import quanta.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servlet filter for monitoring load statistics
 */
@Component
@Order(4)
public class HitFilter extends GenericFilterBean {
	
	private static Logger log = LoggerFactory.getLogger(HitFilter.class);
	private static final HashMap<String, Integer> uniqueHits = new HashMap<>();

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		if (Const.debugRequests) {
			log.debug("HitFilter.doFilter()");
		}
		if (!Util.gracefulReadyCheck(response)) return;
		HttpServletRequest sreq = null;
		if (request instanceof HttpServletRequest) {
			sreq = (HttpServletRequest) request;
			updateHitCounter(sreq);
		}
		chain.doFilter(request, response);
	}

	private void updateHitCounter(HttpServletRequest httpReq) {
		HttpSession session = ((HttpServletRequest) httpReq).getSession(false);
		if (session != null) {
			addHit(session.getId());
		}
	}

	// Identifier can be a username OR a sessionId, depending on which map is being updated
	public static void addHit(String id) {
		synchronized (uniqueHits) {
			Integer hitCount = id != null ? uniqueHits.get(id) : null;
			if (hitCount == null) {
				uniqueHits.put(id, 1);
			} else {
				hitCount = hitCount.intValue() + 1;
				// Throttle heavy users
				if (hitCount > 20) {
					Util.sleep(hitCount);
				}
				uniqueHits.put(id, hitCount);
			}
		}
	}

	public static HashMap<String, Integer> getHits() {
		return uniqueHits;
	}

	public void destroy() {
	}
}
