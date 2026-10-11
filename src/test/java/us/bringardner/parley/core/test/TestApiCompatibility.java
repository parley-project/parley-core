package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.BaseThread;
import us.bringardner.parley.core.SecureBaseObject;

/**
 * Public API that the libraries built on parley-core use, so it can't be changed or removed by accident.
 */
public class TestApiCompatibility {

	@Test
	public void forceTlsVersionPropertyName() {
		assertEquals("ForceTlsVersion", SecureBaseObject.PROPERTY_FORCE_TLS_VERSION);
	}

	static class Worker extends BaseThread {
		@Override
		public void run() {
		}
	}

	@Test
	public void stopOnError() {
		Worker w = new Worker();
		assertFalse(w.isStopOnError());
		w.setStopOnError(true);
		assertTrue(w.isStopOnError());
	}

}
