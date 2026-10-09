// ~version~V000.01.02-V000.00.01-V000.00.00-
package us.bringardner.parley.core.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocket;

import us.bringardner.parley.core.BaseThread;


/**
 * <PRE>
 * This class is a general purpose TCP/IP Server.
 * It implements all of the functionality required to accepts connection 
 * requests for both secure and insecure channels and establishes a 
 * framework for managing sessions.
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
 *
 */
public abstract class AbstractCoreServer extends BaseThread  {

	
	public static final int DEFAULT_PORT = 9200;

	public static final String PROPERTY_PORT = "Port";

	public static final String PROPERTY_ACCEPT_TIMEOUT = "AcceptTimeout";

	public static final int DEFAULT_ACCEPT_TIMEOUT = 60000;

	public static final String PROPERTY_SOCKET_TIMEOUT = SocketOptions.PROPERTY_SOCKET_TIMEOUT;

	public static final int DEFAULT_SOCKET_TIMEOUT = SocketOptions.DEFAULT_SOCKET_TIMEOUT;

	/** Milliseconds before answering a failed login, which slows down password guessing */
	public static final String PROPERTY_LOGIN_FAILURE_DELAY = "LoginFailureDelay";
	public static final int DEFAULT_LOGIN_FAILURE_DELAY = 1000;
	/** Failed logins before the connection is closed */
	public static final String PROPERTY_MAX_LOGIN_ATTEMPTS = "MaxLoginAttempts";
	public static final int DEFAULT_MAX_LOGIN_ATTEMPTS = 3;
	/** Milliseconds a connection may take to log in before it is closed, 0 for no limit */
	public static final String PROPERTY_LOGIN_TIME_LIMIT = "LoginTimeLimit";
	public static final int DEFAULT_LOGIN_TIME_LIMIT = 0;

	public static final String PROPERTY_BACKLOG = "Backlog";

	public static final int DEFAULT_BACKLOG = 0;

	public static final String PROPERTY_SO_LINGER = SocketOptions.PROPERTY_SO_LINGER;

	/**
	 * SO_LINGER is in SECONDS (see Socket.setSoLinger).
	 * This was 60000 (almost 17 hours) which could block Socket.close() for a very long time.
	 */
	public static final int DEFAULT_SO_LINGER = SocketOptions.DEFAULT_SO_LINGER;

	public static final String PROPERTY_IS_SO_LINGER = SocketOptions.PROPERTY_IS_SO_LINGER;

	public static final String PROPERTY_BIND_ADDRESS = "BindAddress";

	/** "true" turns on SO_KEEPALIVE for accepted sockets, so a peer that disappears is detected. Default false. */
	public static final String PROPERTY_KEEP_ALIVE = SocketOptions.PROPERTY_KEEP_ALIVE;

	/** "true" turns on TCP_NODELAY (no Nagle delay) for accepted sockets. Default false. */
	public static final String PROPERTY_TCP_NO_DELAY = SocketOptions.PROPERTY_TCP_NO_DELAY;

	/** The most connections {@link #tryAcquireConnection()} allows at once, see {@link #DEFAULT_MAX_CONNECTIONS}. */
	public static final String PROPERTY_MAX_CONNECTIONS = "MaxConnections";

	/** 0: no limit. */
	public static final int DEFAULT_MAX_CONNECTIONS = 0;

	private volatile ServerSocketFactory factory;
	
	
	private volatile int port=-1;

	//  Attributes may be accessed by many connection threads at the same time.
	private volatile Map<String, Object> attributes = new ConcurrentHashMap<String, Object>();

	private volatile boolean needClientAuth=false;

	private volatile int acceptTimeout=-1;



	private volatile int loginFailureDelay=-1;

	private volatile int maxLoginAttempts=-1;

	private volatile int loginTimeLimit=-1;

	private volatile int backlog = -1;

	private volatile InetAddress bindAddr;
	//  The bind address may legitimately be null so we need a separate flag to know if the property has been read.
	private volatile boolean bindAddrConfigured = false;

	//  null means the IsSoLinger property has not been read yet

	//  null means the property has not been read yet

	private volatile int maxConnections = -1;
	private final AtomicInteger activeConnections = new AtomicInteger();

	private volatile ServerSocket serverSocket;
	
	
	
	public AbstractCoreServer(boolean secure) {
		setSecure(secure);
	}

	public AbstractCoreServer(int port, boolean secure) {
		this(port);
		setSecure(secure);
	}

	public AbstractCoreServer(int port) {
		this();
		setPort(port);
	}

	public AbstractCoreServer() {
		super();
	}

	
	/**
	 * The socket is created the first time this is called and then reused. If it has been
	 * closed (by {@link #stop()}, {@link #closeServerSocket()} or the run method) a new
	 * one is created, so a server can be stopped and started again.
	 * <p>
	 * While the server thread is stopping (after {@link #stop()} and before the thread ends)
	 * a new socket is not created: this throws a SocketException instead. Before, a run method
	 * that asked for the socket just after stop() was called (a start() followed at once by
	 * stop(), say) opened a new socket that nothing closed, so the port stayed in use after the
	 * server had stopped.
	 *
	 * @return ServerSocket used by this Server
	 *
	 * @throws IOException
	 * @throws SocketException if the server is stopping and the socket has been closed
	 */
	public ServerSocket getServerSocket() throws IOException {
		ServerSocket ret = serverSocket;
		if( ret == null || ret.isClosed() ) {
			synchronized (this) {
				ret = serverSocket;
				if( ret == null || ret.isClosed() ) {
					//  stop() sets stopping before closeServerSocket() takes this lock, so either the socket
					//  created here is closed by stop() or stopping is seen here: never one left open.
					if( stopping && isAlive() ) {
						throw new SocketException("The server is stopping");
					}
					ret = getServerSocketFactory().createServerSocket(getPort(),getBacklog(),getBindAddr());
					try {
						ret.setSoTimeout(getAcceptTimeout());
						if( ret instanceof SSLServerSocket ) {
							((SSLServerSocket)ret).setNeedClientAuth(isNeedClientAuth());
						}
					} catch (IOException | RuntimeException e) {
						//  Don't leave the port bound
						try {
							ret.close();
						} catch (IOException e2) {
						}
						throw e;
					}
					serverSocket = ret;
				}
			}
		}

		return ret;
	}

	/**
	 * Close the listening socket, if it is open. A thread blocked in accept() gets a
	 * SocketException at once. Connections that were already accepted are not affected.
	 * The next call to {@link #getServerSocket()} creates a new socket.
	 */
	public void closeServerSocket() {
		ServerSocket s;
		synchronized (this) {
			s = serverSocket;
			serverSocket = null;
		}
		if( s != null ) {
			try {
				s.close();
			} catch (IOException e) {
				// already closed
			}
		}
	}

	/**
	 * Stop the server. As well as setting {@link #stopping}, this closes the listening
	 * socket so a blocked accept() returns at once (with a SocketException) instead of
	 * waiting for the accept timeout. The run method should check {@link #stopping} when
	 * accept() throws and exit quietly.
	 */
	@Override
	public void stop() {
		super.stop();
		closeServerSocket();
	}

	
	/**
	 * @return the backlog used to create the ServerSocket.
	 * 
	 * @see java.net.ServerSocket   
	 */
	public int getBacklog() {
		if( backlog < 0 ) {
			synchronized(this) {
				if( backlog < 0 ) {
					backlog = getIntProperty(PROPERTY_BACKLOG,DEFAULT_BACKLOG);
				}
			}
		}
		
		return backlog;
	}

	/**
	 * set the listen backlog for the server
	 *  
	 * @param backlog
	 * @see java.net.ServerSocket
	 */
	public void setBacklog(int backlog) {
		this.backlog = backlog;
	}

	
	/**
	 * @return the local InetAddress the server will bind to 
	 * @see java.net.ServerSocket 
	 */
	public InetAddress getBindAddr() {
		//  The bind address may be null (all local addresses) so we use a flag to know if the property has been read.
		if( !bindAddrConfigured ) {
			synchronized (this) {
				if( !bindAddrConfigured ) {
					String tmp = getProperty(PROPERTY_BIND_ADDRESS);
					if( tmp != null ) {
						try {
							bindAddr = InetAddress.getByName(tmp.trim());
						} catch (UnknownHostException e) {
							logError("Cannot create BindAddress ("+tmp+").",e);
							throw new IllegalStateException(e);
						}
					}
					bindAddrConfigured = true;
				}
			}
		}
		return bindAddr;
	}

	/**
	 * set the local InetAddress the server will bind to
	 * 
	 * @param bindAddr
	 * @see java.net.ServerSocket
	 */
	public void setBindAddr(InetAddress bindAddr) {
		this.bindAddr = bindAddr;
		this.bindAddrConfigured = true;
	}

	/**
	 * 
	 * @param needClientAuth true is client authentication is required on a secure connection.
	 */
	public void setNeedClientAuth(boolean needClientAuth) {
		this.needClientAuth = needClientAuth;
	}

	/**
	 * @return true is client authentication is required on a secure connection.
	 */
	public boolean isNeedClientAuth() {		
		return needClientAuth;
	}


	/**
	 * 
	 * @return the attributes maintained by this server.
	 */
	public Map<String, Object> getAttributes() {
		return attributes;
	}

	/**
	 * @param attributes replace the server attributes.
	 */
	public void setAttributes(Map<String, Object> attributes) {
		this.attributes = attributes;
	}


	
	/**
	 * @return the ServerSocketFactory that this server should use to listen for connections.
	 * 
	 * @throws IOException
	 */
	public ServerSocketFactory getServerSocketFactory() throws IOException {
		ServerSocketFactory ret = factory;
		if( ret == null ) {
			synchronized(this) {
				ret = factory;
				if( ret == null ) {
					if( isSecure() ) {
						ret = getSSLContext().getServerSocketFactory();						
					} else {
						ret = ServerSocketFactory.getDefault();
					}
					factory = ret;
				}
			}
		}
		
		//  Return the value read or built here, not the field: resetSecurityContext that
		//  ran after the lock was released could have set the field to null again.
		return ret;
	}

	/**
	 * @param factory to use when creating the ServerSocket 
	 */
	public synchronized void setServerSocketFactory(ServerSocketFactory factory) {
		this.factory = factory;
	}

	@Override
	protected synchronized void resetSecurityContext() {
		super.resetSecurityContext();
		//  The factory was created from the old SSLContext
		factory = null;
	}


	/**
	 * The Server maintains a set of arbitrary values called attributes.  
	 * Attributes are shared by every connection the server handles (the map is thread safe).
	 * 
	 * @param name
	 * @return the Object associated with the named attribute.
	 */
	public Object getAttribute(String name) {
		return name == null ? null : attributes.get(name);
	}

	/**
	 * The Server maintains a set of arbitrary values called attributes.  
	 * Attributes are shared by every connection the server handles (the map is thread safe).
	 *  
	 * @param name
	 * @param attribute
	 */
	public void setAttribute(String name, Object attribute) {
		if( attribute == null ) {
			//  ConcurrentHashMap does not allow null values, setting null is the same as removing it.
			removeAttribute(name);
		} else {
			attributes.put(name, attribute);
		}
	}

	/**
	 * The Server maintains a set of arbitrary values called attributes.  
	 * Attributes are shared by every connection the server handles (the map is thread safe).
	 * 
	 * @param name
	 * @return the values of the attribute that was removed or null in none existed.
	 */
	public Object removeAttribute(String name) {
		return name == null ? null : attributes.remove(name);
	}

	/**
	 * @return the port that the server should listen on for connection requests.
	 */
	public int getPort() {
		if( port == -1 ) {
			port = getIntProperty(PROPERTY_PORT,DEFAULT_PORT);
		}
		return port;
	}


	/**
	 * @param port that the server should listen on for connection requests.
	 */
	public void setPort(int port) {
		this.port = port;
	}



	/**
	 * Called by processors and/or connections to notify the server that a connection was closed
	 * 
	 * @param connectionObject
	 */
	public void connectionClosed(Object connectionObject) {
		//  Nothing to do is core server
	}

	/**
	 * @return the most connections {@link #tryAcquireConnection()} allows at once, 0 (or less) for no limit.
	 */
	public int getMaxConnections() {
		if( maxConnections < 0 ) {
			synchronized(this) {
				if( maxConnections < 0 ) {
					maxConnections = getIntProperty(PROPERTY_MAX_CONNECTIONS, DEFAULT_MAX_CONNECTIONS);
				}
			}
		}
		return maxConnections;
	}

	/**
	 * @param value the most connections {@link #tryAcquireConnection()} allows at once, 0 for no limit.
	 * Lowering it doesn't close connections already open; new ones are refused until enough have closed.
	 */
	public void setMaxConnections(int value) {
		//  -1 means "read the property", so any negative value is stored as 0 (no limit)
		maxConnections = Math.max(value, 0);
	}

	/**
	 * Reserve a place for a new connection. The server doesn't call this itself; an accept loop that
	 * wants to limit how many connections (and handler threads) it has at once uses it like this:
	 * <pre>
	 *  Socket socket = getServerSocket().accept();
	 *  if( !tryAcquireConnection() ) {
	 *      socket.close();   // too many connections
	 *      continue;
	 *  }
	 *  // start the handler, which calls releaseConnection() in a finally block when it is done
	 * </pre>
	 *
	 * @return true if the connection may go ahead (it must then be released with
	 *  {@link #releaseConnection()}), false if {@link #getMaxConnections()} are already open.
	 */
	public boolean tryAcquireConnection() {
		int max = getMaxConnections();
		while( true ) {
			int current = activeConnections.get();
			if( max > 0 && current >= max ) {
				return false;
			}
			if( activeConnections.compareAndSet(current, current+1) ) {
				return true;
			}
		}
	}

	/**
	 * Give back a place reserved by {@link #tryAcquireConnection()}. Call it exactly once per
	 * successful tryAcquireConnection(); extra calls are ignored (the count never goes below 0).
	 */
	public void releaseConnection() {
		activeConnections.updateAndGet(n -> n > 0 ? n-1 : 0);
	}

	/**
	 * @return how many connections are reserved by {@link #tryAcquireConnection()} and not yet released.
	 */
	public int getActiveConnections() {
		return activeConnections.get();
	}

	
	
	// ------------------------------------------------------------------ login limits
	// The same for every protocol; each server applies them to its own login commands.

	/**
	 * @return milliseconds to wait before answering a failed login (0 = none), from the
	 * {@value #PROPERTY_LOGIN_FAILURE_DELAY} property, else {@link #getDefaultLoginFailureDelay()}
	 */
	public int getLoginFailureDelay() {
		if( loginFailureDelay < 0 ) {
			synchronized(this) {
				if( loginFailureDelay < 0 ) {
					loginFailureDelay = Math.max(0, getIntProperty(PROPERTY_LOGIN_FAILURE_DELAY, getDefaultLoginFailureDelay()));
				}
			}
		}
		return loginFailureDelay;
	}

	/**
	 * @param milliSeconds delay before answering a failed login (0 = none)
	 * @throws IllegalArgumentException if negative
	 */
	public void setLoginFailureDelay(int milliSeconds) {
		if( milliSeconds < 0 ) {
			throw new IllegalArgumentException("loginFailureDelay must be >= 0");
		}
		loginFailureDelay = milliSeconds;
	}

	/**
	 * @return {@value #DEFAULT_LOGIN_FAILURE_DELAY}; a server whose clients fail logins as part
	 * of normal use (SSH clients trying each of their keys) uses less
	 */
	protected int getDefaultLoginFailureDelay() {
		return DEFAULT_LOGIN_FAILURE_DELAY;
	}

	/**
	 * @return failed logins before the connection is closed, from the
	 * {@value #PROPERTY_MAX_LOGIN_ATTEMPTS} property, else {@link #getDefaultMaxLoginAttempts()}
	 */
	public int getMaxLoginAttempts() {
		if( maxLoginAttempts < 0 ) {
			synchronized(this) {
				if( maxLoginAttempts < 0 ) {
					maxLoginAttempts = Math.max(1, getIntProperty(PROPERTY_MAX_LOGIN_ATTEMPTS, getDefaultMaxLoginAttempts()));
				}
			}
		}
		return maxLoginAttempts;
	}

	/**
	 * @param attempts failed logins before the connection is closed
	 * @throws IllegalArgumentException if less than 1
	 */
	public void setMaxLoginAttempts(int attempts) {
		if( attempts < 1 ) {
			throw new IllegalArgumentException("maxLoginAttempts must be >= 1");
		}
		maxLoginAttempts = attempts;
	}

	/**
	 * @return {@value #DEFAULT_MAX_LOGIN_ATTEMPTS}
	 */
	protected int getDefaultMaxLoginAttempts() {
		return DEFAULT_MAX_LOGIN_ATTEMPTS;
	}

	/**
	 * @param failures failed logins on a connection so far
	 * @return true if the connection should be closed
	 */
	public boolean isTooManyLoginFailures(int failures) {
		return failures >= getMaxLoginAttempts();
	}

	/**
	 * @return milliseconds a connection may take to log in (0 = no limit), from the
	 * {@value #PROPERTY_LOGIN_TIME_LIMIT} property, else {@link #getDefaultLoginTimeLimit()}
	 */
	public int getLoginTimeLimit() {
		if( loginTimeLimit < 0 ) {
			synchronized(this) {
				if( loginTimeLimit < 0 ) {
					loginTimeLimit = Math.max(0, getIntProperty(PROPERTY_LOGIN_TIME_LIMIT, getDefaultLoginTimeLimit()));
				}
			}
		}
		return loginTimeLimit;
	}

	/**
	 * @param milliSeconds time a connection may take to log in (0 = no limit)
	 * @throws IllegalArgumentException if negative
	 */
	public void setLoginTimeLimit(int milliSeconds) {
		if( milliSeconds < 0 ) {
			throw new IllegalArgumentException("loginTimeLimit must be >= 0");
		}
		loginTimeLimit = milliSeconds;
	}

	/**
	 * @return {@value #DEFAULT_LOGIN_TIME_LIMIT} (no limit)
	 */
	protected int getDefaultLoginTimeLimit() {
		return DEFAULT_LOGIN_TIME_LIMIT;
	}

	/**
	 * Set the timeout value used for the accept call
	 * @param value
	 */
	public void setAcceptTimeout(int value) {
		acceptTimeout = value;
	}
	
	/**
	 * @return the timeout value for ServerSocket.accept
	 * @see ServerSocket#accept()
	 */
	public int getAcceptTimeout() {
		if( acceptTimeout < 0 ) {
			synchronized(this) {
				if(acceptTimeout < 0 ) {
					acceptTimeout = getIntProperty(PROPERTY_ACCEPT_TIMEOUT, DEFAULT_ACCEPT_TIMEOUT);
				}
			}
		}
		
		return acceptTimeout;
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
