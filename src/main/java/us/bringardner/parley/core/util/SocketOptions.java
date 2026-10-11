package us.bringardner.parley.core.util;

import java.net.Socket;
import java.net.SocketException;

import us.bringardner.parley.core.BaseObject;

/**
 * The socket settings a server applies to accepted sockets and a client to the sockets it opens:
 * read timeout, SO_LINGER, SO_KEEPALIVE and TCP_NODELAY. Each is read from the owner's properties
 * the first time it is needed, unless it was set.
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
public final class SocketOptions {

	public static final String PROPERTY_SOCKET_TIMEOUT = "SocketTimeout";
	public static final int DEFAULT_SOCKET_TIMEOUT = 60000;

	public static final String PROPERTY_SO_LINGER = "SoLinger";
	/**
	 * SO_LINGER is in SECONDS (see Socket.setSoLinger).
	 * This was 60000 (almost 17 hours) which could block Socket.close() for a very long time.
	 */
	public static final int DEFAULT_SO_LINGER = 10;
	public static final String PROPERTY_IS_SO_LINGER = "IsSoLinger";

	/** "true" turns on SO_KEEPALIVE, so a peer that disappears is detected. Default false. */
	public static final String PROPERTY_KEEP_ALIVE = "KeepAlive";

	/** "true" turns on TCP_NODELAY (no Nagle delay). Default false. */
	public static final String PROPERTY_TCP_NO_DELAY = "TcpNoDelay";

	private final BaseObject properties;

	private volatile int lingerTime = -1;
	private volatile int socketTimeout = -1;
	//  null means the property has not been read yet
	private volatile Boolean soLinger;
	private volatile Boolean keepAlive;
	private volatile Boolean tcpNoDelay;

	/** @param properties where the settings that were not set are read from */
	public SocketOptions(BaseObject properties) {
		this.properties = properties;
	}

	/**
	 * Set SoTimeout, SoLinger, KeepAlive and TcpNoDelay on a socket, based on the current settings.
	 */
	public void configure(Socket socket) throws SocketException {
		socket.setSoTimeout(getSocketTimeout());
		if (isSoLinger()) {
			socket.setSoLinger(true, getLingerTime());
		}
		if (isKeepAlive()) {
			socket.setKeepAlive(true);
		}
		if (isTcpNoDelay()) {
			socket.setTcpNoDelay(true);
		}
	}

	/** The time (seconds) to linger on a Socket.close(), see {@link Socket#setSoLinger(boolean, int)}. */
	public int getLingerTime() {
		if (lingerTime < 0) {
			synchronized (this) {
				if (lingerTime < 0) {
					lingerTime = properties.getIntProperty(PROPERTY_SO_LINGER, DEFAULT_SO_LINGER);
				}
			}
		}
		return lingerTime;
	}

	public void setLingerTime(int lingerTime) {
		this.lingerTime = lingerTime;
	}

	/** True if SO_LINGER should be enabled (default false). */
	public boolean isSoLinger() {
		Boolean ret = soLinger;
		if (ret == null) {
			ret = properties.getBooleanProperty(PROPERTY_IS_SO_LINGER, false);
			soLinger = ret;
		}
		return ret;
	}

	public void setSoLinger(boolean soLinger) {
		this.soLinger = soLinger;
	}

	/** True if SO_KEEPALIVE should be enabled (default false). */
	public boolean isKeepAlive() {
		Boolean ret = keepAlive;
		if (ret == null) {
			ret = properties.getBooleanProperty(PROPERTY_KEEP_ALIVE, false);
			keepAlive = ret;
		}
		return ret;
	}

	public void setKeepAlive(boolean keepAlive) {
		this.keepAlive = keepAlive;
	}

	/**
	 * True if TCP_NODELAY should be enabled (default false). Turning it on avoids a delay (often
	 * about 40ms) on small writes in request/response protocols.
	 */
	public boolean isTcpNoDelay() {
		Boolean ret = tcpNoDelay;
		if (ret == null) {
			ret = properties.getBooleanProperty(PROPERTY_TCP_NO_DELAY, false);
			tcpNoDelay = ret;
		}
		return ret;
	}

	public void setTcpNoDelay(boolean tcpNoDelay) {
		this.tcpNoDelay = tcpNoDelay;
	}

	/** The read timeout (ms) for sockets, controlling how long a read or write may block. */
	public int getSocketTimeout() {
		if (socketTimeout < 0) {
			synchronized (this) {
				if (socketTimeout < 0) {
					socketTimeout = properties.getIntProperty(PROPERTY_SOCKET_TIMEOUT, DEFAULT_SOCKET_TIMEOUT);
				}
			}
		}
		return socketTimeout;
	}

	public void setSocketTimeout(int value) {
		socketTimeout = value;
	}
}
