package us.bringardner.parley.core.test;

import us.bringardner.parley.core.BaseObject;

/**
 * An object whose settings have a prefix and prefixes to fall back to, as the servers of a protocol have.
 * Its file, PrefixTarget.properties, has settings of the prefix parley.test.ftp. TestPropertyPrefixes uses it.
 */
public class PrefixTarget extends BaseObject {

	public PrefixTarget(String... prefixes) {
		if( prefixes.length > 0 ) {
			setPropertyPrefixes(prefixes);
		}
	}

	/** Set the one prefix, as a class does that has no fall back */
	public void prefix(String prefix) {
		setPropertyPrefix(prefix);
	}

	public void prefixes(String... prefixes) {
		setPropertyPrefixes(prefixes);
	}
}
