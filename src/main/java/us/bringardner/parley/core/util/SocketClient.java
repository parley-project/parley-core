/**
 * <PRE>
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
 *   
 *	@author Tony Bringardner   
 *
 *
 * ~version~V000.01.02-V000.00.01-V000.00.00-
 */
package us.bringardner.parley.core.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import us.bringardner.parley.core.SecureBaseObject;


public class SocketClient extends SecureBaseObject {


	public static final String PROPERTY_SOCKET_TIMEOUT = SocketOptions.PROPERTY_SOCKET_TIMEOUT;

	public static final int DEFAULT_SOCKET_TIMEOUT = SocketOptions.DEFAULT_SOCKET_TIMEOUT;

	public static final String PROPERTY_SO_LINGER = SocketOptions.PROPERTY_SO_LINGER;

	/**
	 * SO_LINGER is in SECONDS (see Socket.setSoLinger).
	 * This was 60000 (almost 17 hours) which could block Socket.close() for a very long time.
	 */
	public static final int DEFAULT_SO_LINGER = SocketOptions.DEFAULT_SO_LINGER;

	public static final String PROPERTY_IS_SO_LINGER = SocketOptions.PROPERTY_IS_SO_LINGER;

	/** How long (in milliseconds) {@link #getSocket(String, int)} waits for the connection to be made. */
	public static final String PROPERTY_CONNECT_TIMEOUT = "ConnectTimeout";

	/**
	 * 60 seconds. Before this setting existed there was no limit, so an unreachable host
	 * blocked for as long as the operating system kept trying (about 75 seconds on macOS,
	 * several minutes on Linux). 0 means no limit.
	 */
	public static final int DEFAULT_CONNECT_TIMEOUT = 60000;

	/** "true" turns on SO_KEEPALIVE, so a server that disappears is detected. Default false. */
	public static final String PROPERTY_KEEP_ALIVE = SocketOptions.PROPERTY_KEEP_ALIVE;

	/** "true" turns on TCP_NODELAY (no Nagle delay). Default false. */
	public static final String PROPERTY_TCP_NO_DELAY = SocketOptions.PROPERTY_TCP_NO_DELAY;

	/** "false" turns off the host name check for secure connections, see {@link #isVerifyHostname()}. */
	public static final String PROPERTY_VERIFY_HOSTNAME = "VerifyHostname";



	//  null means the VerifyHostname property has not been read yet
	private volatile Boolean verifyHostname;


	private volatile int connectTimeout=-1;

	//  null means the IsSoLinger property has not been read yet

	//  null means the property has not been read yet


	private volatile SocketFactory factory;
	
	public SocketClient() {
		this(false);
	}
	
	public SocketClient(boolean useSSL) {
		setSecure(useSSL);
	}


	/**
	 * When secure, the SSL sockets this factory makes check that the server's certificate
	 * was issued for the host being connected to, unless {@link #isVerifyHostname()} is false.
	 *
	 * @return the SocketFactory that a client should use to connect to a server
	 *
	 * @throws KeyManagementException
	 * @throws CertificateException
	 * @throws FileNotFoundException
	 * @throws KeyStoreException
	 * @throws NoSuchAlgorithmException
	 * @throws UnrecoverableKeyException
	 * @throws IOException
	 */
	public SocketFactory getSocketFactory() throws KeyManagementException, CertificateException, FileNotFoundException, KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException, IOException {
		SocketFactory ret = factory;
		if( ret == null ) {
			synchronized(this) {
				ret = factory;
				if( ret == null ) {
					if( isSecure() ) {
						SSLSocketFactory sf = getSSLContext().getSocketFactory();
						ret = isVerifyHostname() ? TlsSockets.hostnameVerifying(sf) : sf;
					} else {
						ret = SocketFactory.getDefault();
					}
					factory = ret;
				}
			}
		}

		//  Return the value read or built here, not the field: setVerifyHostname or resetSecurityContext that
		//  ran after the lock was released could have set the field to null again.
		return ret;
	}

	/**
	 * @return true (the default) if secure connections check that the server's certificate was
	 *  issued for the host name (or address) being connected to. Without this check any certificate
	 *  the trust managers accept is accepted for every host, so a server with any trusted
	 *  certificate could pretend to be another.
	 */
	public boolean isVerifyHostname() {
		Boolean ret = verifyHostname;
		if( ret == null ) {
			ret = getBooleanProperty(PROPERTY_VERIFY_HOSTNAME, true);
			verifyHostname = ret;
		}
		return ret;
	}

	/**
	 * Turn the host name check for secure connections on or off. Turn it off only for servers
	 * whose certificate is known not to match the name used to reach them (a test certificate, say).
	 *
	 * @param verifyHostname
	 */
	public synchronized void setVerifyHostname(boolean verifyHostname) {
		this.verifyHostname = verifyHostname;
		//  The factory depends on it
		factory = null;
	}

	/**
	 * Put TLS on a connected socket, as the client (STARTTLS and the like), using this object's
	 * SSLContext and host name check ({@link #isVerifyHostname()}), and do the handshake. Settings
	 * that must be made before the handshake go in an override of {@link #configure(Socket)}, which
	 * is called with the TLS socket first.
	 *
	 * @param plain a connected socket; closing the returned socket closes it too
	 * @param host the host name the connection was made to (for SNI and the host name check)
	 * @return the TLS socket, after the handshake
	 * @throws IOException if the handshake fails (plain is closed) or the SSLContext can't be made
	 */
	public SSLSocket startTls(Socket plain, String host) throws IOException {
		SSLSocket ret = TlsSockets.layer(getSSLContext(), plain, host, true, isVerifyHostname(), true);
		try {
			configure(ret);
			ret.startHandshake();
			return ret;
		} catch (IOException | RuntimeException e) {
			try {
				ret.close();
			} catch (IOException e2) {
			}
			throw e;
		}
	}

	/**
	 * Create a socket connected to the host:port and configured with the appropriate timeout values.
	 * The connection attempt gives up after {@link #getConnectTimeout()} milliseconds
	 * (with a SocketTimeoutException). A secure socket has finished its TLS handshake when it is
	 * returned, so set TLS options (enabled protocols, say) in {@link #configure(Socket)}.
	 * If anything fails the socket is closed before the exception is thrown.
	 *
	 * @param host
	 * @param port
	 * @return a connected socket
	 * @throws KeyManagementException
	 * @throws UnrecoverableKeyException
	 * @throws UnknownHostException
	 * @throws CertificateException
	 * @throws FileNotFoundException
	 * @throws KeyStoreException
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 */
	public Socket getSocket(String host,int port) throws KeyManagementException, UnrecoverableKeyException, UnknownHostException, CertificateException, FileNotFoundException, KeyStoreException, NoSuchAlgorithmException, IOException {
		SocketFactory sf = getSocketFactory();
		Socket ret = null;
		try {
			try {
				ret = sf.createSocket();
			} catch (SocketException | UnsupportedOperationException e) {
				//  Some custom factories can't create unconnected sockets, connect without a timeout.
				ret = null;
			}
			if( ret != null ) {
				ret.connect(new InetSocketAddress(host, port), getConnectTimeout());
			} else {
				ret = sf.createSocket(host, port);
			}
			configure(ret);
			if( ret instanceof SSLSocket ) {
				//  Do the TLS handshake now (with the socket timeout set by configure) so a bad certificate,
				//  a host name that doesn't match it or a server that doesn't answer fails here, and the
				//  socket is closed below. Before, the handshake waited for the first read or write, so these
				//  failures turned up in the caller's code and the socket was left for the caller to close.
				//  TLS settings (enabled protocols, say) must be made in configure(), before this.
				((SSLSocket) ret).startHandshake();
			}
			return ret;
		} catch (IOException | RuntimeException e) {
			if( ret != null ) {
				try {
					ret.close();
				} catch (IOException e2) {
				}
			}
			throw e;
		}
	}

	/**
	 * @return how long (in milliseconds) to wait for a connection to be made. 0 means no limit.
	 */
	public int getConnectTimeout() {
		if( connectTimeout < 0 ) {
			synchronized(this) {
				if( connectTimeout < 0 ) {
					connectTimeout = getIntProperty(PROPERTY_CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT);
				}
			}
		}

		return connectTimeout;
	}

	/**
	 * @param value how long (in milliseconds) to wait for a connection to be made. 0 means no limit.
	 */
	public void setConnectTimeout(int value) {
		connectTimeout = value;
	}
	

	@Override
	protected synchronized void resetSecurityContext() {
		super.resetSecurityContext();
		//  The factory was created from the old SSLContext
		factory = null;
	}

	private final SocketOptions options = new SocketOptions(this);

	// ------------------------------------------------------------------ socket options (see SocketOptions)

	public int getLingerTime() {
		return options.getLingerTime();
	}

	public void setLingerTime(int lingerTime) {
		options.setLingerTime(lingerTime);
	}

	public boolean isSoLinger() {
		return options.isSoLinger();
	}

	public void setSoLinger(boolean isLinger) {
		options.setSoLinger(isLinger);
	}

	public boolean isKeepAlive() {
		return options.isKeepAlive();
	}

	public void setKeepAlive(boolean keepAlive) {
		options.setKeepAlive(keepAlive);
	}

	public boolean isTcpNoDelay() {
		return options.isTcpNoDelay();
	}

	public void setTcpNoDelay(boolean tcpNoDelay) {
		options.setTcpNoDelay(tcpNoDelay);
	}

	public int getSocketTimeout() {
		return options.getSocketTimeout();
	}

	public void setSocketTimeout(int value) {
		options.setSocketTimeout(value);
	}

	/**
	 * Configure a socket: SoTimeout, SoLinger, KeepAlive and TcpNoDelay are set based on the current settings.
	 */
	public void configure(java.net.Socket socket) throws java.net.SocketException {
		options.configure(socket);
	}
}
