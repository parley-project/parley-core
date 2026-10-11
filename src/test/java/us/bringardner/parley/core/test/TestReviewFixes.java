package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.BaseThread;
import us.bringardner.parley.core.ILogger;
import us.bringardner.parley.core.JulLogger;
import us.bringardner.parley.core.util.AbstractCoreServer;
import us.bringardner.parley.core.util.LogHelper;

/**
 * Fixes from the October 2026 reliability review.
 */
public class TestReviewFixes {

	// ---------------- AbstractCoreServer: no listening socket left open after stop ----------------

	/** Asks for its socket only after it has been told to go, like a run method that does some set up first. */
	static class SlowStartServer extends AbstractCoreServer {
		final CountDownLatch go = new CountDownLatch(1);
		final AtomicReference<IOException> error = new AtomicReference<>();
		volatile ServerSocket socket;

		SlowStartServer() {
			super(0, false);
		}

		@Override
		public void run() {
			try {
				go.await(10, TimeUnit.SECONDS);
				socket = getServerSocket();
			} catch (IOException e) {
				error.set(e);
				return;
			} catch (InterruptedException e) {
				return;
			}
			started = running = true;
			while( !stopping && !socket.isClosed() ) {
				try (Socket s = socket.accept()) {
					// nothing to do
				} catch (IOException e) {
					// stopped
				}
			}
		}
	}

	@Test
	public void testStoppedServerDoesNotOpenItsSocket() throws Exception {
		SlowStartServer svr = new SlowStartServer();
		svr.start();
		svr.stop();
		svr.go.countDown();
		svr.join(10000);
		assertFalse(svr.isAlive(), "The server thread should end");
		assertTrue(svr.error.get() instanceof SocketException, "getServerSocket() should refuse while stopping, got "+svr.error.get());
		assertTrue(svr.socket == null || svr.socket.isClosed(), "No listening socket may be left open");
	}

	@Test
	public void testStoppedServerCanStillBeRestarted() throws Exception {
		SlowStartServer svr = new SlowStartServer();
		svr.start();
		svr.stop();
		svr.go.countDown();
		svr.join(10000);

		//  After the thread ends, getServerSocket() works again (for a restart, or to read the port)
		ServerSocket ss = svr.getServerSocket();
		assertFalse(ss.isClosed());
		svr.closeServerSocket();

		SlowStartServer svr2 = new SlowStartServer();
		svr2.go.countDown();
		svr2.start();
		long end = System.currentTimeMillis()+10000;
		while( !svr2.hasStarted() && System.currentTimeMillis() < end ) {
			Thread.sleep(10);
		}
		assertTrue(svr2.hasStarted());
		assertTrue(svr2.stop(10000, false));
		assertTrue(svr2.socket.isClosed());
	}

	// ---------------- BaseThread: restart after run() throws ----------------

	@Test
	public void testThreadCanBeRestartedAfterRunThrows() throws Exception {
		AtomicInteger runs = new AtomicInteger();
		BaseThread thread = new BaseThread() {
			@Override
			public void run() {
				started = running = true;
				runs.incrementAndGet();
				throw new IllegalStateException("test: run failed");
			}
		};
		thread.setUncaughtExceptionHandler((t, e) -> {
			// expected
		});
		thread.start();
		thread.join(5000);
		assertFalse(thread.isRunning(), "running must be cleared when run() throws");

		thread.start();
		thread.join(5000);
		assertEquals(2, runs.get(), "start() should run the thread again");
		assertFalse(thread.isRunning());
	}

	// ---------------- JulLogger: debug level and caller ----------------

	@Test
	public void testJulDebugMatchesLevel() {
		JulLogger logger = new JulLogger();
		logger.init("test.review.jul.level");
		Logger jul = Logger.getLogger("test.review.jul.level");
		try {
			for (java.util.logging.Level l : new java.util.logging.Level[] {
					java.util.logging.Level.CONFIG, java.util.logging.Level.FINE,
					java.util.logging.Level.FINER, java.util.logging.Level.FINEST}) {
				jul.setLevel(l);
				assertEquals(logger.getLevel() == ILogger.Level.DEBUG, logger.isDebugEnabled(), "At "+l+" getLevel()="+logger.getLevel());
			}
			jul.setLevel(java.util.logging.Level.FINE);
			assertTrue(logger.isDebugEnabled(), "FINE should show debug messages");
			jul.setLevel(java.util.logging.Level.CONFIG);
			assertEquals(ILogger.Level.INFO, logger.getLevel());

			logger.setLevel(ILogger.Level.DEBUG);
			assertTrue(logger.isDebugEnabled());
			assertEquals(ILogger.Level.DEBUG, logger.getLevel());
		} finally {
			jul.setLevel(null);
		}
	}

	//  Not a direct BaseObject subclass: TestCore.testClassLoader counts those in the test classes
	static class JulUser extends LogHelper {
		JulUser() {
			super(JulUser.class);
		}

		void logBoth() {
			logError("through BaseObject");
			logError(() -> "through BaseObject with a Supplier");
		}
	}

	@Test
	public void testJulReportsTheRealCaller() {
		String name = "test.review.jul.caller";
		JulLogger logger = new JulLogger();
		logger.init(name);
		Logger jul = Logger.getLogger(name);
		List<LogRecord> records = new ArrayList<>();
		Handler h = new Handler() {
			@Override
			public void publish(LogRecord r) {
				records.add(r);
			}
			@Override
			public void flush() {
			}
			@Override
			public void close() {
			}
		};
		jul.addHandler(h);
		jul.setUseParentHandlers(false);
		try {
			logger.error("direct");
			logger.error(() -> "direct with a Supplier");
			JulUser user = new JulUser();
			user.setLogger(logger);
			user.logBoth();

			assertEquals(4, records.size());
			assertEquals(TestReviewFixes.class.getName(), records.get(0).getSourceClassName());
			assertEquals("testJulReportsTheRealCaller", records.get(0).getSourceMethodName());
			assertEquals(TestReviewFixes.class.getName(), records.get(1).getSourceClassName());
			assertEquals(JulUser.class.getName(), records.get(2).getSourceClassName());
			assertEquals("logBoth", records.get(2).getSourceMethodName());
			assertEquals(JulUser.class.getName(), records.get(3).getSourceClassName());
		} finally {
			jul.removeHandler(h);
			jul.setUseParentHandlers(true);
		}
	}

}
