package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.BaseObject;

/**
 * Property lookups use strings worked out once per class (and per property name). These tests make sure
 * that doesn't change what is found: many names, propertyName with a changed prefix or none, a
 * getPropertyClass() override and a cleared cache.
 */
public class TestPropertyLookupPlan {

	public static class A extends BaseObject {
	}

	public static class B extends A {
		public void prefix(String prefix) {
			setPropertyPrefix(prefix);
		}
	}

	/** Searches the properties of B, whatever its own class is */
	public static class SearchesB extends BaseObject {
		@Override
		protected Class<?> getPropertyClass() {
			return B.class;
		}
	}

	private static final String A_NAME = A.class.getName();
	private static final String B_NAME = B.class.getName();

	@Test
	public void moreNamesThanAreKeptStillResolve() {
		B b = new B();
		int count = 400;
		for(int i = 0; i < count; i++) {
			System.setProperty("plan.n"+i, "v"+i);
		}
		try {
			//  twice: the first pass fills the per class cache (and stops at its limit), the second uses it
			for(int pass = 0; pass < 2; pass++) {
				for(int i = 0; i < count; i++) {
					assertEquals("v"+i, b.getProperty("plan.n"+i), "name "+i+" pass "+pass);
				}
			}
			assertNull(b.getProperty("plan.n"+count));
		} finally {
			for(int i = 0; i < count; i++) {
				System.clearProperty("plan.n"+i);
			}
		}
	}

	@Test
	public void namesAreNotPrefixed() {
		System.setProperty(A_NAME+".timeout", "5");
		try {
			assertNull(new B().getProperty("timeout"), "getProperty adds nothing to the name");
			assertEquals("5", new B().getProperty(A_NAME+".timeout"));
			assertEquals(B_NAME+".timeout", new B().propertyName("timeout"));
		} finally {
			System.clearProperty(A_NAME+".timeout");
		}
	}

	@Test
	public void changedPrefixChangesPropertyName() {
		B b = new B();
		b.prefix("custom");
		System.setProperty("custom.size", "1");
		try {
			assertEquals("custom.size", b.propertyName("size"));
			assertEquals("1", b.getProperty(b.propertyName("size")));
			//  another object of the same class is not affected by this one's prefix
			assertEquals(B_NAME+".size", new B().propertyName("size"));
		} finally {
			System.clearProperty("custom.size");
		}
	}

	@Test
	public void noPrefixSearchesOnlyTheName() {
		B b = new B();
		b.setSupportPrefixProperty(false);
		assertEquals("mode", b.propertyName("mode"));
		System.setProperty(B_NAME+".mode", "prefixed");
		try {
			assertNull(b.getProperty(b.propertyName("mode")));
			System.setProperty("mode", "plain");
			assertEquals("plain", b.getProperty("mode"));
		} finally {
			System.clearProperty(B_NAME+".mode");
			System.clearProperty("mode");
		}
		assertEquals("dflt", b.getProperty("mode", "dflt"));
	}

	@Test
	public void getPropertyClassOverrideSearchesThatClass() {
		SearchesB o = new SearchesB();
		System.setProperty(A_NAME+".where", "super of B");
		try {
			//  the name is looked up as it is given
			assertEquals("super of B", o.getProperty(A_NAME+".where"));
			assertNull(o.getProperty("where"));
		} finally {
			System.clearProperty(A_NAME+".where");
		}
		assertNull(o.getProperty("where"));
	}

	@Test
	public void clearedCacheGivesTheSameAnswers() {
		BaseObject.clearPropertyCache();
		String before = new PropSub().getProperty("layer");
		BaseObject.clearPropertyCache();
		assertEquals(before, new PropSub().getProperty("layer"));
		assertEquals("sub", before);
	}

	@Test
	public void defaultIsReturnedOnlyWhenNothingIsFound() {
		B b = new B();
		assertEquals("d", b.getProperty("plan.nothing", "d"));
		assertNull(b.getProperty("plan.nothing"));
		System.setProperty("plan.something", "x");
		try {
			assertEquals("x", b.getProperty("plan.something", "d"));
		} finally {
			System.clearProperty("plan.something");
		}
	}
}
