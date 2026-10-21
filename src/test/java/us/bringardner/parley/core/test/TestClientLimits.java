package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.util.AbstractCoreServer;

/**
 * The limits per client every server shares: an allow list of client addresses
 * (AllowedClients) and a connection limit per address (MaxConnectionsPerClient),
 * so one host can't take every place under MaxConnections.
 */
public class TestClientLimits {

	static class Server extends AbstractCoreServer {
		@Override
		public void run() {
		}
	}

	private static InetAddress ip(String s) throws Exception {
		return InetAddress.getByName(s);
	}

	@Test
	public void defaultsChangeNothing() throws Exception {
		Server s = new Server();
		assertEquals(0, s.getMaxConnectionsPerClient());
		assertTrue(s.getAllowedClients().isEmpty());
		assertTrue(s.isClientAllowed(ip("192.0.2.1")));
		assertTrue(s.isClientAllowed(null));
		for(int i=0; i < 100; i++ ) {
			assertTrue(s.tryAcquireConnection(ip("192.0.2.1")));
		}
		assertEquals(100, s.getClientConnections(ip("192.0.2.1")));
	}

	@Test
	public void allowList() throws Exception {
		Server s = new Server();
		s.setAllowedClients("127.0.0.1 10.0.0.0/8 2001:db8::/32");
		assertTrue(s.isClientAllowed(ip("127.0.0.1")));
		assertTrue(s.isClientAllowed(ip("10.1.2.3")));
		assertTrue(s.isClientAllowed(ip("2001:db8::5")));
		assertFalse(s.isClientAllowed(ip("192.0.2.1")));
		assertFalse(s.isClientAllowed(null), "an unknown address isn't on the list");
		s.setAllowedClients("");
		assertTrue(s.isClientAllowed(ip("192.0.2.1")));
		assertThrows(IllegalArgumentException.class, () -> s.setAllowedClients("not an address/99"));
	}

	@Test
	public void allowListFromAProperty() throws Exception {
		String key = Server.class.getName()+"."+AbstractCoreServer.PROPERTY_ALLOWED_CLIENTS;
		System.setProperty(key, "198.51.100.0/24");
		try {
			Server s = new Server();
			assertTrue(s.isClientAllowed(ip("198.51.100.7")));
			assertFalse(s.isClientAllowed(ip("198.51.101.7")));
		} finally {
			System.clearProperty(key);
		}
	}

	@Test
	public void perClientLimit() throws Exception {
		Server s = new Server();
		s.setMaxConnectionsPerClient(2);
		InetAddress a = ip("192.0.2.1");
		InetAddress b = ip("192.0.2.2");
		assertTrue(s.tryAcquireConnection(a));
		assertTrue(s.tryAcquireConnection(a));
		assertFalse(s.tryAcquireConnection(a), "a third from the same address");
		assertTrue(s.tryAcquireConnection(b), "another address still gets in");
		assertEquals(3, s.getActiveConnections(), "a refused one holds no global place");
		s.releaseConnection(a);
		assertTrue(s.tryAcquireConnection(a));
		s.releaseConnection(a);
		s.releaseConnection(a);
		s.releaseConnection(b);
		assertEquals(0, s.getActiveConnections());
		assertEquals(0, s.getClientConnections(a));
		s.releaseConnection(a);
		assertEquals(0, s.getClientConnections(a), "never below 0");
	}

	@Test
	public void bothLimitsOrNeither() throws Exception {
		Server s = new Server();
		s.setMaxConnections(1);
		s.setMaxConnectionsPerClient(5);
		assertTrue(s.tryAcquireConnection(ip("192.0.2.1")));
		assertFalse(s.tryAcquireConnection(ip("192.0.2.2")), "the global limit");
		assertEquals(0, s.getClientConnections(ip("192.0.2.2")), "its per-client place was given back");
	}

	@Test
	public void perClientLimitFromAProperty() throws Exception {
		String key = Server.class.getName()+"."+AbstractCoreServer.PROPERTY_MAX_CONNECTIONS_PER_CLIENT;
		System.setProperty(key, "3");
		try {
			assertEquals(3, new Server().getMaxConnectionsPerClient());
		} finally {
			System.clearProperty(key);
		}
	}

	@Test
	public void concurrentClientsNeverExceedTheLimit() throws Exception {
		Server s = new Server();
		s.setMaxConnectionsPerClient(5);
		InetAddress a = ip("192.0.2.9");
		AtomicInteger granted = new AtomicInteger();
		CountDownLatch go = new CountDownLatch(1);
		List<Thread> threads = new ArrayList<Thread>();
		for(int i=0; i < 32; i++ ) {
			Thread t = new Thread(() -> {
				try {
					go.await();
				} catch (InterruptedException e) {
					return;
				}
				if( s.tryAcquireClient(a) ) {
					granted.incrementAndGet();
				}
			});
			t.start();
			threads.add(t);
		}
		go.countDown();
		for(Thread t : threads) {
			t.join();
		}
		assertEquals(5, granted.get());
		assertEquals(5, s.getClientConnections(a));
	}
}
