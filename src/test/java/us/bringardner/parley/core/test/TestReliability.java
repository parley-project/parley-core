package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.BaseObject;
import us.bringardner.parley.core.BaseThread;
import us.bringardner.parley.core.util.AbstractCoreServer;

/**
 * Tests for the reliability fixes (thread visibility,
 * property cache concurrency, property parsing, class loader search).
 */
public class TestReliability {

	@Test
	public void testBaseThreadStopRestartAndJoin() throws InterruptedException {
		AtomicInteger runs = new AtomicInteger();
		BaseThread thread = new BaseThread() {
			@Override
			public void run() {
				started = running = true;
				runs.incrementAndGet();
				//  Busy loop (no sleep) - only works if 'stopping' is visible to this thread
				while (!stopping) {
					Thread.onSpinWait();
				}
				running = false;
			}
		};

		thread.start();
		waitFor(thread::hasStarted, 5000);
		assertTrue(thread.isRunning());
		//  a second start while running must not create a second thread
		thread.start();
		assertTrue(thread.stop(5000, false), "Thread did not stop");
		assertFalse(thread.isRunning());

		//  restart after stop
		thread.start();
		waitFor(thread::hasStarted, 5000);
		assertTrue(thread.isRunning(), "Thread should run again after a restart");
		assertTrue(thread.stop(5000, false), "Thread did not stop after restart");
		assertEquals(2, runs.get());
	}

	@Test
	public void testBaseThreadInterruptWakesSleepingThread() throws InterruptedException {
		BaseThread thread = new BaseThread() {
			@Override
			public void run() {
				started = running = true;
				while (!stopping) {
					try {
						Thread.sleep(60000);
					} catch (InterruptedException e) {
					}
				}
				running = false;
			}
		};
		thread.start();
		waitFor(thread::hasStarted, 5000);
		long start = System.currentTimeMillis();
		assertTrue(thread.stop(5000, true), "Interrupt should wake the sleeping thread");
		assertTrue(System.currentTimeMillis() - start < 5000);
	}

	static class PropertyUser1 extends TestCoreBase {}
	static class PropertyUser2 extends TestCoreBase {}
	static class PropertyUser3 extends TestCoreBase {}
	static class PropertyUser4 extends TestCoreBase {}

	@Test
	public void testPropertyCacheConcurrency() throws Exception {
		//  a tiny cache forces constant eviction while many threads use it
		BaseObject.setMaxProperties(2);
		ExecutorService ex = Executors.newFixedThreadPool(8);
		List<Future<?>> futures = new ArrayList<>();
		try {
			for (int t = 0; t < 8; t++) {
				futures.add(ex.submit(() -> {
					BaseObject[] objects = { new PropertyUser1(), new PropertyUser2(), new PropertyUser3(),
							new PropertyUser4(), new TestCoreBase() };
					for (int idx = 0; idx < 5000; idx++) {
						BaseObject obj = objects[idx % objects.length];
						assertNull(obj.getProperty("NoSuchProperty"));
					}
					assertEquals("2", new TestCoreBase().getProperty("Value02"));
				}));
			}
			for (Future<?> f : futures) {
				f.get(60, TimeUnit.SECONDS);
			}
		} finally {
			ex.shutdownNow();
			BaseObject.setMaxProperties(BaseObject.DEFAULT_MAX_PROPERTIES);
		}
	}

	@Test
	public void testIntPropertyParsing() {
		BaseObject obj = new BaseObject();
		System.setProperty("TestIntValue", "  42 ");
		System.setProperty("TestBadIntValue", "forty-two");
		try {
			assertEquals(42, obj.getIntProperty("TestIntValue", 7));
			assertEquals(7, obj.getIntProperty("TestBadIntValue", 7), "An invalid value should use the default, not throw");
			assertEquals(7, obj.getIntProperty("TestMissingIntValue", 7));
		} finally {
			System.clearProperty("TestIntValue");
			System.clearProperty("TestBadIntValue");
		}
	}

	@Test
	public void testServerAttributesAndLinger() {
		AbstractCoreServer svr = new AbstractCoreServer(0) {
			@Override
			public void run() {
			}
		};
		svr.setAttribute("a", "value");
		assertEquals("value", svr.getAttribute("a"));
		//  null values are allowed (and remove the attribute)
		svr.setAttribute("a", null);
		assertNull(svr.getAttribute("a"));
		assertNull(svr.getAttribute(null));
		//  SO_LINGER is in seconds
		assertTrue(svr.getLingerTime() <= 60, "Default linger time should be seconds, not milliseconds");
		assertFalse(svr.isSoLinger());
		assertFalse(svr.isSecure());
	}

	private static void waitFor(java.util.function.BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
		long end = System.currentTimeMillis() + timeoutMillis;
		while (!condition.getAsBoolean() && System.currentTimeMillis() < end) {
			Thread.sleep(10);
		}
	}
}
