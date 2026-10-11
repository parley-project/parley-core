package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.BaseThread;
import us.bringardner.parley.core.ParleyLogger;
import us.bringardner.parley.core.SecureBaseObject;

/**
 * Public API that BjlCore (parley-core's predecessor) had and that the libraries built on
 * parley-core still use.
 * This class compiling is most of the test.
 */
@SuppressWarnings("deprecation")
public class TestApiCompatibility {

	@Test
	public void forceTlsVersionPropertyName() {
		assertEquals("ForceTlsVersion", SecureBaseObject.PROPERTY_FORCE_TLS_VERSION);
	}

	/** Like a protocol server in another project: overrides init() and calls super.init(). */
	static class InitOverride extends SecureBaseObject {
		boolean called;

		@Override
		protected void init() {
			super.init();
			called = true;
		}
	}

	@Test
	public void initCanBeOverridden() {
		InitOverride o = new InitOverride();
		o.init();
		assertTrue(o.called);
		assertFalse(o.isSecure(), "settings are still read lazily");
	}

	static class Worker extends BaseThread {
		@Override
		public void run() {
		}
	}

	@Test
	public void stopOnErrorAndErrorSleepTime() {
		Worker w = new Worker();
		assertFalse(w.isStopOnError());
		assertEquals(BaseThread.DEFAULT_ERROR_SLEEP_TIME, w.getErrorSleepTime());
		assertEquals(60000, BaseThread.DEFAULT_ERROR_SLEEP_TIME);
		w.setStopOnError(true);
		w.setErrorSleepTime(5);
		assertTrue(w.isStopOnError());
		assertEquals(5, w.getErrorSleepTime());
	}

	@Test
	public void loggerFormat() {
		assertNotNull(ParleyLogger.format);
		assertEquals(19 + 4, ParleyLogger.format.format(new Date(0)).length(), "MM-dd-yyyy HH:mm:ss.SSS");
	}

}
