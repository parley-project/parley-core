// ~version~V000.00.01-V000.00.00-
package us.bringardner.parley.core;

/**
 * <PRE>
 *  An implementation of ILogger that uses the JDK's System.Logger (java.lang.System.Logger, Java 9+).
 *
 *  Choose it with the system property parley.core.logger=us.bringardner.parley.core.SystemLogger.
 *  It is never chosen on its own.
 *
 *  System.Logger is only a front end: where the messages go is decided by the logging system
 *  installed behind it. With nothing else installed that is java.util.logging. An application on
 *  SLF4J/Logback or log4j can add that library's System.Logger provider (slf4j-jdk-platform-logging,
 *  log4j-jpl) and Parley's messages then go straight to it.
 *
 *  Two things work differently from the other ILoggers:
 *
 *  1) System.Logger can't set a level; the logging system behind it decides what is logged.
 *     setLevel sets a level of this logger's own, in front of that: a message is logged only when
 *     both this level and the logging system allow it. setLevel(null) removes it again.
 *
 *  2) System.Logger can't be told which code logged. The logging system works that out itself
 *     and may report SystemLogger or BaseObject as the source of a message.
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
 *  @since 2.0.0
 */
public class SystemLogger implements ILogger {

	private volatile System.Logger logger;

	//  This logger's own level, or null to leave it all to the logging system
	private volatile Level level;

	/**
	 * @return the System.Logger messages go to
	 */
	public System.Logger getLogger() {
		if( logger == null ) {
			init(null);
		}
		return logger;
	}

	public void init(String name) {
		if( name == null || name.isEmpty() ) {
			name = getClass().getName();
		}
		logger = System.getLogger(name);
	}

	/**
	 * Set this logger's own level. Messages above it are not logged, whatever the logging system
	 * allows; messages at or below it are logged if the logging system allows them too.
	 *
	 * @param level the level, or null to leave it all to the logging system again
	 */
	public void setLevel(Level level) {
		this.level = level;
	}

	/**
	 * @return the level set with setLevel, or, when none is, the most detailed level the logging
	 *         system logs for this name (NONE when it logs nothing)
	 */
	public Level getLevel() {
		Level ret = level;
		if( ret == null ) {
			System.Logger tmp = getLogger();
			if( tmp.isLoggable(System.Logger.Level.DEBUG) ) {
				ret = Level.DEBUG;
			} else if( tmp.isLoggable(System.Logger.Level.INFO) ) {
				ret = Level.INFO;
			} else if( tmp.isLoggable(System.Logger.Level.WARNING) ) {
				ret = Level.WARN;
			} else if( tmp.isLoggable(System.Logger.Level.ERROR) ) {
				ret = Level.ERROR;
			} else {
				ret = Level.NONE;
			}
		}
		return ret;
	}

	//  The System.Logger level for each ILogger level (NONE is never logged)
	private static System.Logger.Level toSystem(Level level) {
		switch (level) {
		case ERROR: return System.Logger.Level.ERROR;
		case WARN:  return System.Logger.Level.WARNING;
		case INFO:  return System.Logger.Level.INFO;
		case DEBUG: return System.Logger.Level.DEBUG;
		default:    return System.Logger.Level.OFF;
		}
	}

	private boolean isEnabled(Level wanted) {
		Level own = level;
		//  The levels are in order NONE, ERROR, WARN, INFO, DEBUG
		if( own != null && wanted.ordinal() > own.ordinal() ) {
			return false;
		}
		return getLogger().isLoggable(toSystem(wanted));
	}

	private void log(Level wanted, String msg, Throwable error) {
		if( isEnabled(wanted) ) {
			if( error == null ) {
				getLogger().log(toSystem(wanted), msg);
			} else {
				getLogger().log(toSystem(wanted), msg, error);
			}
		}
	}

	public void debug(String msg) {
		log(Level.DEBUG, msg, null);
	}

	public void debug(String msg, Throwable error) {
		log(Level.DEBUG, msg, error);
	}

	public void info(String msg) {
		log(Level.INFO, msg, null);
	}

	public void info(String msg, Throwable error) {
		log(Level.INFO, msg, error);
	}

	public void warn(String msg) {
		log(Level.WARN, msg, null);
	}

	public void warn(String msg, Throwable error) {
		log(Level.WARN, msg, error);
	}

	public void error(String msg) {
		log(Level.ERROR, msg, null);
	}

	public void error(String msg, Throwable error) {
		log(Level.ERROR, msg, error);
	}

	public boolean isDebugEnabled() {
		return isEnabled(Level.DEBUG);
	}

	public boolean isInfoEnabled() {
		return isEnabled(Level.INFO);
	}

	public boolean isWarnEnabled() {
		return isEnabled(Level.WARN);
	}

	public boolean isErrorEnabled() {
		return isEnabled(Level.ERROR);
	}

}
