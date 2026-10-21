package us.bringardner.parley.core.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import us.bringardner.parley.core.BaseObject;

/**
 * Reads a system property that was renamed: the new name wins; the old name still
 * works but logs (once per name) that it is deprecated.
 * <p>
 * From parley-dns, whose properties are all named JDns.* now.
 *
 * @author Tony Bringardner
 */
public final class RenamedProperty extends BaseObject {

	private static final RenamedProperty LOG = new RenamedProperty();
	private static final Set<String> warned = ConcurrentHashMap.newKeySet();

	private RenamedProperty() {
	}

	/**
	 * @return the value of name, else of oldName (logging once that it is deprecated),
	 *  else null
	 */
	public static String get(String name, String oldName) {
		String v = System.getProperty(name);
		if( v != null ) {
			return v;
		}
		v = oldName == null ? null : System.getProperty(oldName);
		if( v != null && warned.add(oldName) ) {
			LOG.logWarn("Property "+oldName+" is deprecated: use "+name);
		}
		return v;
	}

	/** Like {@link #get(String, String)}, with a default. */
	public static String get(String name, String oldName, String def) {
		String v = get(name, oldName);
		return v == null ? def : v;
	}
}
