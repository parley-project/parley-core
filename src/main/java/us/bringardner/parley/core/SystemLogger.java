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
 *  1) System.Logger can't set a level; the logging system behind it decides what is logged, so
 *     this logger passes every message on and setLevel does nothing. Set levels in that system's own
 *     configuration (log4j2.xml, logback.xml, logging.properties).
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
	 * Does nothing: the logging system behind System.Logger decides what is logged.
	 *
	 * @param level ignored
	 */
	public void setLevel(LogLevel level) {
	}

	/**
	 * @return the most detailed level the logging system logs for this name (NONE when it logs nothing)
	 */
	public LogLevel getLevel() {
		LogLevel ret;
		{
			System.Logger tmp = getLogger();
			if( tmp.isLoggable(System.Logger.Level.DEBUG) ) {
				ret = LogLevel.DEBUG;
			} else if( tmp.isLoggable(System.Logger.Level.INFO) ) {
				ret = LogLevel.INFO;
			} else if( tmp.isLoggable(System.Logger.Level.WARNING) ) {
				ret = LogLevel.WARN;
			} else if( tmp.isLoggable(System.Logger.Level.ERROR) ) {
				ret = LogLevel.ERROR;
			} else {
				ret = LogLevel.NONE;
			}
		}
		return ret;
	}

	//  The System.Logger level for each ILogger level (NONE is never logged)
	private static System.Logger.Level toSystem(LogLevel level) {
		switch (level) {
		case ERROR: return System.Logger.Level.ERROR;
		case WARN:  return System.Logger.Level.WARNING;
		case INFO:  return System.Logger.Level.INFO;
		case DEBUG: return System.Logger.Level.DEBUG;
		default:    return System.Logger.Level.OFF;
		}
	}

	private boolean isEnabled(LogLevel wanted) {
		return getLogger().isLoggable(toSystem(wanted));
	}

	private void log(LogLevel wanted, String msg, Throwable error) {
		if( isEnabled(wanted) ) {
			if( error == null ) {
				getLogger().log(toSystem(wanted), msg);
			} else {
				getLogger().log(toSystem(wanted), msg, error);
			}
		}
	}

	public void debug(String msg) {
		log(LogLevel.DEBUG, msg, null);
	}

	public void debug(String msg, Throwable error) {
		log(LogLevel.DEBUG, msg, error);
	}

	public void info(String msg) {
		log(LogLevel.INFO, msg, null);
	}

	public void info(String msg, Throwable error) {
		log(LogLevel.INFO, msg, error);
	}

	public void warn(String msg) {
		log(LogLevel.WARN, msg, null);
	}

	public void warn(String msg, Throwable error) {
		log(LogLevel.WARN, msg, error);
	}

	public void error(String msg) {
		log(LogLevel.ERROR, msg, null);
	}

	public void error(String msg, Throwable error) {
		log(LogLevel.ERROR, msg, error);
	}

	public boolean isDebugEnabled() {
		return isEnabled(LogLevel.DEBUG);
	}

	public boolean isInfoEnabled() {
		return isEnabled(LogLevel.INFO);
	}

	public boolean isWarnEnabled() {
		return isEnabled(LogLevel.WARN);
	}

	public boolean isErrorEnabled() {
		return isEnabled(LogLevel.ERROR);
	}

}
