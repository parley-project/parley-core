package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.parley.core.BaseObject;

/**
 * Runs BaseObject sub classes that live in two named modules (neither opens its package to parley-core)
 * on the module path, to check that properties files can still be found.
 *
 * Module a.base:  Base extends BaseObject
 * Module b.sub:   Sub extends Base, and Main, which prints the property values
 *
 * Everything is built in a temporary directory (javac, jar and java are the ones running this test).
 */
public class TestModulePathProperties {

	private static String tool(String name) {
		File f = new File(System.getProperty("java.home"), "bin"+File.separator+name);
		if( !f.exists() ) {
			// Windows tools are javac.exe, jar.exe and java.exe
			File exe = new File(f.getPath()+".exe");
			if( exe.exists() ) {
				f = exe;
			}
		}
		return f.getAbsolutePath();
	}

	private static String run(Path dir, String... cmd) throws Exception {
		Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
		String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertTrue(p.waitFor(120, TimeUnit.SECONDS), "timeout: "+String.join(" ", cmd));
		assertEquals(0, p.exitValue(), String.join(" ", cmd)+"\n"+out);
		return out;
	}

	private static void write(Path root, String rel, String content) throws Exception {
		Path p = root.resolve(rel);
		Files.createDirectories(p.getParent());
		Files.writeString(p, content);
	}

	@Test
	public void propertiesAreFoundForClassesInNamedModules(@TempDir Path root) throws Exception {
		Assumptions.assumeTrue(new File(tool("javac")).exists(), "needs a JDK");

		Path classes = Path.of(BaseObject.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		Assumptions.assumeTrue(Files.isDirectory(classes), "parley-core must be run from classes");

		// parley-core as an (automatic) module
		run(root, tool("jar"), "--create", "--file", "parley-core.jar", "-C", classes.toString(), ".");

		write(root, "src/a.base/module-info.java", "module a.base { requires parley.core; exports a.base; }");
		write(root, "src/a.base/a/base/Base.java", "package a.base; public class Base extends us.bringardner.parley.core.BaseObject { }");
		write(root, "src/b.sub/module-info.java", "module b.sub { requires a.base; requires parley.core; exports b.sub; }");
		write(root, "src/b.sub/b/sub/Sub.java", "package b.sub; public class Sub extends a.base.Base { }");
		write(root, "src/b.sub/b/sub/Main.java", 
				"package b.sub; public class Main { public static void main(String[] a) {"
				+ " Sub s = new Sub();"
				+ " for(String k : a) System.out.println(k+\"=\"+s.getProperty(k));"
				+ "} }");

		List<String> javac = new ArrayList<>(List.of(tool("javac"), "-d", "mods", "--module-source-path", "src",
				"--module-path", "parley-core.jar", "-m", "a.base,b.sub"));
		run(root, javac.toArray(new String[0]));

		// Not copied by javac. The package ones are encapsulated in their (closed) modules, the META-INF ones are not.
		write(root, "mods/a.base/a/base/Base.properties", "inPackage=base\nlayer=base\n");
		write(root, "mods/a.base/META-INF/parley/a.base.Base.properties", "inMetaInf=base\nlayer=base\nbaseOnly=base\n");
		write(root, "mods/b.sub/META-INF/parley/b.sub.Sub.properties", "layer=sub\n");

		String out = run(root, tool("java"), "--module-path", "mods"+File.pathSeparator+"parley-core.jar",
				"-m", "b.sub/b.sub.Main", "inMetaInf", "baseOnly", "layer", "inPackage");

		assertTrue(out.contains("inMetaInf=base"), "META-INF/parley file of the super class module, not found\n"+out);
		assertTrue(out.contains("baseOnly=base"), out);
		assertTrue(out.contains("layer=sub"), "the sub class's file should override the super class's\n"+out);
	}
}
