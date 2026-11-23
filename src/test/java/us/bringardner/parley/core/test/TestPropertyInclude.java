package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * The parley.include setting through BaseObject.getProperty: the files of the class path next to the file that
 * names them, and a file only a service (MemoryIncludeResolver) knows.
 */
public class TestPropertyInclude {

	@Test
	public void anIncludedClassPathFileIsReadWhereTheSettingStands() {
		IncludeTarget t = new IncludeTarget();
		assertEquals("main", t.getProperty("name"));
		assertEquals("more", t.getProperty("setting.c"));
		assertEquals("main", t.getProperty("setting.b"), "the line after the include overrides it");
	}

	@Test
	public void anIncludeOfAnIncludeIsFoundNextToTheFileThatNamesIt() {
		assertEquals("deeper", new IncludeTarget().getProperty("setting.d"));
	}

	@Test
	public void aServiceResolverFindsAFileTheClassPathDoesNot() {
		IncludeTarget t = new IncludeTarget();
		assertEquals("memory", t.getProperty("setting.e"));
		assertEquals("memory", t.getProperty("setting.a"), "the last include overrides the first");
	}

	@Test
	public void theIncludeSettingIsNotASetting() {
		assertNull(new IncludeTarget().getProperty("parley.include"));
	}

}
