package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.BaseObject;

/**
 * BaseObject logs through a System.Logger and leaves the level to it.
 */
public class TestBaseObjectSystemLogger {

	private static class Target extends BaseObject {
		private final String loggerName;

		Target(String loggerName) {
			this.loggerName = loggerName;
		}

		@Override
		protected String getLoggerName() {
			return loggerName;
		}
	}

	@Test
	public void testLoggerIsNamedAndKept() {
		Target t = new Target("test.named.logger");
		System.Logger l = t.getLogger();
		assertEquals("test.named.logger", l.getName());
		assertSame(l, t.getLogger());
	}

	@Test
	public void testMessagesGoToTheLoggerAtItsLevel() {
		Target t = new Target("test.levels");
		RecordingLogger rec = new RecordingLogger();
		t.setLogger(rec);

		t.logError("e");
		t.logWarn("w");
		t.logInfo("i");
		t.logDebug("d");
		t.logWarn("with error", new IllegalStateException("boom"));
		assertEquals(5, rec.messages.size());
		assertEquals("ERROR e", rec.messages.get(0));
		assertEquals("WARN w", rec.messages.get(1));
		assertEquals("INFO i", rec.messages.get(2));
		assertEquals("DEBUG d", rec.messages.get(3));
		assertEquals("WARN with error boom", rec.messages.get(4));
	}

	@Test
	public void testTheLoggersLevelDecides() {
		Target t = new Target("test.decides");
		RecordingLogger rec = new RecordingLogger();
		rec.setLevel(System.Logger.Level.WARNING);
		t.setLogger(rec);

		assertTrue(t.isErrorEnabled());
		assertTrue(t.isWarnEnabled());
		assertFalse(t.isInfoEnabled());
		assertFalse(t.isDebugEnabled());

		t.logInfo("hidden");
		t.logDebug("hidden");
		t.logWarn("shown");
		assertEquals(1, rec.messages.size());
		assertEquals("WARN shown", rec.messages.get(0));

		// a change at the logger takes effect with nothing done to the object
		rec.setLevel(System.Logger.Level.ALL);
		assertTrue(t.isDebugEnabled());
		t.logDebug("now shown");
		assertEquals("DEBUG now shown", rec.messages.get(1));
	}

	@Test
	public void testSupplierIsOnlyCalledWhenLogged() {
		Target t = new Target("test.supplier");
		RecordingLogger rec = new RecordingLogger();
		rec.setLevel(System.Logger.Level.ERROR);
		t.setLogger(rec);

		AtomicInteger calls = new AtomicInteger();
		t.logDebug(() -> "d"+calls.incrementAndGet());
		t.logInfo(() -> "i"+calls.incrementAndGet());
		t.logWarn(() -> "w"+calls.incrementAndGet());
		assertEquals(0, calls.get());
		assertTrue(rec.messages.isEmpty());

		t.logError(() -> "e"+calls.incrementAndGet());
		assertEquals(1, calls.get());
		assertEquals("ERROR e1", rec.messages.get(0));
	}

	@Test
	public void testSetLoggerNullLooksTheLoggerUpAgain() {
		Target t = new Target("test.again");
		t.setLogger(new RecordingLogger());
		t.setLogger(null);
		assertEquals("test.again", t.getLogger().getName());
	}
}
