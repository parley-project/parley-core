package us.bringardner.parley.core.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import us.bringardner.parley.core.ILogger;
import us.bringardner.parley.core.LogLevel;

/**
 * An ILogger that records every message it is asked to log (respecting its level)
 * so tests can verify what was logged without parsing console output.
 * Only the abstract methods are implemented, so the ILogger default (Supplier) methods are exercised as-is.
 */
public class RecordingLogger implements ILogger {

	public final List<String> messages = Collections.synchronizedList(new ArrayList<>());
	private volatile LogLevel level = LogLevel.DEBUG;
	private volatile String name;

	public String getName() {
		return name;
	}

	private void record(LogLevel target, String msg, Throwable error) {
		if( isEnabled(target)) {
			messages.add(target+" "+msg+(error == null ? "" : " "+error.getMessage()));
		}
	}

	private boolean isEnabled(LogLevel target) {
		return level != LogLevel.NONE && level.ordinal() >= target.ordinal();
	}

	@Override
	public void init(String name) {
		this.name = name;
	}

	@Override
	public void setLevel(LogLevel level) {
		this.level = level == null ? LogLevel.NONE : level;
	}

	@Override
	public LogLevel getLevel() {
		return level;
	}

	@Override
	public void info(String msg) {
		record(LogLevel.INFO, msg, null);
	}

	@Override
	public void info(String msg, Throwable error) {
		record(LogLevel.INFO, msg, error);
	}

	@Override
	public boolean isInfoEnabled() {
		return isEnabled(LogLevel.INFO);
	}

	@Override
	public void error(String msg) {
		record(LogLevel.ERROR, msg, null);
	}

	@Override
	public void error(String msg, Throwable error) {
		record(LogLevel.ERROR, msg, error);
	}

	@Override
	public boolean isErrorEnabled() {
		return isEnabled(LogLevel.ERROR);
	}

	@Override
	public void debug(String msg) {
		record(LogLevel.DEBUG, msg, null);
	}

	@Override
	public void debug(String msg, Throwable error) {
		record(LogLevel.DEBUG, msg, error);
	}

	@Override
	public boolean isDebugEnabled() {
		return isEnabled(LogLevel.DEBUG);
	}

	@Override
	public void warn(String msg) {
		record(LogLevel.WARN, msg, null);
	}

	@Override
	public void warn(String msg, Throwable error) {
		record(LogLevel.WARN, msg, error);
	}

	@Override
	public boolean isWarnEnabled() {
		return isEnabled(LogLevel.WARN);
	}
}
