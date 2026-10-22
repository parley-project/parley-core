package us.bringardner.parley.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <PRE>
 * What Plugins.load found: each folder searched, each jar in it and what became of it (loaded as a
 * plugin, loaded as a library, skipped and why, or shadowed by a jar of the same name found
 * earlier), and warnings (a class in two jars: the first one's is used).
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
public final class PluginReport {

	/** what became of a jar */
	public enum Status {
		/** a plugin, for this API version */
		PLUGIN,
		/** a jar with no Parley-Plugin-Api (a library: a JDBC driver, a dependency) */
		LIBRARY,
		/** a jar of the same name was found earlier (see shadowedBy) */
		SHADOWED,
		/** not loaded (see reason) */
		SKIPPED
	}

	/** one jar */
	public static final class Entry {
		public final File jar;
		public final PluginFolders.Folder folder;
		public final Status status;
		/** why it was skipped, or the jar that shadows it; else null */
		public final String reason;

		Entry(File jar, PluginFolders.Folder folder, Status status, String reason) {
			this.jar = jar;
			this.folder = folder;
			this.status = status;
			this.reason = reason;
		}

		@Override
		public String toString() {
			return status+" "+jar+(reason == null ? "" : " ("+reason+")");
		}
	}

	private final String app;
	private final List<PluginFolders.Folder> folders;
	private final List<Entry> entries = new ArrayList<>();
	private final List<String> warnings = new ArrayList<>();
	private String notLoaded;

	PluginReport(String app, List<PluginFolders.Folder> folders) {
		this.app = app;
		this.folders = folders;
	}

	void add(Entry e) {
		entries.add(e);
	}

	void warn(String warning) {
		warnings.add(warning);
	}

	void notLoaded(String why) {
		notLoaded = why;
	}

	public String app() {
		return app;
	}

	public List<PluginFolders.Folder> folders() {
		return Collections.unmodifiableList(folders);
	}

	public List<Entry> entries() {
		return Collections.unmodifiableList(entries);
	}

	public List<String> warnings() {
		return Collections.unmodifiableList(warnings);
	}

	/** why no plugins were loaded at all (a native image), or null */
	public String notLoaded() {
		return notLoaded;
	}

	/** the jars that were loaded (plugins and libraries) */
	public List<File> loaded() {
		List<File> ret = new ArrayList<>();
		for(Entry e : entries) {
			if( e.status == Status.PLUGIN || e.status == Status.LIBRARY ) {
				ret.add(e.jar);
			}
		}
		return ret;
	}

	/** The report as text: the folders in order, each with its jars, then the warnings. */
	public String text() {
		StringBuilder b = new StringBuilder();
		if( notLoaded != null ) {
			b.append("plugins are not loaded: ").append(notLoaded).append('\n');
			return b.toString();
		}
		for(PluginFolders.Folder f : folders) {
			b.append(f.dir).append(" (").append(f.kind.label(app)).append(")");
			boolean any = false;
			for(Entry e : entries) {
				if( e.folder == f ) {
					if( !any ) {
						b.append('\n');
						any = true;
					}
					b.append("  ").append(relative(f.dir, e.jar)).append(": ").append(describe(e)).append('\n');
				}
			}
			if( !any ) {
				b.append(f.dir.isDirectory() ? ": empty\n" : ": not there\n");
			}
		}
		for(String w : warnings) {
			b.append("warning: ").append(w).append('\n');
		}
		return b.toString();
	}

	private static String describe(Entry e) {
		switch( e.status ) {
		case PLUGIN: return "plugin";
		case LIBRARY: return "library";
		case SHADOWED: return "not loaded, shadowed by "+e.reason;
		default: return "skipped, "+e.reason;
		}
	}

	private static String relative(File dir, File jar) {
		String d = dir.getPath()+File.separator;
		String j = jar.getPath();
		return j.startsWith(d) ? j.substring(d.length()) : j;
	}

	@Override
	public String toString() {
		return text();
	}
}
