package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.BaseObject;

/**
 * Property lookups use strings worked out once per class (and per property name). These tests make sure
 * that doesn't change what is found: more names than are kept, a changed prefix, no prefix, a
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
	public void superClassNameIsAPrefixForSubClassObjects() {
		System.setProperty(A_NAME+".timeout", "5");
		try {
			assertEquals("5", new B().getProperty("timeout"));
			System.setProperty(B_NAME+".timeout", "7");
			assertEquals("7", new B().getProperty("timeout"), "the object's own class name wins over a super class's");
		} finally {
			System.clearProperty(A_NAME+".timeout");
			System.clearProperty(B_NAME+".timeout");
		}
	}

	@Test
	public void changedPrefixWinsAndClassNamesStillApply() {
		B b = new B();
		b.prefix("custom");
		System.setProperty("custom.size", "1");
		System.setProperty(A_NAME+".size", "2");
		try {
			assertEquals("1", b.getProperty("size"));
			System.clearProperty("custom.size");
			assertEquals("2", b.getProperty("size"), "without the custom prefixed value the class names are searched");
		} finally {
			System.clearProperty("custom.size");
			System.clearProperty(A_NAME+".size");
		}
		//  another object of the same class is not affected by this one's prefix
		System.setProperty(B_NAME+".size", "3");
		try {
			assertEquals("3", new B().getProperty("size"));
		} finally {
			System.clearProperty(B_NAME+".size");
		}
	}

	@Test
	public void noPrefixSearchesOnlyTheName() {
		B b = new B();
		b.setSupportPrefixProperty(false);
		System.setProperty(B_NAME+".mode", "prefixed");
		try {
			assertNull(b.getProperty("mode"));
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
			//  the search starts at B, so A's name is one of its prefixes
			assertEquals("super of B", o.getProperty("where"));
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
