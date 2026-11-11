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
 * ~version~000.01.09-V000.01.06-V000.01.02-V000.00.02-V000.00.01-V000.00.00-
 */
package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.BaseThread;
import us.bringardner.parley.core.util.AbstractCoreServer;
import us.bringardner.parley.core.util.SocketClient;

public class TestCore extends TestCoreBase {


	@BeforeAll
	public static void setUpBeforeClass() throws Exception {
		/*
Generating 2,048 bit RSA key pair and self-signed certificate (SHA256withRSA) with a validity of 90 days
	for: CN=bringardner.us, OU=AA, O=BBB, L=Bringardner, ST=CCCC, C=DD

		 */
	}

	@AfterAll
	public static void tearDownAfterClass() throws Exception {
	}


	@AfterEach
	public void tearDown() throws Exception {

	}
	@Test
	public void testProperty01() {
		String [] expected = {
				"1", //BaseClass property logic finds it in us.bringardner.parley.core.test.TestCore.properties
				"1",//BaseClass property logic finds it in us.bringardner.parley.core.test.TestCore.properties
				"2",//BaseClass property logic overrides with com.TestCoreBase.properties
				"1",//BaseClass property logic finds it in us.bringardner.parley.core.test.TestCore.properties
				"System", //BaseClass property logic overrides with System.properties
				"Default6" // No property exists use default
		};
		System.setProperty("Value05", "System");

		for(int idx=1; idx <= 6; idx++) {
			assertEquals(expected[idx-1], getProperty("Value0"+idx,"Default"+idx), "idx="+idx);
		}
	}

	@Test
	public void testBaseThread() {

		BaseThread thread = new BaseThread() {

			@Override
			public void run() {
				started = running = true;
				while( !stopping ) {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
					}
				}
				running = false;
			}
		};
		thread.start();
		int timeout = 1000;
		long startTime = System.currentTimeMillis();
		while( System.currentTimeMillis()-startTime<= timeout && !thread.hasStarted()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}	
		}
		assertTrue(thread.isRunning(), "Thread is not running");

		thread.stop();		
		startTime = System.currentTimeMillis();
		while( System.currentTimeMillis()-startTime<= timeout && thread.isRunning()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}	
		}

		assertTrue(!thread.isRunning(), "Thread did not stop");


	}

	@Test
	public void testAbstractServer01() throws IOException {
		// test insecure server
		runAbstractServer01(false);
		//  test secure server
		runAbstractServer01(true);

	}

	private void runAbstractServer01(boolean useSSL) throws IOException {
		if( useSSL) {
			String ksname= getProperty("keyStoreName");
			File file = new File(ksname);
			file = file.getCanonicalFile();
			if( !file.exists()) {
				createCert();
			}
		}

		class Tm implements X509TrustManager {

			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
				throw new CertificateException("This object is not intended for clinet processing.");					
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
				//normally the trust manager will throw an exception here if it does not like the certificate.
				//  for testing just accept anything.					

			}

			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}

		};
		TrustManager[] mgr = {
				new Tm(),
		};

		//  port 0 = use any free port, so the test can't fail because a fixed port is in use.
		AbstractCoreServer svr = new AbstractCoreServer(0,useSSL) {
			{
				//  the key store settings are in TestCore.properties by their names alone
				setSupportPrefixProperty(false);
			}
			Socket socket = null;
			InputStream in = null;
			OutputStream out = null;
			ServerSocket ss=null;

			@Override
			public void run() {
				try {
					ss = getServerSocket();
					//  wait until ServerSocket is created before claiming to have started
					started = running = true;
					while(!stopping) {
						try {
							socket = ss.accept();
							if( socket != null ) {
								in = socket.getInputStream();
								out = socket.getOutputStream();
								int i = in.read();
								while(i >=0 ) {
									out.write(i);
									i = in.read();
								}
								try {in.close();} catch (Exception e) {	}
								try {out.close();} catch (Exception e) {	}							
								try {socket.close();} catch (Exception e) {	}
								socket=null;
							}
						} catch (SocketTimeoutException e) {
						}
					}
				} catch (Throwable e) {					
					e.printStackTrace();
				}
				if( socket != null ) {
					try {in.close();} catch (Exception e) {	}
					try {out.close();} catch (Exception e) {	}							
					try {socket.close();} catch (Exception e) {}
				}
				try {ss.close();} catch (Exception e) {					}
				running = false;
			}
		};

		svr.setAcceptTimeout(100);
		if( useSSL) {
			svr.setTrustManagers(mgr);
		}
		svr.start();
		int timeout = 60000;
		long startTime = System.currentTimeMillis();
		while( System.currentTimeMillis()-startTime<= timeout && !svr.hasStarted()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}	
		}
		assertTrue(svr.isRunning(), "Server is not running");
		int port = svr.getServerSocket().getLocalPort();
		String clientMEssage = "Client is here\n";
		try {
			SocketClient client = new SocketClient(useSSL);
			client.setTrustManagers(mgr);
			//  The test certificate is for bringardner.us and we connect to localhost (see TestConnectionReliability for the host name check)
			client.setVerifyHostname(false);
			SocketFactory factory = client.getSocketFactory();

			Socket socket = factory.createSocket("localhost", port);
			InputStream in = socket.getInputStream();
			OutputStream out = socket.getOutputStream();
			out.write(clientMEssage.getBytes());
			byte [] data = new byte[clientMEssage.length()];
			int got = in.read(data);
			while(got < data.length) {
				got += in.read(data, got, data.length-got);
			}

			String response = new String(data);
			assertEquals(clientMEssage, response, "Server response is not what we expected");
			try {in.close();} catch (Exception e) {	}
			try {out.close();} catch (Exception e) {	}							
			try {socket.close();} catch (Exception e) {}
			svr.stop();
			startTime = System.currentTimeMillis();
			while( System.currentTimeMillis()-startTime<= timeout && svr.isRunning()) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}	
			}
			assertTrue(!svr.isRunning(), "Server is did not stop");
		} catch (Throwable e) {
			e.printStackTrace();
			fail("Could not process client code. e="+e, e);			
		}
		if( useSSL) {
			String ksname= getProperty("keyStoreName");
			File file = new File(ksname);
			file = file.getCanonicalFile();
			if( file.exists()) {
				file.delete();
			}
		}
	}

	public void createCert() throws IOException {

		//excuteCommand("./makecert.sh");
		ProcessBuilder pb =new ProcessBuilder("keytool"
				, "-genkey"
				, "-noprompt"
				, "-alias"
				, "serverkey"
				, "-dname"
				, "CN=bringardner.us, OU=AA, O=BBB, L=Bringardner, S=CCCC, C=DD"
				, "-keystore"
				, "serverkeystore.p12"
				, "-storepass"
				, "changeit"
				, "-keypass"
				, "changeit"
				, "-keyalg"
				, "RSA"
				, "-keysize"
				, "2048"
				, "-sigalg"
				, "SHA256withRSA"
				);

		Process p = pb.start();
		try {
			p.waitFor();
			//System.out.println("i="+i);
		} catch (InterruptedException e) {
			throw new IOException(e);
		}

	}


	public static void excuteCommand(String filePath) throws IOException{
		boolean isWindows = System.getProperty("os.name").toLowerCase().indexOf("windows") >= 0;
		Process p = null;
		if(isWindows){
			p = Runtime.getRuntime().exec("cmd /c start " + filePath);	        
		}else {
			p = Runtime.getRuntime().exec(new String[] {filePath}, null);
			//p = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", filePath}, null);
		}
		try {
			p.waitFor(10, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		int exit = p.exitValue();
		assertEquals(0, exit, "Invalid exit code");
		String expect = "Generating 2,048 bit RSA key pair and self-signed certificate (SHA256withRSA) with a validity of 90 days\n"
				+ "	for: CN=bringardner.us, OU=AA, O=BBB, L=Bringardner, ST=CCCC, C=DD";
		String tmp =  readAllBytes(p.getInputStream())+ readAllBytes(p.getErrorStream());
		assertEquals(expect.trim(), tmp.trim(), "Invalid response text ");

	}

	private static String readAllBytes(InputStream in) throws IOException {
		byte[] ret = new byte[in.available()];
		int got = in.read(ret);
		while( got >=0 && got < ret.length) {
			int tmp = in.read(ret, got, ret.length-got);
			if( tmp < 0 ) {
				throw new IOException("Unexpected EOF");
			}
		}
		return new String(ret);
	}

}
