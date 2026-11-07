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
package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.util.Wildcards;
import us.bringardner.parley.core.util.Wildcards.CaseFolding;

public class TestWildcards {

	@Test
	public void questionMarkIsOneCharacter() {
		assertTrue(Wildcards.matches("a?c", "abc"));
		assertFalse(Wildcards.matches("a?c", "ac"));
		assertFalse(Wildcards.matches("a?c", "abbc"));
	}

	@Test
	public void starIsAnyNumber() {
		assertTrue(Wildcards.matches("a*c", "ac"));
		assertTrue(Wildcards.matches("a*c", "abbbc"));
		assertFalse(Wildcards.matches("a*c", "abcd"));
		assertTrue(Wildcards.matches("*", ""));
		assertTrue(Wildcards.matches("**", "x"));
		assertTrue(Wildcards.matches("*a*b*", "xxaxxbxx"));
		assertFalse(Wildcards.matches("*a*b*", "xxbxxaxx"));
		assertTrue(Wildcards.matches("*.example.net", "host.example.net"));
		assertFalse(Wildcards.matches("*.example.net", "example.net"));
	}

	@Test
	public void wholeTextMustMatch() {
		assertFalse(Wildcards.matches("abc", "abcd"));
		assertFalse(Wildcards.matches("abcd", "abc"));
		assertFalse(Wildcards.matches("", "a"));
		assertTrue(Wildcards.matches("", ""));
	}

	@Test
	public void caseByDefaultMatters() {
		assertFalse(Wildcards.matches("ABC", "abc"));
		assertTrue(Wildcards.matches("ABC", "abc", false, CaseFolding.ASCII));
		assertTrue(Wildcards.matches("*!*@*.example.net", "Tony!tb@HOST.example.net", true, CaseFolding.ASCII));
	}

	@Test
	public void customFolding() {
		CaseFolding braces = new CaseFolding() {
			@Override
			public char fold(char c) {
				return c == '[' ? '{' : c;
			}
		};
		assertTrue(Wildcards.matches("a[*", "a{b", false, braces));
		assertFalse(Wildcards.matches("a[*", "a{b"));
	}

	@Test
	public void escapes() {
		assertTrue(Wildcards.matches("a\\*c", "a*c", true, CaseFolding.EXACT));
		assertFalse(Wildcards.matches("a\\*c", "abc", true, CaseFolding.EXACT));
		assertTrue(Wildcards.matches("a\\?", "a?", true, CaseFolding.EXACT));
		assertFalse(Wildcards.matches("a\\?", "ab", true, CaseFolding.EXACT));
		assertTrue(Wildcards.matches("a\\\\b", "a\\b", true, CaseFolding.EXACT));
		assertTrue(Wildcards.matches("a\\b", "a\\b", true, CaseFolding.EXACT), "\\ before an ordinary character is itself");
		// Without escapes, \ is an ordinary character and * still a wildcard
		assertTrue(Wildcards.matches("a\\*", "a\\xyz"));
		assertFalse(Wildcards.matches("a\\*c", "a*c"));
	}

	@Test
	public void hasWildcards() {
		assertTrue(Wildcards.hasWildcards("a*", false));
		assertTrue(Wildcards.hasWildcards("a?", false));
		assertFalse(Wildcards.hasWildcards("abc", false));
		assertFalse(Wildcards.hasWildcards("a\\*b", true));
		assertTrue(Wildcards.hasWildcards("a\\*b", false));
		assertTrue(Wildcards.hasWildcards("a\\*b*", true));
	}

	@Test
	public void noExponentialBacktracking() {
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < 5000; i++) {
			text.append('a');
		}
		String pattern = "*a*a*a*a*a*a*a*a*a*a*b";
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
			assertFalse(Wildcards.matches(pattern, text.toString()));
		});
	}
}
