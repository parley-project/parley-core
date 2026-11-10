// ~version~V000.01.11-V000.01.02-V000.00.01-V000.00.00-


package us.bringardner.parley.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;



/**
 * 
 * <PRE>
 * The objective of the BaseObject class is to provide a common foundation for all, non trivial classes.
 * It provides for the simplest and most fundamental functions required by most non trivial classes 
 * such as logging and property management.
 *
 * Logging: Parley code logs with logDebug, logInfo, logWarn and logError. Each object has a level
 * (setLogLevel, else the parley.log.level settings); what it lets through is written with the JDK's
 * System.Logger, so the application decides what writes the messages (java.util.logging by default,
 * or whatever System.LoggerFinder it installs, such as parley-logging's). When no level is set,
 * everything goes to the System.Logger, which decides.
 *  
 *
 * Copyright 1998-2026 <A href="http://bringardner.us/tony">Tony Bringardner</A>
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
 *	@author Tony Bringardner   
 *	
 */
public class BaseObject { 




	/**
	 * The most properties files (one per class searched, found or not) kept in the cache for each
	 * class loader, see {@link #setMaxProperties(int)}. It was 200, which an application with a few
	 * hundred BaseObject classes went past, so files were read again and again.
	 */
	public static final int DEFAULT_MAX_PROPERTIES = 1000;

	//  Cached for a properties file that was looked for and not found. Never changed.
	private static final Properties MISSING = new Properties();

	//  Where a library in a named module can put its defaults (see getPropertyEntry). Not in a package, so not encapsulated.
	private static final String META_INF_DIR = "/META-INF/parley/";

	//  The properties files read through each class loader, by class name (MISSING when there is no file).
	//  Kept per loader because the same class name can have a different file (or none) in another loader:
	//  with one cache for every loader, the first loader searched decided the result for all of them.
	//  The keys are weak so a plugin's loader can still be garbage collected; the values only hold Strings.
	//  Each map is a ConcurrentHashMap, so looking up a property doesn't take a lock. (It used to be an LRU map,
	//  and because even get() reorders an LRU map every lookup took the same global lock.)
	private static final Map<ClassLoader, ConcurrentHashMap<String, Properties>> loaderCaches = new WeakHashMap<>();

	//  The cache for a class's loader, found once per class without a lock. A ClassValue doesn't keep
	//  the class (or its loader) from being garbage collected.
	private static final ClassValue<ConcurrentHashMap<String, Properties>> classCaches = new ClassValue<>() {
		@Override
		protected ConcurrentHashMap<String, Properties> computeValue(Class<?> type) {
			synchronized (loaderCaches) {
				return loaderCaches.computeIfAbsent(type.getClassLoader(), k -> new ConcurrentHashMap<>());
			}
		}
	};

	private static volatile int maxProperties = DEFAULT_MAX_PROPERTIES;

	private volatile boolean supportPrefixProperty = true;

	/**
	 * @param maxSize the most properties files kept in the cache for each class loader, 0 (or less) for no limit. 
	 *  When there are more, some (not necessarily the least recently used) are dropped and read again when needed.
	 */
	public static void setMaxProperties(int maxSize) {
		maxProperties = maxSize;
		for(ConcurrentHashMap<String, Properties> cache : propertyCaches()) {
			trimPropertyCache(cache);
		}
	}

	private static void trimPropertyCache(ConcurrentHashMap<String, Properties> cache) {
		int max = maxProperties;
		if( max <= 0 ) {
			return;
		}
		Iterator<String> it = cache.keySet().iterator();
		while( cache.size() > max && it.hasNext() ) {
			it.next();
			it.remove();
		}
	}

	//  A copy, so the caches can be changed without holding the lock
	private static ConcurrentHashMap<String, Properties>[] propertyCaches() {
		synchronized (loaderCaches) {
			@SuppressWarnings("unchecked")
			ConcurrentHashMap<String, Properties>[] ret = loaderCaches.values().toArray(new ConcurrentHashMap[0]);
			return ret;
		}
	}

	/**
	 * Clear the properties cache.
	 * 
	 * Properties are cache to improve performance when accessing them.
	 * If the application uses properties only during initialization,
	 * the cache may be cleared to reduce the memory footprint.  
	 */
	public static void clearPropertyCache() {
		//  Cleared rather than replaced: the ClassValue keeps a reference to each map
		for(ConcurrentHashMap<String, Properties> cache : propertyCaches()) {
			cache.clear();
		}
	}

	/**
	 * The prefix of this object's settings, see {@link #propertyName(String)}.
	 */
	private volatile String propertyPrefix;
	/** This object's own settings, or null: read them from the system properties */
	private volatile Properties configuration;


	/**
	 * BaseObject constructor  
	 */
	public BaseObject()  {
		super();
		this.configuration = MAKING_WITH.get();
	}

	/** The configuration objects made on this thread get ({@link #makeWith}) */
	private static final ThreadLocal<Properties> MAKING_WITH = new ThreadLocal<>();

	/**
	 * Make an object (a server, say) with settings of its own: every BaseObject made on this thread
	 * while make runs starts with this configuration ({@link #setConfiguration(Properties)}), so
	 * what its constructor reads comes from the configuration too.
	 *
	 * @param configuration the settings, or null for the system properties
	 * @param make          makes the object
	 * @return what make made
	 * @throws Exception what make throws
	 * @since 2.0.0
	 */
	public static <T> T makeWith(Properties configuration, java.util.concurrent.Callable<T> make) throws Exception {
		Properties before = MAKING_WITH.get();
		MAKING_WITH.set(configuration);
		try {
			return make.call();
		} finally {
			if( before == null ) {
				MAKING_WITH.remove();
			} else {
				MAKING_WITH.set(before);
			}
		}
	}



	/**
	 * The prefix of this object's settings, which {@link #propertyName(String)} puts in front of a
	 * setting's name. The default is the class name of this object. getProperty doesn't use it: it
	 * looks up the name it is given.
	 *  
	 * @return the property prefix for this Object, or null when it isn't used
	 * @see #propertyName(String)
	 * @see #isSupportPrefixProperty()
	 */
	public String getPropertyPrefix() {
		if( !isSupportPrefixProperty()) {
			return null;
		}
		
		if( propertyPrefix == null ) {
			synchronized (this) {
				if( propertyPrefix == null ) {
					 propertyPrefix = getClass().getName();
				}
			}
		}
		
		return propertyPrefix;
	}

	/**
	 * Set the prefix of this object's settings (see {@link #propertyName(String)}).
	 * The default is the class name of this object.
	 *   
	 * @param propertyPrefix
	 * @see #propertyName(String)
	 * @see #isSupportPrefixProperty()
	 */
	protected void setPropertyPrefix(String propertyPrefix) {
		this.propertyPrefix = propertyPrefix;
	}

	/**
	 * Give this object settings of its own. {@link #getProperty(String, String)} then looks a name
	 * up in them, and in the properties files of its class chain for defaults, and never in the
	 * system properties: two objects in one JVM (two servers, say) can then have different values
	 * for the same setting. With no configuration (the default) the system properties are read, as
	 * for an object alone in its JVM.
	 *
	 * @param configuration the settings, or null to read the system properties again
	 * @since 2.0.0
	 */
	public void setConfiguration(Properties configuration) {
		this.configuration = configuration;
		this.levelSetting = null;
	}

	/**
	 * @return the settings given with {@link #setConfiguration(Properties)}, or null when this
	 *         object reads the system properties
	 * @since 2.0.0
	 */
	public Properties getConfiguration() {
		return configuration;
	}

	/**
	 * Read the same settings as another object (a server's helper, say): its configuration, or the
	 * system properties when it has none.
	 *
	 * @param other the object whose settings to use; null is ignored
	 * @since 2.0.0
	 */
	public void useConfigurationOf(BaseObject other) {
		if( other != null ) {
			this.configuration = other.getConfiguration();
			this.levelSetting = null;
		}
	}

	/** A setting from this object's configuration, or the system properties when it has none */
	private String fromConfiguration(String propertyName) {
		Properties c = configuration;
		return c != null ? c.getProperty(propertyName) : System.getProperty(propertyName);
	}

	/**
	 * The full name of one of this object's settings: its property prefix, a dot and the name
	 * (<code>parley.mail.pop3</code> and <code>port</code> make <code>parley.mail.pop3.port</code>), or the
	 * name alone when the prefix isn't used (see {@link #setSupportPrefixProperty(boolean)}).
	 * {@link #getProperty(String)} looks up the name it is given as it is, so a class whose settings
	 * share its prefix asks for <code>getProperty(propertyName("port"))</code>.
	 *
	 * @param name the setting's name, without the prefix
	 * @return the full name to look up
	 */
	public String propertyName(String name) {
		String prefix = getPropertyPrefix();
		return prefix == null || prefix.isEmpty() ? name : prefix+"."+name;
	}



	/**
	 * Find the Properties of the given name.
	 * 
	 * The objective is to allow each level, or 'name' to externals
	 * values into property files that can be easily overridden at run time.
	 * 
	 * The places searched, the first one that has the file wins:
	 * <ol>
	 * <li><code>/pkg/Name.properties</code> through this object's class. On the class path this lets an
	 *     extending class replace the file of its super class.</li>
	 * <li>The same file through cls, when cls is in another module or class loader than this object's class
	 *     (a super class in another module, a LogHelper or getPropertyClass() for a plugin class).</li>
	 * <li><code>/META-INF/parley/pkg.Name.properties</code> through cls. A resource in a package of a named
	 *     module is encapsulated, and is not visible to BaseObject unless that module opens the package to it.
	 *     A META-INF resource never is, so a library in a named module can put its defaults there.</li>
	 * </ol>
	 * If more than one file of that name is on the class path, they are merged: the first one on the class
	 * path wins for each key, and the later ones fill in the keys it doesn't define.
	 * What was found (or not) is cached for each class loader and module, so the files are not searched
	 * for every time a property is read.
	 *  
	 * @param cls the class whose properties these are (used if this object's class can't see the file)
	 * @param name
	 * @return the Properties associated with the given name (empty if there is no file).
	 */
	private Properties getPropertyEntry(Class<?> cls, String name) {
		// First, see it we can find a file name "name.properties"
		String path = null;
		if( name.length() > 0 ) {
			path = "/"+name.replace('.', '/');
		} else {
			path = name;
		}
		String fn = path+".properties";

		/*
		 * If you run a bug detector this will show up as a bug...
		 * Calling this.getClass().getResource(...) could give results other than expected if this class is extended by a class in another package
		 * In this case we want that behavior.  It allows a property file to be replaced or overwritten by the extending class. 
		 */
		Class<?> mine = getClass();
		Properties ret = loadProperties(mine, fn);
		if( ret == MISSING && cls != null && !sameSource(cls, mine) ) {
			//  The class being searched may be in a module or class loader this object's class can't see
			ret = loadProperties(cls, fn);
		}
		if( ret == MISSING && cls != null ) {
			ret = loadProperties(cls, META_INF_DIR+name+".properties");
		}
		return ret;
	}

	private static boolean sameSource(Class<?> a, Class<?> b) {
		return a == b || (a.getClassLoader() == b.getClassLoader() && a.getModule() == b.getModule());
	}

	/**
	 * @param via the class whose module (or class loader) is searched
	 * @param fn the resource name of the properties file, it is also the cache key
	 * @return the file's Properties, from the cache of via's class loader if it has been read before,
	 *  or {@link #MISSING} if it isn't there.
	 */
	private static Properties loadProperties(Class<?> via, String fn) {
		return loadProperties(via, fn, cacheKey(via, fn));
	}

	/**
	 * A class in a named module only sees its own module, so the same resource name
	 * can be found (or not) through two classes in the same class loader.
	 */
	private static String cacheKey(Class<?> via, String fn) {
		Module module = via.getModule();
		return module.isNamed() ? module.getName()+":"+fn : fn;
	}

	private static Properties loadProperties(Class<?> via, String fn, String key) {
		ConcurrentHashMap<String, Properties> cache = classCaches.get(via);
		Properties ret = cache.get(key);
		if( ret == null ) {
			//  Load outside any lock so a slow class path search does not block every other thread.
			//  If two threads load the same file at the same time, the last one wins (the content is the same).
			ret = readProperties(via, fn);
			cache.put(key, ret);
			if( cache.size() > maxProperties && maxProperties > 0 ) {
				trimPropertyCache(cache);
			}
		}
		return ret;
	}

	/**
	 * Reads the file of this name, and merges the other files of that name the class's loader can see:
	 * the first one found (the first on the class path) wins for each key, the others fill in what it
	 * doesn't define. A class in a named module only sees the file of its own module.
	 * <p>
	 * The first file is found the way it always was, with {@link Class#getResourceAsStream(String)}, so a
	 * class loader that overrides getResourceAsStream is still asked. The loader's other files
	 * (the first of them is usually the same file again, which is harmless) only fill in missing keys.
	 * 
	 * @return the merged Properties, or {@link #MISSING} if there is no file.
	 */
	private static Properties readProperties(Class<?> via, String fn) {
		Properties ret = null;
		try(InputStream in = via.getResourceAsStream(fn)) {
			if( in != null ) {
				ret = load(new Properties(), in, fn);
			}
		} catch(IOException e) {
			// We cannot call the normal logging functions here because it could potentially cause a deadlock.
			System.err.println("Error reading properties for "+fn+" e=("+e+")");
			e.printStackTrace(System.err);
		}

		if( via.getModule().isNamed() ) {
			//  A class in a named module can't see the files of any other module (or the class path)
			return ret == null ? MISSING : ret;
		}

		List<URL> urls = new ArrayList<>();
		try {
			String rn = fn.startsWith("/") ? fn.substring(1) : fn;
			ClassLoader cl = via.getClassLoader();
			Enumeration<URL> e = cl == null ? ClassLoader.getSystemResources(rn) : cl.getResources(rn);
			while( e.hasMoreElements() ) {
				urls.add(e.nextElement());
			}
		} catch(IOException e) {
			System.err.println("Error searching for properties "+fn+" e=("+e+")");
		}

		for(URL url : urls) {
			try(InputStream in = url.openStream()) {
				Properties tmp = load(new Properties(), in, fn);
				if( ret == null ) {
					ret = tmp;
				} else {
					for(String key : tmp.stringPropertyNames()) {
						if( !ret.containsKey(key) ) {
							ret.setProperty(key, tmp.getProperty(key));
						}
					}
				}
			} catch(IOException e) {
				System.err.println("Error reading properties for "+fn+" from "+url+" e=("+e+")");
				e.printStackTrace(System.err);
			}
		}
		return ret == null ? MISSING : ret;
	}

	private static Properties load(Properties p, InputStream in, String fn) throws IOException {
		p.load(in);
		return p;
	}

	/**
	 * Find a property by exactly the name given: nothing is added to it. The first value found is
	 * returned:
	 * <ol>
	 * <li>The object's configuration ({@link #setConfiguration(Properties)}) when it has one, else the system property <code>propertyName</code>.</li>
	 * <li>The properties file named after the class (for example <code>/com/example/Mailer.properties</code>),
	 *     then the properties files of each super class, up to (not including) BaseObject. Inner classes
	 *     use the file of their outer class.</li>
	 * </ol>
	 * A class whose settings share a prefix (a server's <code>parley.mail.pop3</code>) builds the full name
	 * itself, for example with {@link #propertyName(String)}.
	 * <p>
	 * Properties files are cached, see {@link #setMaxProperties(int)} and {@link #clearPropertyCache()}.
	 * <p>
	 * A class in a named module (module path) can't have its <code>/pkg/Name.properties</code> read unless the
	 * module opens the package to this module, so the same file may instead be put in
	 * <code>/META-INF/parley/pkg.Name.properties</code>. On the module path two modules can't contain the same
	 * package, so a file can't be replaced by an extending class there: use system properties, or the file of
	 * the extending class.
	 *
	 * @param propertyName the full name of the property
	 * @return the value of the property, or null if it is not defined.
	 */
	public String getProperty(String propertyName) {
		return getProperty(propertyName,null);
	}


	/**
	 * @return true if {@link #propertyName(String)} puts the property prefix in front of a name (the default)
	 */
	public boolean isSupportPrefixProperty() {
		return supportPrefixProperty;
	}

	/**
	 * Whether {@link #propertyName(String)} uses the property prefix (true, the default) or gives the
	 * name alone.
	 * @param supportPrefixProperty
	 */
	public void setSupportPrefixProperty(boolean supportPrefixProperty) {
		this.supportPrefixProperty = supportPrefixProperty;
	}


	/**
	 * Everything {@link #getProperty(String, String)} needs to know about a class that doesn't depend on
	 * the property being looked up: the classes searched (the class, then its super classes up to
	 * BaseObject), and their properties file names and cache keys. Computed
	 * once per class, so a lookup doesn't build any of these strings again.
	 * <p>
	 * It holds only strings and the classes themselves (a super class can't be unloaded before its
	 * sub class), never the Properties: those stay in the per-class-loader caches, so
	 * {@link BaseObject#clearPropertyCache()} and {@link BaseObject#setMaxProperties(int)} work as before.
	 */
	private static final class LookupPlan {
		private final Class<?> start;
		private final Class<?>[] classes;
		private final String[] fn;
		private final String[] keyViaStart;
		private final String[] keyViaClass;
		private final boolean[] sameSource;
		private final String[] metaFn;
		private final String[] metaKey;

		LookupPlan(Class<?> start) {
			this.start = start;
			java.util.List<Class<?>> chain = new java.util.ArrayList<>();
			for(Class<?> c = start; c != null && c != BaseObject.class; c = c.getSuperclass()) {
				chain.add(c);
			}
			int n = chain.size();
			classes = chain.toArray(new Class<?>[n]);
			fn = new String[n];
			keyViaStart = new String[n];
			keyViaClass = new String[n];
			sameSource = new boolean[n];
			metaFn = new String[n];
			metaKey = new String[n];
			for(int i = 0; i < n; i++) {
				Class<?> c = classes[i];
				String full = c.getName();
				//  An inner class uses the file of its outer class
				String name = full;
				int idx = name.indexOf('$');
				if( idx > 0 ) {
					name = name.substring(0, idx);
				}
				String path = name.length() > 0 ? "/"+name.replace('.', '/') : name;
				fn[i] = path+".properties";
				keyViaStart[i] = cacheKey(start, fn[i]);
				keyViaClass[i] = cacheKey(c, fn[i]);
				sameSource[i] = BaseObject.sameSource(c, start);
				metaFn[i] = META_INF_DIR+name+".properties";
				metaKey[i] = cacheKey(c, metaFn[i]);
			}
		}

		/** The same places, in the same order, as {@link BaseObject#getPropertyEntry(Class, String)}. */
		Properties entry(int i) {
			Properties ret = loadProperties(start, fn[i], keyViaStart[i]);
			if( ret == MISSING && !sameSource[i] ) {
				ret = loadProperties(classes[i], fn[i], keyViaClass[i]);
			}
			if( ret == MISSING ) {
				ret = loadProperties(classes[i], metaFn[i], metaKey[i]);
			}
			return ret;
		}
	}

	private static final ClassValue<LookupPlan> lookupPlans = new ClassValue<>() {
		@Override
		protected LookupPlan computeValue(Class<?> type) {
			return new LookupPlan(type);
		}
	};

	/**
	 * The class whose properties files are searched by {@link #getProperty(String, String)}
	 * (then its super classes, up to BaseObject). The default is the class of this object.
	 * 
	 * @return the class to start the properties file search from.
	 */
	protected Class<?> getPropertyClass() {
		return getClass();
	}

	/**
	 * @param propertyName Name of the property
	 * @param defaultValue returned if the property is not found
	 * @return the value of the property, or defaultValue.
	 * @see #getProperty(String)
	 */
	public String getProperty(String propertyName,String defaultValue) {
		Class<?> start = getPropertyClass();
		String ret;
		if( start == getClass() ) {
			ret = findProperty(lookupPlans.get(start), propertyName);
		} else {
			//  A subclass searches the files of another class (a LogHelper for a plugin class, say);
			//  that class has no plan of its own for this object's class loader, so take the long way.
			ret = findPropertyOf(start, propertyName);
		}
		return ret == null ? defaultValue : ret;
	}

	/**
	 * {@link #getProperty(String, String)} for an object that searches its own class, which is nearly
	 * always: the properties files to search come from its {@link LookupPlan}.
	 */
	private String findProperty(LookupPlan plan, String propertyName) {
		String ret = fromConfiguration(propertyName);
		for(int i = 0; ret == null && i < plan.classes.length; i++) {
			Properties p = plan.entry(i);
			if( p != MISSING ) {
				ret = p.getProperty(propertyName);
			}
		}
		return ret;
	}

	/** The search for an object whose {@link #getPropertyClass()} is not its own class. */
	private String findPropertyOf(Class<?> start, String propertyName) {
		String ret = fromConfiguration(propertyName);
		for(Class<?> cls = start; ret == null && cls != null && cls != BaseObject.class; cls = cls.getSuperclass()) {
			String path = cls.getName();
			int idx = path.indexOf('$');
			if( idx > 0) {
				path = path.substring(0,idx);
			}
			Properties p = getPropertyEntry(cls, path);
			if( p != null && p != MISSING ) {
				ret = p.getProperty(propertyName);
			}
		}
		return ret;
	}



	/**
	 * Get an integer property. If the property is not defined, or is not a valid integer,
	 * the default value is returned (an invalid value is reported to System.err).
	 *  
	 * @param propertyName Name of the property
	 * @param defaultValue value to use if the property is not defined or is invalid
	 * @return the integer value of the property
	 * @see #getProperty(String, String)
	 */
	public int getIntProperty(String propertyName, int defaultValue) {
		String tmp = getProperty(propertyName);
		if( tmp == null ) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(tmp.trim());
		} catch (NumberFormatException e) {
			// Don't use the logger here, getProperty is used while loggers are being created.
			System.err.println("Invalid integer value for property "+propertyName+" ("+tmp+") in "+getClass().getName()+". Using default "+defaultValue);
			return defaultValue;
		}
	}

	/**
	 * Get a duration property: a whole number with a unit ({@code 250ms}, {@code 30s}, {@code 5m},
	 * {@code 1h}, {@code 5d}), or a number of milliseconds (see
	 * {@link us.bringardner.parley.core.util.Durations}). If the property is not defined, or is not a
	 * valid duration, the default is returned (an invalid value is reported to System.err).
	 *
	 * @param propertyName  Name of the property
	 * @param defaultMillis the default, in milliseconds
	 * @return the duration in milliseconds
	 * @since 2.0.0
	 */
	public long getDuration(String propertyName, long defaultMillis) {
		String tmp = getProperty(propertyName);
		if( tmp == null || tmp.trim().isEmpty() ) {
			return defaultMillis;
		}
		try {
			return us.bringardner.parley.core.util.Durations.parse(tmp);
		} catch (IllegalArgumentException e) {
			// Don't use the logger here, getProperty is used while loggers are being created.
			System.err.println("Invalid duration for property "+propertyName+" in "+getClass().getName()+": "+e.getMessage()+". Using default "+us.bringardner.parley.core.util.Durations.format(Math.max(0, defaultMillis)));
			return defaultMillis;
		}
	}

	/**
	 * Get a list of durations, separated by commas ({@code 1m,5m,15m,1h}); see
	 * {@link #getDuration(String, long)}.
	 *
	 * @param propertyName  Name of the property
	 * @param defaultMillis the default, in milliseconds
	 * @return the durations in milliseconds
	 * @since 2.0.0
	 */
	public long[] getDurations(String propertyName, long... defaultMillis) {
		String tmp = getProperty(propertyName);
		if( tmp == null || tmp.trim().isEmpty() ) {
			return defaultMillis.clone();
		}
		try {
			return us.bringardner.parley.core.util.Durations.parseList(tmp);
		} catch (IllegalArgumentException e) {
			System.err.println("Invalid durations for property "+propertyName+" in "+getClass().getName()+": "+e.getMessage()+". Using default "+us.bringardner.parley.core.util.Durations.formatList(defaultMillis));
			return defaultMillis.clone();
		}
	}

	/**
	 * Get a boolean property ("true" in any case is true, anything else is false).
	 * 
	 * @param propertyName Name of the property
	 * @param defaultValue value to use if the property is not defined
	 * @return the boolean value of the property
	 */
	public boolean getBooleanProperty(String propertyName, boolean defaultValue) {
		String tmp = getProperty(propertyName);
		if( tmp == null ) {
			return defaultValue;
		}
		return "true".equalsIgnoreCase(tmp.trim());
	}

	// ------------------------------------------------------------------ logging

	/**
	 * Sets the level of Parley's loggers when nothing else does (NONE, ERROR, WARN, INFO, DEBUG, see
	 * {@link LogLevel#parse(String, LogLevel)}). One logger, or every logger under a name, gets its own
	 * with a longer name: {@code parley.log.level.pop3}, {@code parley.log.level.pop3.server.Pop3Server};
	 * the nearest one set wins (see {@link #levelName(String)}). Read with {@link #getProperty(String)},
	 * so an object with a configuration of its own ({@link #setConfiguration(Properties)}) reads it there.
	 * @since 2.0.0
	 */
	public static final String PROPERTY_LOG_LEVEL = "parley.log.level";

	private static final String PARLEY_PACKAGE = "us.bringardner.parley.";
	private static final String BRINGARDNER_PACKAGE = "us.bringardner.";

	/** The System.Loggers, by name, shared by every object that logs under the name. */
	private static final ConcurrentHashMap<String, System.Logger> systemLoggers = new ConcurrentHashMap<>();

	/** Changed by {@link #refreshLogLevels()}: the {@value #PROPERTY_LOG_LEVEL} settings are read again. */
	private static volatile int levelGeneration;

	/** The level found in the settings, and the {@link #levelGeneration} it was found in. */
	private static final class LevelSetting {
		final LogLevel level;
		final int generation;

		LevelSetting(LogLevel level, int generation) {
			this.level = level;
			this.generation = generation;
		}
	}

	/** Set with setLogLevel; null when it isn't set */
	private volatile LogLevel logLevel;
	/** What the parley.log.level settings say (its level may be null: nothing is set); null until it's looked up */
	private volatile LevelSetting levelSetting;
	private volatile System.Logger systemLogger;

	/**
	 * Set this object's level. It wins over the {@value #PROPERTY_LOG_LEVEL} settings, and is this
	 * object's alone: other objects of the same class keep theirs.
	 *
	 * @param level the level, NONE to log nothing, or null to go back to the settings
	 * @since 2.0.0
	 */
	public void setLogLevel(LogLevel level) {
		this.logLevel = level;
	}

	/**
	 * @return the level set with {@link #setLogLevel(LogLevel)}, or null when none is (the
	 *         {@value #PROPERTY_LOG_LEVEL} settings then decide, and when they don't either, the installed
	 *         System.Logger does)
	 * @since 2.0.0
	 */
	public LogLevel getLogLevel() {
		return logLevel;
	}

	/**
	 * Read the {@value #PROPERTY_LOG_LEVEL} settings again: every object looks up its level again the
	 * next time it logs. An object reads them once, when it first logs, so call this after changing them.
	 * @since 2.0.0
	 */
	public static void refreshLogLevels() {
		levelGeneration++;
	}

	/**
	 * The name this object logs under: the name of its System.Logger, and the name its
	 * {@value #PROPERTY_LOG_LEVEL} settings are looked up by. The default is the class name.
	 *
	 * @return the logger name
	 * @since 2.0.0
	 */
	protected String getLoggerName() {
		return getClass().getName();
	}

	/**
	 * The name a logger's level is set under, after {@value #PROPERTY_LOG_LEVEL}: its name without
	 * {@code us.bringardner.parley.} (or {@code us.bringardner.}), so the logger
	 * us.bringardner.parley.pop3.server.Pop3Server is {@code pop3.server.Pop3Server}, and
	 * {@code parley.log.level.pop3} sets every POP3 logger. Other names are used as they are.
	 *
	 * @param loggerName a logger's name (usually a class name), or null
	 * @return the name to look its level up by, "" for none
	 * @since 2.0.0
	 */
	public static String levelName(String loggerName) {
		if( loggerName == null ) {
			return "";
		}
		String n = loggerName.trim();
		if( n.startsWith(PARLEY_PACKAGE) ) {
			return n.substring(PARLEY_PACKAGE.length());
		}
		if( n.startsWith(BRINGARDNER_PACKAGE) ) {
			return n.substring(BRINGARDNER_PACKAGE.length());
		}
		return n;
	}

	/**
	 * @return the level that decides what this object logs: the one set with
	 *         {@link #setLogLevel(LogLevel)}, else the nearest {@value #PROPERTY_LOG_LEVEL} setting, else
	 *         null (everything goes to the System.Logger, which decides)
	 */
	private LogLevel effectiveLevel() {
		LogLevel ret = logLevel;
		if( ret != null ) {
			return ret;
		}
		LevelSetting s = levelSetting;
		int generation = levelGeneration;
		if( s == null || s.generation != generation ) {
			s = new LevelSetting(findLevel(), generation);
			levelSetting = s;
		}
		return s.level;
	}

	/** The nearest level set: parley.log.level.NAME, then each shorter name, then parley.log.level; null for none. */
	private LogLevel findLevel() {
		String n = levelName(getLoggerName());
		String v = null;
		while( v == null && !n.isEmpty() ) {
			v = setting(PROPERTY_LOG_LEVEL + "." + n);
			int dot = n.lastIndexOf('.');
			n = dot < 0 ? "" : n.substring(0, dot);
		}
		if( v == null ) {
			v = setting(PROPERTY_LOG_LEVEL);
		}
		return v == null ? null : LogLevel.parse(v, null);
	}

	private String setting(String name) {
		String v = getProperty(name);
		return v == null || v.trim().isEmpty() ? null : v;
	}

	/**
	 * @return the System.Logger this object writes to (shared by every object with the same
	 *         {@link #getLoggerName() name})
	 * @since 2.0.0
	 */
	protected System.Logger getSystemLogger() {
		System.Logger ret = systemLogger;
		if( ret == null ) {
			String name = getLoggerName();
			if( name == null ) {
				name = "";
			}
			ret = systemLoggers.get(name);
			if( ret == null ) {
				//  Not computeIfAbsent: the installed LoggerFinder may be loaded now, and it may log
				ret = System.getLogger(name);
				System.Logger prev = systemLoggers.putIfAbsent(name, ret);
				if( prev != null ) {
					ret = prev;
				}
			}
			systemLogger = ret;
		}
		return ret;
	}

	/** Whether this object's level lets a message at this level through (the System.Logger is not asked). */
	private boolean levelAllows(LogLevel level) {
		LogLevel l = effectiveLevel();
		return l == null || l.allows(level);
	}

	private boolean isEnabled(LogLevel level) {
		return levelAllows(level) && getSystemLogger().isLoggable(level.toSystemLevel());
	}

	private void log(LogLevel level, String msg) {
		if( levelAllows(level) ) {
			getSystemLogger().log(level.toSystemLevel(), msg);
		}
	}

	private void log(LogLevel level, String msg, Throwable error) {
		if( levelAllows(level) ) {
			getSystemLogger().log(level.toSystemLevel(), msg, error);
		}
	}

	private void log(LogLevel level, Supplier<String> msg) {
		if( levelAllows(level) ) {
			getSystemLogger().log(level.toSystemLevel(), msg);
		}
	}

	/**
	 * @param msg The message to log if Debug logging is enabled
	 */
	public void logDebug(String msg) {
		log(LogLevel.DEBUG, msg);
	}

	/**
	 * The message is only created if Debug logging is enabled.
	 * Example: logDebug(() -> "value="+expensiveCall());
	 * 
	 * @param msg Supplies the message to log if Debug logging is enabled
	 */
	public void logDebug(Supplier<String> msg) {
		log(LogLevel.DEBUG, msg);
	}

	/**
	 * @param msg The message to log if Debug logging is enabled
	 * @param error The stack trace of the error is logged if Debug is enabled 
	 */
	public void logDebug(String msg, Throwable error) {
		log(LogLevel.DEBUG, msg, error);
	}

	/**
	 * @param msg The message to log if Error logging is enabled
	 */
	public void logError(String msg) {
		log(LogLevel.ERROR, msg);
	}

	/**
	 * The message is only created if Error logging is enabled.
	 * 
	 * @param msg Supplies the message to log if Error logging is enabled
	 */
	public void logError(Supplier<String> msg) {
		log(LogLevel.ERROR, msg);
	}

	/**
	 * @param msg The message to log if Error logging is enabled
	 * @param error The stack trace of the error is logged if Error is enabled 
	 */
	public void logError(String msg, Throwable error) {
		log(LogLevel.ERROR, msg, error);
	}

	/**
	 * @param msg The message to log if Warn logging is enabled
	 */
	public void logWarn(String msg) {
		log(LogLevel.WARN, msg);
	}

	/**
	 * The message is only created if Warn logging is enabled.
	 * 
	 * @param msg Supplies the message to log if Warn logging is enabled
	 */
	public void logWarn(Supplier<String> msg) {
		log(LogLevel.WARN, msg);
	}

	/**
	 * @param msg The message to log if Warn logging is enabled
	 * @param error The stack trace of the error is logged if Warn is enabled 
	 */
	public void logWarn(String msg, Throwable error) {
		log(LogLevel.WARN, msg, error);
	}

	/**
	 * @param msg The message to log if Info logging is enabled
	 */
	public void logInfo(String msg) {
		log(LogLevel.INFO, msg);
	}

	/**
	 * The message is only created if Info logging is enabled.
	 * 
	 * @param msg Supplies the message to log if Info logging is enabled
	 */
	public void logInfo(Supplier<String> msg) {
		log(LogLevel.INFO, msg);
	}

	/**
	 * @param msg The message to log if Info logging is enabled
	 * @param error log the stack trace of the error if Info is enabled 
	 */
	public void logInfo(String msg, Throwable error) {
		log(LogLevel.INFO, msg, error);
	}

	/**
	 * @return true if Debug logging is enabled: this object's level lets it through, and so does its System.Logger
	 */
	public boolean isDebugEnabled() {
		return isEnabled(LogLevel.DEBUG);
	}

	/**
	 * @return true if Error logging is enabled
	 */
	public boolean isErrorEnabled() {
		return isEnabled(LogLevel.ERROR);
	}

	/**
	 * @return true if Warn logging is enabled
	 */
	public boolean isWarnEnabled() {
		return isEnabled(LogLevel.WARN);
	}

	/**
	 * @return true if Info logging is enabled
	 */
	public boolean isInfoEnabled() {
		return isEnabled(LogLevel.INFO);
	}
}
