package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.parley.core.BaseObject;
import us.bringardner.parley.core.ILogger;
import us.bringardner.parley.core.LogLevel;
import us.bringardner.parley.core.ParleyLogger;

/**
 * A logger reads its settings where the object that made it does: two servers in one JVM
 * can have different levels and log files, however and whenever their loggers are made.
 */
public class TestLoggerConfiguration {

	@AfterEach
	public void closeFiles() {
		ParleyLogger.closeLogFiles();
	}

	private static Properties settings(String... nameValue) {
		Properties p = new Properties();
		for (int i = 0; i < nameValue.length; i += 2) {
			p.setProperty(nameValue[i], nameValue[i + 1]);
		}
		return p;
	}

	private static BaseObject make(Properties configuration) throws Exception {
		return BaseObject.makeWith(configuration, BaseObject::new);
	}

	@Test
	public void testTwoServersHaveTheirOwnLevels() throws Exception {
		BaseObject one = make(settings(ParleyLogger.PROPERTY_LOG_LEVEL, "DEBUG"));
		BaseObject two = make(settings(ParleyLogger.PROPERTY_LOG_LEVEL, "ERROR"));
		assertEquals(LogLevel.DEBUG, one.getLogLevel());
		assertEquals(LogLevel.ERROR, two.getLogLevel());
	}

	@Test
	public void testLoggerMadeOnAnotherThreadUsesTheOwnersConfiguration() throws Exception {
		BaseObject server = make(settings(ParleyLogger.PROPERTY_LOG_LEVEL, "WARN"));
		AtomicReference<LogLevel> level = new AtomicReference<>();
		Thread t = new Thread(() -> level.set(server.getLogLevel()));
		t.start();
		t.join();
		assertEquals(LogLevel.WARN, level.get());
	}

	@Test
	public void testTwoServersHaveTheirOwnLogFiles(@TempDir Path dir) throws Exception {
		File a = dir.resolve("a.log").toFile();
		File b = dir.resolve("b.log").toFile();
		BaseObject one = make(settings(ParleyLogger.PROPERTY_LOG_LEVEL, "INFO", ParleyLogger.PROPERTY_LOG_FILE, a.getPath()));
		BaseObject two = make(settings(ParleyLogger.PROPERTY_LOG_LEVEL, "INFO", ParleyLogger.PROPERTY_LOG_FILE, b.getPath()));
		one.logInfo("from server one");
		two.logInfo("from server two");
		String ta = new String(Files.readAllBytes(a.toPath()), StandardCharsets.UTF_8);
		String tb = new String(Files.readAllBytes(b.toPath()), StandardCharsets.UTF_8);
		assertTrue(ta.contains("from server one") && !ta.contains("from server two"), ta);
		assertTrue(tb.contains("from server two") && !tb.contains("from server one"), tb);
	}

	@Test
	public void testSettingTheConfigurationLaterMakesTheLoggerAgain() throws Exception {
		BaseObject server = new BaseObject();
		ILogger before = server.getLogger();
		server.setConfiguration(settings(ParleyLogger.PROPERTY_LOG_LEVEL, "DEBUG"));
		assertEquals(LogLevel.DEBUG, server.getLogLevel());
		assertFalse(before == server.getLogger(), "A new logger reads the new configuration");
	}

	@Test
	public void testUseConfigurationOfMakesTheLoggerAgain() throws Exception {
		BaseObject owner = make(settings(ParleyLogger.PROPERTY_LOG_LEVEL, "DEBUG"));
		BaseObject helper = new BaseObject();
		helper.getLogger();
		helper.useConfigurationOf(owner);
		assertEquals(LogLevel.DEBUG, helper.getLogLevel());
	}

	@Test
	public void testALoggerGivenWithSetLoggerIsKept() throws Exception {
		BaseObject server = new BaseObject();
		ILogger mine = new RecordingLogger();
		server.setLogger(mine);
		server.setConfiguration(settings(ParleyLogger.PROPERTY_LOG_LEVEL, "DEBUG"));
		assertSame(mine, server.getLogger());
	}

	@Test
	public void testNoConfigurationReadsTheSystemProperties() throws Exception {
		String before = System.getProperty(ParleyLogger.PROPERTY_LOG_LEVEL);
		System.setProperty(ParleyLogger.PROPERTY_LOG_LEVEL, "INFO");
		try {
			assertEquals(LogLevel.INFO, new BaseObject().getLogLevel());
		} finally {
			if (before == null) {
				System.clearProperty(ParleyLogger.PROPERTY_LOG_LEVEL);
			} else {
				System.setProperty(ParleyLogger.PROPERTY_LOG_LEVEL, before);
			}
		}
	}
}
