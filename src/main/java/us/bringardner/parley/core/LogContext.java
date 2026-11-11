/**
 * <PRE>
 *
 * Copyright 2026 Tony Bringardner
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
 */
package us.bringardner.parley.core;

import java.util.concurrent.Callable;

/**
 * The ID of the work the current thread is doing, a server's session ({@code pop3-4127}) say.
 * {@link BaseObject} puts it in front of every message logged on the thread
 * ({@code [pop3-4127] Can't open the maildrop}), so everything logged for one session can be
 * found by its ID, whichever object logged it and whatever writes the log.
 * <p>
 * The code that starts a piece of work sets the ID for as long as the work runs on the thread:
 *
 * <pre>
 * try (LogContext.Scope scope = LogContext.enter("pop3-4127")) {
 *     // everything logged here, by any BaseObject, starts with [pop3-4127]
 * }
 * </pre>
 *
 * Closing the scope puts back what was there before, so a thread that serves many connections
 * (an NIO reactor, a pool thread) never keeps the ID of work it has finished. A {@link BaseThread}
 * does this itself for its own thread ({@link BaseThread#setLogContext(String)}).
 * <p>
 * The ID belongs to the thread, so it doesn't follow work that is handed to another thread.
 * {@link #wrap(Runnable)} and {@link #wrap(Callable)} make a task that carries it along.
 *
 * @since 2.0.0
 */
public final class LogContext {

	private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

	private LogContext() {
	}

	/** @return the ID of the work this thread is doing, or null when none is set */
	public static String get() {
		return CURRENT.get();
	}

	/**
	 * Sets the ID of the work this thread is doing, until the returned scope is closed.
	 *
	 * @param id the ID; null (or empty) for none, which hides an ID that was set before
	 * @return the scope to close when the work is done (use try-with-resources)
	 */
	public static Scope enter(String id) {
		String before = CURRENT.get();
		set(id);
		return new Scope(before);
	}

	private static void set(String id) {
		if( id == null || id.isEmpty() ) {
			CURRENT.remove();
		} else {
			CURRENT.set(id);
		}
	}

	/**
	 * @param task work for another thread
	 * @return a task that runs it with the ID this thread has now (the task itself when there is none)
	 */
	public static Runnable wrap(Runnable task) {
		return wrap(CURRENT.get(), task);
	}

	/**
	 * @param id the ID the task's messages get; null for none
	 * @param task work for another thread
	 * @return a task that runs it with that ID (the task itself when the ID is null or empty)
	 */
	public static Runnable wrap(String id, Runnable task) {
		if( task == null || id == null || id.isEmpty() ) {
			return task;
		}
		return new Runnable() {
			@Override
			public void run() {
				try (Scope scope = enter(id)) {
					task.run();
				}
			}
		};
	}

	/**
	 * @param <T> what the task returns
	 * @param task work for another thread
	 * @return a task that runs it with the ID this thread has now (the task itself when there is none)
	 */
	public static <T> Callable<T> wrap(Callable<T> task) {
		String id = CURRENT.get();
		if( task == null || id == null ) {
			return task;
		}
		return new Callable<T>() {
			@Override
			public T call() throws Exception {
				try (Scope scope = enter(id)) {
					return task.call();
				}
			}
		};
	}

	/** Returned by {@link LogContext#enter(String)}: closing it puts back the ID the thread had before. */
	public static final class Scope implements AutoCloseable {
		private final String before;
		private boolean closed;

		private Scope(String before) {
			this.before = before;
		}

		/** Puts back the ID the thread had before {@link LogContext#enter(String)}. Closing it twice does nothing more. */
		@Override
		public void close() {
			if( !closed ) {
				closed = true;
				set(before);
			}
		}
	}
}
