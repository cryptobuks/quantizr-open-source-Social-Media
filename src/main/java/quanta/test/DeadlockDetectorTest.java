package quanta.test;

import lombok.extern.slf4j.Slf4j;
import quanta.util.LockEx;

/**
 * This class intentionally creates a deadlock and using LockEx the deadlock is
 * detected. The way it works is this flow:
 * 
 * Thread 1 gets lock a, then waits a second and tries to get lock b, but by then Thread 2 will have obtained lock b and then also be making
 * an attempt to get lock a. This condition will never resolve itself, if they continue waiting, but the purpose of LockEx is that it can 
 * detect there's a deadlock probably happening, and log a warning about it.
 * 
 * This test case verifies a deadlock DID happen and then eventually the following code in LockEx.java does run and breaks
 * the deadlock and then allowes the JUnit test to complete:
 * 
 *    if (abortWhenDeadlockSuspected) {
 *		throw new RuntimeEx("Aborting. Thread "+Thread.currentThread().getName()+" was hung waiting for lock "+lockName+" which was held by thread "+getOwner().getName());
 *	}
 */
@Slf4j 
public class DeadlockDetectorTest {
	private int threadsRunning = 0;
	private final LockEx a = new LockEx("a", true, 7000, 1);
	private final LockEx b = new LockEx("b", true, 7000, 1);

	class T1 extends Thread {
		public void run() {
			try {
				threadsRunning++;
				Thread.currentThread().setName("T1");
				log.debug("Thread T1 Started.");

				// Thread 1 gets lock "a"
				a.lockEx();
				System.out.println("T1: Lock a entered. And sleeping 3 seconds.");
				delay(3000);

				try {
					// Thread 1 tries to get lock 'b'
					b.lockEx();
					System.out.println("T1: Lock b entered");
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					b.unlockEx();
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				a.unlockEx();
				threadsRunning--;
			}
		}
	}

	class T2 extends Thread {
		public void run() {
			try {
				threadsRunning++;
				Thread.currentThread().setName("T2");
				log.debug("Thread T2 Started.");

				//small sleep just enough to ensure Thread 1 has gotten lock a.
				delay(2000);

				// Thread 2 gets lock b
				b.lockEx();
				System.out.println("T2: Lock b entered. And sleeping two seconds.");
				delay(1000);
				try {
					// Thread 2 tries to get lock a, but will fail, because thread 1 has the lock already.
					a.lockEx();
					System.out.println("T2: Lock a entered");
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					a.unlockEx();
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				b.unlockEx();
				threadsRunning--;
			}
		}
	}

	private static void delay(long time) {
		try {
			Thread.sleep(time);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void run() {
		new T1().start();
		new T2().start();

		//give threads a chance to start before we start waiting on them.
		delay(100);

		//i know there's a cleaner way to wait for multiple threads, but i want this test to be ultra simple
		//with the smallest possible amount of 'Threading-specific' API calls in it.
		while (threadsRunning > 0) {
			delay(250);
		}
		log.debug("Deadlock Test exiting normally.");
	}
}
