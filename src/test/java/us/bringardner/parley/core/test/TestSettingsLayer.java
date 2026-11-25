package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.BaseObject;

/**
 * The order of {@link BaseObject#getProperty}: system properties, then the settings a server
 * added from its file, then the defaults in the class's properties files, then the default in code.
 */
public class TestSettingsLayer {

	private static final String NAME = "parley.test.settingsLayer";

	@AfterEach
	void cleanUp() {
		BaseObject.clearSettings();
		System.clearProperty(NAME);
	}

	private static Properties one(String value) {
		Properties p = new Properties();
		p.setProperty(NAME, value);
		return p;
	}

	@Test
	void unsetIsTheDefaultInCode() {
		assertNull(new BaseObject().getProperty(NAME));
		assertEquals("dflt", new BaseObject().getProperty(NAME, "dflt"));
	}

	@Test
	void settingsWinOverTheDefaultInCode() {
		BaseObject.addSettings(one("fromFile"));
		assertEquals("fromFile", new BaseObject().getProperty(NAME, "dflt"));
	}

	@Test
	void systemPropertiesWinOverSettings() {
		BaseObject.addSettings(one("fromFile"));
		System.setProperty(NAME, "fromD");
		assertEquals("fromD", new BaseObject().getProperty(NAME, "dflt"));
	}

	@Test
	void addedAgainTakesTheNewValueAndClearForgets() {
		BaseObject.addSettings(one("first"));
		BaseObject.addSettings(one("second"));
		assertEquals("second", new BaseObject().getProperty(NAME));
		BaseObject.clearSettings();
		assertNull(new BaseObject().getProperty(NAME));
	}
}
