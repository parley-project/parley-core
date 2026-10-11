package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.BaseObject;

/**
 * Properties files with the same name in more than one place on the class path are merged:
 * the first one on the class path wins for each key, the later ones fill in what it doesn't define.
 */
public class TestPropertyFileMerge {

	/** Defines MergeTarget itself (so its resources come from this loader), everything else is the parent's. */
	static class IsolatingLoader extends URLClassLoader {
		IsolatingLoader(URL[] urls, ClassLoader parent) {
			super(urls, parent);
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if( !name.equals(MergeTarget.class.getName()) ) {
				return super.loadClass(name, resolve);
			}
			synchronized (getClassLoadingLock(name)) {
				Class<?> c = findLoadedClass(name);
				if( c == null ) {
					try(InputStream in = getParent().getResourceAsStream(name.replace('.', '/')+".class")) {
						byte[] b = in.readAllBytes();
						c = defineClass(name, b, 0, b.length);
					} catch(java.io.IOException e) {
						throw new ClassNotFoundException(name, e);
					}
				}
				return c;
			}
		}
	}

	private static void write(Path dir, String content) throws Exception {
		Path f = dir.resolve("us/bringardner/parley/core/test/MergeTarget.properties");
		Files.createDirectories(f.getParent());
		Files.writeString(f, content);
	}

	@Test
	public void firstFileWinsAndLaterFilesFillIn() throws Exception {
		Path root = Path.of("target", "merge-test").toAbsolutePath();
		if( Files.exists(root) ) {
			try(Stream<Path> s = Files.walk(root)) {
				s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
			}
		}
		Path first = root.resolve("first");
		Path second = root.resolve("second");
		write(first, "a = first\nb = first\n");
		write(second, "b = second\nc = second\n");

		try(IsolatingLoader loader = new IsolatingLoader(
				new URL[] {first.toUri().toURL(), second.toUri().toURL()}, getClass().getClassLoader())) {
			BaseObject o = (BaseObject) loader.loadClass(MergeTarget.class.getName()).getDeclaredConstructor().newInstance();
			assertEquals(loader, o.getClass().getClassLoader());

			assertEquals("first", o.getProperty("a"));
			assertEquals("first", o.getProperty("b"), "the first file on the class path wins");
			assertEquals("second", o.getProperty("c"), "a value only the later file defines is kept");
			assertNull(o.getProperty("d"));
		}
	}
}
