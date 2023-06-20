package quanta.service;

import java.io.IOException;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;
import quanta.actpub.APConst;
import quanta.util.Const;
import quanta.util.Util;
import quanta.util.XString;

/* This does processing for pretty much everything that we don't do "logic" around which would be in AppFilter */
// See AppConfiguration.java for Bean Registration
@Component
public class UtilFilter extends GenericFilterBean {

    private static Logger log = LoggerFactory.getLogger(UtilFilter.class);
    public static boolean debug = false;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        if (!Util.gracefulReadyCheck(res)) return;

        HttpServletRequest httpReq = (HttpServletRequest) req;
        HttpServletResponse httpRes = (HttpServletResponse) res;

        if (Const.debugFilterEntry || debug) {
            String url = "UtilFilter: URI=" + httpReq.getRequestURI();
            if (httpReq.getQueryString() != null) {
                url += " q=" + httpReq.getQueryString();
            }
            Map params = httpReq.getParameterMap();
            if (params != null && params.size() > 0) {
                url += "\n    Params: " + XString.prettyPrint(httpReq.getParameterMap());
            }
            log.debug(url);
        }

        // Special checks for Cache-Controls
        if (
            httpReq.getRequestURI().contains("/images/") ||
            httpReq.getRequestURI().contains("/fonts/") ||
            httpReq.getRequestURI().contains("/dist/main.") ||
            httpReq.getRequestURI().endsWith("/images/favicon.ico")
        ) {
            httpRes.setHeader("Cache-Control", "public, must-revalidate, max-age=31536000");
        }

        // Special check for CORS
        if (
            httpReq.getRequestURI().contains(APConst.PATH_WEBFINGER) || //
            httpReq.getRequestURI().contains(APConst.PATH_AP + "/")
        ) {
            httpRes.setHeader("Access-Control-Allow-Origin", "*");
        }

        chain.doFilter(httpReq, httpRes);
    }

    public void destroy() {}
}
