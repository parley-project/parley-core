package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.parley.core.BaseObject;

/**
 * The expected behavior in one example. An author ships defaults a=1, b=2, c=3. Something higher in
 * the stack supplies b=10. The result is a=1, b=10, c=3 (resolved key by key, not file by file).
 * A system property still wins over both.
 */
public class TestPropertyOverrideExample {

	private static void assertAbc(BaseObject o, String a, String b, String c) {
		assertEquals(a, o.getProperty("a"), "a");
		assertEquals(b, o.getProperty("b"), "b");
		assertEquals(c, o.getProperty("c"), "c");
	}

	/** The library's class has the defaults, a sub class (higher in the stack) has its own file with b only. */
	@Test
	public void subClassFileOverridesOneKey() {
		assertAbc(new AbcBase(), "1", "2", "3");
		assertAbc(new AbcSub(), "1", "10", "3");
	}

	@Test
	public void systemPropertyStillWins() {
		System.setProperty(AbcBase.class.getName()+".b", "99");
		try {
			assertAbc(new AbcSub(), "1", "99", "3");
			assertAbc(new AbcBase(), "1", "99", "3");
		} finally {
			System.clearProperty(AbcBase.class.getName()+".b");
		}
	}

	private static Path dir(Path root, String name, String content) throws Exception {
		Path d = root.resolve(name);
		Path f = d.resolve("us/bringardner/parley/core/test/MergeTarget.properties");
		Files.createDirectories(f.getParent());
		Files.writeString(f, content);
		return d;
	}

	/** The values of a, b and c seen by an object whose class loader searches these directories in this order. */
	private static String[] load(URL... path) throws Exception {
		try(TestPropertyFileMerge.IsolatingLoader loader =
				new TestPropertyFileMerge.IsolatingLoader(path, TestPropertyOverrideExample.class.getClassLoader())) {
			BaseObject o = (BaseObject) loader.loadClass(MergeTarget.class.getName()).getDeclaredConstructor().newInstance();
			return new String[] {o.getProperty("a"), o.getProperty("b"), o.getProperty("c")};
		}
	}

	/** A file of the same name higher in the stack (earlier on the class path) with b only. */
	@Test
	public void sameNamedFileEarlierOnTheClassPathOverridesOneKey(@TempDir Path root) throws Exception {
		URL library = dir(root, "library", "a = 1\nb = 2\nc = 3\n").toUri().toURL();
		URL application = dir(root, "application", "b = 10\n").toUri().toURL();

		assertArrayEquals(new String[] {"1", "10", "3"}, load(application, library));

		//  The order of the class path decides, not the name of the package: library first, library wins.
		assertArrayEquals(new String[] {"1", "2", "3"}, load(library, application));
	}
}

class AbcBase extends TestCoreBase {
}

class AbcSub extends AbcBase {
}
