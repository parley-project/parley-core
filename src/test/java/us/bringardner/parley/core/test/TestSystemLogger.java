package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.BaseObject;
import us.bringardner.parley.core.ILogger;
import us.bringardner.parley.core.SystemLogger;

/**
 * SystemLogger through the JDK's default System.Logger provider, java.util.logging (the test class
 * path has no other provider): what arrives at java.util.logging, at which level.
 */
public class TestSystemLogger {

	/** Keeps every record given to it */
	private static final class Capture extends Handler {
		final List<LogRecord> records = new ArrayList<>();

		@Override
		public synchronized void publish(LogRecord r) {
			records.add(r);
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}
	}

	//  java.util.logging keeps loggers only while they're referenced, so hold on to them
	private final List<Logger> juls = new ArrayList<>();
	private final List<Capture> captures = new ArrayList<>();

	private Capture capture(String name, Level julLevel) {
		Logger jul = Logger.getLogger(name);
		juls.add(jul);
		jul.setUseParentHandlers(false);
		jul.setLevel(julLevel);
		Capture c = new Capture();
		c.setLevel(Level.ALL);
		jul.addHandler(c);
		captures.add(c);
		return c;
	}

	@AfterEach
	public void tearDown() {
		for (int i = 0; i < juls.size(); i++) {
			juls.get(i).removeHandler(captures.get(i));
			juls.get(i).setLevel(null);
			juls.get(i).setUseParentHandlers(true);
		}
	}

	private static SystemLogger logger(String name) {
		SystemLogger ret = new SystemLogger();
		ret.init(name);
		return ret;
	}

	@Test
	public void testLevelsArriveMapped() {
		String name = "test.systemlogger.levels";
		Capture c = capture(name, Level.ALL);
		SystemLogger log = logger(name);

		log.error("e");
		log.warn("w");
		log.info("i");
		log.debug("d");

		assertEquals(4, c.records.size());
		assertEquals(Level.SEVERE, c.records.get(0).getLevel());
		assertEquals(Level.WARNING, c.records.get(1).getLevel());
		assertEquals(Level.INFO, c.records.get(2).getLevel());
		//  The JDK gives System.Logger's DEBUG to java.util.logging as FINE
		assertEquals(Level.FINE, c.records.get(3).getLevel());
		assertEquals("e", c.records.get(0).getMessage());
		assertEquals(name, c.records.get(0).getLoggerName());
	}

	@Test
	public void testErrorPassedOn() {
		String name = "test.systemlogger.error";
		Capture c = capture(name, Level.ALL);
		SystemLogger log = logger(name);
		Exception e = new Exception("boom");

		log.error("e", e);
		log.warn("w", e);
		log.info("i", e);
		log.debug("d", e);

		assertEquals(4, c.records.size());
		for (LogRecord r : c.records) {
			assertSame(e, r.getThrown());
		}
	}

	@Test
	public void testLoggingSystemDecidesWithoutOwnLevel() {
		String name = "test.systemlogger.backend";
		Capture c = capture(name, Level.WARNING);
		SystemLogger log = logger(name);

		assertEquals(ILogger.Level.WARN, log.getLevel());
		assertTrue(log.isErrorEnabled());
		assertTrue(log.isWarnEnabled());
		assertFalse(log.isInfoEnabled());
		assertFalse(log.isDebugEnabled());

		log.info("not logged");
		log.debug("not logged");
		log.warn("logged");
		assertEquals(1, c.records.size());
		assertEquals("logged", c.records.get(0).getMessage());

		Logger.getLogger(name).setLevel(Level.OFF);
		assertEquals(ILogger.Level.NONE, log.getLevel());
		Logger.getLogger(name).setLevel(Level.FINE);
		assertEquals(ILogger.Level.DEBUG, log.getLevel());
	}

	@Test
	public void testOwnLevelInFrontOfLoggingSystem() {
		String name = "test.systemlogger.own";
		Capture c = capture(name, Level.ALL);
		SystemLogger log = logger(name);

		log.setLevel(ILogger.Level.WARN);
		assertEquals(ILogger.Level.WARN, log.getLevel());
		assertFalse(log.isInfoEnabled());
		log.info("not logged");
		log.debug("not logged");
		log.warn("logged");
		log.error("logged");
		assertEquals(2, c.records.size());

		//  Both must allow a message: the logging system still stops what it doesn't log
		log.setLevel(ILogger.Level.DEBUG);
		Logger.getLogger(name).setLevel(Level.INFO);
		assertFalse(log.isDebugEnabled());
		log.debug("not logged");
		assertEquals(2, c.records.size());

		log.setLevel(ILogger.Level.NONE);
		assertFalse(log.isErrorEnabled());
		log.error("not logged");
		assertEquals(2, c.records.size());

		//  null hands it back to the logging system
		log.setLevel(null);
		assertEquals(ILogger.Level.INFO, log.getLevel());
		log.info("logged");
		assertEquals(3, c.records.size());
	}

	@Test
	public void testLazyMessagesOnlyBuiltWhenEnabled() {
		String name = "test.systemlogger.lazy";
		Capture c = capture(name, Level.INFO);
		SystemLogger log = logger(name);
		int[] built = {0};

		log.debug(() -> {
			built[0]++;
			return "d";
		});
		log.info(() -> {
			built[0]++;
			return "i";
		});
		assertEquals(1, built[0]);
		assertEquals(1, c.records.size());
	}

	@Test
	public void testBracesStayInTheMessage() {
		String name = "test.systemlogger.braces";
		Capture c = capture(name, Level.ALL);
		SystemLogger log = logger(name);

		log.info("removed {0} from {1}");
		assertEquals("removed {0} from {1}", c.records.get(0).getMessage());
		assertEquals(0, c.records.get(0).getParameters() == null ? 0 : c.records.get(0).getParameters().length);
	}

	@Test
	public void testChosenWithParleyCoreLogger() throws Exception {
		String name = "test.systemlogger.chosen";
		Capture c = capture(name, Level.ALL);
		Field field = BaseObject.class.getDeclaredField("loggerClass");
		field.setAccessible(true);
		Object original = field.get(null);
		String originalProperty = System.getProperty(BaseObject.PROPERTY_LOGGER);
		try {
			field.set(null, null);
			System.setProperty(BaseObject.PROPERTY_LOGGER, SystemLogger.class.getName());
			ILogger logger = BaseObject.findLogger(name);
			assertEquals(SystemLogger.class, logger.getClass());
			logger.warn("through BaseObject");
			assertEquals(1, c.records.size());
			assertEquals(name, c.records.get(0).getLoggerName());
		} finally {
			field.set(null, original);
			if( originalProperty == null ) {
				System.clearProperty(BaseObject.PROPERTY_LOGGER);
			} else {
				System.setProperty(BaseObject.PROPERTY_LOGGER, originalProperty);
			}
			BaseObject.clearLoggerCache();
		}
	}

	@Test
	public void testDefaultName() {
		SystemLogger log = new SystemLogger();
		assertEquals(SystemLogger.class.getName(), log.getLogger().getName());
	}
}
