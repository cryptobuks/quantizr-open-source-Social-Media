package quanta.filter;

import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;
import quanta.util.Const;
import quanta.util.Util;

/**
 * Filter for logging details of any request/response
 */
@Component
@Order(1)
public class AuditFilter extends GenericFilterBean {

    private static Logger log = LoggerFactory.getLogger(AuditFilter.class);
    private static String INDENT = "    ";
    public static boolean enabled = false;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        if (Const.debugRequests) {
            log.debug("AuditFilter.doFilter()");
        }
        if (!Util.gracefulReadyCheck(response)) return;
        HttpServletRequest sreq = null;
        if (request instanceof HttpServletRequest) {
            sreq = (HttpServletRequest) request;
        }
        try {
            if (enabled) {
                preProcess(sreq);
            }
            chain.doFilter(request, response);
        } finally {
            if (enabled) {
                if (log.isTraceEnabled()) {
                    if (response instanceof HttpServletResponse) {
                        HttpServletResponse sres = (HttpServletResponse) response;
                        postProcess(sreq, sres);
                    }
                }
            }
        }
    }

    private String getConfigParamInfo() {
        StringBuilder sb = new StringBuilder();
        Enumeration<?> en = getFilterConfig().getInitParameterNames();
        if (en != null) {
            while (en.hasMoreElements()) {
                String param = (String) en.nextElement();
                sb.append(INDENT);
                sb.append(param);
                sb.append(": ");
                sb.append(getFilterConfig().getInitParameter(param));
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public static String getRequestInfo(HttpServletRequest sreq) {
        StringBuilder sb = new StringBuilder();
        sb.append(INDENT);
        sb.append("meth url: ");
        sb.append(sreq.getMethod());
        sb.append(" ");
        sb.append(sreq.getRequestURI());
        sb.append("\n");
        sb.append(INDENT);
        sb.append("proto path: ");
        sb.append(sreq.getProtocol());
        sb.append(" ");
        sb.append(sreq.getServletPath());
        sb.append("\n");
        if (sreq.getPathInfo() != null || sreq.getPathTranslated() != null) {
            sb.append(INDENT);
            sb.append("pinfo -> ptrans: ");
            sb.append(sreq.getPathInfo());
            sb.append(" -> ");
            sb.append(sreq.getPathTranslated());
            sb.append("\n");
        }
        if (sreq.getQueryString() != null) {
            sb.append(INDENT);
            sb.append("q: ");
            sb.append(sreq.getQueryString());
            sb.append("\n");
        }
        sb.append(INDENT);
        sb.append("len typ: ");
        sb.append(sreq.getContentLength());
        sb.append(" ");
        sb.append(sreq.getContentType());
        sb.append("\n");
        sb.append(INDENT);
        sb.append("server port usr: ");
        sb.append(sreq.getServerName());
        sb.append(" ");
        sb.append(sreq.getServerPort());
        sb.append(" ");
        sb.append(sreq.getRemoteUser());
        sb.append("\n");
        sb.append(INDENT);
        sb.append("adrs host auth: ");
        sb.append(sreq.getRemoteAddr());
        sb.append(" ");
        sb.append(sreq.getRemoteHost());
        sb.append(" ");
        sb.append(sreq.getAuthType());
        sb.append("\n");
        return sb.toString();
    }

    private String getHeaderInfo(HttpServletRequest sreq) {
        StringBuilder sb = new StringBuilder();
        Enumeration<?> e = sreq.getHeaderNames();
        if (e.hasMoreElements()) {
            sb.append("headers:\n");

            while (e.hasMoreElements()) {
                String name = (String) e.nextElement();
                sb.append(INDENT);
                sb.append(name);
                sb.append(": ");
                sb.append(sreq.getHeader(name));
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String getHeaderInfo(HttpServletResponse sres) {
        StringBuilder sb = new StringBuilder();
        Collection<String> names = sres.getHeaderNames();
        if (names.size() > 0) {
            sb.append("headers:\n");

            for (String name : names) {
                sb.append(INDENT);
                sb.append(name);
                sb.append(": ");
                sb.append(sres.getHeader(name));
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String getParameterInfo(HttpServletRequest sreq) {
        StringBuilder sb = new StringBuilder();
        Enumeration<?> e = sreq.getParameterNames();
        if (e.hasMoreElements()) {
            sb.append("Servlet parameters (Multiple Value style):\n");

            while (e.hasMoreElements()) {
                String name = (String) e.nextElement();
                String[] vals = (String[]) sreq.getParameterValues(name);
                if (vals != null) {
                    sb.append(INDENT);
                    sb.append("[");
                    sb.append(name);
                    sb.append("]=");
                    sb.append(vals[0]);

                    for (int i = 1; i < vals.length; i++) {
                        sb.append(INDENT);
                        sb.append(INDENT); // double indent (not a typo)
                        sb.append(vals[i]);
                        sb.append("\n");
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String getAttributeInfo(HttpServletRequest sreq) {
        StringBuilder sb = new StringBuilder();
        Object reqAttrs = sreq.getAttributeNames();
        if (reqAttrs != null && reqAttrs instanceof Enumeration<?>) {
            Enumeration<?> attrs = (Enumeration<?>) reqAttrs;
            sb.append("Req Attrs:\n");

            while (attrs.hasMoreElements()) {
                String attr = attrs.nextElement().toString();
                if (sreq.getAttribute(attr) != null) {
                    sb.append(INDENT);
                    sb.append(attr);
                    sb.append("=");
                    sb.append(sreq.getAttribute(attr).toString());
                } else {
                    sb.append(INDENT);
                    sb.append(attr);
                    sb.append("=null");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String getSessionAttributeInfo(HttpServletRequest sreq) {
        HttpSession session = ((HttpServletRequest) sreq).getSession(false);
        if (session == null) {
            return "[no session]\n";
        }
        StringBuilder sb = new StringBuilder();
        Object sessionAattrs = session.getAttributeNames();
        if (sessionAattrs != null && sessionAattrs instanceof Enumeration<?>) {
            Enumeration<?> attrs = (Enumeration<?>) sessionAattrs;
            sb.append("Sess Attrs:\n");

            while (attrs.hasMoreElements()) {
                String attr = attrs.nextElement().toString();
                if (session.getAttribute(attr) != null) {
                    sb.append(INDENT);
                    sb.append(attr);
                    sb.append(" = ");
                    sb.append(session.getAttribute(attr).toString());
                } else {
                    sb.append(INDENT);
                    sb.append(attr);
                    sb.append("=null");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unused")
    private String getRequestParameterInfo(HttpServletRequest sreq) {
        Enumeration<?> e = sreq.getParameterNames();
        StringBuilder sb = new StringBuilder();
        if (e.hasMoreElements()) {
            sb.append("Servlet parameters (Single Value style):\n");

            while (e.hasMoreElements()) {
                String name = (String) e.nextElement();
                sb.append(INDENT + name + " = " + sreq.getParameter(name) + "\n");
            }
        }
        return sb.toString();
    }

    private void preProcess(HttpServletRequest sreq) {
        if (sreq == null) return;
        // NON-VERBOSE Logging
        if (log.isDebugEnabled() && !log.isTraceEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("REQ: ");
            sb.append(sreq.getMethod());
            sb.append(" ");
            sb.append(sreq.getRequestURI());
            if (sreq.getQueryString() != null) {
                sb.append(" -> ");
                sb.append(sreq.getQueryString());
            }
            sb.append(" [from ");
            sb.append(sreq.getRemoteAddr());
            sb.append("]");
            // sb.append(" SpringAuth=" + Util.isSpringAuthenticated());
            log.debug(sb.toString());
        } //
        else if (log.isTraceEnabled()) { // VERBOSE Logging
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("\n>\n");
                sb.append(getConfigParamInfo());
                sb.append(getRequestInfo(sreq));
                sb.append(getRequestParameterInfo(sreq));
                sb.append(getHeaderInfo(sreq));
                sb.append(getParameterInfo(sreq));
                sb.append(getAttributeInfo(sreq));
                sb.append(getSessionAttributeInfo(sreq));
                // sb.append(" SpringAuth=" + Util.isSpringAuthenticated());
                log.trace(sb.toString());
            } catch (Exception e) {
                log.error("error", e);
            }
        }
    }

    private void postProcess(HttpServletRequest sreq, HttpServletResponse sres) {
        try {
            if (sreq == null || sres == null) return;
            StringBuilder sb = new StringBuilder();
            sb.append("\n<: " + String.valueOf(sres.getStatus()) + " ctyp: " + sres.getContentType() + "\n");
            sb.append(getHeaderInfo(sres));
            sb.append(getSessionAttributeInfo(sreq));
            sb.append("++++++\n");
            log.trace(sb.toString());
        } catch (Exception e) {
            log.error("error", e);
        }
    }

    public void destroy() {}
}
