package us.bringardner.parley.core.test;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

import us.bringardner.parley.core.LogLevel;

/**
 * A System.Logger that records every message it is given ("LEVEL message [error message]"),
 * when its level lets it through (DEBUG, everything, unless a test sets another). parley-core's tests
 * install {@link RecordingLoggerFinder}, so this is what BaseObject writes to; get one by name with
 * {@link RecordingLoggerFinder#get(String)}.
 */
public class RecordingLogger implements System.Logger {

	public final List<String> messages = Collections.synchronizedList(new ArrayList<>());
	private final String name;
	private volatile LogLevel level = LogLevel.DEBUG;

	public RecordingLogger(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}

	public void setLevel(LogLevel level) {
		this.level = level == null ? LogLevel.NONE : level;
	}

	@Override
	public boolean isLoggable(Level target) {
		return level.allows(LogLevel.of(target));
	}

	@Override
	public void log(Level target, ResourceBundle bundle, String msg, Throwable error) {
		if( isLoggable(target) ) {
			messages.add(LogLevel.of(target)+" "+msg+(error == null ? "" : " "+error.getMessage()));
		}
	}

	@Override
	public void log(Level target, ResourceBundle bundle, String format, Object... params) {
		if( isLoggable(target) ) {
			String msg = params == null || params.length == 0 ? format : MessageFormat.format(format, params);
			messages.add(LogLevel.of(target)+" "+msg);
		}
	}
}
