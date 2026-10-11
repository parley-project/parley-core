package us.bringardner.parley.core;

/**
 * Creates the threads {@link BaseThread} runs on.
 * <p>
 * This is the Java 21 version, packaged in META-INF/versions/21 of the multi-release jar and
 * used only by Java 21+ JVMs; Java 11-20 use src/main/java/us/bringardner/parley/core/Threads.java.
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

	/** @return true: this JVM can create virtual threads. */
	static boolean virtualSupported() {
		return true;
	}

	/**
	 * @param task what the thread runs
	 * @param virtual true for a virtual thread, false for a platform thread
	 * @return a new, unstarted thread
	 */
	static Thread create(Runnable task, boolean virtual) {
		return virtual ? Thread.ofVirtual().unstarted(task) : new Thread(task);
	}

	/** @return true if the thread is a virtual thread */
	static boolean isVirtual(Thread thread) {
		return thread != null && thread.isVirtual();
	}
}
