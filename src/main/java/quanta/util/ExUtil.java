package quanta.util;

import org.slf4j.Logger;
import lombok.extern.slf4j.Slf4j;
import quanta.exception.base.RuntimeEx;

/**
 * We use RuntimeExceptions primarily for all exception handling, throughout the app because of the
 * cleanness of the API when it doesn't have to declare checked exceptions everywhere, and this
 * utility encapsulates the conversion of most checked exceptions to RuntimeExceptions.
 * 
 * Note: This code doesn't ignore exceptions or alter our ability to properly handle ALL exceptions
 * of both types, but it just makes the code cleaner, by doing what the Java-language SHOULD have
 * done to begin with.
 */
@Slf4j 
public class ExUtil {
	public static void run(Runnable runnable) {
		try {
			runnable.run();
		} catch (Exception e) {
			throw wrapEx(e);
		}
	}

	public static RuntimeException logAndWrapEx(Logger logger, String msg, Throwable ex) {
		error(logger, msg, ex);
		return wrapEx(ex);
	}

	public static RuntimeException wrapEx(Throwable ex) {
		if (ex instanceof RuntimeException) {
			return (RuntimeException) ex;
		}
		return new RuntimeEx(ex);
	}

	public static RuntimeEx wrapEx(String msg) {
		RuntimeEx ex = new RuntimeEx(msg);
		return ex;
	}

	// Note: We can's use ExceptionUtils.getStackTrace(e), because we support thread
	// argument here
	public static final String getStackTrace(Thread thread) {
		if (thread == null) {
			thread = Thread.currentThread();
		}
		StringBuilder sb = new StringBuilder();
		StackTraceElement[] trace = thread.getStackTrace();
		for (int i = 0; i < trace.length; i++) {
			StackTraceElement e = trace[i];
			sb.append("    ");
			sb.append(e.toString());
			sb.append("\n");
		}
		return (sb.toString());
	}

	public static void warn(String msg) {
		log.warn(msg + "\n" + getStackTrace(null));
	}

	public static void error(String msg) {
		log.error(msg + "\n" + getStackTrace(null));
	}

	public static void debug(Logger logger, String msg, Throwable e) {
		logger.debug(msg, e);

		/* Not showing all sub-causes in the chain, but just the immediate one */
		if (e.getCause() != null) {
			logger.debug("cause:", e);
		}
	}

	public static void error(Logger logger, String msg, Throwable e) {
		logger.error(msg, e);

		/* Not showing all sub-causes in the chain, but just the immediate one */
		if (e.getCause() != null) {
			logger.error("cause:", e);
		}
	}

	public static void warn(Logger logger, String msg, Throwable e) {
		logger.warn(msg, e);

		/* Not showing all sub-causes in the chain, but just the immediate one */
		if (e.getCause() != null) {
			logger.warn("cause:", e);
		}
	}
}
