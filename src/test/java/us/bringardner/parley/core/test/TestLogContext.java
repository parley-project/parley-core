package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.BaseThread;
import us.bringardner.parley.core.LogContext;

/**
 * The ID of the work a thread does (LogContext) and how BaseObject puts it in front of messages.
 * Each test has its own logging class, so its messages are its own (a RecordingLogger per class name).
 */
public class TestLogContext {

	static class Logs extends TestCoreBase {
		List<String> messages() {
			return RecordingLoggerFinder.get(getClass().getName()).messages;
		}
	}

	static class Plain extends Logs {}
	static class Entered extends Logs {}
	static class Nested extends Logs {}
	static class OtherThread extends Logs {}
	static class Wrapped extends Logs {}
	static class Called extends Logs {}
	static class InThread extends Logs {}

	/** An object with an ID of its own, as a connection has. */
	static class Own extends Logs {
		@Override
		protected String getLogContext() {
			return "own-7";
		}
	}

	/** A thread that logs through another object, as a session does through a mail store. */
	static class Session extends BaseThread {
		final Logs other;
		volatile String seen;

		Session(Logs other) {
			this.other = other;
		}

		@Override
		public void run() {
			seen = LogContext.get();
			other.logInfo("from the thread");
		}
	}

	@AfterEach
	public void noContextIsLeftOnTheTestThread() {
		assertNull(LogContext.get(), "a test left a log context on its thread");
	}

	@Test
	public void testWithoutAContextAMessageIsUnchanged() {
		Plain obj = new Plain();
		assertNull(LogContext.get());
		obj.logInfo("one");
		obj.logInfo(() -> "two");
		obj.logError("three", new IOException("boom"));
		assertEquals(Arrays.asList("INFO one", "INFO two", "ERROR three boom"), obj.messages());
	}

	@Test
	public void testEveryFormOfMessageStartsWithTheThreadsId() {
		Entered obj = new Entered();
		try (LogContext.Scope scope = LogContext.enter("pop3-4127")) {
			assertEquals("pop3-4127", LogContext.get());
			obj.logInfo("one");
			obj.logWarn(() -> "two");
			obj.logError("three", new IOException("boom"));
			obj.logDebug("four");
		}
		obj.logInfo("after");
		assertEquals(Arrays.asList("INFO [pop3-4127] one", "WARN [pop3-4127] two", "ERROR [pop3-4127] three boom",
				"DEBUG [pop3-4127] four", "INFO after"), obj.messages());
	}

	@Test
	public void testClosingAScopePutsBackWhatWasThereBefore() {
		Nested obj = new Nested();
		try (LogContext.Scope outer = LogContext.enter("outer-1")) {
			LogContext.Scope inner = LogContext.enter("inner-2");
			obj.logInfo("a");
			inner.close();
			obj.logInfo("b");
			//  closing it again changes nothing
			inner.close();
			assertEquals("outer-1", LogContext.get());
			//  null (or empty) hides the ID for a while
			try (LogContext.Scope none = LogContext.enter(null)) {
				assertNull(LogContext.get());
				obj.logInfo("c");
			}
			try (LogContext.Scope none = LogContext.enter("")) {
				assertNull(LogContext.get());
			}
			obj.logInfo("d");
		}
		assertNull(LogContext.get());
		assertEquals(Arrays.asList("INFO [inner-2] a", "INFO [outer-1] b", "INFO c", "INFO [outer-1] d"), obj.messages());
	}

	@Test
	public void testAnObjectsOwnIdIsUsedWhenTheThreadHasNone() {
		Own obj = new Own();
		obj.logInfo("alone");
		obj.logInfo(() -> "alone too");
		try (LogContext.Scope scope = LogContext.enter("smtp-3")) {
			//  the thread's ID wins: the object is working for that session now
			obj.logInfo("for a session");
		}
		assertEquals(Arrays.asList("INFO [own-7] alone", "INFO [own-7] alone too", "INFO [smtp-3] for a session"), obj.messages());
	}

	@Test
	public void testTheIdStaysOnItsThreadUnlessTheTaskIsWrapped() throws Exception {
		OtherThread plain = new OtherThread();
		Wrapped wrapped = new Wrapped();
		Runnable plainTask;
		Runnable wrappedTask;
		Runnable named;
		try (LogContext.Scope scope = LogContext.enter("imap-9")) {
			plainTask = () -> plain.logInfo("not wrapped");
			wrappedTask = LogContext.wrap(() -> wrapped.logInfo("wrapped"));
			named = LogContext.wrap("queue-MWC3", () -> wrapped.logInfo("named"));
		}
		for(Runnable task : Arrays.asList(plainTask, wrappedTask, named)) {
			Thread t = new Thread(task);
			t.start();
			t.join(10_000);
		}
		assertEquals(Arrays.asList("INFO not wrapped"), plain.messages());
		assertEquals(Arrays.asList("INFO [imap-9] wrapped", "INFO [queue-MWC3] named"), wrapped.messages());

		//  with no ID there is nothing to carry: the task itself comes back
		Runnable same = () -> { };
		assertSame(same, LogContext.wrap(same));
		assertSame(same, LogContext.wrap(null, same));
		assertNull(LogContext.wrap((Runnable) null));
	}

	@Test
	public void testAWrappedCallableCarriesTheId() throws Exception {
		Called obj = new Called();
		Callable<String> task;
		try (LogContext.Scope scope = LogContext.enter("ftp-12")) {
			task = LogContext.wrap(() -> {
				obj.logInfo("called");
				return LogContext.get();
			});
		}
		AtomicReference<String> seen = new AtomicReference<>();
		AtomicReference<Exception> failed = new AtomicReference<>();
		Thread t = new Thread(() -> {
			try {
				seen.set(task.call());
			} catch (Exception e) {
				failed.set(e);
			}
		});
		t.start();
		t.join(10_000);
		assertNull(failed.get());
		assertEquals("ftp-12", seen.get());
		assertEquals(Arrays.asList("INFO [ftp-12] called"), obj.messages());

		Callable<String> same = () -> "x";
		assertSame(same, LogContext.wrap(same));
	}

	@Test
	public void testABaseThreadRunsWithItsId() throws Exception {
		InThread other = new InThread();
		Session session = new Session(other);
		assertNull(session.getLogContext());
		session.setLogContext("pop3-1");
		assertEquals("pop3-1", session.getLogContext());
		session.start();
		session.join(10_000);
		assertEquals("pop3-1", session.seen);
		//  the thread that started it is not changed
		assertNull(LogContext.get());
		other.logInfo("from the test");
		assertEquals(Arrays.asList("INFO [pop3-1] from the thread", "INFO from the test"), other.messages());

		//  without an ID the thread runs without one
		InThread again = new InThread();
		Session plain = new Session(again);
		plain.start();
		plain.join(10_000);
		assertNull(plain.seen);
	}
}
