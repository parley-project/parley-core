package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.BaseObject;
import us.bringardner.parley.core.LogLevel;
import us.bringardner.parley.core.ParleyLogger;

/**
 * A logger's level comes from its name (the nearest parley.log.level setting), never from the
 * object that logs: two objects of one class log at the same level.
 */
public class TestLoggerLevelByName {

	private static final String SETTING = ParleyLogger.PROPERTY_LOG_LEVEL;

	@AfterEach
	public void clear() {
		System.clearProperty(SETTING);
		System.clearProperty(SETTING + ".core.test");
		System.clearProperty(SETTING + ".core.test.TestLoggerLevelByName$Quiet");
		ParleyLogger.closeLogFiles();
	}

	/** An object that logs under its class name */
	public static class Quiet extends BaseObject {
	}

	private static LogLevel levelOf(BaseObject o) {
		return o.getLogger().getLevel();
	}

	@Test
	public void theNearestSettingForTheNameWins() {
		System.setProperty(SETTING, "ERROR");
		System.setProperty(SETTING + ".core.test", "INFO");
		assertEquals(LogLevel.INFO, levelOf(new Quiet()));
		System.setProperty(SETTING + ".core.test.TestLoggerLevelByName$Quiet", "DEBUG");
		assertEquals(LogLevel.DEBUG, levelOf(new Quiet()));
	}

	@Test
	public void twoObjectsOfOneClassShareTheLevel() {
		System.setProperty(SETTING + ".core.test", "WARN");
		Quiet one = new Quiet();
		Quiet two = new Quiet();
		assertEquals(levelOf(one), levelOf(two));
		assertEquals(LogLevel.WARN, levelOf(one));
	}
}
