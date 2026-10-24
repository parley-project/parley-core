package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.util.AbstractCoreServer;
import us.bringardner.parley.core.util.SocketClient;

/**
 * SocketClient checks that a TLS server's certificate was issued for the host it connects to.
 * The trust manager accepts every certificate, so the only thing that can reject one is the host name check.
 */
public class TestTlsHostname {

	private static final String PASSWORD = "test-only";

	/** A TLS server that echoes one byte per connection. */
	static class TlsEchoServer extends AbstractCoreServer {
		TlsEchoServer(File keyStore) {
			super(0, true);
			setKeyStoreFileName(keyStore.getAbsolutePath());
			setKeyStorePassword(PASSWORD);
			setKeyStoreType("PKCS12");
			setAcceptTimeout(60000);
		}

		@Override
		public void run() {
			ServerSocket ss;
			try {
				ss = getServerSocket();
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
			started = running = true;
			try {
				while( !stopping && !ss.isClosed() ) {
					try (Socket s = ss.accept()) {
						s.setSoTimeout(5000);
						int i = s.getInputStream().read();
						s.getOutputStream().write(i);
						s.getOutputStream().flush();
					} catch (SocketTimeoutException e) {
						// check stopping
					} catch (IOException e) {
						// a client that rejected our certificate, or stop() closed the socket
					}
				}
			} finally {
				running = false;
			}
		}
	}

	static class TrustEverything implements X509TrustManager {
		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) {
		}
		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) {
		}
		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}
	}

	private static File dir;
	private static TlsEchoServer localhostServer;
	private static TlsEchoServer otherNameServer;

	private static File createKeyStore(String name, String dname, String san) throws Exception {
		File ks = new File(dir, name);
		String keytool = System.getProperty("java.home")+File.separator+"bin"+File.separator+"keytool";
		ProcessBuilder pb = new ProcessBuilder(keytool, "-genkeypair", "-noprompt",
				"-alias", "server", "-dname", dname,
				"-keystore", ks.getAbsolutePath(), "-storetype", "PKCS12",
				"-storepass", PASSWORD, "-keypass", PASSWORD,
				"-keyalg", "RSA", "-keysize", "2048", "-validity", "2");
		if( san != null ) {
			pb.command().add("-ext");
			pb.command().add("SAN="+san);
		}
		pb.redirectErrorStream(true);
		Process p = pb.start();
		String out = new String(p.getInputStream().readAllBytes());
		assertTrue(p.waitFor(60, TimeUnit.SECONDS), "keytool did not finish");
		assertEquals(0, p.exitValue(), "keytool failed: "+out);
		return ks;
	}

	private static TlsEchoServer start(File keyStore) throws InterruptedException {
		TlsEchoServer svr = new TlsEchoServer(keyStore);
		svr.start();
		long end = System.currentTimeMillis()+10000;
		while( !svr.hasStarted() && System.currentTimeMillis() < end ) {
			Thread.sleep(10);
		}
		assertTrue(svr.isRunning(), "TLS server did not start");
		return svr;
	}

	@BeforeAll
	public static void setUp() throws Exception {
		dir = Files.createTempDirectory("parley-tls").toFile();
		localhostServer = start(createKeyStore("localhost.p12", "CN=localhost", "dns:localhost,ip:127.0.0.1"));
		otherNameServer = start(createKeyStore("other.p12", "CN=bringardner.us", null));
	}

	@AfterAll
	public static void tearDown() throws InterruptedException {
		if( localhostServer != null ) {
			localhostServer.stop(10000, false);
		}
		if( otherNameServer != null ) {
			otherNameServer.stop(10000, false);
		}
		if( dir != null ) {
			for (File f : dir.listFiles()) {
				f.delete();
			}
			dir.delete();
		}
	}

	private static SocketClient client() {
		SocketClient client = new SocketClient(true);
		client.setTrustManagers(new TrustManager[] {new TrustEverything()});
		client.setConnectTimeout(5000);
		client.setSocketTimeout(5000);
		return client;
	}

	private static void echo(Socket s) throws IOException {
		((SSLSocket) s).startHandshake();
		s.getOutputStream().write(7);
		s.getOutputStream().flush();
		assertEquals(7, s.getInputStream().read());
	}

	@Test
	public void testVerifyHostnameIsOnByDefault() {
		SocketClient client = new SocketClient(true);
		assertTrue(client.isVerifyHostname());
		client.setVerifyHostname(false);
		assertFalse(client.isVerifyHostname());
	}

	@Test
	public void testMatchingCertificateIsAccepted() throws Exception {
		try (Socket s = client().getSocket("localhost", localhostServer.getServerSocket().getLocalPort())) {
			echo(s);
		}
	}

	@Test
	public void testCertificateForAnotherHostIsRejected() throws Exception {
		//  getSocket() does the handshake, so the check fails there (not on the first read or write)
		int port = otherNameServer.getServerSocket().getLocalPort();
		assertThrows(SSLException.class, () -> client().getSocket("localhost", port), "A certificate for bringardner.us must not be accepted for localhost");
	}

	@Test
	public void testGetSocketReturnsAfterTheHandshake() throws Exception {
		try (Socket s = client().getSocket("localhost", localhostServer.getServerSocket().getLocalPort())) {
			assertNotNull(((SSLSocket) s).getSession().getPeerCertificates(), "The handshake should be done");
			s.getOutputStream().write(7);
			s.getOutputStream().flush();
			assertEquals(7, s.getInputStream().read());
		}
	}

	@Test
	public void testServerThatNeverAnswersTlsTimesOutInGetSocket() throws Exception {
		//  A plain TCP server that accepts and never says anything: before, getSocket() returned a socket
		//  and the caller's first read or write hung (up to the socket timeout) in the handshake.
		try (ServerSocket silent = new ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())) {
			Thread acceptor = new Thread(() -> {
				try (Socket s = silent.accept()) {
					Thread.sleep(10000);
				} catch (Exception e) {
				}
			});
			acceptor.setDaemon(true);
			acceptor.start();
			SocketClient client = client();
			client.setSocketTimeout(1000);
			long start = System.currentTimeMillis();
			assertThrows(java.net.SocketTimeoutException.class, () -> client.getSocket("localhost", silent.getLocalPort()));
			long elapsed = System.currentTimeMillis()-start;
			assertTrue(elapsed < 5000, "getSocket took "+elapsed+" ms");
			acceptor.interrupt();
		}
	}

	@Test
	public void testFactorySocketsAreCheckedToo() throws Exception {
		//  Sockets from getSocketFactory() (not only getSocket()) must be checked
		SocketFactory factory = client().getSocketFactory();
		try (Socket s = factory.createSocket("localhost", otherNameServer.getServerSocket().getLocalPort())) {
			s.setSoTimeout(5000);
			assertThrows(SSLException.class, () -> echo(s));
		}
	}

	@Test
	public void testCheckCanBeTurnedOff() throws Exception {
		SocketClient client = client();
		client.setVerifyHostname(false);
		try (Socket s = client.getSocket("localhost", otherNameServer.getServerSocket().getLocalPort())) {
			echo(s);
		}
	}

	/**
	 * A TLS factory that can't create unconnected sockets (createSocket() fails, as SocketFactory's
	 * default does) and counts the calls to createSocket(host, port), the one that connects with no timeout.
	 */
	static class ConnectedOnlyTlsFactory extends SSLSocketFactory {
		final SSLSocketFactory delegate;
		final AtomicInteger hostPortCalls = new AtomicInteger();

		ConnectedOnlyTlsFactory(SSLSocketFactory delegate) {
			this.delegate = delegate;
		}
		@Override
		public Socket createSocket() throws IOException {
			throw new SocketException("Unconnected sockets not implemented");
		}
		@Override
		public String[] getDefaultCipherSuites() {
			return delegate.getDefaultCipherSuites();
		}
		@Override
		public String[] getSupportedCipherSuites() {
			return delegate.getSupportedCipherSuites();
		}
		@Override
		public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
			return delegate.createSocket(s, host, port, autoClose);
		}
		@Override
		public Socket createSocket(String host, int port) throws IOException {
			hostPortCalls.incrementAndGet();
			return delegate.createSocket(host, port);
		}
		@Override
		public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
			hostPortCalls.incrementAndGet();
			return delegate.createSocket(host, port, localHost, localPort);
		}
		@Override
		public Socket createSocket(InetAddress host, int port) throws IOException {
			hostPortCalls.incrementAndGet();
			return delegate.createSocket(host, port);
		}
		@Override
		public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
			hostPortCalls.incrementAndGet();
			return delegate.createSocket(address, port, localAddress, localPort);
		}
	}

	/** A client whose factory is a ConnectedOnlyTlsFactory around the one it would have used. */
	private static class ConnectedOnlyTlsClient extends SocketClient {
		ConnectedOnlyTlsFactory factory;

		ConnectedOnlyTlsClient() {
			super(true);
			setTrustManagers(new TrustManager[] {new TrustEverything()});
			setConnectTimeout(5000);
			setSocketTimeout(5000);
		}

		@Override
		public synchronized SocketFactory getSocketFactory() {
			if( factory == null ) {
				try {
					factory = new ConnectedOnlyTlsFactory((SSLSocketFactory) super.getSocketFactory());
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}
			return factory;
		}
	}

	@Test
	public void testTlsFactoryWithoutUnconnectedSocketsUsesTheConnectTimeout() throws Exception {
		//  The plain socket is connected with the timeout and the factory layers TLS on it,
		//  so createSocket(host, port) (no timeout) isn't used, and the host name check still applies.
		ConnectedOnlyTlsClient client = new ConnectedOnlyTlsClient();
		try (Socket s = client.getSocket("localhost", localhostServer.getServerSocket().getLocalPort())) {
			assertTrue(s instanceof SSLSocket);
			assertEquals(5000, s.getSoTimeout(), "The socket was configured");
			echo(s);
		}
		int port = otherNameServer.getServerSocket().getLocalPort();
		assertThrows(SSLException.class, () -> client.getSocket("localhost", port), "A certificate for bringardner.us must not be accepted for localhost");
		assertEquals(0, client.factory.hostPortCalls.get(), "createSocket(host, port) connects with no timeout and must not be used");
	}

	@Test
	public void testTlsFactoryWithoutUnconnectedSocketsAndNoTimeout() throws Exception {
		//  With no connect timeout the factory connects (createSocket(host, port)), as before
		ConnectedOnlyTlsClient client = new ConnectedOnlyTlsClient();
		client.setConnectTimeout(0);
		try (Socket s = client.getSocket("localhost", localhostServer.getServerSocket().getLocalPort())) {
			echo(s);
		}
		assertEquals(1, client.factory.hostPortCalls.get());
	}

	@Test
	public void testTlsFactoryWithoutUnconnectedSocketsTimesOutAStalledConnect() throws Exception {
		//  A server that never accepts: once its backlog is full, the operating system stops
		//  answering new connections (on Linux and macOS), so a connect stalls until it times out.
		List<Socket> filling = new ArrayList<>();
		try (ServerSocket full = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			InetSocketAddress addr = new InetSocketAddress(InetAddress.getLoopbackAddress(), full.getLocalPort());
			boolean stalled = false;
			for (int i = 0; i < 50 && !stalled; i++) {
				Socket s = new Socket();
				filling.add(s);
				try {
					s.connect(addr, 300);
				} catch (SocketTimeoutException e) {
					stalled = true;
				} catch (IOException e) {
					break;
				}
			}
			assumeTrue(stalled, "This system doesn't stall connections to a full backlog");

			ConnectedOnlyTlsClient client = new ConnectedOnlyTlsClient();
			client.setConnectTimeout(500);
			long start = System.currentTimeMillis();
			assertThrows(SocketTimeoutException.class, () -> client.getSocket(addr.getHostString(), addr.getPort()));
			long elapsed = System.currentTimeMillis()-start;
			assertTrue(elapsed < 5000, "getSocket took "+elapsed+" ms");
			assertEquals(0, client.factory.hostPortCalls.get());
		} finally {
			for (Socket s : filling) {
				try {
					s.close();
				} catch (IOException e) {
				}
			}
		}
	}
}
