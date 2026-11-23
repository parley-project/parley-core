package us.bringardner.parley.core;

import java.io.IOException;
import java.io.InputStream;

/**
 * Finds a properties file named by a <code>parley.include</code> setting of another properties file (see
 * {@link BaseObject#getProperty(String)}), so a module that knows how to open files of some kind (a file
 * system, a FileSource, a database) can supply them without parley-core knowing about them.
 * <p>
 * The resolvers are found with {@link java.util.ServiceLoader}: a module lists its implementation in
 * <code>META-INF/services/us.bringardner.parley.core.IncludeResolver</code>. The files of the class path
 * are found by parley-core itself, first; then the resolvers are asked in turn, and the first one that
 * knows the name wins.
 */
public interface IncludeResolver {

	/** A file a resolver found: the name that identifies it and its content. */
	final class Found {
		private final String name;
		private final InputStream stream;

		/**
		 * @param name the name that identifies the file, which is the <code>from</code> of the includes
		 *        it has itself, and how a file that includes itself is noticed
		 * @param stream the content, closed by the caller
		 */
		public Found(String name, InputStream stream) {
			this.name = name;
			this.stream = stream;
		}

		public String getName() {
			return name;
		}

		public InputStream getStream() {
			return stream;
		}
	}

	/**
	 * @param from the name of the file that has the include, as given by the resolver (or parley-core for a
	 *        class-path file) that found it
	 * @param name the file named in the include, with its variables replaced
	 * @return the file, or null if this resolver doesn't know the name
	 * @throws IOException if this resolver knows the name but can't read the file
	 */
	Found find(String from, String name) throws IOException;
}
