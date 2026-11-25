package us.bringardner.parley.core;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

/**
 * The settings of a settings file and of the files it includes (see
 * {@link BaseObject#findSettingsFile(String)}), and the file each setting is in.
 */
public final class SettingsFile {

	private final String name;
	private final Properties properties;
	private final Map<String, String> origins;

	SettingsFile(String name, Properties properties, Map<String, String> origins) {
		this.name = name;
		this.properties = properties;
		this.origins = origins;
	}

	/** @return the name of the file, as the resolver that found it knows it */
	public String getName() {
		return name;
	}

	/** @return the settings, without the include settings */
	public Properties getProperties() {
		return properties;
	}

	/**
	 * @param setting a name in {@link #getProperties()}
	 * @return the name of the file the setting is in (its last value, when files include each other), which
	 *         is {@link #getName()} unless an included file has it
	 */
	public String getOrigin(String setting) {
		return origins.getOrDefault(setting, name);
	}

	/** @return the file each setting is in */
	public Map<String, String> getOrigins() {
		return Collections.unmodifiableMap(origins);
	}
}
