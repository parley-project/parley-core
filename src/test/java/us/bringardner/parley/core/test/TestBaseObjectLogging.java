package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.BaseObject;
import us.bringardner.parley.core.LogLevel;
import us.bringardner.parley.core.util.LogHelper;

/**
 * BaseObject's logging: each object's level (setLogLevel, else the parley.log.level settings), and
 * the System.Logger it writes to (parley-core's tests install {@link RecordingLoggerFinder}).
 * <p>
 * Each test uses classes of its own, so loggers (shared by name) and settings don't meet. They extend
 * TestCoreBase, not BaseObject: TestCore.testClassLoader counts BaseObject's direct sub classes.
 */
public class TestBaseObjectLogging {

	static class Logs extends TestCoreBase {
		System.Logger systemLogger() {
			return getSystemLogger();
		}

		RecordingLogger recorder() {
			return RecordingLoggerFinder.get(getClass().getName());
		}

		List<String> messages() {
			return recorder().messages;
		}
	}

	static class All extends Logs {}
	static class Warn extends Logs {}
	static class None extends Logs {}
	static class PerObject extends Logs {}
	static class FromSettings extends Logs {}
	static class Nearest extends Logs {}
	static class FromConfiguration extends Logs {}
	static class InstalledDecides extends Logs {}
	static class Invalid extends Logs {}
	static class Shared extends Logs {}

	private static void logEverything(BaseObject obj, Throwable err) {
		obj.logDebug("d1");
		obj.logDebug(() -> "d2");
		obj.logDebug("d3", err);
		obj.logInfo("i1");
		obj.logInfo(() -> "i2");
		obj.logInfo("i3", err);
		obj.logWarn("w1");
		obj.logWarn(() -> "w2");
		obj.logWarn("w3", err);
		obj.logError("e1");
		obj.logError(() -> "e2");
		obj.logError("e3", err);
	}

	@Test
	public void testNoLevelLetsEverythingThrough() {
		All obj = new All();
		assertNull(obj.getLogLevel());
		logEverything(obj, new IOException("boom"));
		assertEquals(Arrays.asList(
				"DEBUG d1", "DEBUG d2", "DEBUG d3 boom",
				"INFO i1", "INFO i2", "INFO i3 boom",
				"WARN w1", "WARN w2", "WARN w3 boom",
				"ERROR e1", "ERROR e2", "ERROR e3 boom"), obj.messages());
		assertTrue(obj.isDebugEnabled());
		assertTrue(obj.isInfoEnabled());
		assertTrue(obj.isWarnEnabled());
		assertTrue(obj.isErrorEnabled());
	}

	@Test
	public void testSetLogLevel() {
		Warn obj = new Warn();
		obj.setLogLevel(LogLevel.WARN);
		assertEquals(LogLevel.WARN, obj.getLogLevel());
		AtomicInteger built = new AtomicInteger();
		obj.logDebug(() -> "d" + built.incrementAndGet());
		obj.logInfo(() -> "i" + built.incrementAndGet());
		assertEquals(0, built.get(), "A message the level doesn't let through must not be built");
		logEverything(obj, null);
		assertEquals(Arrays.asList("WARN w1", "WARN w2", "WARN w3", "ERROR e1", "ERROR e2", "ERROR e3"), obj.messages());
		assertFalse(obj.isDebugEnabled());
		assertFalse(obj.isInfoEnabled());
		assertTrue(obj.isWarnEnabled());
		assertTrue(obj.isErrorEnabled());

		obj.messages().clear();
		obj.setLogLevel(null);
		assertNull(obj.getLogLevel());
		obj.logDebug("back");
		assertEquals(Collections.singletonList("DEBUG back"), obj.messages(), "null goes back to the settings (none: everything)");
	}

	@Test
	public void testNoneLogsNothing() {
		None obj = new None();
		obj.setLogLevel(LogLevel.NONE);
		logEverything(obj, null);
		obj.logError(() -> {
			throw new IllegalStateException("must not be called");
		});
		assertTrue(obj.messages().isEmpty());
		assertFalse(obj.isErrorEnabled());
	}

	@Test
	public void testLevelIsPerObject() {
		PerObject quiet = new PerObject();
		PerObject loud = new PerObject();
		quiet.setLogLevel(LogLevel.ERROR);
		loud.setLogLevel(LogLevel.DEBUG);
		quiet.logInfo("quiet");
		loud.logInfo("loud");
		assertEquals(Collections.singletonList("INFO loud"), loud.messages(), "Objects of one class share the logger, not the level");
		assertFalse(quiet.isInfoEnabled());
		assertTrue(loud.isInfoEnabled());
	}

	@Test
	public void testLevelFromSettings() {
		String key = BaseObject.PROPERTY_LOG_LEVEL + "." + BaseObject.levelName(FromSettings.class.getName());
		FromSettings obj = new FromSettings();
		obj.logDebug("before");
		System.setProperty(key, "warn");
		try {
			obj.logDebug("cached");
			BaseObject.refreshLogLevels();
			obj.logDebug("hidden");
			obj.logWarn("shown");
			assertNull(obj.getLogLevel(), "getLogLevel is only the level set on the object");

			//  The object's own level wins over the settings
			obj.setLogLevel(LogLevel.DEBUG);
			obj.logDebug("own");
		} finally {
			System.clearProperty(key);
			BaseObject.refreshLogLevels();
		}
		assertEquals(Arrays.asList("DEBUG before", "DEBUG cached", "WARN shown", "DEBUG own"), obj.messages(),
				"Settings are read once, and again after refreshLogLevels");
	}

	@Test
	public void testNearestSettingWins() {
		String name = BaseObject.levelName(Nearest.class.getName());
		assertEquals("core.test.TestBaseObjectLogging$Nearest", name);
		String all = BaseObject.PROPERTY_LOG_LEVEL;
		String pkg = BaseObject.PROPERTY_LOG_LEVEL + ".core.test";
		String own = BaseObject.PROPERTY_LOG_LEVEL + "." + name;
		Nearest obj = new Nearest();
		try {
			System.setProperty(all, "NONE");
			System.setProperty(pkg, "ERROR");
			BaseObject.refreshLogLevels();
			obj.logWarn("w1");
			obj.logError("e1");

			System.setProperty(own, "WARN");
			BaseObject.refreshLogLevels();
			obj.logWarn("w2");

			System.clearProperty(own);
			System.clearProperty(pkg);
			BaseObject.refreshLogLevels();
			obj.logError("e3");
		} finally {
			System.clearProperty(all);
			System.clearProperty(pkg);
			System.clearProperty(own);
			BaseObject.refreshLogLevels();
		}
		assertEquals(Arrays.asList("ERROR e1", "WARN w2"), obj.messages());
	}

	@Test
	public void testLevelFromConfiguration() {
		FromConfiguration server = new FromConfiguration();
		Properties config = new Properties();
		config.setProperty(BaseObject.PROPERTY_LOG_LEVEL + ".core.test", "ERROR");
		server.logInfo("system properties");
		server.setConfiguration(config);
		server.logInfo("configured");
		server.logError("configured");

		FromConfiguration helper = new FromConfiguration();
		helper.useConfigurationOf(server);
		helper.logInfo("helper");

		FromConfiguration other = new FromConfiguration();
		other.logInfo("other");
		assertEquals(Arrays.asList("INFO system properties", "ERROR configured", "INFO other"), server.messages(),
				"A configuration's level is read from it, and changing the configuration reads it again");
	}

	@Test
	public void testInstalledLoggerDecidesWhenNoLevelIsSet() {
		InstalledDecides obj = new InstalledDecides();
		obj.recorder().setLevel(LogLevel.WARN);
		try {
			obj.logInfo("dropped by the System.Logger");
			obj.logWarn("kept");
			assertFalse(obj.isInfoEnabled(), "isInfoEnabled asks the System.Logger too");

			//  The object's level can only hold messages back: DEBUG doesn't make the System.Logger write INFO
			obj.setLogLevel(LogLevel.DEBUG);
			obj.logInfo("still dropped");
			assertFalse(obj.isInfoEnabled());
		} finally {
			obj.recorder().setLevel(LogLevel.DEBUG);
		}
		assertEquals(Collections.singletonList("WARN kept"), obj.messages());
	}

	@Test
	public void testInvalidSettingIsIgnored() {
		String key = BaseObject.PROPERTY_LOG_LEVEL + "." + BaseObject.levelName(Invalid.class.getName());
		Invalid obj = new Invalid();
		System.setProperty(key, "LOUD");
		try {
			BaseObject.refreshLogLevels();
			obj.logDebug("through");
		} finally {
			System.clearProperty(key);
			BaseObject.refreshLogLevels();
		}
		assertEquals(Collections.singletonList("DEBUG through"), obj.messages(), "An invalid value sets no level");
	}

	@Test
	public void testLoggersAreSharedByName() {
		Shared a = new Shared();
		Shared b = new Shared();
		assertSame(a.systemLogger(), b.systemLogger(), "Objects of the same class share one System.Logger");
		assertEquals(Shared.class.getName(), a.systemLogger().getName());

		LogHelper h1 = new LogHelper("test.shared.helper.name");
		LogHelper h2 = new LogHelper("test.shared.helper.name");
		h1.logError("one");
		h2.logError("two");
		assertEquals(Arrays.asList("ERROR one", "ERROR two"), RecordingLoggerFinder.get("test.shared.helper.name").messages,
				"A LogHelper logs under its name");

		LogHelper forClass = new LogHelper(TestBaseObjectLogging.class);
		forClass.logError("class");
		assertEquals(Collections.singletonList("ERROR class"), RecordingLoggerFinder.get(TestBaseObjectLogging.class.getName()).messages);
	}

	@Test
	public void testLevelName() {
		assertEquals("pop3.server.Pop3Server", BaseObject.levelName("us.bringardner.parley.pop3.server.Pop3Server"));
		assertEquals("files.sftp.SftpFactory", BaseObject.levelName("us.bringardner.parley.files.sftp.SftpFactory"));
		assertEquals("fsh.Shell", BaseObject.levelName("us.bringardner.fsh.Shell"));
		assertEquals("com.example.Mailer", BaseObject.levelName(" com.example.Mailer "));
		assertEquals("", BaseObject.levelName(null));
	}

	@Test
	public void testLogLevel() {
		assertEquals(LogLevel.DEBUG, LogLevel.parse("debug", LogLevel.ERROR));
		assertEquals(LogLevel.WARN, LogLevel.parse(" Warning ", LogLevel.ERROR));
		assertEquals(LogLevel.ERROR, LogLevel.parse("FATAL", LogLevel.NONE));
		assertEquals(LogLevel.ERROR, LogLevel.parse("severe", LogLevel.NONE));
		assertEquals(LogLevel.NONE, LogLevel.parse("off", LogLevel.ERROR));
		for(String name : new String[] {"TRACE", "ALL", "FINE", "FINER", "FINEST"}) {
			assertEquals(LogLevel.DEBUG, LogLevel.parse(name, LogLevel.ERROR), name);
		}
		assertEquals(LogLevel.INFO, LogLevel.parse("not-a-level", LogLevel.INFO));
		assertEquals(LogLevel.INFO, LogLevel.parse(null, LogLevel.INFO));
		assertEquals(LogLevel.INFO, LogLevel.parse("  ", LogLevel.INFO));

		assertTrue(LogLevel.WARN.allows(LogLevel.ERROR));
		assertTrue(LogLevel.WARN.allows(LogLevel.WARN));
		assertFalse(LogLevel.WARN.allows(LogLevel.INFO));
		assertFalse(LogLevel.NONE.allows(LogLevel.ERROR));
		assertFalse(LogLevel.DEBUG.allows(LogLevel.NONE));

		for(LogLevel l : LogLevel.values()) {
			assertEquals(l, LogLevel.of(l.toSystemLevel()), l.name());
		}
		assertEquals(LogLevel.DEBUG, LogLevel.of(System.Logger.Level.TRACE));
		assertEquals(LogLevel.DEBUG, LogLevel.of(System.Logger.Level.ALL));
		assertEquals(LogLevel.NONE, LogLevel.of(null));
	}
}
