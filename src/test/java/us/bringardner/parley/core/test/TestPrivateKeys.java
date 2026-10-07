package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.parley.core.util.Pem;
import us.bringardner.parley.core.util.PrivateKeys;
import us.bringardner.parley.core.util.PrivateKeys.KeyFormatException;

/**
 * PrivateKeys: PEM private keys in the formats OpenSSL writes (made with the openssl
 * command when it is installed), key stores, and writing.
 */
public class TestPrivateKeys {

	@TempDir
	File dir;

	private static KeyPair rsa() throws Exception {
		KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
		g.initialize(2048);
		return g.generateKeyPair();
	}

	private static KeyPair ec(String curve) throws Exception {
		KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
		g.initialize(new ECGenParameterSpec(curve));
		return g.generateKeyPair();
	}

	/** The loaded pair is the same key, and its halves belong together */
	private static void assertSame(KeyPair want, KeyPair got) throws Exception {
		assertArrayEquals(want.getPublic().getEncoded(), got.getPublic().getEncoded());
		String alg = "RSA".equals(got.getPrivate().getAlgorithm()) ? "SHA256withRSA" : "SHA256withECDSA";
		Signature s = Signature.getInstance(alg);
		s.initSign(got.getPrivate());
		s.update("x".getBytes(StandardCharsets.UTF_8));
		byte[] sig = s.sign();
		s.initVerify(want.getPublic());
		s.update("x".getBytes(StandardCharsets.UTF_8));
		assertTrue(s.verify(sig));
	}

	@Test
	public void pkcs8RoundTrip() throws Exception {
		for (KeyPair kp : new KeyPair[] {rsa(), ec("secp256r1"), ec("secp384r1"), ec("secp521r1")}) {
			String pem = PrivateKeys.toPkcs8Pem(kp.getPrivate());
			assertTrue(pem.startsWith("-----BEGIN PRIVATE KEY-----\n"));
			assertFalse(PrivateKeys.isEncrypted(Pem.parse(pem)));
			assertSame(kp, PrivateKeys.parse(pem, null));
			assertSame(kp, PrivateKeys.fromPrivate(kp.getPrivate()));
		}
	}

	@Test
	public void writePrivateFileIsOwnerOnly() throws Exception {
		File f = new File(dir, "key.pem");
		KeyPair kp = ec("secp256r1");
		PrivateKeys.writePrivateFile(f, PrivateKeys.toPkcs8Pem(kp.getPrivate()));
		assertSame(kp, PrivateKeys.load(f, null));
		if( f.toPath().getFileSystem().supportedFileAttributeViews().contains("posix") ) {
			assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(f.toPath())));
		}
		assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> PrivateKeys.writePrivateFile(f, "x"));
	}

	@Test
	public void keyStore() throws Exception {
		File ksFile = new File(dir, "test.p12");
		String keytool = new File(System.getProperty("java.home"), "bin/keytool").getPath();
		for (String[] alias : new String[][] {{"rsa", "RSA", "2048"}, {"ec", "EC", "256"}}) {
			Process p = new ProcessBuilder(keytool, "-genkeypair", "-alias", alias[0], "-keyalg", alias[1], "-keysize", alias[2],
					"-dname", "CN=test", "-validity", "1", "-storetype", "PKCS12", "-keystore", ksFile.getPath(),
					"-storepass", "changeit").redirectErrorStream(true).start();
			p.getInputStream().readAllBytes();
			assertTrue(p.waitFor(60, TimeUnit.SECONDS));
			assertEquals(0, p.exitValue(), "keytool");
		}
		KeyStore ks = KeyStore.getInstance("PKCS12");
		try (java.io.InputStream in = new java.io.FileInputStream(ksFile)) {
			ks.load(in, "changeit".toCharArray());
		}
		List<KeyPair> keys = PrivateKeys.fromKeyStore(ks, "changeit".toCharArray());
		assertEquals(2, keys.size());
		for (KeyPair kp : keys) {
			assertSame(kp, PrivateKeys.fromPrivate(kp.getPrivate()));
		}
	}

	@Test
	public void badFiles() throws Exception {
		assertThrows(java.io.IOException.class, () -> PrivateKeys.parse("no pem here", null));
		KeyFormatException e = assertThrows(KeyFormatException.class, () -> PrivateKeys.parse("-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----\n", null));
		assertTrue(e.getMessage().contains("BEGIN CERTIFICATE"), e.getMessage());
		e = assertThrows(KeyFormatException.class, () -> PrivateKeys.parse("-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n", null));
		assertFalse(e.getMessage().contains("passphrase"), "not encrypted: "+e.getMessage());
	}

	// ------------------------------------------------------------------ files openssl writes

	private File openssl(String name, String... args) throws Exception {
		File out = new File(dir, name);
		String[] cmd = new String[args.length+3];
		cmd[0] = "openssl";
		System.arraycopy(args, 0, cmd, 1, args.length);
		cmd[args.length+1] = "-out";
		cmd[args.length+2] = out.getPath();
		Process p;
		try {
			p = new ProcessBuilder(cmd).directory(dir).redirectErrorStream(true).start();
		} catch (java.io.IOException e) {
			assumeTrue(false, "no openssl command");
			return null;
		}
		String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertTrue(p.waitFor(60, TimeUnit.SECONDS));
		assumeTrue(p.exitValue() == 0, "openssl "+String.join(" ", args)+": "+output);
		return out;
	}

	@Test
	public void opensslFormats() throws Exception {
		File rsa = openssl("rsa.pem", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048");
		File ec = openssl("ec.pem", "genpkey", "-algorithm", "EC", "-pkeyopt", "ec_paramgen_curve:P-384");
		KeyPair rsaPair = PrivateKeys.load(rsa, null);
		KeyPair ecPair = PrivateKeys.load(ec, null);

		// Traditional (PKCS#1, SEC1), plain and encrypted
		File pkcs1 = openssl("rsa1.pem", "rsa", "-in", rsa.getPath(), "-traditional");
		assertEquals("RSA PRIVATE KEY", Pem.parse(new String(Files.readAllBytes(pkcs1.toPath()), StandardCharsets.US_ASCII)).getType());
		assertSame(rsaPair, PrivateKeys.load(pkcs1, null));
		File sec1 = openssl("ec1.pem", "ec", "-in", ec.getPath());
		assertSame(ecPair, PrivateKeys.load(sec1, null));
		File sec1NoPub = openssl("ec2.pem", "ec", "-in", ec.getPath(), "-no_public");
		assertSame(ecPair, PrivateKeys.load(sec1NoPub, null));
		File explicit = openssl("ec3.pem", "ec", "-in", ec.getPath(), "-param_enc", "explicit");
		assertSame(ecPair, PrivateKeys.load(explicit, null));
		for (String cipher : new String[] {"-aes128", "-aes256", "-des3"}) {
			File enc = openssl("rsa-enc"+cipher+".pem", "rsa", "-in", rsa.getPath(), "-traditional", cipher, "-passout", "pass:secret");
			assertTrue(PrivateKeys.isEncrypted(Pem.parse(new String(Files.readAllBytes(enc.toPath()), StandardCharsets.US_ASCII))));
			assertSame(rsaPair, PrivateKeys.load(enc, "secret".toCharArray()));
			KeyFormatException e = assertThrows(KeyFormatException.class, () -> PrivateKeys.load(enc, "wrong".toCharArray()));
			assertTrue(e.getMessage().contains("wrong passphrase"), e.getMessage());
			e = assertThrows(KeyFormatException.class, () -> PrivateKeys.load(enc, null));
			assertTrue(e.getMessage().contains("passphrase is needed"), e.getMessage());
		}

		// PKCS#8 encrypted with PBES2 (PBKDF2, AES-CBC)
		File p8 = openssl("ec-p8.pem", "pkcs8", "-topk8", "-in", ec.getPath(), "-v2", "aes-256-cbc", "-passout", "pass:secret");
		assertSame(ecPair, PrivateKeys.load(p8, "secret".toCharArray()));
		assertThrows(KeyFormatException.class, () -> PrivateKeys.load(p8, "wrong".toCharArray()));
		File p8sha1 = openssl("rsa-p8.pem", "pkcs8", "-topk8", "-in", rsa.getPath(), "-v2", "aes-128-cbc", "-v2prf", "hmacWithSHA1", "-passout", "pass:secret");
		assertSame(rsaPair, PrivateKeys.load(p8sha1, "secret".toCharArray()));
	}
}
