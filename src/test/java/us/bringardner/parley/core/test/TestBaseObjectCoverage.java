package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.BaseObject;

/**
 * Covers the parts of BaseObject the original tests did not reach: the logging
 * convenience methods, property prefix handling, boolean properties, the cache
 * reset methods. Logging is covered by TestBaseObjectLogging.
 *
 * NOTE: test classes must not extend BaseObject directly (TestCore.testClassLoader
 * expects a fixed list of direct sub classes on the class path), so TestCoreBase is used.
 */
public class TestBaseObjectCoverage {

	/** Exposes the protected prefix setter. */
	static class PrefixUser extends TestCoreBase {
		void prefix(String prefix) {
			setPropertyPrefix(prefix);
		}
		String prefix() {
			return getPropertyPrefix();
		}
	}

	@Test
	public void testPropertyPrefix() {
		PrefixUser obj = new PrefixUser();
		assertEquals(PrefixUser.class.getName(), obj.prefix(), "The default prefix is the class name");

		System.setProperty("test.custom.prefix.Color", "red");
		System.setProperty("Color", "blue");
		try {
			obj.prefix("test.custom.prefix");
			assertEquals("test.custom.prefix", obj.prefix());
			assertEquals("test.custom.prefix.Color", obj.propertyName("Color"));
			assertEquals("red", obj.getProperty(obj.propertyName("Color")), "The caller builds the prefixed name");
			assertEquals("blue", obj.getProperty("Color"), "getProperty looks up the name it is given, as it is");

			obj.setSupportPrefixProperty(false);
			assertFalse(obj.isSupportPrefixProperty());
			assertNull(obj.prefix(), "No prefix when prefix support is off");
			assertEquals("Color", obj.propertyName("Color"), "No prefix in the name when prefix support is off");
			//  property files are still searched (TestCoreBase.properties defines Value02)
			assertEquals("2", obj.getProperty("Value02"));
		} finally {
			System.clearProperty("test.custom.prefix.Color");
			System.clearProperty("Color");
		}
	}

	@Test
	public void testBooleanProperty() {
		BaseObject obj = new BaseObject();
		System.setProperty("TestBooleanTrue", "  TRUE ");
		System.setProperty("TestBooleanOther", "yes");
		try {
			assertTrue(obj.getBooleanProperty("TestBooleanTrue", false));
			assertFalse(obj.getBooleanProperty("TestBooleanOther", true), "Only 'true' is true");
			assertTrue(obj.getBooleanProperty("TestBooleanMissing", true));
			assertFalse(obj.getBooleanProperty("TestBooleanMissing", false));
		} finally {
			System.clearProperty("TestBooleanTrue");
			System.clearProperty("TestBooleanOther");
		}
	}

	@Test
	public void testClearCaches() {
		TestCoreBase obj = new TestCoreBase();
		assertEquals("2", obj.getProperty("Value02"));
		BaseObject.clearPropertyCache();
		assertEquals("2", obj.getProperty("Value02"), "Properties are re-read after the cache is cleared");

	}
}
