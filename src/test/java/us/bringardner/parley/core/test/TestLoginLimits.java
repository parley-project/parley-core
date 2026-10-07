package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.util.AbstractCoreServer;

/**
 * The login limits every server shares: defaults, properties, setters and a server's own defaults.
 */
public class TestLoginLimits {

	static class Server extends AbstractCoreServer {
		@Override
		public void run() {
		}
	}

	/** A protocol whose clients fail logins in normal use (like SSH) */
	static class LenientServer extends AbstractCoreServer {
		@Override
		public void run() {
		}

		@Override
		protected int getDefaultMaxLoginAttempts() {
			return 6;
		}

		@Override
		protected int getDefaultLoginFailureDelay() {
			return 250;
		}

		@Override
		protected int getDefaultLoginTimeLimit() {
			return 120000;
		}
	}

	@Test
	public void defaults() {
		Server s = new Server();
		assertEquals(AbstractCoreServer.DEFAULT_LOGIN_FAILURE_DELAY, s.getLoginFailureDelay());
		assertEquals(AbstractCoreServer.DEFAULT_MAX_LOGIN_ATTEMPTS, s.getMaxLoginAttempts());
		assertEquals(0, s.getLoginTimeLimit(), "no limit");
		assertFalse(s.isTooManyLoginFailures(2));
		assertTrue(s.isTooManyLoginFailures(3));
	}

	@Test
	public void aServersOwnDefaults() {
		LenientServer s = new LenientServer();
		assertEquals(250, s.getLoginFailureDelay());
		assertEquals(6, s.getMaxLoginAttempts());
		assertEquals(120000, s.getLoginTimeLimit());
		assertFalse(s.isTooManyLoginFailures(5));
	}

	@Test
	public void properties() {
		String prefix = LenientServer.class.getName()+".";
		System.setProperty(prefix+AbstractCoreServer.PROPERTY_LOGIN_FAILURE_DELAY, "10");
		System.setProperty(prefix+AbstractCoreServer.PROPERTY_MAX_LOGIN_ATTEMPTS, "2");
		System.setProperty(prefix+AbstractCoreServer.PROPERTY_LOGIN_TIME_LIMIT, "5000");
		try {
			LenientServer s = new LenientServer();
			assertEquals(10, s.getLoginFailureDelay());
			assertEquals(2, s.getMaxLoginAttempts());
			assertEquals(5000, s.getLoginTimeLimit());
			assertTrue(s.isTooManyLoginFailures(2));
		} finally {
			System.clearProperty(prefix+AbstractCoreServer.PROPERTY_LOGIN_FAILURE_DELAY);
			System.clearProperty(prefix+AbstractCoreServer.PROPERTY_MAX_LOGIN_ATTEMPTS);
			System.clearProperty(prefix+AbstractCoreServer.PROPERTY_LOGIN_TIME_LIMIT);
		}
	}

	@Test
	public void setters() {
		Server s = new Server();
		s.setLoginFailureDelay(0);
		s.setMaxLoginAttempts(1);
		s.setLoginTimeLimit(30000);
		assertEquals(0, s.getLoginFailureDelay());
		assertTrue(s.isTooManyLoginFailures(1));
		assertEquals(30000, s.getLoginTimeLimit());
		assertThrows(IllegalArgumentException.class, () -> s.setLoginFailureDelay(-1));
		assertThrows(IllegalArgumentException.class, () -> s.setMaxLoginAttempts(0));
		assertThrows(IllegalArgumentException.class, () -> s.setLoginTimeLimit(-1));
	}
}
