package us.bringardner.parley.core.util;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Loads RSA and EC (P-256, P-384, P-521) key pairs from the PEM files OpenSSL, keytool exports
 * and ssh-keygen -m PEM/PKCS8 write, and from key stores; writes PKCS#8 PEM.
 * <ul>
 * <li>PKCS#8 ("BEGIN PRIVATE KEY", and "BEGIN ENCRYPTED PRIVATE KEY" with PBES2: PBKDF2 and AES-CBC)</li>
 * <li>Traditional PEM ("BEGIN RSA PRIVATE KEY", "BEGIN EC PRIVATE KEY"), plain or with a
 * passphrase (DEK-Info AES-128/192/256-CBC or DES-EDE3-CBC)</li>
 * <li>a Java KeyStore (PKCS12, JKS): every RSA and EC private key entry</li>
 * </ul>
 * The public key is rebuilt from the private key where the file doesn't have it, and an EC
 * public key in the file must belong to the private key.
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
public final class PrivateKeys {

	private static final String OID_RSA = "1.2.840.113549.1.1.1";
	private static final String OID_EC = "1.2.840.10045.2.1";
	private static final String OID_ED25519 = "1.3.101.112";
	private static final String OID_PBES2 = "1.2.840.113549.1.5.13";
	private static final String OID_PBKDF2 = "1.2.840.113549.1.5.12";
	/** Named curve OID to the JDK's name */
	private static final Map<String, String> CURVES = new LinkedHashMap<String, String>();
	private static final Map<String, String> PRFS = new LinkedHashMap<String, String>();
	private static final Map<String, Integer> AES_CBC = new LinkedHashMap<String, Integer>();

	static {
		CURVES.put("1.2.840.10045.3.1.7", "secp256r1");
		CURVES.put("1.3.132.0.34", "secp384r1");
		CURVES.put("1.3.132.0.35", "secp521r1");
		PRFS.put("1.2.840.113549.2.7", "PBKDF2WithHmacSHA1");
		PRFS.put("1.2.840.113549.2.9", "PBKDF2WithHmacSHA256");
		PRFS.put("1.2.840.113549.2.10", "PBKDF2WithHmacSHA384");
		PRFS.put("1.2.840.113549.2.11", "PBKDF2WithHmacSHA512");
		AES_CBC.put("2.16.840.1.101.3.4.1.2", 16);
		AES_CBC.put("2.16.840.1.101.3.4.1.22", 24);
		AES_CBC.put("2.16.840.1.101.3.4.1.42", 32);
	}

	private PrivateKeys() {
	}

	/**
	 * @param file a PEM private key file
	 * @param passphrase for an encrypted key, null if it isn't
	 */
	public static KeyPair load(File file, char[] passphrase) throws IOException {
		return parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.US_ASCII), passphrase);
	}

	/**
	 * @param text the contents of a PEM private key file
	 * @param passphrase for an encrypted key, null if it isn't
	 * @throws IOException for an unsupported format or key, a missing or wrong passphrase
	 */
	public static KeyPair parse(String text, char[] passphrase) throws IOException {
		return parse(Pem.parse(text), passphrase);
	}

	/**
	 * @param pem a private key block
	 * @param passphrase for an encrypted key, null if it isn't
	 * @throws IOException for an unsupported format or key, a missing or wrong passphrase
	 */
	public static KeyPair parse(Pem pem, char[] passphrase) throws IOException {
		String type = pem.getType();
		try {
			switch (type) {
			case "PRIVATE KEY":
				return pkcs8(pem.getBody());
			case "ENCRYPTED PRIVATE KEY":
				return pkcs8(decryptPbes2(pem.getBody(), need(passphrase)));
			case "RSA PRIVATE KEY":
				return pkcs1(pem.isEncrypted() ? pem.decrypt(need(passphrase)) : pem.getBody());
			case "EC PRIVATE KEY":
				return sec1(pem.isEncrypted() ? pem.decrypt(need(passphrase)) : pem.getBody(), null);
			default:
				throw new KeyFormatException("Unsupported key file: BEGIN "+type);
			}
		} catch (GeneralSecurityException e) {
			throw new KeyFormatException("Can't load the "+type+" ("+e.getMessage()+"): wrong passphrase?");
		} catch (KeyFormatException e) {
			throw e;
		} catch (IOException | RuntimeException e) {
			// A wrong passphrase can decrypt to garbage that fails to parse
			throw new KeyFormatException("Can't read the "+type+" ("+e.getMessage()+")"+(isEncrypted(pem) ? ": wrong passphrase?" : ""));
		}
	}

	/**
	 * @return true if the key needs a passphrase
	 */
	public static boolean isEncrypted(Pem pem) {
		return "ENCRYPTED PRIVATE KEY".equals(pem.getType()) || pem.isEncrypted();
	}

	/**
	 * @return every RSA and EC private key in the key store with a certificate (for its public key)
	 */
	public static List<KeyPair> fromKeyStore(KeyStore ks, char[] password) throws IOException {
		List<KeyPair> ret = new ArrayList<KeyPair>();
		try {
			for (Enumeration<String> e = ks.aliases(); e.hasMoreElements();) {
				String alias = e.nextElement();
				if( !ks.isKeyEntry(alias) ) {
					continue;
				}
				Key key = ks.getKey(alias, password);
				Certificate cert = ks.getCertificate(alias);
				if( key instanceof PrivateKey && cert != null && ("RSA".equals(key.getAlgorithm()) || "EC".equals(key.getAlgorithm())) ) {
					ret.add(new KeyPair(cert.getPublicKey(), (PrivateKey) key));
				}
			}
		} catch (GeneralSecurityException e) {
			throw new IOException("Can't read the key store: "+e.getMessage(), e);
		}
		return ret;
	}

	/**
	 * @return the key pair of an RSA or EC private key whose public key isn't at hand
	 * (e.g. from a key store entry without a certificate)
	 */
	public static KeyPair fromPrivate(PrivateKey key) throws IOException {
		try {
			if( key instanceof RSAPrivateCrtKey ) {
				RSAPrivateCrtKey k = (RSAPrivateCrtKey) key;
				return new KeyPair(KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(k.getModulus(), k.getPublicExponent())), key);
			}
			if( key instanceof ECPrivateKey ) {
				ECPrivateKey k = (ECPrivateKey) key;
				ECPoint w = multiply(k.getParams().getGenerator(), k.getS(), k.getParams());
				return new KeyPair(KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, k.getParams())), key);
			}
		} catch (GeneralSecurityException e) {
			throw new IOException("Can't make the public key: "+e.getMessage(), e);
		}
		throw new KeyFormatException("Unsupported private key "+key.getAlgorithm());
	}

	/**
	 * @return the key as a "BEGIN PRIVATE KEY" (unencrypted PKCS#8) PEM block
	 */
	public static String toPkcs8Pem(PrivateKey key) {
		return Pem.format("PRIVATE KEY", key.getEncoded());
	}

	/**
	 * Write the text to a new file that only its owner can read (where the file system has
	 * POSIX permissions), as a private key file must be.
	 *
	 * @throws IOException if the file exists or can't be written
	 */
	public static void writePrivateFile(File file, String text) throws IOException {
		byte[] data = text.getBytes(StandardCharsets.US_ASCII);
		try {
			Files.createFile(file.toPath(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
		} catch (UnsupportedOperationException e) {
			// Not POSIX (Windows): the file inherits its folder's access
			Files.createFile(file.toPath());
		}
		Files.write(file.toPath(), data);
	}

	/**
	 * @return the RSA key pair of these CRT parameters
	 */
	public static KeyPair rsa(BigInteger n, BigInteger e, BigInteger d, BigInteger p, BigInteger q, BigInteger dp, BigInteger dq, BigInteger qinv)
			throws GeneralSecurityException {
		KeyFactory kf = KeyFactory.getInstance("RSA");
		PrivateKey priv = kf.generatePrivate(new RSAPrivateCrtKeySpec(n, e, d, p, q, dp, dq, qinv));
		PublicKey pub = kf.generatePublic(new RSAPublicKeySpec(n, e));
		return new KeyPair(pub, priv);
	}

	/**
	 * @return the EC key pair of the private value d and public point w
	 * @throws KeyFormatException if w doesn't belong to d (signatures would fail later)
	 */
	public static KeyPair ec(ECParameterSpec params, BigInteger d, ECPoint w) throws GeneralSecurityException, KeyFormatException {
		if( !multiply(params.getGenerator(), d, params).equals(w) ) {
			throw new KeyFormatException("The EC public key doesn't match the private key");
		}
		KeyFactory kf = KeyFactory.getInstance("EC");
		PrivateKey priv = kf.generatePrivate(new ECPrivateKeySpec(d, params));
		PublicKey pub = kf.generatePublic(new ECPublicKeySpec(w, params));
		return new KeyPair(pub, priv);
	}

	/**
	 * @param name the JDK's name of a curve, e.g. "secp256r1"
	 */
	public static ECParameterSpec curve(String name) throws GeneralSecurityException {
		AlgorithmParameters ap = AlgorithmParameters.getInstance("EC");
		ap.init(new ECGenParameterSpec(name));
		return ap.getParameterSpec(ECParameterSpec.class);
	}

	/**
	 * k * P on the curve (affine double and add). Not constant time: only for loading keys,
	 * where it runs once.
	 */
	public static ECPoint multiply(ECPoint point, BigInteger k, ECParameterSpec params) {
		BigInteger p = ((ECFieldFp) params.getCurve().getField()).getP();
		BigInteger a = params.getCurve().getA();
		ECPoint result = ECPoint.POINT_INFINITY;
		ECPoint addend = point;
		for (int i = 0; i < k.bitLength(); i++) {
			if( k.testBit(i) ) {
				result = add(result, addend, p, a);
			}
			addend = add(addend, addend, p, a);
		}
		return result;
	}

	private static ECPoint add(ECPoint q, ECPoint r, BigInteger p, BigInteger a) {
		if( q.equals(ECPoint.POINT_INFINITY) ) {
			return r;
		}
		if( r.equals(ECPoint.POINT_INFINITY) ) {
			return q;
		}
		BigInteger x1 = q.getAffineX(), y1 = q.getAffineY(), x2 = r.getAffineX(), y2 = r.getAffineY();
		BigInteger lambda;
		if( x1.equals(x2) ) {
			if( !y1.equals(y2) || y1.signum() == 0 ) {
				return ECPoint.POINT_INFINITY;
			}
			lambda = x1.pow(2).multiply(BigInteger.valueOf(3)).add(a).multiply(y1.shiftLeft(1).modInverse(p)).mod(p);
		} else {
			lambda = y2.subtract(y1).multiply(x2.subtract(x1).modInverse(p)).mod(p);
		}
		BigInteger x3 = lambda.pow(2).subtract(x1).subtract(x2).mod(p);
		BigInteger y3 = lambda.multiply(x1.subtract(x3)).subtract(y1).mod(p);
		return new ECPoint(x3, y3);
	}

	private static char[] need(char[] passphrase) throws KeyFormatException {
		if( passphrase == null ) {
			throw new KeyFormatException("The key is encrypted, a passphrase is needed");
		}
		return passphrase;
	}

	// ------------------------------------------------------------------ PKCS#8, PKCS#1, SEC1

	private static KeyPair pkcs8(byte[] der) throws IOException, GeneralSecurityException {
		Der info = new Der(der).sequence();
		info.integer();
		Der alg = info.sequence();
		String oid = alg.oid();
		if( OID_RSA.equals(oid) ) {
			PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
			return fromPrivate(key);
		}
		if( OID_EC.equals(oid) ) {
			return sec1(info.octetString(), curve(alg));
		}
		if( OID_ED25519.equals(oid) ) {
			throw new KeyFormatException("PKCS#8 Ed25519 keys have no public key: not supported");
		}
		throw new KeyFormatException("Unsupported key algorithm "+oid);
	}

	/**
	 * RSAPrivateKey (PKCS#1): version, n, e, d, p, q, dp, dq, qinv
	 */
	private static KeyPair pkcs1(byte[] der) throws IOException, GeneralSecurityException {
		Der s = new Der(der).sequence();
		s.integer();
		return rsa(s.integer(), s.integer(), s.integer(), s.integer(), s.integer(), s.integer(), s.integer(), s.integer());
	}

	/**
	 * ECPrivateKey (SEC1): version, d, [0] curve, [1] public point
	 *
	 * @param curve the curve from the PKCS#8 wrapper, or null to read it from [0]
	 */
	private static KeyPair sec1(byte[] der, ECParameterSpec curve) throws IOException, GeneralSecurityException {
		Der s = new Der(der).sequence();
		s.integer();
		BigInteger d = new BigInteger(1, s.octetString());
		Der params = s.explicit(0);
		if( params != null ) {
			curve = curve(params);
		}
		if( curve == null ) {
			throw new KeyFormatException("The EC key doesn't name its curve");
		}
		Der pub = s.explicit(1);
		ECPoint w = pub != null ? decodePoint(pub.bitString(), curve) : multiply(curve.getGenerator(), d, curve);
		return ec(curve, d, w);
	}

	/**
	 * @return the point of an uncompressed encoding (SEC1): 0x04 || x || y
	 */
	private static ECPoint decodePoint(byte[] data, ECParameterSpec params) throws KeyFormatException {
		int size = (params.getCurve().getField().getFieldSize()+7)/8;
		if( data.length != 1+2*size || data[0] != 4 ) {
			throw new KeyFormatException("Invalid EC point (only uncompressed points are supported)");
		}
		return new ECPoint(new BigInteger(1, Arrays.copyOfRange(data, 1, 1+size)), new BigInteger(1, Arrays.copyOfRange(data, 1+size, data.length)));
	}

	/**
	 * The curve of an EC key: a named curve OID, or explicit parameters (some OpenSSL and
	 * LibreSSL builds write those), matched to a known curve by its prime and order.
	 */
	private static ECParameterSpec curve(Der d) throws IOException, GeneralSecurityException {
		if( d.peekTag() == Der.OID ) {
			String c = CURVES.get(d.oid());
			if( c == null ) {
				throw new KeyFormatException("Unsupported EC curve");
			}
			return curve(c);
		}
		// ECParameters: version, fieldID (prime-field, p), curve (a, b), base, order, cofactor
		Der ecp = d.sequence();
		ecp.integer();
		Der field = ecp.sequence();
		field.oid();
		BigInteger p = field.integer();
		ecp.skip();
		ecp.skip();
		BigInteger order = ecp.integer();
		for (String c : CURVES.values()) {
			ECParameterSpec spec = curve(c);
			if( ((ECFieldFp) spec.getCurve().getField()).getP().equals(p) && spec.getOrder().equals(order) ) {
				return spec;
			}
		}
		throw new KeyFormatException("Unsupported EC curve (explicit parameters)");
	}

	// ------------------------------------------------------------------ PBES2 (RFC 8018)

	/**
	 * EncryptedPrivateKeyInfo with PBES2 (PBKDF2 and AES-CBC), as OpenSSL and ssh-keygen -m PKCS8 write.
	 */
	private static byte[] decryptPbes2(byte[] der, char[] passphrase) throws IOException, GeneralSecurityException {
		Der epki = new Der(der).sequence();
		Der alg = epki.sequence();
		if( !OID_PBES2.equals(alg.oid()) ) {
			throw new KeyFormatException("Only PBES2 encrypted keys are supported");
		}
		Der params = alg.sequence();
		Der kdf = params.sequence();
		if( !OID_PBKDF2.equals(kdf.oid()) ) {
			throw new KeyFormatException("Only PBKDF2 is supported");
		}
		Der kdfParams = kdf.sequence();
		byte[] salt = kdfParams.octetString();
		int iterations = kdfParams.integer().intValueExact();
		if( iterations < 1 || iterations > 10_000_000 ) {
			throw new KeyFormatException("Unreasonable PBKDF2 iteration count "+iterations);
		}
		String prf = "PBKDF2WithHmacSHA1";
		if( kdfParams.hasMore() && kdfParams.peekTag() == Der.INTEGER ) {
			kdfParams.integer();
		}
		if( kdfParams.hasMore() ) {
			prf = PRFS.get(kdfParams.sequence().oid());
			if( prf == null ) {
				throw new KeyFormatException("Unsupported PBKDF2 PRF");
			}
		}
		Der enc = params.sequence();
		Integer keySize = AES_CBC.get(enc.oid());
		if( keySize == null ) {
			throw new KeyFormatException("Only AES-CBC encrypted keys are supported");
		}
		byte[] iv = enc.octetString();
		byte[] data = epki.octetString();
		byte[] key = SecretKeyFactory.getInstance(prf).generateSecret(new PBEKeySpec(passphrase, salt, iterations, keySize*8)).getEncoded();
		try {
			Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
			c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
			return c.doFinal(data);
		} finally {
			Arrays.fill(key, (byte) 0);
		}
	}

	/**
	 * A key file that can't be used: unsupported, corrupt, or a missing or wrong passphrase.
	 * The message says which.
	 */
	public static class KeyFormatException extends IOException {
		private static final long serialVersionUID = 1L;

		public KeyFormatException(String message) {
			super(message);
		}
	}
}
