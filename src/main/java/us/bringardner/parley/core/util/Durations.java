package us.bringardner.parley.core.util;

import java.util.Locale;

/**
 * Parley's durations: a whole number with a unit, {@code ms}, {@code s}, {@code m}, {@code h} or
 * {@code d} ({@code 250ms}, {@code 30s}, {@code 5m}, {@code 1h}, {@code 5d}). A number without a unit
 * is milliseconds. The unit's case doesn't matter, and a space may come before it. A list is
 * separated by commas ({@code 1m,5m,15m,1h}).
 * <p>
 * Every Parley setting that is a length of time is read this way, so no setting has its unit in
 * its name.
 *
 * @since 2.0.0
 */
public final class Durations {

	private static final long SECOND = 1000L;
	private static final long MINUTE = 60 * SECOND;
	private static final long HOUR = 60 * MINUTE;
	private static final long DAY = 24 * HOUR;

	private Durations() {
	}

	/**
	 * @param value a duration ({@code 30s}), or a number of milliseconds
	 * @return the duration in milliseconds
	 * @throws IllegalArgumentException when value isn't a duration (empty, negative, not a whole
	 *                                  number, an unknown unit, or too long for a long)
	 */
	public static long parse(String value) {
		if( value == null ) {
			throw new IllegalArgumentException("No duration given");
		}
		String v = value.trim();
		int end = 0;
		while( end < v.length() && Character.isDigit(v.charAt(end)) ) {
			end++;
		}
		if( end == 0 ) {
			throw new IllegalArgumentException("Not a duration: \"" + value + "\" (a whole number with ms, s, m, h or d, e.g. 30s)");
		}
		String unit = v.substring(end).trim().toLowerCase(Locale.ROOT);
		long scale;
		switch (unit) {
		case "":
		case "ms": scale = 1; break;
		case "s": scale = SECOND; break;
		case "m": scale = MINUTE; break;
		case "h": scale = HOUR; break;
		case "d": scale = DAY; break;
		default:
			throw new IllegalArgumentException("Unknown unit in duration \"" + value + "\" (use ms, s, m, h or d)");
		}
		try {
			return Math.multiplyExact(Long.parseLong(v.substring(0, end)), scale);
		} catch (NumberFormatException | ArithmeticException e) {
			throw new IllegalArgumentException("Duration too long: \"" + value + "\"");
		}
	}

	/**
	 * @param value durations separated by commas ({@code 1m,5m,15m})
	 * @return each in milliseconds, in the order given
	 * @throws IllegalArgumentException when one of them isn't a duration, or the list is empty
	 */
	public static long[] parseList(String value) {
		if( value == null || value.trim().isEmpty() ) {
			throw new IllegalArgumentException("No durations given");
		}
		String[] parts = value.split(",");
		long[] ret = new long[parts.length];
		for(int i = 0; i < parts.length; i++) {
			ret[i] = parse(parts[i]);
		}
		return ret;
	}

	/**
	 * The shortest way to write a duration exactly: {@code 5d}, {@code 90m}, {@code 1500ms}.
	 *
	 * @param millis a duration in milliseconds, 0 or more
	 * @return it with the largest unit that divides it
	 */
	public static String format(long millis) {
		if( millis < 0 ) {
			throw new IllegalArgumentException("A duration can't be negative: " + millis);
		}
		if( millis == 0 ) {
			return "0";
		}
		if( millis % DAY == 0 ) {
			return millis / DAY + "d";
		}
		if( millis % HOUR == 0 ) {
			return millis / HOUR + "h";
		}
		if( millis % MINUTE == 0 ) {
			return millis / MINUTE + "m";
		}
		if( millis % SECOND == 0 ) {
			return millis / SECOND + "s";
		}
		return millis + "ms";
	}

	/**
	 * @param millis durations in milliseconds
	 * @return them as {@link #format(long)} writes them, separated by commas
	 */
	public static String formatList(long... millis) {
		StringBuilder ret = new StringBuilder();
		for(long m : millis) {
			if( ret.length() > 0 ) {
				ret.append(',');
			}
			ret.append(format(m));
		}
		return ret.toString();
	}

	/** A duration in milliseconds as an int (for APIs that take one), at most Integer.MAX_VALUE. */
	public static int toIntMillis(long millis) {
		return (int) Math.min(Integer.MAX_VALUE, Math.max(0, millis));
	}
}
