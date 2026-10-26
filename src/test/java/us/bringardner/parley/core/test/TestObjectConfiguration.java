package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.BaseObject;

/**
 * An object given its own configuration ({@link BaseObject#setConfiguration(Properties)}) reads its
 * settings from it, with the class files as defaults, and never from the system properties: two
 * objects in one JVM can have different values for one name.
 */
public class TestObjectConfiguration {

	private static Properties props(String... kv) {
		Properties p = new Properties();
		for (int i = 0; i < kv.length; i += 2) {
			p.setProperty(kv[i], kv[i + 1]);
		}
		return p;
	}

	@Test
	public void twoObjectsOneNameTwoValues() {
		PropSub a = new PropSub();
		PropSub b = new PropSub();
		a.setConfiguration(props("parley.test.root", "/a"));
		b.setConfiguration(props("parley.test.root", "/b"));
		assertEquals("/a", a.getProperty("parley.test.root"));
		assertEquals("/b", b.getProperty("parley.test.root"));
	}

	@Test
	public void configurationIgnoresSystemProperties() {
		System.setProperty("parley.test.onlySystem", "system");
		System.setProperty("layer", "system");
		try {
			PropSub o = new PropSub();
			o.setConfiguration(props());
			assertNull(o.getProperty("parley.test.onlySystem"));
			// the class files are still the defaults
			assertEquals("sub", o.getProperty("layer"));
			assertEquals("base", o.getProperty("baseOnly"));
			// without a configuration the system properties win, as before
			assertEquals("system", new PropSub().getProperty("layer"));
		} finally {
			System.clearProperty("parley.test.onlySystem");
			System.clearProperty("layer");
		}
	}

	@Test
	public void configurationWinsOverClassFiles() {
		PropSub o = new PropSub();
		o.setConfiguration(props("layer", "mine"));
		assertEquals("mine", o.getProperty("layer"));
		assertEquals(7, new PropSub() {
			{
				setConfiguration(props("n", "7"));
			}
		}.getIntProperty("n", 0));
	}

	@Test
	public void useConfigurationOf() {
		PropSub server = new PropSub();
		Properties p = props("x", "1");
		server.setConfiguration(p);
		PropBase helper = new PropBase();
		helper.useConfigurationOf(server);
		assertSame(p, helper.getConfiguration());
		assertEquals("1", helper.getProperty("x"));
		helper.setConfiguration(null);
		assertNull(helper.getConfiguration());
	}

	@Test
	public void makeWithReachesTheConstructor() throws Exception {
		class ReadsInConstructor extends PropSub {
			final String seen = getProperty("parley.test.root");
		}
		ReadsInConstructor o = BaseObject.makeWith(props("parley.test.root", "/made"), ReadsInConstructor::new);
		assertEquals("/made", o.seen);
		assertNull(new PropSub().getConfiguration(), "only while makeWith runs");
		assertNull(BaseObject.makeWith(null, PropSub::new).getConfiguration());
	}

	@Test
	public void makeWithIsUndoneWhenTheConstructorThrowsAndWhenNested() throws Exception {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> BaseObject.makeWith(props("a", "1"), () -> {
			throw new IllegalStateException("can't make it");
		}));
		assertNull(new PropSub().getConfiguration(), "reset after a failure");
		Properties outer = props("x", "outer");
		Properties inner = props("x", "inner");
		PropSub[] seen = new PropSub[3];
		BaseObject.makeWith(outer, () -> {
			seen[0] = new PropSub();
			seen[1] = BaseObject.makeWith(inner, PropSub::new);
			seen[2] = new PropSub();
			return null;
		});
		assertSame(outer, seen[0].getConfiguration());
		assertSame(inner, seen[1].getConfiguration());
		assertSame(outer, seen[2].getConfiguration(), "the outer one again after the inner call");
		assertNull(new PropSub().getConfiguration());
	}
}
