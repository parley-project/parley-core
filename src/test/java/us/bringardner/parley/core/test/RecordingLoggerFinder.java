package us.bringardner.parley.core.test;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The System.LoggerFinder of parley-core's tests (META-INF/services in the test resources): a
 * {@link RecordingLogger} for each name, so a test can see what BaseObject logged.
 */
public class RecordingLoggerFinder extends System.LoggerFinder {

	private static final ConcurrentHashMap<String, RecordingLogger> LOGGERS = new ConcurrentHashMap<>();

	/**
	 * @param name a logger name
	 * @return the RecordingLogger of that name (the one BaseObject writes to)
	 */
	public static RecordingLogger get(String name) {
		return LOGGERS.computeIfAbsent(name, RecordingLogger::new);
	}

	@Override
	public System.Logger getLogger(String name, Module module) {
		return get(name);
	}
}
