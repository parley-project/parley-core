/**
 * <PRE>
 *
 * Copyright Tony Bringardner 2026 <A href="http://bringardner.com/tony">Tony Bringardner</A>
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
 *
 *	@author Tony Bringardner
 *
 *
 */
package us.bringardner.parley.core.util;

/**
 * Wildcard patterns: {@code ?} matches exactly one character and {@code *} any number of characters
 * (none included); every other character matches itself. The whole text must match.
 * <p>
 * Protocols differ in two ways, so both can be chosen:
 * <ul>
 * <li>an escape: with {@code escapes} true, a {@code \} before {@code *}, {@code ?} or {@code \}
 * makes it an ordinary character (IRC masks, RFC 2812 section 2.5); a {@code \} before anything
 * else is itself</li>
 * <li>how characters compare: a {@link CaseFolding} maps each character before comparing, e.g.
 * {@link CaseFolding#ASCII} for host names, or IRC's case mapping, where {}|^ are the lower case of
 * []\~</li>
 * </ul>
 * <pre>
 * Wildcards.matches("*.example.net", "host.example.net");                      // true
 * Wildcards.matches("*!*@*.example.net", "Tony!tb@HOST.example.net", true, CaseFolding.ASCII); // true
 * </pre>
 * Matching takes time proportional to the pattern's length times the text's at worst, never more.
 *
 * @since 2.0.0
 */
public final class Wildcards {

	/**
	 * How characters compare: two characters match when {@link #fold} gives the same result for
	 * both.
	 */
	public interface CaseFolding {

		/** @return the character to compare in place of c (e.g. its lower case) */
		char fold(char c);

		/** Every character is compared as it is */
		CaseFolding EXACT = new CaseFolding() {
			@Override
			public char fold(char c) {
				return c;
			}
		};

		/** A-Z compare equal to a-z; every other character as it is */
		CaseFolding ASCII = new CaseFolding() {
			@Override
			public char fold(char c) {
				return c >= 'A' && c <= 'Z' ? (char) (c+('a'-'A')) : c;
			}
		};
	}

	private Wildcards() {
	}

	/**
	 * Case matters and {@code \} is an ordinary character.
	 *
	 * @return true if the whole text matches the pattern
	 */
	public static boolean matches(String pattern, String text) {
		return matches(pattern, text, false, CaseFolding.EXACT);
	}

	/**
	 * @param pattern the pattern
	 * @param text what to test
	 * @param escapes true if {@code \} escapes {@code *}, {@code ?} and {@code \}
	 * @param folding how characters compare
	 * @return true if the whole text matches the pattern
	 */
	public static boolean matches(String pattern, String text, boolean escapes, CaseFolding folding) {
		int p = 0;
		int t = 0;
		// Where to go back to when what follows the last '*' fails: the pattern after it, and the text
		int starPattern = -1;
		int starText = -1;
		while( t < text.length() ) {
			if( p < pattern.length() ) {
				char c = pattern.charAt(p);
				if( c == '*' ) {
					starPattern = ++p;
					starText = t;
					continue;
				}
				if( c == '?' ) {
					p++;
					t++;
					continue;
				}
				int len = 1;
				if( escapes && c == '\\' && p+1 < pattern.length() && isSpecial(pattern.charAt(p+1)) ) {
					c = pattern.charAt(p+1);
					len = 2;
				}
				if( folding.fold(c) == folding.fold(text.charAt(t)) ) {
					p += len;
					t++;
					continue;
				}
			}
			if( starPattern < 0 ) {
				return false;
			}
			// Let the last '*' take one more character
			p = starPattern;
			t = ++starText;
		}
		while( p < pattern.length() && pattern.charAt(p) == '*' ) {
			p++;
		}
		return p == pattern.length();
	}

	private static boolean isSpecial(char c) {
		return c == '*' || c == '?' || c == '\\';
	}

	/**
	 * @param escapes true if {@code \} escapes {@code *}, {@code ?} and {@code \}
	 * @return true if the pattern has a {@code *} or {@code ?} that isn't escaped
	 */
	public static boolean hasWildcards(String pattern, boolean escapes) {
		for (int i = 0; i < pattern.length(); i++) {
			char c = pattern.charAt(i);
			if( escapes && c == '\\' && i+1 < pattern.length() && isSpecial(pattern.charAt(i+1)) ) {
				i++;
			} else if( c == '*' || c == '?' ) {
				return true;
			}
		}
		return false;
	}
}
