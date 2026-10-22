package us.bringardner.parley.plugin;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * <PRE>
 * Plugins: jars an application finds at run time in its plugin folders (see PluginFolders), so a
 * feature is added by dropping its jar in a folder instead of rebuilding the application.
 *
 *   Plugins.load("fsh", restricted);                          once, as the application starts
 *   for(CommandProvider p : Plugins.services(CommandProvider.class)) ...
 *
 * All the jars share one class loader, a child of the application's. A plugin is found through
 * ServiceLoader, as anything in Parley that is looked up at run time already is (FileSourceFactory,
 * CredentialProvider): load() also makes the plugin class loader this thread's context class
 * loader, so ServiceLoader.load(type) sees the plugins too, and modules that looked their services
 * up before the plugins were loaded hear of it through onLoad.
 *
 * A folder holds jars, and folders of jars (a plugin with its dependencies: name/*.jar and
 * name/lib/*.jar). A jar whose manifest says Parley-Plugin-Api: N is a plugin for that version of
 * this API, and is skipped if N is not API_VERSION; a jar without it is loaded as a library (a JDBC
 * driver, a dependency). When two folders have a jar of the same name, the first one found is
 * loaded and the other is reported as shadowed.
 *
 * JDBC drivers in plugin jars are registered with DriverManager (through a small wrapper, since
 * DriverManager only lets code use drivers its own class loader can see).
 *
 * A native image (GraalVM) cannot load jars: there load() reports that and loads nothing.
 *
 * Copyright 2026 <A href="http://bringardner.us/tony">Tony Bringardner</A>
 *
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       <A href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</A>
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  </PRE>
 *
 *  @author Tony Bringardner
 */
public final class Plugins {

	/** the version of this API a plugin's manifest names (Parley-Plugin-Api) */
	public static final int API_VERSION = 1;
	/** the manifest attribute that makes a jar a plugin */
	public static final String MANIFEST_ATTRIBUTE = "Parley-Plugin-Api";

	private static final Object lock = new Object();
	private static volatile URLClassLoader loader;
	private static volatile PluginReport report;
	private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();

	private Plugins() {
	}

	/**
	 * Load app's plugins (the first call; later calls return the first report). restricted: the
	 * user's folders and settings are left out (a restricted shell).
	 */
	public static PluginReport load(String app, boolean restricted) {
		return load(PluginFolders.forApp(app, restricted), Thread.currentThread().getContextClassLoader());
	}

	/** Load the plugins in folders, as children of parent (null: this class's loader). */
	public static PluginReport load(PluginFolders folders, ClassLoader parent) {
		synchronized (lock) {
			if( report != null ) {
				return report;
			}
			PluginReport r = new PluginReport(folders.app(), folders.folders());
			if( "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode")) ) {
				r.notLoaded("a native image cannot load jars");
				report = r;
				return r;
			}
			List<File> jars = scan(r);
			if( parent == null ) {
				parent = Plugins.class.getClassLoader();
			}
			if( !jars.isEmpty()) {
				List<URL> urls = new ArrayList<>();
				for(File jar : jars) {
					try {
						urls.add(jar.toURI().toURL());
					} catch (MalformedURLException e) {
						// (a file's URI is always a URL)
					}
				}
				loader = new URLClassLoader("parley-plugins", urls.toArray(new URL[0]), parent);
				Thread.currentThread().setContextClassLoader(loader);
				duplicates(jars, r);
				registerDrivers(loader);
			}
			report = r;
		}
		for(Runnable l : listeners) {
			try {
				l.run();
			} catch (RuntimeException e) {
				// (one module's trouble is not the others')
			}
		}
		return report;
	}

	/** what load found, or null before it ran */
	public static PluginReport report() {
		return report;
	}

	/** the plugins' class loader, or (none were loaded) the context class loader */
	public static ClassLoader classLoader() {
		ClassLoader l = loader;
		if( l == null ) {
			l = Thread.currentThread().getContextClassLoader();
		}
		return l != null ? l : Plugins.class.getClassLoader();
	}

	/** the providers of type, from the application and its plugins */
	public static <S> ServiceLoader<S> services(Class<S> type) {
		return ServiceLoader.load(type, classLoader());
	}

	/**
	 * The providers of type that can be made, in order; a plugin that fails to load is left out
	 * (its error goes to onError, which may be null).
	 */
	public static <S> List<S> providers(Class<S> type, java.util.function.Consumer<Throwable> onError) {
		List<S> ret = new ArrayList<>();
		java.util.Iterator<S> it = services(type).iterator();
		while( true ) {
			try {
				if( !it.hasNext()) {
					break;
				}
				ret.add(it.next());
			} catch (ServiceConfigurationError | LinkageError e) {
				if( onError != null ) {
					onError.accept(e);
				}
			}
		}
		return ret;
	}

	/**
	 * Run listener after plugins are loaded (now, if they already are): for a module that looked
	 * up its providers before, so it looks again.
	 */
	public static void onLoad(Runnable listener) {
		listeners.add(listener);
		if( report != null ) {
			listener.run();
		}
	}

	/** (for tests) forget the plugins, their loader and the listeners */
	static void reset() {
		synchronized (lock) {
			if( loader != null ) {
				try {
					loader.close();
				} catch (IOException e) {
				}
			}
			loader = null;
			report = null;
			listeners.clear();
		}
	}

	/** the jars to load, in order; the rest reported */
	private static List<File> scan(PluginReport r) {
		List<File> ret = new ArrayList<>();
		Map<String, File> byName = new HashMap<>();
		for(PluginFolders.Folder f : r.folders()) {
			if( !f.dir.isDirectory()) {
				continue;
			}
			File [] children = f.dir.listFiles();
			if( children == null ) {
				continue;
			}
			Arrays.sort(children);
			for(File c : children) {
				if( c.isFile() && c.getName().endsWith(".jar")) {
					consider(c, c.getName(), f, r, ret, byName, null);
				} else if( c.isDirectory()) {
					// a plugin with its dependencies: its jars, then lib/'s
					List<File> jars = jarsIn(c);
					File lib = new File(c, "lib");
					List<File> libs = lib.isDirectory() ? jarsIn(lib) : new ArrayList<>();
					String version = null;
					for(File jar : jars) {
						String v = apiVersion(jar);
						if( v != null ) {
							version = v;
						}
					}
					if( version != null && !String.valueOf(API_VERSION).equals(version)) {
						for(File jar : jars) {
							r.add(new PluginReport.Entry(jar, f, PluginReport.Status.SKIPPED, wrongVersion(version)));
						}
						continue;
					}
					for(File jar : jars) {
						consider(jar, c.getName()+"/"+jar.getName(), f, r, ret, byName, version);
					}
					for(File jar : libs) {
						consider(jar, c.getName()+"/lib/"+jar.getName(), f, r, ret, byName, "");
					}
				}
			}
		}
		return ret;
	}

	private static List<File> jarsIn(File dir) {
		List<File> ret = new ArrayList<>();
		File [] files = dir.listFiles();
		if( files != null ) {
			Arrays.sort(files);
			for(File f : files) {
				if( f.isFile() && f.getName().endsWith(".jar")) {
					ret.add(f);
				}
			}
		}
		return ret;
	}

	/**
	 * Add jar unless one of its name came first or it is for another API version. version: the
	 * plugin folder's version (a library in a plugin folder: ""), or null to read the jar's own.
	 */
	private static void consider(File jar, String name, PluginFolders.Folder f, PluginReport r,
			List<File> ret, Map<String, File> byName, String version) {
		File first = byName.get(name);
		if( first != null ) {
			r.add(new PluginReport.Entry(jar, f, PluginReport.Status.SHADOWED, first.getPath()));
			return;
		}
		if( version == null ) {
			try {
				version = apiVersion(jar);
			} catch (RuntimeException e) {
				r.add(new PluginReport.Entry(jar, f, PluginReport.Status.SKIPPED, "cannot read it: "+e.getMessage()));
				return;
			}
		}
		if( version != null && !version.isEmpty() && !String.valueOf(API_VERSION).equals(version)) {
			r.add(new PluginReport.Entry(jar, f, PluginReport.Status.SKIPPED, wrongVersion(version)));
			return;
		}
		if( !readable(jar)) {
			r.add(new PluginReport.Entry(jar, f, PluginReport.Status.SKIPPED, "not a jar that can be read"));
			return;
		}
		byName.put(name, jar);
		ret.add(jar);
		r.add(new PluginReport.Entry(jar, f, version == null || version.isEmpty()
				? PluginReport.Status.LIBRARY : PluginReport.Status.PLUGIN, null));
	}

	private static String wrongVersion(String version) {
		return "built for plugin API "+version+", this is "+API_VERSION;
	}

	/** the jar's Parley-Plugin-Api, or null */
	static String apiVersion(File jar) {
		try (JarFile j = new JarFile(jar)) {
			Manifest m = j.getManifest();
			String v = m == null ? null : m.getMainAttributes().getValue(MANIFEST_ATTRIBUTE);
			return v == null ? null : v.trim();
		} catch (IOException e) {
			return null;
		}
	}

	private static boolean readable(File jar) {
		try (JarFile j = new JarFile(jar)) {
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	/** a warning for each class that more than one jar has (the first jar's is the one used) */
	private static void duplicates(List<File> jars, PluginReport r) {
		Map<String, File> owner = new HashMap<>();
		Map<String, List<String>> clashes = new java.util.LinkedHashMap<>();
		for(File jar : jars) {
			try (JarFile j = new JarFile(jar)) {
				Enumeration<JarEntry> en = j.entries();
				while( en.hasMoreElements()) {
					String name = en.nextElement().getName();
					if( !name.endsWith(".class") || name.startsWith("META-INF/") || name.equals("module-info.class")) {
						continue;
					}
					File first = owner.putIfAbsent(name, jar);
					if( first != null ) {
						clashes.computeIfAbsent(first.getName()+" and "+jar.getName(), k -> new ArrayList<>()).add(name);
					}
				}
			} catch (IOException e) {
				// (reported as unreadable already)
			}
		}
		for(Map.Entry<String, List<String>> e : clashes.entrySet()) {
			List<String> classes = e.getValue();
			String first = classes.get(0).replace('/', '.').replaceAll("\\.class$", "");
			r.warn(e.getKey()+" both have "+classes.size()+" class"+(classes.size() == 1 ? "" : "es")
					+" ("+first+(classes.size() > 1 ? " ..." : "")+"): the first jar's are used");
		}
	}

	/** register the JDBC drivers the plugin jars have */
	private static void registerDrivers(ClassLoader plugins) {
		for(Driver d : providers(Driver.class, null)) {
			if( d.getClass().getClassLoader() != plugins ) {
				// (the application's own: DriverManager has it already)
				continue;
			}
			try {
				DriverManager.registerDriver(new DriverShim(d));
			} catch (SQLException e) {
				// (not this one)
			}
		}
	}

	/** A plugin's JDBC driver, as a class DriverManager lets everyone use. */
	static final class DriverShim implements Driver {
		final Driver driver;

		DriverShim(Driver driver) {
			this.driver = driver;
		}

		@Override
		public java.sql.Connection connect(String url, java.util.Properties info) throws SQLException {
			return driver.connect(url, info);
		}

		@Override
		public boolean acceptsURL(String url) throws SQLException {
			return driver.acceptsURL(url);
		}

		@Override
		public java.sql.DriverPropertyInfo[] getPropertyInfo(String url, java.util.Properties info) throws SQLException {
			return driver.getPropertyInfo(url, info);
		}

		@Override
		public int getMajorVersion() {
			return driver.getMajorVersion();
		}

		@Override
		public int getMinorVersion() {
			return driver.getMinorVersion();
		}

		@Override
		public boolean jdbcCompliant() {
			return driver.jdbcCompliant();
		}

		@Override
		public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
			return driver.getParentLogger();
		}

		@Override
		public String toString() {
			return driver.toString();
		}
	}
}
