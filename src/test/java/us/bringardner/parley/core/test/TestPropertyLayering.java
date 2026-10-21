package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.BaseObject;

/**
 * Pins down the layering of {@link BaseObject#getProperty(String)}:
 * deployed defaults (class property files), overridden by sub classes, overridden by system properties.
 *
 * The name is looked up as it is given: nothing is added to it.
 */
public class TestPropertyLayering {

	private static final String BASE = PropBase.class.getName();
	private static final String SUB = PropSub.class.getName();

	@Test
	public void subClassFileOverridesSuperClassFile() {
		BaseObject.clearPropertyCache();
		assertEquals("sub", new PropSub().getProperty("layer"));
		assertEquals("base", new PropBase().getProperty("layer"));
	}

	@Test
	public void subClassInheritsSuperClassDefaults() {
		assertEquals("base", new PropSub().getProperty("baseOnly"));
	}

	@Test
	public void systemPropertyOverridesFiles() {
		System.setProperty("layer", "system");
		try {
			assertEquals("system", new PropSub().getProperty("layer"));
		} finally {
			System.clearProperty("layer");
		}
	}

	/**
	 * getProperty looks up exactly the name it is given. PropSub.properties has "<PropSub>.port=9000":
	 * that is found by its full name only, and a -Dport=1 is another property.
	 */
	@Test
	public void namesAreLookedUpAsGiven() {
		assertEquals("9000", new PropSub().getProperty(SUB+".port"));
		assertNull(new PropSub().getProperty("port"));
		System.setProperty("port", "1");
		try {
			assertEquals("9000", new PropSub().getProperty(SUB+".port"));
			assertEquals("1", new PropSub().getProperty("port"));
		} finally {
			System.clearProperty("port");
		}
	}

	/**
	 * PropBase.properties has "<PropBase>.timeout=5": a sub class's object finds it in its super
	 * class's file, by that name.
	 */
	@Test
	public void superClassFileIsSearchedByTheFullName() {
		assertEquals("5", new PropSub().getProperty(BASE+".timeout"));
		assertNull(new PropSub().getProperty("timeout"), "No prefix is added");
	}

	/** A system property is found by its full name, whatever object asks. */
	@Test
	public void systemPropertyByFullName() {
		System.setProperty(BASE+".sysKey", "x");
		try {
			assertEquals("x", new PropBase().getProperty(BASE+".sysKey"));
			assertEquals("x", new PropSub().getProperty(BASE+".sysKey"));
			assertNull(new PropSub().getProperty("sysKey"));
		} finally {
			System.clearProperty(BASE+".sysKey");
		}
	}

	/** propertyName builds the name with the object's prefix, its class name by default. */
	@Test
	public void propertyNameUsesThePrefix() {
		System.setProperty(SUB+".sysKey2", "y");
		try {
			PropSub sub = new PropSub();
			assertEquals(SUB+".sysKey2", sub.propertyName("sysKey2"));
			assertEquals("y", sub.getProperty(sub.propertyName("sysKey2")));
		} finally {
			System.clearProperty(SUB+".sysKey2");
		}
	}
}

//  Test classes must not extend BaseObject directly (see TestBaseObjectCoverage), so these go through TestCoreBase.
//  They are top level (not nested) because a nested class uses its outer class's properties file.
class PropBase extends TestCoreBase {
}

class PropSub extends PropBase {
}
