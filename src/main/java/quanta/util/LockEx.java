package quanta.util;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import quanta.exception.base.RuntimeEx;

@Slf4j 
public class LockEx extends ReentrantLock {
	private boolean allowRetries = true;

	/* Initial wait before logging something (in seconds). */
	private long loopTimeoutSecs = 7;

	/*
	 * How long the lock waits before it assumes a deadlock might be happening, and logs the deadlock
	 * warning
	 */
	private long deadlockTimeoutMillis = 3 * 60 * 1000;

	/*
	 * This boolean makes it so that rather than letting server threads get hung we just throw an
	 * exception and fail one of the threads whenever we detect a probable deadlock, so in this way we
	 * do 'recover' from deadlocks although not gracefully.
	 */
	private boolean abortWhenDeadlockSuspected = true;

	private String lockName;

	public LockEx(String lockName, boolean allowRetries) {
		this.allowRetries = allowRetries;
		this.lockName = lockName;
	}

	public LockEx(String lockName, boolean allowRetries, long deadlockTimeoutMillis, long loopTimeoutSecs) {
		this(lockName, allowRetries);
		this.deadlockTimeoutMillis = deadlockTimeoutMillis;
		this.loopTimeoutSecs = loopTimeoutSecs;
	}

	/**
	 * lock method which differs from the basic tryLock because it will keep trying over and over and
	 * printing messages to error log if the lock is not able to be obtained. So the logging is the
	 * important thing we are doing here. This means that deadlocks will be able to be identified in the
	 * log file.
	 */
	public void lockEx() {
		boolean success = false;
		boolean warningShown = false;

		try {
			log.trace("trying to lock: LockEx=[" + hashCode() + "] " + lockName);
			success = tryLock(loopTimeoutSecs, TimeUnit.SECONDS);

			if (success) {
				// log.trace("GOT LOCK: " + lockName + "\nSTACK: " + getStackTrace(null));
			}
		} catch (Exception e) {
			if (!allowRetries) {
				throw new RuntimeEx("FAILED to obtain the lock during the allowed timeout. Lock: " + lockName, e);
			}
			success = false;
		}

		if (!success && allowRetries) {
			log.trace("lock was not obtained, will retry: " + lockName);
			/*
			 * if we timed out trying to get the lock we will go into a retry loop here trying again every few
			 * seconds.
			 */
			long startTime = System.currentTimeMillis();
			while (!success) {
				long totalWaitTime = System.currentTimeMillis() - startTime;
				if (!warningShown && totalWaitTime > deadlockTimeoutMillis) {
					logDeadlockWarning();
					warningShown = true;
					if (abortWhenDeadlockSuspected) {
						throw new RuntimeEx("Aborting. Thread " + Thread.currentThread().getName() + " was hung waiting for lock "
								+ lockName + " which was held by thread " + getOwner().getName());
					}
				}

				try {
					success = tryLock(loopTimeoutSecs, TimeUnit.SECONDS);

					if (success) {
						// log.trace("finally GOT LOCK: " + lockName + ". Waited " + totalWaitTime + " ms.\nSTACK: "
						// 		+ getStackTrace(null));
					}
				} catch (Exception e) {
					success = false;
				}

				if (!success) {
					log.trace("lock still busy[LockEx=" + hashCode() + "], continuing endless retries: " + lockName);
				}
			}
		}

		/*
		 * if we printed a warning message because lock took a while to obtain then print a mia culpa on
		 * that and because we got the lock now!!! Sometimes things just take some time. That' ok. These
		 * messages are just to help diagnose REAL confirmed deadlock situations.
		 */
		if (warningShown && success) {
			warningShown = false;
			Thread thread = Thread.currentThread();
			StringBuilder sb = new StringBuilder();
			sb.append("********** DISREGARD DEADLOCK WARNING **********\n");
			sb.append("Thread FINALLY DID obtain lock: " + thread.getName() + "\n");
			sb.append(ExUtil.getStackTrace(thread));
			sb.append("\n");
			log.trace(sb.toString());
		}
	}

	public void unlockEx() {
		try {
			// ALog.printStackTrace("unlocking");
			if (!isHeldByCurrentThread()) {
				log.trace("impossible unlock call being ignored. thread " + Thread.currentThread().getName()
						+ " not holding lock " + lockName);
				return;
			}
			super.unlock();
			log.trace("globalLockCounter=" + getHoldCount());

		} catch (Exception e) {
			log.trace("unlock failed: " + ExUtil.getStackTrace(null));
			throw new RuntimeEx("LockEx.unlock failed.");
		}
	}

	private void logDeadlockWarning() {
		StringBuilder sb = new StringBuilder();
		sb.append("Lock timed out or deadlock occurred.\n");

		Thread thread = Thread.currentThread();
		sb.append("Thread attempting to get the lock: " + thread.getName() + "\n");
		sb.append(ExUtil.getStackTrace(thread));
		sb.append("\n");
		Thread holdingThread = getOwner();
		sb.append("Thread that has the lock: " + holdingThread.getName());
		sb.append(ExUtil.getStackTrace(holdingThread));
		log.trace(sb.toString());
	}
}
