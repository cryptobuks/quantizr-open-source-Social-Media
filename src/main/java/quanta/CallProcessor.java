package quanta;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
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
import quanta.util.MongoRunnableEx;
import quanta.util.ThreadLocals;
import quanta.util.XString;

@Component
public class CallProcessor extends ServiceBase {

    private static Logger log = LoggerFactory.getLogger(CallProcessor.class);

    /*
     * Wraps the processing of any command by using whatever info is on the session and/or the request
     * to perform the login if the user is not logged in, and then call the function to be processed
     */
    public Object run(
        String command,
        boolean authBearer,
        boolean authSig,
        RequestBase req,
        HttpSession httpSession,
        MongoRunnableEx<Object> runner
    ) {
        if (AppServer.isShuttingDown()) {
            throw ExUtil.wrapEx("Server not available.");
        }
        SessionContext sc = ThreadLocals.getSC();
        sc.setCommand(command);

        if (authBearer) {
            user.authBearer();
        }
        /*
         * #sig: this works fine, but I'm disabling for now (except for admin) until there's a better way to
         * inform the user that this can happen when their key on their browser is different than expected,
         * which CAN even happen simply from using a different browser that hasn't had the signature key
         * imported into it. And also all the flow around how this can be encountered during login/logout
         * needs to be tested and more well thought out.
         */
        if (authSig && sc.isAdmin()) {
            user.authSig();
        }
        logRequest(command, req, httpSession);
        /*
         * Instantiating this, runs its constructor and ensures our threadlocal at least has response object
         * on it, but most (not all) implementations of methods end up instantiating their own which
         * overwrites this
         */
        new ResponseBase();

        Object ret = null;

        long startTime = System.currentTimeMillis();
        String userName = null;
        try {
            if (req instanceof LogoutRequest) {
                // Note: all this run will be doing in this case is a session invalidate.
                ret = runner.run(null);
            } else {
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
                        orb.setMessage("Oops, something went wrong.");
                    }
                }
                orb.setStackTrace(ExceptionUtils.getStackTrace(e));
            }
        } finally {
            int duration = (int) (System.currentTimeMillis() - startTime);
            if (duration > Instrument.CAPTURE_THRESHOLD) {
                new PerfMonEvent(duration, "callProc." + command, userName);
            }
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
