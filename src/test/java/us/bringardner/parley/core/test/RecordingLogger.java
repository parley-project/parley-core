package us.bringardner.parley.core.test;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

/**
 * A System.Logger that records every message it is asked to log (at or above its level)
 * so tests can verify what was logged without parsing console output. Each message is recorded
 * as the level (ERROR, WARN, INFO or DEBUG), a space and the message.
 */
public class RecordingLogger implements System.Logger {

	public final List<String> messages = Collections.synchronizedList(new ArrayList<>());
	private volatile Level level = Level.ALL;
	private final String name;

	public RecordingLogger() {
		this("recording");
	}

	public RecordingLogger(String name) {
		this.name = name;
	}

	/** Records only messages at this level or above (the default is ALL) */
	public void setLevel(Level level) {
		this.level = level == null ? Level.OFF : level;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public boolean isLoggable(Level target) {
		return level != Level.OFF && target != Level.OFF && target.getSeverity() >= level.getSeverity();
	}

	private static String label(Level target) {
		return target == Level.WARNING ? "WARN" : target.name();
	}

	@Override
	public void log(Level target, ResourceBundle bundle, String msg, Throwable error) {
		if( isLoggable(target) ) {
			messages.add(label(target)+" "+msg+(error == null ? "" : " "+error.getMessage()));
		}
	}

	@Override
	public void log(Level target, ResourceBundle bundle, String format, Object... params) {
		if( isLoggable(target) ) {
			String msg = params == null || params.length == 0 ? format : MessageFormat.format(format, params);
			messages.add(label(target)+" "+msg);
		}
	}
}
