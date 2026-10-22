package us.bringardner.parley.plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * <PRE>
 * Where an application's plugins are, in the order they are searched (the first jar of a name
 * found wins):
 *
 *   1. the system folder for the application   /usr/local/lib/parley/APP/plugins
 *   2. the system folder shared by every app   /usr/local/lib/parley/plugins
 *   3. the user's folder for the application   ~/.parley/APP/plugins
 *   4. the user's folder shared by every app   ~/.parley/plugins
 *
 * On Windows the system root is %ProgramData%\Parley instead of /usr/local/lib/parley. System
 * folders always come before user folders, so a user can add plugins but not replace the ones an
 * administrator installed (as a system directory early in $PATH).
 *
 * Settings, from the administrator's file and then from system properties:
 *
 *   parley.plugins.systemRoot    replaces /usr/local/lib/parley (or %ProgramData%\Parley)
 *   parley.plugins.userRoot      replaces ~/.parley
 *   parley.plugins.path          the folders, in order (path separator between them): all four
 *   parley.APP.plugins.path      the same for one application
 *
 * The administrator's file is /etc/parley/parley.properties (Windows:
 * %ProgramData%\Parley\parley.properties), or the file the PARLEY_CONF environment variable names.
 * For every setting but parley.plugins.userRoot, a value in that file wins over a system property,
 * so a user's -D cannot move the system folders. Restricted (a restricted shell): only the
 * administrator's file is read, and the user's folders are left out.
 *
 * This is policy, not security: anyone who can start a JVM can give it any class path.
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
public final class PluginFolders {

	public static final String PROP_SYSTEM_ROOT = "parley.plugins.systemRoot";
	public static final String PROP_USER_ROOT = "parley.plugins.userRoot";
	public static final String PROP_PATH = "parley.plugins.path";
	/** the administrator's file, instead of /etc/parley/parley.properties */
	public static final String ENV_CONF = "PARLEY_CONF";

	/** which of the four a folder is (or one named by a path setting) */
	public enum Kind {
		SYSTEM_APP("system, "), SYSTEM_SHARED("system, shared"), USER_APP("user, "), USER_SHARED("user, shared"), PATH("path setting");

		private final String label;

		Kind(String label) {
			this.label = label;
		}

		/** how a report shows it, for app */
		public String label(String app) {
			return label.endsWith(", ") ? label+app : label;
		}

		public boolean isUser() {
			return this == USER_APP || this == USER_SHARED;
		}
	}

	/** a folder to search, and which one it is */
	public static final class Folder {
		public final File dir;
		public final Kind kind;

		public Folder(File dir, Kind kind) {
			this.dir = dir;
			this.kind = kind;
		}

		@Override
		public String toString() {
			return dir.getPath();
		}
	}

	private final String app;
	private final boolean restricted;
	private final Properties admin;
	private final Properties system;
	private final boolean windows;
	private final Map<String, String> env;
	private final String userHome;
	private final File adminFile;

	/**
	 * The folders for app as this JVM sees them: its system properties, environment and operating
	 * system, and the administrator's file.
	 */
	public static PluginFolders forApp(String app, boolean restricted) {
		Map<String, String> env = System.getenv();
		boolean windows = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
		File file = adminFile(env, windows);
		return new PluginFolders(app, restricted, readProperties(file), System.getProperties(), env,
				windows, System.getProperty("user.home", "."), file);
	}

	/** (for tests: everything given) */
	public PluginFolders(String app, boolean restricted, Properties admin, Properties system,
			Map<String, String> env, boolean windows, String userHome, File adminFile) {
		if( app == null || app.isEmpty() || app.contains("/") || app.contains("\\") || app.contains("..")) {
			throw new IllegalArgumentException("not an application name: "+app);
		}
		this.app = app;
		this.restricted = restricted;
		this.admin = admin == null ? new Properties() : admin;
		this.system = system == null ? new Properties() : system;
		this.env = env == null ? Collections.emptyMap() : env;
		this.windows = windows;
		this.userHome = userHome;
		this.adminFile = adminFile;
	}

	/** /etc/parley/parley.properties, %ProgramData%\Parley\parley.properties, or $PARLEY_CONF */
	public static File adminFile(Map<String, String> env, boolean windows) {
		String conf = env.get(ENV_CONF);
		if( conf != null && !conf.isEmpty()) {
			return new File(conf);
		}
		if( windows ) {
			String data = env.getOrDefault("ProgramData", "C:\\ProgramData");
			return new File(new File(data, "Parley"), "parley.properties");
		}
		return new File("/etc/parley/parley.properties");
	}

	/** the file's properties, or none if it is not there or cannot be read */
	public static Properties readProperties(File file) {
		Properties ret = new Properties();
		if( file != null && file.isFile()) {
			try (InputStream in = Files.newInputStream(file.toPath())) {
				ret.load(in);
			} catch (IOException e) {
				// (as if it were not there)
			}
		}
		return ret;
	}

	public String app() {
		return app;
	}

	public boolean restricted() {
		return restricted;
	}

	/** the administrator's file (it may not exist) */
	public File adminFile() {
		return adminFile;
	}

	/** a setting the administrator's file decides when it has it */
	private String systemSetting(String name) {
		String v = admin.getProperty(name);
		if( v == null && !restricted ) {
			v = system.getProperty(name);
		}
		return v == null || v.trim().isEmpty() ? null : v.trim();
	}

	/** a setting the user decides (-D) before the administrator's default */
	private String userSetting(String name) {
		String v = restricted ? null : system.getProperty(name);
		if( v == null || v.trim().isEmpty()) {
			v = admin.getProperty(name);
		}
		return v == null || v.trim().isEmpty() ? null : v.trim();
	}

	public File systemRoot() {
		String root = systemSetting(PROP_SYSTEM_ROOT);
		if( root != null ) {
			return new File(root);
		}
		if( windows ) {
			return new File(env.getOrDefault("ProgramData", "C:\\ProgramData"), "Parley");
		}
		return new File("/usr/local/lib/parley");
	}

	public File userRoot() {
		String root = userSetting(PROP_USER_ROOT);
		return root != null ? new File(root) : new File(userHome, ".parley");
	}

	/** the folders to search, in order */
	public List<Folder> folders() {
		List<Folder> ret = new ArrayList<>();
		String path = systemSetting("parley."+app+".plugins.path");
		if( path == null ) {
			path = systemSetting(PROP_PATH);
		}
		if( path != null ) {
			for(String p : path.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
				if( !p.trim().isEmpty()) {
					ret.add(new Folder(new File(p.trim()), Kind.PATH));
				}
			}
			return ret;
		}
		File sys = systemRoot();
		ret.add(new Folder(new File(new File(sys, app), "plugins"), Kind.SYSTEM_APP));
		ret.add(new Folder(new File(sys, "plugins"), Kind.SYSTEM_SHARED));
		if( !restricted ) {
			File user = userRoot();
			ret.add(new Folder(new File(new File(user, app), "plugins"), Kind.USER_APP));
			ret.add(new Folder(new File(user, "plugins"), Kind.USER_SHARED));
		}
		return ret;
	}
}
