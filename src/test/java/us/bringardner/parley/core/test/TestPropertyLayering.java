package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.BaseObject;

/**
 * Pins down the layering of {@link BaseObject#getProperty(String)}:
 * deployed defaults (class property files), overridden by sub classes, overridden by system properties.
 *
 * A prefix is the instance's prefix or the class name of any level of its hierarchy.
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
	 * Characterization: the unprefixed system property is global. A -Dport=1 beats the
	 * class-specific PropSub.port=9000 in the file, and would also hit every other class that asks for "port".
	 */
	@Test
	public void plainSystemPropertyBeatsClassSpecificFileValue() {
		assertEquals("9000", new PropSub().getProperty("port"));
		System.setProperty("port", "1");
		try {
			assertEquals("1", new PropSub().getProperty("port"));
		} finally {
			System.clearProperty("port");
		}
	}

	/**
	 * PropBase.properties has "<PropBase>.timeout=5". The prefix used for the search is always the
	 * concrete class (PropSub), so the super class's own prefixed key is never matched for a sub class instance.
	 */
	@Test
	public void superClassPrefixedKeyInFileAppliesToSubClass() {
		assertEquals("5", new PropSub().getProperty("timeout"));
	}

	/**
	 * Same thing for system properties. -D<PropBase>.sysKey=x configures PropBase
	 * but not its sub classes.
	 */
	@Test
	public void superClassPrefixedSystemPropertyAppliesToSubClass() {
		System.setProperty(BASE+".sysKey", "x");
		try {
			assertEquals("x", new PropBase().getProperty("sysKey"));
			assertEquals("x", new PropSub().getProperty("sysKey"));
		} finally {
			System.clearProperty(BASE+".sysKey");
		}
	}

	/**
	 * Control for the gap above: the concrete class's own prefix does work.
	 */
	@Test
	public void concreteClassPrefixedSystemPropertyWorks() {
		System.setProperty(SUB+".sysKey2", "y");
		try {
			assertEquals("y", new PropSub().getProperty("sysKey2"));
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
