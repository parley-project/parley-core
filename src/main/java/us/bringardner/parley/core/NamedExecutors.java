package us.bringardner.parley.core;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * <PRE>
 * The executors and threads Parley code needs, each made with a {@link NamedThreadFactory}: the threads are named (they
 * show up by name in a thread dump) and are daemon threads (they don't keep the JVM running). Code in Parley makes its
 * threads here, or with a NamedThreadFactory or a {@link BaseThread}, and not with new Thread(...) or
 * the bare java.util.concurrent.Executors.
 *
 * ExecutorService one = NamedExecutors.singleThread("NfsCallbacks");     thread "NfsCallbacks"
 * ExecutorService many = NamedExecutors.cached("NfsConnection");        threads "NfsConnection", "NfsConnection-2", ...
 * NamedExecutors.start("Acceptor", () -&gt; accept());                    a thread that has begun
 *
 * The caller shuts down what it makes. For threads that aren't daemon threads, or that are virtual threads, make the
 * executor with NamedThreadFactory directly.
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
 *  @author Tony Bringardner
 *  @since 2.0.0
 */
public final class NamedExecutors {

	private NamedExecutors() {
	}

	/**
	 * @param name the thread's name
	 * @return an executor of one thread: its tasks run one at a time, in the order they were given
	 */
	public static ExecutorService singleThread(String name) {
		return Executors.newSingleThreadExecutor(new NamedThreadFactory(name));
	}

	/**
	 * @param name the thread's name
	 * @return an executor of one thread that also runs tasks later and on a period
	 */
	public static ScheduledExecutorService singleThreadScheduled(String name) {
		return Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(name));
	}

	/**
	 * @param name the threads' name, then name-2, name-3 ...
	 * @return an executor that makes a thread for a task when none is free and lets one go after 60 seconds idle
	 */
	public static ExecutorService cached(String name) {
		return Executors.newCachedThreadPool(new NamedThreadFactory(name));
	}

	/**
	 * @param name the threads' name, then name-2, name-3 ...
	 * @param threads how many threads run tasks; the others wait in a queue
	 * @return an executor of that many threads, which stay until it is shut down
	 */
	public static ThreadPoolExecutor fixed(String name, int threads) {
		int n = Math.max(1, threads);
		return new ThreadPoolExecutor(n, n, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), new NamedThreadFactory(name));
	}

	/**
	 * @param name the threads' name, then name-2, name-3 ...
	 * @param threads how many threads run tasks at most; the others wait in a queue
	 * @param idle how long a thread with nothing to do lives before it ends (new ones are made when there is work)
	 * @return an executor of that many threads, whose idle threads end
	 */
	public static ThreadPoolExecutor fixed(String name, int threads, Duration idle) {
		ThreadPoolExecutor ret = fixed(name, threads);
		ret.setKeepAliveTime(Math.max(1, Objects.requireNonNull(idle, "idle").toMillis()), TimeUnit.MILLISECONDS);
		ret.allowCoreThreadTimeOut(true);
		return ret;
	}

	/**
	 * @param name the thread's name
	 * @param task what it runs
	 * @return a new daemon thread that has not begun
	 */
	public static Thread newThread(String name, Runnable task) {
		return new NamedThreadFactory(name).newThread(Objects.requireNonNull(task, "task"));
	}

	/**
	 * @param name the thread's name
	 * @param task what it runs
	 * @return a new daemon thread that has begun
	 */
	public static Thread start(String name, Runnable task) {
		Thread ret = newThread(name, task);
		ret.start();
		return ret;
	}
}
