package us.bringardner.parley.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The plugin folders, their order and settings, and loading plugin jars. */
public class TestPlugins {

	@TempDir
	Path tmp;

	@AfterEach
	public void forget() {
		Plugins.reset();
	}

	private static Properties props(String ... kv) {
		Properties p = new Properties();
		for(int i = 0; i < kv.length; i += 2) {
			p.setProperty(kv[i], kv[i+1]);
		}
		return p;
	}

	private static List<String> paths(PluginFolders f) {
		List<String> ret = new ArrayList<>();
		for(PluginFolders.Folder d : f.folders()) {
			ret.add(d.dir.getPath().replace('\\', '/')+" "+d.kind);
		}
		return ret;
	}

	@Test
	public void defaultFolders() {
		PluginFolders f = new PluginFolders("fsh", false, null, null, null, false, "/home/u", null);
		assertEquals(List.of("/usr/local/lib/parley/fsh/plugins SYSTEM_APP", "/usr/local/lib/parley/plugins SYSTEM_SHARED",
				"/home/u/.parley/fsh/plugins USER_APP", "/home/u/.parley/plugins USER_SHARED"), paths(f));
		// restricted: no user folders
		f = new PluginFolders("fsh", true, null, null, null, false, "/home/u", null);
		assertEquals(List.of("/usr/local/lib/parley/fsh/plugins SYSTEM_APP", "/usr/local/lib/parley/plugins SYSTEM_SHARED"), paths(f));
		// Windows: %ProgramData%\Parley
		Map<String, String> env = new HashMap<>();
		env.put("ProgramData", "D:/Data");
		f = new PluginFolders("fsh", false, null, null, env, true, "C:/Users/u", null);
		assertEquals("D:/Data/Parley/fsh/plugins SYSTEM_APP", paths(f).get(0));
	}

	@Test
	public void settings() {
		// -D moves both roots when the administrator's file says nothing
		Properties sys = props(PluginFolders.PROP_SYSTEM_ROOT, "/opt/p", PluginFolders.PROP_USER_ROOT, "/u/p");
		PluginFolders f = new PluginFolders("fsh", false, null, sys, null, false, "/home/u", null);
		assertEquals(List.of("/opt/p/fsh/plugins SYSTEM_APP", "/opt/p/plugins SYSTEM_SHARED",
				"/u/p/fsh/plugins USER_APP", "/u/p/plugins USER_SHARED"), paths(f));
		// the administrator's systemRoot wins over -D; the user's userRoot wins over the administrator's
		Properties admin = props(PluginFolders.PROP_SYSTEM_ROOT, "/srv/parley", PluginFolders.PROP_USER_ROOT, "/admin/default");
		f = new PluginFolders("fsh", false, admin, sys, null, false, "/home/u", null);
		assertEquals("/srv/parley/fsh/plugins SYSTEM_APP", paths(f).get(0));
		assertEquals("/u/p/plugins USER_SHARED", paths(f).get(3));
		// restricted: the administrator's file only
		f = new PluginFolders("fsh", true, admin, sys, null, false, "/home/u", null);
		assertEquals(List.of("/srv/parley/fsh/plugins SYSTEM_APP", "/srv/parley/plugins SYSTEM_SHARED"), paths(f));
		// a path setting replaces all four; the application's own before the shared one
		String sep = File.pathSeparator;
		f = new PluginFolders("fsh", false, null, props(PluginFolders.PROP_PATH, "/a"+sep+"/b", "parley.fsh.plugins.path", "/c"), null, false, "/home/u", null);
		assertEquals(List.of("/c PATH"), paths(f));
		f = new PluginFolders("other", false, null, props(PluginFolders.PROP_PATH, "/a"+sep+"/b"), null, false, "/home/u", null);
		assertEquals(List.of("/a PATH", "/b PATH"), paths(f));
	}

	@Test
	public void adminFile() throws IOException {
		Map<String, String> env = new HashMap<>();
		assertEquals(new File("/etc/parley/parley.properties"), PluginFolders.adminFile(env, false));
		File conf = tmp.resolve("my.properties").toFile();
		Files.write(conf.toPath(), "parley.plugins.systemRoot=/from/conf\n".getBytes(StandardCharsets.UTF_8));
		env.put(PluginFolders.ENV_CONF, conf.getPath());
		assertEquals(conf, PluginFolders.adminFile(env, false));
		assertEquals("/from/conf", PluginFolders.readProperties(conf).getProperty(PluginFolders.PROP_SYSTEM_ROOT));
		assertTrue(PluginFolders.readProperties(new File(tmp.toFile(), "none")).isEmpty());
	}

	// jars made for the tests

	/** compile the classes (name -> source) into dir */
	private static void compile(File dir, Map<String, String> sources) throws IOException {
		JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
		List<String> args = new ArrayList<>(List.of("-d", dir.getPath(), "-cp", System.getProperty("java.class.path")));
		File src = new File(dir, "src");
		for(Map.Entry<String, String> e : sources.entrySet()) {
			File f = new File(src, e.getKey().replace('.', '/')+".java");
			f.getParentFile().mkdirs();
			Files.write(f.toPath(), e.getValue().getBytes(StandardCharsets.UTF_8));
			args.add(f.getPath());
		}
		assertEquals(0, javac.run(null, null, null, args.toArray(new String[0])));
	}

	/** a jar of the classes (name -> source), with these services (type -> class) and API version (null: none) */
	private File jar(File dir, String name, String api, Map<String, String> sources, Map<String, String> services) throws IOException {
		File classes = Files.createTempDirectory(tmp, "classes").toFile();
		if( !sources.isEmpty()) {
			compile(classes, sources);
		}
		Manifest m = new Manifest();
		m.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		if( api != null ) {
			m.getMainAttributes().putValue(Plugins.MANIFEST_ATTRIBUTE, api);
		}
		dir.mkdirs();
		File jar = new File(dir, name);
		try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar), m)) {
			for(String cls : sources.keySet()) {
				String path = cls.replace('.', '/')+".class";
				out.putNextEntry(new JarEntry(path));
				out.write(Files.readAllBytes(new File(classes, path).toPath()));
				out.closeEntry();
			}
			for(Map.Entry<String, String> e : services.entrySet()) {
				out.putNextEntry(new JarEntry("META-INF/services/"+e.getKey()));
				out.write((e.getValue()+"\n").getBytes(StandardCharsets.UTF_8));
				out.closeEntry();
			}
		}
		return jar;
	}

	private static String greeter(String pkg, String cls, String text) {
		return "package "+pkg+"; public class "+cls+" implements java.util.function.Supplier<String> {"
				+ " public String get() { return \""+text+"\"; } }";
	}

	private PluginFolders folders(boolean restricted) {
		Properties sys = props(PluginFolders.PROP_SYSTEM_ROOT, tmp.resolve("sys").toString(),
				PluginFolders.PROP_USER_ROOT, tmp.resolve("user").toString());
		return new PluginFolders("app", restricted, null, sys, null, false, tmp.toString(), null);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static List<String> greetings() {
		List<String> ret = new ArrayList<>();
		for(java.util.function.Supplier s : Plugins.providers(java.util.function.Supplier.class, null)) {
			ret.add((String) s.get());
		}
		return ret;
	}

	@Test
	public void loading() throws Exception {
		File sysApp = tmp.resolve("sys/app/plugins").toFile();
		File userShared = tmp.resolve("user/plugins").toFile();
		String sup = "java.util.function.Supplier";
		jar(sysApp, "hello.jar", "1", Map.of("p1.Hello", greeter("p1", "Hello", "system")), Map.of(sup, "p1.Hello"));
		// the same name in a user folder: shadowed
		jar(userShared, "hello.jar", "1", Map.of("p2.Hello", greeter("p2", "Hello", "user")), Map.of(sup, "p2.Hello"));
		// another version of the API: skipped
		jar(userShared, "future.jar", "2", Map.of("p3.Hello", greeter("p3", "Hello", "future")), Map.of(sup, "p3.Hello"));
		// a plugin folder with a library of its own
		File folder = new File(userShared, "multi");
		jar(folder, "multi.jar", "1", Map.of("p4.Hello", greeter("p4", "Hello", "multi")), Map.of(sup, "p4.Hello"));
		jar(new File(folder, "lib"), "dep.jar", null, Map.of("p4.Dep", "package p4; public class Dep {}"), Map.of());
		// a library with a class the multi plugin also has
		jar(userShared, "zlib.jar", null, Map.of("p4.Dep", "package p4; public class Dep {}"), Map.of());
		// not a jar
		Files.write(new File(userShared, "broken.jar").toPath(), new byte[] {1, 2, 3});

		List<Runnable> heard = new ArrayList<>();
		Plugins.onLoad(() -> heard.add(null));
		ClassLoader before = Thread.currentThread().getContextClassLoader();
		try {
			PluginReport r = Plugins.load(folders(false), before);
			assertEquals(1, heard.size());
			assertSame(r, Plugins.report());
			assertSame(r, Plugins.load(folders(true), before), "only the first load counts");
			assertEquals(List.of("system", "multi"), greetings());
			assertSame(Plugins.classLoader(), Thread.currentThread().getContextClassLoader());

			Map<String, PluginReport.Status> status = new HashMap<>();
			for(PluginReport.Entry e : r.entries()) {
				status.put(tmp.relativize(e.jar.toPath()).toString().replace('\\', '/'), e.status);
			}
			assertEquals(PluginReport.Status.PLUGIN, status.get("sys/app/plugins/hello.jar"));
			assertEquals(PluginReport.Status.SHADOWED, status.get("user/plugins/hello.jar"));
			assertEquals(PluginReport.Status.SKIPPED, status.get("user/plugins/future.jar"));
			assertEquals(PluginReport.Status.SKIPPED, status.get("user/plugins/broken.jar"));
			assertEquals(PluginReport.Status.PLUGIN, status.get("user/plugins/multi/multi.jar"));
			assertEquals(PluginReport.Status.LIBRARY, status.get("user/plugins/multi/lib/dep.jar"));
			assertEquals(PluginReport.Status.LIBRARY, status.get("user/plugins/zlib.jar"));
			assertEquals(4, r.loaded().size());
			String text = r.text();
			assertTrue(text.contains("hello.jar: not loaded, shadowed by"), text);
			assertTrue(text.contains("future.jar: skipped, built for plugin API 2, this is 1"), text);
			assertTrue(text.contains("(system, app)"), text);
			assertTrue(text.contains("warning: dep.jar and zlib.jar both have 1 class (p4.Dep)"), text);
			assertTrue(text.contains("/sys/plugins (system, shared): not there"), text.replace('\\', '/'));
		} finally {
			Thread.currentThread().setContextClassLoader(before);
		}
	}

	@Test
	public void restrictedLeavesOutTheUser() throws Exception {
		String sup = "java.util.function.Supplier";
		jar(tmp.resolve("user/app/plugins").toFile(), "u.jar", "1", Map.of("p5.Hello", greeter("p5", "Hello", "user")), Map.of(sup, "p5.Hello"));
		ClassLoader before = Thread.currentThread().getContextClassLoader();
		try {
			PluginReport r = Plugins.load(folders(true), before);
			assertTrue(r.entries().isEmpty());
			assertTrue(greetings().isEmpty());
			// nothing loaded: the context class loader is left alone
			assertSame(before, Thread.currentThread().getContextClassLoader());
		} finally {
			Thread.currentThread().setContextClassLoader(before);
		}
	}

	@Test
	public void jdbcDriver() throws Exception {
		String driver = "package p6; import java.sql.*; import java.util.Properties; import java.util.logging.Logger;"
				+ " public class D implements Driver {"
				+ " public Connection connect(String url, Properties info) { return null; }"
				+ " public boolean acceptsURL(String url) { return url.startsWith(\"jdbc:plugintest:\"); }"
				+ " public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }"
				+ " public int getMajorVersion() { return 1; } public int getMinorVersion() { return 2; }"
				+ " public boolean jdbcCompliant() { return false; }"
				+ " public Logger getParentLogger() { return null; } }";
		jar(tmp.resolve("sys/plugins").toFile(), "driver.jar", null, Map.of("p6.D", driver), Map.of("java.sql.Driver", "p6.D"));
		ClassLoader before = Thread.currentThread().getContextClassLoader();
		try {
			Plugins.load(folders(false), before);
			java.sql.Driver d = DriverManager.getDriver("jdbc:plugintest:x");
			assertNotNull(d);
			assertEquals(1, d.getMajorVersion());
			assertEquals(2, d.getMinorVersion());
			Connection c = DriverManager.getDriver("jdbc:plugintest:x").connect("jdbc:plugintest:x", new Properties());
			assertEquals(null, c);
			assertFalse(d.jdbcCompliant());
			DriverManager.deregisterDriver(d);
		} finally {
			Thread.currentThread().setContextClassLoader(before);
		}
	}
}
