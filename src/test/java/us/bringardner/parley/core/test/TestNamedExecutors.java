package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.NamedExecutors;

public class TestNamedExecutors {

	private final List<ExecutorService> made = new ArrayList<ExecutorService>();

	private <T extends ExecutorService> T keep(T e) {
		made.add(e);
		return e;
	}

	@AfterEach
	public void shutDown() throws Exception {
		for (ExecutorService e : made) {
			e.shutdownNow();
			assertTrue(e.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	private static Thread runOn(ExecutorService e) throws Exception {
		AtomicReference<Thread> t = new AtomicReference<Thread>();
		e.submit(() -> t.set(Thread.currentThread())).get(5, TimeUnit.SECONDS);
		return t.get();
	}

	@Test
	public void aSingleThreadIsNamedAndDaemon() throws Exception {
		Thread t = runOn(keep(NamedExecutors.singleThread("OneThread")));
		assertEquals("OneThread", t.getName());
		assertTrue(t.isDaemon());
	}

	@Test
	public void aSingleThreadRunsInOrder() throws Exception {
		ExecutorService e = keep(NamedExecutors.singleThread("Ordered"));
		List<Integer> seen = Collections.synchronizedList(new ArrayList<Integer>());
		for (int i = 0; i < 50; i++) {
			int n = i;
			e.execute(() -> seen.add(n));
		}
		e.submit(() -> { }).get(5, TimeUnit.SECONDS);
		for (int i = 0; i < 50; i++) {
			assertEquals(i, seen.get(i).intValue());
		}
	}

	@Test
	public void aScheduledThreadRunsOnAPeriod() throws Exception {
		ScheduledExecutorService s = keep(NamedExecutors.singleThreadScheduled("Periodic"));
		CountDownLatch three = new CountDownLatch(3);
		AtomicReference<String> name = new AtomicReference<String>();
		s.scheduleWithFixedDelay(() -> {
			name.set(Thread.currentThread().getName());
			three.countDown();
		}, 10, 10, TimeUnit.MILLISECONDS);
		assertTrue(three.await(5, TimeUnit.SECONDS));
		assertEquals("Periodic", name.get());
	}

	@Test
	public void aCachedPoolMakesThreadsForTasksThatWaitOnEachOther() throws Exception {
		ExecutorService e = keep(NamedExecutors.cached("Cached"));
		CountDownLatch both = new CountDownLatch(2);
		List<String> names = Collections.synchronizedList(new ArrayList<String>());
		for (int i = 0; i < 2; i++) {
			e.execute(() -> {
				names.add(Thread.currentThread().getName());
				both.countDown();
				try {
					both.await(5, TimeUnit.SECONDS);
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			});
		}
		assertTrue(both.await(5, TimeUnit.SECONDS));
		assertEquals(2, names.stream().distinct().count());
		assertTrue(names.stream().allMatch(n -> n.startsWith("Cached")));
	}

	@Test
	public void aFixedPoolKeepsItsThreads() throws Exception {
		ThreadPoolExecutor e = keep(NamedExecutors.fixed("Fixed", 2));
		assertEquals(2, e.getCorePoolSize());
		assertEquals(2, e.getMaximumPoolSize());
		assertFalse(e.allowsCoreThreadTimeOut());
		assertTrue(runOn(e).getName().startsWith("Fixed"));
		assertEquals(1, NamedExecutors.fixed("Zero", 0).getCorePoolSize());
	}

	@Test
	public void aFixedPoolLetsIdleThreadsGo() throws Exception {
		ThreadPoolExecutor e = keep(NamedExecutors.fixed("Idle", 2, Duration.ofMillis(50)));
		assertTrue(e.allowsCoreThreadTimeOut());
		runOn(e);
		long end = System.currentTimeMillis()+5000;
		while( e.getPoolSize() > 0 && System.currentTimeMillis() < end ) {
			Thread.sleep(20);
		}
		assertEquals(0, e.getPoolSize());
		// and it makes one again for new work
		assertTrue(runOn(e).getName().startsWith("Idle"));
		assertThrows(NullPointerException.class, () -> NamedExecutors.fixed("Null", 1, null));
	}

	@Test
	public void startBeginsAThreadAndNewThreadDoesNot() throws Exception {
		CountDownLatch ran = new CountDownLatch(1);
		AtomicReference<Thread> in = new AtomicReference<Thread>();
		Thread t = NamedExecutors.start("Started", () -> {
			in.set(Thread.currentThread());
			ran.countDown();
		});
		assertTrue(ran.await(5, TimeUnit.SECONDS));
		t.join(5000);
		assertEquals("Started", t.getName());
		assertTrue(t.isDaemon());

		Thread n = NamedExecutors.newThread("NotYet", () -> { });
		assertEquals(Thread.State.NEW, n.getState());
		assertEquals("NotYet", n.getName());
		assertThrows(NullPointerException.class, () -> NamedExecutors.newThread("x", null));
	}
}
