/**
 * Copyright 1998-2026 Tony Bringardner
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *	@author Tony Bringardner
 */
package us.bringardner.parley.core;

import java.util.Locale;

/**
 * The levels Parley logs at, least to most detail. An object's level ({@link BaseObject#setLogLevel(LogLevel)}
 * or the {@value BaseObject#PROPERTY_LOG_LEVEL} settings) lets through messages at that level and the
 * levels before it: WARN lets ERROR and WARN through. NONE lets nothing through.
 * <p>
 * Messages are written through the JDK's {@link System.Logger}; {@link #toSystemLevel()} and
 * {@link #of(System.Logger.Level)} convert between the two.
 *
 * @since 2.0.0
 */
public enum LogLevel {
	NONE,
	ERROR,
	WARN,
	INFO,
	DEBUG;

	/**
	 * @param message the level of a message (ERROR, WARN, INFO or DEBUG)
	 * @return true if this level lets a message at that level through
	 */
	public boolean allows(LogLevel message) {
		return this != NONE && message != NONE && message.ordinal() <= ordinal();
	}

	/**
	 * @return the System.Logger level a message at this level is logged at (NONE is OFF)
	 */
	public System.Logger.Level toSystemLevel() {
		switch (this) {
		case ERROR:
			return System.Logger.Level.ERROR;
		case WARN:
			return System.Logger.Level.WARNING;
		case INFO:
			return System.Logger.Level.INFO;
		case DEBUG:
			return System.Logger.Level.DEBUG;
		default:
			return System.Logger.Level.OFF;
		}
	}

	/**
	 * @param level a System.Logger level, or null
	 * @return the nearest LogLevel: TRACE and ALL are DEBUG, OFF (and null) is NONE
	 */
	public static LogLevel of(System.Logger.Level level) {
		if( level == null ) {
			return NONE;
		}
		switch (level) {
		case ALL:
		case TRACE:
		case DEBUG:
			return DEBUG;
		case INFO:
			return INFO;
		case WARNING:
			return WARN;
		case ERROR:
			return ERROR;
		default:
			return NONE;
		}
	}

	/**
	 * Convert a setting's value to a LogLevel (not case sensitive). The names other logging frameworks
	 * use are accepted too: OFF, SEVERE, FATAL, WARNING, TRACE, ALL, FINE, FINER, FINEST.
	 *
	 * @param value        the value, or null
	 * @param defaultLevel returned when value is null, empty or invalid (an invalid value is reported to System.err)
	 * @return the level
	 */
	public static LogLevel parse(String value, LogLevel defaultLevel) {
		if( value == null || value.trim().isEmpty() ) {
			return defaultLevel;
		}
		String tmp = value.trim().toUpperCase(Locale.ROOT);
		switch (tmp) {
		case "OFF":
			return NONE;
		case "SEVERE":
		case "FATAL":
			return ERROR;
		case "WARNING":
			return WARN;
		case "TRACE":
		case "ALL":
		case "FINE":
		case "FINER":
		case "FINEST":
			return DEBUG;
		default:
			try {
				return valueOf(tmp);
			} catch (IllegalArgumentException e) {
				//  Not logged: the level is being worked out for a logger
				System.err.println("Invalid log level (" + value + "). Using " + defaultLevel);
				return defaultLevel;
			}
		}
	}
}
