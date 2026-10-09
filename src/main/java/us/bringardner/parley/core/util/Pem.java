package us.bringardner.parley.core.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * One PEM block ("-----BEGIN type-----", optional "Name: value" headers, base64, "-----END type-----"),
 * the text form of keys and certificates. OpenSSL's traditional encryption of a private key
 * (Proc-Type: 4,ENCRYPTED and DEK-Info) can be undone with {@link #decrypt(char[])}.
 *
 * @author Tony Bringardner
 */
public final class Pem {

	private final String type;
	private final Map<String, String> headers;
	private final byte[] body;

	private Pem(String type, Map<String, String> headers, byte[] body) {
		this.type = type;
		this.headers = headers;
		this.body = body;
	}

	/**
	 * @return the first PEM block in the text (anything before its BEGIN line is ignored)
	 * @throws IOException if there is no BEGIN line or the base64 is bad
	 */
	public static Pem parse(String text) throws IOException {
		String type = null;
		Map<String, String> headers = new LinkedHashMap<String, String>();
		StringBuilder b64 = new StringBuilder();
		boolean in = false;
		for (String raw : text.split("\r?\n")) {
			String line = raw.trim();
			if( !in ) {
				if( line.startsWith("-----BEGIN ") && line.endsWith("-----") ) {
					type = line.substring(11, line.length()-5);
					in = true;
				}
			} else if( line.startsWith("-----END ") ) {
				break;
			} else if( line.contains(":") ) {
				int i = line.indexOf(':');
				headers.put(line.substring(0, i).trim(), line.substring(i+1).trim());
			} else {
				b64.append(line);
			}
		}
		if( type == null ) {
			throw new IOException("Not a PEM file (no BEGIN line)");
		}
		try {
			return new Pem(type, headers, Base64.getDecoder().decode(b64.toString()));
		} catch (IllegalArgumentException e) {
			throw new IOException("Bad base64 in the PEM file");
		}
	}

	/**
	 * @return the text of a PEM block: 64 base64 characters a line, no headers
	 */
	public static String format(String type, byte[] body) {
		String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(body);
		return "-----BEGIN "+type+"-----\n"+b64+"\n-----END "+type+"-----\n";
	}

	/**
	 * @return what the BEGIN line names, e.g. "PRIVATE KEY" or "RSA PRIVATE KEY"
	 */
	public String getType() {
		return type;
	}

	/**
	 * @return the decoded base64 (still encrypted if {@link #isEncrypted()})
	 */
	public byte[] getBody() {
		return body.clone();
	}

	/**
	 * @return the header's value, or null
	 */
	public String getHeader(String name) {
		return headers.get(name);
	}

	/**
	 * @return true if OpenSSL's traditional encryption was used (Proc-Type: 4,ENCRYPTED)
	 */
	public boolean isEncrypted() {
		String pt = headers.get("Proc-Type");
		return pt != null && pt.contains("ENCRYPTED");
	}

	/**
	 * Undo OpenSSL's traditional encryption: DEK-Info names the cipher (AES-128/192/256-CBC or
	 * DES-EDE3-CBC) and IV; the key is EVP_BytesToKey(MD5, passphrase, the IV's first 8 bytes, 1 round).
	 *
	 * @return the decrypted body
	 * @throws GeneralSecurityException typically for a wrong passphrase (bad padding)
	 */
	public byte[] decrypt(char[] passphrase) throws IOException, GeneralSecurityException {
		String dek = headers.get("DEK-Info");
		if( dek == null || dek.indexOf(',') < 0 ) {
			throw new IOException("Encrypted key without DEK-Info");
		}
		String alg = dek.substring(0, dek.indexOf(',')).trim().toUpperCase(Locale.ROOT);
		byte[] iv = hex(dek.substring(dek.indexOf(',')+1).trim());
		String transform;
		String keyAlg;
		int keySize;
		switch (alg) {
		case "AES-128-CBC": transform = "AES/CBC/PKCS5Padding"; keyAlg = "AES"; keySize = 16; break;
		case "AES-192-CBC": transform = "AES/CBC/PKCS5Padding"; keyAlg = "AES"; keySize = 24; break;
		case "AES-256-CBC": transform = "AES/CBC/PKCS5Padding"; keyAlg = "AES"; keySize = 32; break;
		case "DES-EDE3-CBC": transform = "DESede/CBC/PKCS5Padding"; keyAlg = "DESede"; keySize = 24; break;
		default: throw new IOException("Unsupported key encryption "+alg);
		}
		byte[] pass = new String(passphrase).getBytes(StandardCharsets.UTF_8);
		byte[] salt = Arrays.copyOf(iv, 8);
		byte[] key = new byte[keySize];
		MessageDigest md5 = MessageDigest.getInstance("MD5");
		byte[] prev = new byte[0];
		int filled = 0;
		while( filled < keySize ) {
			md5.update(prev);
			md5.update(pass);
			md5.update(salt);
			prev = md5.digest();
			int n = Math.min(prev.length, keySize-filled);
			System.arraycopy(prev, 0, key, filled, n);
			filled += n;
		}
		Arrays.fill(pass, (byte) 0);
		try {
			Cipher c = Cipher.getInstance(transform);
			c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, keyAlg), new IvParameterSpec(iv));
			return c.doFinal(body);
		} finally {
			Arrays.fill(key, (byte) 0);
		}
	}

	private static byte[] hex(String s) throws IOException {
		try {
			return Hex.decode(s);
		} catch (IllegalArgumentException e) {
			throw new IOException("Bad IV in DEK-Info");
		}
	}
}
