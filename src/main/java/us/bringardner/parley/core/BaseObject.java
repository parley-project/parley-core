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
	public static final String PROPERTY_LOGGER = "parley.core.logger";
	/** Log4j is told this is a logging "wrapper" class, so it reports the code that called logError (etc.) as the caller. */
	private static final String FQCN = BaseObject.class.getName();
	private static volatile Class<?>   loggerClass = null;

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

	//  Loggers are shared by name (like log4j and java.util.logging) so we don't create
	//  (and initialize) a new ILogger for every object instance. Never trimmed: see findLogger().
	private static final ConcurrentHashMap<String, ILogger> loggers = new ConcurrentHashMap<>();

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
	 * Remove all cached ILoggers. The next call to getLogger will create new ones.
	 * Objects that have already obtained a logger will continue to use it.
	 */
	public static void clearLoggerCache() {
		loggers.clear();
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
	 * propertyPrefix provides a "search path" for properties.
	 */
	private volatile String propertyPrefix;

	private volatile ILogger logger ;


	/**
	 * BaseObject constructor  
	 */
	public BaseObject()  {
		super();
	}



	/**
	 * The propertyPrefix is used when searching for properties.
	 * The default is the Class name of this Object.
	 *  
	 * @return the propertyPrefix for this Object
	 * @see #getProperty(String)
	 * @see #getProperty(String, String)
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
	 * Set the propertyPrefix used when searching for properties.
	 * The default is the Class name of this Object.
	 *   
	 * @param propertyPrefix
	 * @see BaseObject#getProperty(String)
	 * @see #getProperty(String, String)
	 * @see #isSupportPrefixProperty()
	 */
	protected void setPropertyPrefix(String propertyPrefix) {
		this.propertyPrefix = propertyPrefix;
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
	 * Find a property. The first value found is returned:
	 * <ol>
	 * <li>The system property <code>prefix.propertyName</code>, where the prefix is the class name
	 *     (see {@link #getPropertyPrefix()} and {@link #setSupportPrefixProperty(boolean)}),
	 *     then <code>superClassName.propertyName</code> for each super class up to (not including) BaseObject.</li>
	 * <li>The system property <code>propertyName</code>.</li>
	 * <li>The properties file named after the class (for example <code>/com/example/Mailer.properties</code>),
	 *     checking <code>prefix.propertyName</code>, <code>className.propertyName</code> (the class the file
	 *     belongs to) then <code>propertyName</code>. Then the properties
	 *     files of each super class, up to (not including) BaseObject. Inner classes use the file of
	 *     their outer class.</li>
	 * </ol>
	 * Properties files are cached, see {@link #setMaxProperties(int)} and {@link #clearPropertyCache()}.
	 * <p>
	 * A class in a named module (module path) can't have its <code>/pkg/Name.properties</code> read unless the
	 * module opens the package to this module, so the same file may instead be put in
	 * <code>/META-INF/parley/pkg.Name.properties</code>. On the module path two modules can't contain the same
	 * package, so a file can't be replaced by an extending class there: use system properties, or the file of
	 * the extending class.
	 *
	 * @param propertyName Name of the property
	 * @return the value of the property, or null if it is not defined.
	 */
	public String getProperty(String propertyName) {
		return getProperty(propertyName,null);
	}


	/**
	 * 
	 * @return
	 */
	public boolean isSupportPrefixProperty() {
		return supportPrefixProperty;
	}

	/**
	 * Enable property logic
	 * @param supportPrefixProperty
	 */
	public void setSupportPrefixProperty(boolean supportPrefixProperty) {
		this.supportPrefixProperty = supportPrefixProperty;
	}


	/**
	 * Everything {@link #getProperty(String, String)} needs to know about a class that doesn't depend on
	 * the property being looked up: the classes searched (the class, then its super classes up to
	 * BaseObject), their properties file names and cache keys, and the "className." prefixes. Computed
	 * once per class, so a lookup doesn't build any of these strings again.
	 * <p>
	 * It holds only strings and the classes themselves (a super class can't be unloaded before its
	 * sub class), never the Properties: those stay in the per-class-loader caches, so
	 * {@link BaseObject#clearPropertyCache()} and {@link BaseObject#setMaxProperties(int)} work as before.
	 */
	private static final class LookupPlan {
		/** Property names whose key strings are kept, so names made up at run time can't grow the map without limit. */
		private static final int MAX_NAMES = 128;

		private final Class<?> start;
		private final Class<?>[] classes;
		private final String[] levelPrefix;
		private final String[] fn;
		private final String[] keyViaStart;
		private final String[] keyViaClass;
		private final boolean[] sameSource;
		private final String[] metaFn;
		private final String[] metaKey;
		/** The default property prefix of an object of this class, its name. */
		private final String defaultPrefix;
		private final ConcurrentHashMap<String, String[]> keys = new ConcurrentHashMap<>();

		LookupPlan(Class<?> start) {
			this.start = start;
			java.util.List<Class<?>> chain = new java.util.ArrayList<>();
			for(Class<?> c = start; c != null && c != BaseObject.class; c = c.getSuperclass()) {
				chain.add(c);
			}
			int n = chain.size();
			classes = chain.toArray(new Class<?>[n]);
			levelPrefix = new String[n];
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
				levelPrefix[i] = full+".";
				fn[i] = path+".properties";
				keyViaStart[i] = cacheKey(start, fn[i]);
				keyViaClass[i] = cacheKey(c, fn[i]);
				sameSource[i] = BaseObject.sameSource(c, start);
				metaFn[i] = META_INF_DIR+name+".properties";
				metaKey[i] = cacheKey(c, metaFn[i]);
			}
			defaultPrefix = start.getName();
		}

		/** @return for each class searched, "className.propertyName" */
		String[] keys(String propertyName) {
			String[] ret = keys.get(propertyName);
			if( ret == null ) {
				ret = new String[levelPrefix.length];
				for(int i = 0; i < ret.length; i++) {
					ret[i] = levelPrefix[i]+propertyName;
				}
				if( keys.size() < MAX_NAMES ) {
					keys.putIfAbsent(propertyName, ret);
				}
			}
			return ret;
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
	 * always: the strings that depend only on the class come from its {@link LookupPlan}, so nothing
	 * is built here unless the property prefix has been changed.
	 */
	private String findProperty(LookupPlan plan, String propertyName) {
		String prefix = getPropertyPrefix();
		String[] keys = plan.keys(propertyName);
		String prefixed = null;
		if( prefix != null ) {
			prefixed = keys.length > 0 && prefix.equals(plan.defaultPrefix) ? keys[0] : prefix+"."+propertyName;
		}

		String ret = null;
		if( prefixed != null ) {
			ret = System.getProperty(prefixed);
			//  The class name of each level is a prefix too, so a property set for a super class
			//  (-Dcom.foo.Base.timeout=5) also applies to its sub classes.
			for(int i = 0; ret == null && i < keys.length; i++) {
				if( keys[i] != prefixed && !keys[i].equals(prefixed) ) {
					ret = System.getProperty(keys[i]);
				}
			}
		}
		if( ret == null ) {
			ret = System.getProperty(propertyName);
		}
		for(int i = 0; ret == null && i < keys.length; i++) {
			Properties p = plan.entry(i);
			if( p != MISSING ) {
				if( prefixed != null ) {
					ret = p.getProperty(prefixed);
					if( ret == null && keys[i] != prefixed && !keys[i].equals(prefixed) ) {
						//  the class whose file this is, even when this object is a sub class of it
						ret = p.getProperty(keys[i]);
					}
				}
				if( ret == null ) {
					ret = p.getProperty(propertyName);
				}
			}
		}
		return ret;
	}

	/** The search for an object whose {@link #getPropertyClass()} is not its own class. */
	private String findPropertyOf(Class<?> start, String propertyName) {
		String ret = null;
		
		String prefix = getPropertyPrefix();
		//  Built once, it is looked up in the system properties and in every class's properties file
		String prefixed = prefix == null ? null : prefix+"."+propertyName;

		if( prefixed!=null ) {
			ret = System.getProperty(prefixed);
			//  The class name of each level is a prefix too, so a property set for a super class
			//  (-Dcom.foo.Base.timeout=5) also applies to its sub classes.
			for(Class<?> cls = start; ret == null && cls != null && cls != BaseObject.class; cls = cls.getSuperclass()) {
				String levelPrefixed = cls.getName()+"."+propertyName;
				if( !levelPrefixed.equals(prefixed) ) {
					ret = System.getProperty(levelPrefixed);
				}
			}
		}
		
		if( ret == null ) {
			ret = System.getProperty(propertyName);
		}

		if( ret == null ) {

			Class<?> cls = start;
			while(ret == null && cls != null && cls != BaseObject.class) {
				String path = cls.getName();
				int idx = path.indexOf('$');
				if( idx > 0) {
					path = path.substring(0,idx);
				}
				Properties p = getPropertyEntry(cls, path);
				if( p != null && p != MISSING ) {
					if( prefixed!=null ) {
						ret = p.getProperty(prefixed);
						if( ret == null ) {
							//  the class whose file this is, even when this object is a sub class of it
							String levelPrefixed = cls.getName()+"."+propertyName;
							if( !levelPrefixed.equals(prefixed) ) {
								ret = p.getProperty(levelPrefixed);
							}
						}
					}
					if(ret==null) {
						ret = p.getProperty(propertyName);
					}
				}
				cls = cls.getSuperclass();
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

	/**
	 * Determine the class to use to implement the ILogger api.
	 * The objective the this class is to implement logging without creating 
	 * runtime dependencies to third party libraries. 
	 * 
	 * 1)  The System.property "parley.core.logger" is defined, that class is used.
	 * 2)  If the log4j2 API (org.apache.logging.log4j) is in the class path, Log4JLogger is used.
	 * 3)  The default is ParleyLogger.
	 * 
	 * @return the Class used to create ILoggers
	 */
	protected static Class<?> getLoggerClass() {
		if( loggerClass == null ) {
			synchronized (BaseObject.class) {
				if( loggerClass == null ) {
					String  tmp = System.getProperty(PROPERTY_LOGGER);
					if( tmp != null ) {
						try {
							Class<?> cls = Class.forName(tmp);
							if( ILogger.class.isAssignableFrom(cls)) {
								loggerClass = cls;
							} else {
								System.err.println("Defined Logger does not implement "+ILogger.class.getName()+". "+PROPERTY_LOGGER+"="+tmp);
							}
						} catch (ClassNotFoundException e) {
							System.err.println("Defined Logger is not availible. "+PROPERTY_LOGGER+"="+tmp);
						}
					}
					if( loggerClass == null ) 	{						
						//  Only with a log4j implementation, the log4j API alone would ignore our logLevel and logFile settings
						loggerClass = Log4JLogger.isLog4jProviderAvailable() ? Log4JLogger.class : ParleyLogger.class;
					}
				}
			}
		}

		return loggerClass;
	}


	/**
	 * @return ILogger for this Object
	 */
	public ILogger getLogger() {
		ILogger ret = logger;
		if( ret == null ) {
			ret = getLogger(getClass().getName());
			logger = ret;
		}

		return ret;
	}

	/**
	 * @param logger ILogger for this object. 
	 */
	public void setLogger(ILogger logger) {
		this.logger = logger;
	}

	/**
	 * @param msg The message to log if Debug logging is enabled
	 */
	public void logDebug(String msg) {
		ILogger l = getLogger();
		if( l != null && l.getClass() == Log4JLogger.class ) {
			//  so log4j reports our caller, not this class, as the caller (a subclass may override the ILogger methods, so only Log4JLogger itself)
			((Log4JLogger) l).log(FQCN, ILogger.Level.DEBUG, msg, Log4JLogger.NO_ERROR);
		} else {
			l.debug(msg);
		}
	}

	/**
	 * The message is only created if Debug logging is enabled.
	 * Example: logDebug(() -> "value="+expensiveCall());
	 * 
	 * @param msg Supplies the message to log if Debug logging is enabled
	 */
	public void logDebug(Supplier<String> msg) {
		ILogger l = getLogger();
		if( l != null && l.getClass() == Log4JLogger.class ) {
			//  so log4j reports our caller, not this class, as the caller (a subclass may override the ILogger methods, so only Log4JLogger itself)
			((Log4JLogger) l).log(FQCN, ILogger.Level.DEBUG, msg);
		} else {
			l.debug(msg);
		}
	}

	/**
	 * @param msg The message to log if Debug logging is enabled
	 * @param error The stack trace of the error is logged if Debug is enabled 
	 */
	public void logDebug(String msg, Throwable error) {
		ILogger l = getLogger();
		if( l != null && l.getClass() == Log4JLogger.class ) {
			//  so log4j reports our caller, not this class, as the caller (a subclass may override the ILogger methods, so only Log4JLogger itself)
			((Log4JLogger) l).log(FQCN, ILogger.Level.DEBUG, msg, error);
		} else {
			l.debug(msg,error);
		}
	}

	/**
	 * @param msg The message to log if Error logging is enabled
	 */
	public void logError(String msg) {
		ILogger l = getLogger();
		if( l != null && l.getClass() == Log4JLogger.class ) {
			//  so log4j reports our caller, not this class, as the caller (a subclass may override the ILogger methods, so only Log4JLogger itself)
			((Log4JLogger) l).log(FQCN, ILogger.Level.ERROR, msg, Log4JLogger.NO_ERROR);
		} else {
			l.error(msg);
		}

	}

	/**
	 * The message is only created if Error logging is enabled.
	 * 
	 * @param msg Supplies the message to log if Error logging is enabled
	 */
	public void logError(Supplier<String> msg) {
		ILogger l = getLogger();
		if( l != null && l.getClass() == Log4JLogger.class ) {
			//  so log4j reports our caller, not this class, as the caller (a subclass may override the ILogger methods, so only Log4JLogger itself)
			((Log4JLogger) l).log(FQCN, ILogger.Level.ERROR, msg);
		} else {
			l.error(msg);
		}
	}

	/**
	 * @param msg The message to log if Error logging is enabled
	 * @param error The stack trace of the error is logged if Error is enabled 
	 */
	public void logError(String msg, Throwable error) {
		ILogger l = getLogger();
		if( l != null && l.getClass() == Log4JLogger.class ) {
			//  so log4j reports our caller, not this class, as the caller (a subclass may override the ILogger methods, so only Log4JLogger itself)
			((Log4JLogger) l).log(FQCN, ILogger.Level.ERROR, msg, error);
		} else {
			l.error(msg,error);
		}

	}

	/**
	 * @param msg The message to log if Warn logging is enabled
	 */
	public void logWarn(String msg) {
		ILogger l = getLogger();
		if( l != null && l.getClass() == Log4JLogger.class ) {
			//  so log4j reports our caller, not this class, as the caller (a subclass may override the ILogger methods, so only Log4JLogger itself)
			((Log4JLogger) l).log(FQCN, ILogger.Level.WARN, msg, Log4JLogger.NO_ERROR);
		} else {
			l.warn(msg);
		}
	}

	/**
	 * The message is only created if Warn logging is enabled.
	 * 
	 * @param msg Supplies the message to log if Warn logging is enabled
	 */
	public void logWarn(Supplier<String> msg) {
		ILogger l = getLogger();
		if( l != null && l.getClass() == Log4JLogger.class ) {
			//  so log4j reports our caller, not this class, as the caller (a subclass may override the ILogger methods, so only Log4JLogger itself)
			((Log4JLogger) l).log(FQCN, ILogger.Level.WARN, msg);
		} else {
			l.warn(msg);
		}
	}

	/**
	 * @param msg The message to log if Warn logging is enabled
	 * @param error The stack trace of the error is logged if Warn is enabled 
	 */
	public void logWarn(String msg, Throwable error) {
		ILogger l = getLogger();
		if( l != null && l.getClass() == Log4JLogger.class ) {
			//  so log4j reports our caller, not this class, as the caller (a subclass may override the ILogger methods, so only Log4JLogger itself)
			((Log4JLogger) l).log(FQCN, ILogger.Level.WARN, msg, error);
		} else {
			l.warn(msg,error);
		}
	}

	/**
	 * @param msg The message to log if Info logging is enabled
	 */
	public void logInfo(String msg) {
		ILogger l = getLogger();
		if( l != null && l.getClass() == Log4JLogger.class ) {
			//  so log4j reports our caller, not this class, as the caller (a subclass may override the ILogger methods, so only Log4JLogger itself)
			((Log4JLogger) l).log(FQCN, ILogger.Level.INFO, msg, Log4JLogger.NO_ERROR);
		} else {
			l.info(msg);
		}
	}

	/**
	 * The message is only created if Info logging is enabled.
	 * 
	 * @param msg Supplies the message to log if Info logging is enabled
	 */
	public void logInfo(Supplier<String> msg) {
		ILogger l = getLogger();
		if( l != null && l.getClass() == Log4JLogger.class ) {
			//  so log4j reports our caller, not this class, as the caller (a subclass may override the ILogger methods, so only Log4JLogger itself)
			((Log4JLogger) l).log(FQCN, ILogger.Level.INFO, msg);
		} else {
			l.info(msg);
		}
	}

	/**
	 * @param msg The message to log if Info logging is enabled
	 * @param error log the stack trace of the error if Info is enabled 
	 */
	public void logInfo(String msg, Throwable error) {
		ILogger l = getLogger();
		if( l != null && l.getClass() == Log4JLogger.class ) {
			//  so log4j reports our caller, not this class, as the caller (a subclass may override the ILogger methods, so only Log4JLogger itself)
			((Log4JLogger) l).log(FQCN, ILogger.Level.INFO, msg, error);
		} else {
			l.info(msg,error);
		}
	}


	/**
	 * @return true is Debug logging is enabled
	 */
	public boolean isDebugEnabled() {
		return getLogger().isDebugEnabled();
	}

	/**
	 * @return true is Error logging is enabled
	 */

	public boolean isErrorEnabled() {
		return getLogger().isErrorEnabled();
	}

	/**
	 * @return true is Warn logging is enabled
	 */
	public boolean isWarnEnabled() {
		return getLogger().isWarnEnabled();
	}

	/**
	 * @return true is Info logging is enabled
	 */

	public boolean isInfoEnabled() {
		return getLogger().isInfoEnabled();
	}



	/**
	 * Find the ILogger for this name.  
	 * ILoggers are cached by name and shared by all objects that use the same name.
	 * 
	 * @param name
	 * @return the ILogger associated with the given name.
	 */
	protected ILogger getLogger(String name) {
		return findLogger(name);
	}

	/**
	 * Find (or create) the shared ILogger for this name.
	 * <p>
	 * Every ILogger created is kept until {@link #clearLoggerCache()} is called (like log4j and
	 * java.util.logging, which also keep their loggers), so use names from a fixed set, such as class
	 * names. A name built from changing data (a user, a session or a request id) adds a logger for
	 * every value, and the cache grows for as long as the application runs.
	 * 
	 * @param name
	 * @return the ILogger associated with the given name.
	 */
	public static ILogger findLogger(String name) {
		if( name == null ) {
			name = "";
		}
		ILogger ret = loggers.get(name);
		if( ret == null ) {
			// Create outside of any lock (ILogger.init may read properties or configuration files).
			// Not using computeIfAbsent because creating a logger could recursively request another logger.
			Class<?> loggerClass = getLoggerClass();
			try {
				ILogger tmp = (ILogger) loggerClass.getDeclaredConstructor().newInstance();
				tmp.init(name);
				ILogger prev = loggers.putIfAbsent(name, tmp);
				ret = prev == null ? tmp : prev;
			} catch (Exception e) {
				throw new IllegalStateException("Fatal error occured attempting to create ILogger. loggerClass="+loggerClass,e);
			}
		}
		return ret;
	}

}
