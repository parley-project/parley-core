package us.bringardner.parley.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

/**
 * Reads a properties file the way {@link Properties#load(InputStream)} does, and also reads the files
 * its <code>parley.include</code> settings name, each one where the setting stands, so what comes after
 * the setting overrides what the included file defines and what comes before is overridden by it.
 * <p>
 * The setting has the shape of any other setting, so a tool that reads or writes the file keeps it.
 * Its value is a file name that may have <code>${name}</code> variables: the settings read so far, then
 * the system properties; <code>${env.NAME}</code> is an environment variable. The setting itself is not
 * in the result. A name that no resolver finds, a variable that has no value, and an include that comes
 * back to a file it is already reading are errors ({@link IllegalStateException}).
 * <p>
 * It relies on {@link Properties#load(InputStream)} calling <code>put</code> for each setting in the
 * order of the file, which TestPropertiesReader checks.
 */
final class PropertiesReader {

	/** The setting that names a file to read at that place */
	static final String INCLUDE = "parley.include";
	/** The most files that include each other: more is an error even when none repeats */
	static final int MAX_DEPTH = 16;

	private PropertiesReader() {
	}

	/**
	 * The resolvers of a class: its class path (a name is found next to the file that names it, or from
	 * the root of the class path when it starts with a slash), then the resolvers listed as services.
	 * The services are looked for only when an include is not on the class path.
	 */
	static IncludeResolver standard(Class<?> via) {
		return (from, name) -> {
			String resource = name.startsWith("/") ? name : directory(from) + name;
			InputStream in = via.getResourceAsStream(resource);
			if( in != null ) {
				return new IncludeResolver.Found(resource, in);
			}
			for(IncludeResolver r : ServiceLoader.load(IncludeResolver.class)) {
				IncludeResolver.Found f = r.find(from, name);
				if( f != null ) {
					return f;
				}
			}
			return null;
		};
	}

	private static String directory(String from) {
		int i = from == null ? -1 : from.lastIndexOf('/');
		return i < 0 ? "/" : from.substring(0, i + 1);
	}

	/**
	 * @param in the content of the file
	 * @param fn the name of the file: how its includes are found and how it is named in an error
	 * @param resolver finds the files its includes name
	 * @return the settings of the file and of the files it includes, without the include settings
	 */
	static Properties read(InputStream in, String fn, IncludeResolver resolver) throws IOException {
		Loading p = new Loading(null, fn, resolver);
		try {
			p.load(in);
		} catch(UncheckedIOException e) {
			throw e.getCause();
		}
		Properties ret = new Properties();
		ret.putAll(p);
		return ret;
	}

	/** The settings of one file while it is read: an include setting is read, and not kept */
	private static final class Loading extends Properties {
		private static final long serialVersionUID = 1L;
		private final transient Loading parent;
		private final transient String fn;
		private final transient IncludeResolver resolver;
		private final transient int depth;

		Loading(Loading parent, String fn, IncludeResolver resolver) {
			this.parent = parent;
			this.fn = fn;
			this.resolver = resolver;
			this.depth = parent == null ? 0 : parent.depth + 1;
		}

		@Override
		public synchronized Object put(Object key, Object value) {
			if( INCLUDE.equals(key) ) {
				include(String.valueOf(value).trim());
				return null;
			}
			return super.put(key, value);
		}

		private void include(String value) {
			String name = expand(value);
			if( name.isEmpty() ) {
				throw fail("has no file name");
			}
			if( depth + 1 >= MAX_DEPTH ) {
				throw fail("is too deep: " + name + " (more than " + MAX_DEPTH + " files include each other)");
			}
			IncludeResolver.Found found;
			try {
				found = resolver.find(fn, name);
			} catch(IOException e) {
				throw fail("can't be read: " + e);
			}
			if( found == null ) {
				throw fail("names a file that isn't there: " + name);
			}
			try(InputStream in = found.getStream()) {
				for(Loading p = this; p != null; p = p.parent) {
					if( found.getName().equals(p.fn) ) {
						throw fail("comes back to " + found.getName() + ", which is being read");
					}
				}
				Loading child = new Loading(this, found.getName(), resolver);
				child.load(in);
				for(Map.Entry<Object, Object> e : child.entrySet()) {
					super.put(e.getKey(), e.getValue());
				}
			} catch(IOException e) {
				throw fail("can't be read: " + e);
			}
		}

		private IllegalStateException fail(String what) {
			return new IllegalStateException(INCLUDE + " in " + fn + " " + what);
		}

		/** The value with its ${name} variables replaced */
		private String expand(String value) {
			StringBuilder ret = null;
			int at = 0;
			while( true ) {
				int open = value.indexOf("${", at);
				if( open < 0 ) {
					break;
				}
				int close = value.indexOf('}', open + 2);
				if( close < 0 ) {
					throw fail("has a ${ without a }: " + value);
				}
				String name = value.substring(open + 2, close);
				String v = lookup(name);
				if( v == null ) {
					throw fail("uses ${" + name + "}, which has no value");
				}
				if( ret == null ) {
					ret = new StringBuilder();
				}
				ret.append(value, at, open).append(v);
				at = close + 1;
			}
			return ret == null ? value : ret.append(value, at, value.length()).toString();
		}

		private String lookup(String name) {
			if( name.startsWith("env.") ) {
				return System.getenv(name.substring(4));
			}
			for(Loading p = this; p != null; p = p.parent) {
				Object v = p.get(name);
				if( v instanceof String ) {
					return (String) v;
				}
			}
			return System.getProperty(name);
		}
	}
}
