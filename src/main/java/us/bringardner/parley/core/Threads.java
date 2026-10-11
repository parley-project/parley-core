package us.bringardner.parley.core;

/**
 * Creates the threads {@link BaseThread} runs on.
 * <p>
 * This is the Java 11 version: virtual threads don't exist, so every thread is a platform
 * thread. parley-core is a multi-release jar: on Java 21 and later the JVM loads the copy in
 * META-INF/versions/21 instead (source in src/main/java21), which can create virtual threads.
 * Both copies must keep the same methods.
 *
 * <PRE>
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
 * @author Tony Bringardner
 */
final class Threads {

	private Threads() {
	}

	/** @return true if this JVM can create virtual threads (Java 21+). */
	static boolean virtualSupported() {
		return false;
	}

	/**
	 * @param task what the thread runs
	 * @param virtual ignored here: there are no virtual threads before Java 21
	 * @return a new, unstarted platform thread
	 */
	static Thread create(Runnable task, boolean virtual) {
		return new Thread(task);
	}

	/** @return true if the thread is a virtual thread (never, before Java 21) */
	static boolean isVirtual(Thread thread) {
		return false;
	}
}
