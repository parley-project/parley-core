package us.bringardner.parley.core.util;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * A minimal DER reader, enough for the private key formats (PKCS#1, PKCS#8, SEC1, PBES2) that {@link PrivateKeys} reads.
 * Reads one element at a time; constructed elements return a reader for their contents.
 *
 * @author Tony Bringardner
 */
public final class Der {

	/** The DER prefix of an Ed25519 SubjectPublicKeyInfo (X.509 public key); the 32 key bytes follow. Do not modify. */
	public static final byte[] ED25519_SPKI_PREFIX = Hex.decode("302a300506032b6570032100");
	/** The DER prefix of an Ed25519 PKCS#8 private key; the 32-byte seed follows. Do not modify. */
	public static final byte[] ED25519_PKCS8_PREFIX = Hex.decode("302e020100300506032b657004220420");

	public static final int INTEGER = 0x02;
	public static final int BIT_STRING = 0x03;
	public static final int OCTET_STRING = 0x04;
	public static final int NULL = 0x05;
	public static final int OID = 0x06;
	public static final int SEQUENCE = 0x30;

	private final byte[] data;
	private int pos;
	private final int end;

	public Der(byte[] data) {
		this(data, 0, data.length);
	}

	private Der(byte[] data, int off, int len) {
		this.data = data;
		this.pos = off;
		this.end = off+len;
	}

	public boolean hasMore() {
		return pos < end;
	}

	public int peekTag() throws IOException {
		if( pos >= end ) {
			throw new IOException("DER: no more elements");
		}
		return data[pos] & 0xff;
	}

	/**
	 * @return the contents of the next element, which must have this tag
	 */
	private int[] element(int tag) throws IOException {
		int t = peekTag();
		if( t != tag ) {
			throw new IOException("DER: expected tag 0x"+Integer.toHexString(tag)+", found 0x"+Integer.toHexString(t));
		}
		pos++;
		if( pos >= end ) {
			throw new IOException("DER: truncated");
		}
		int len = data[pos++] & 0xff;
		if( len >= 0x80 ) {
			int n = len & 0x7f;
			if( n < 1 || n > 3 || pos+n > end ) {
				throw new IOException("DER: bad length");
			}
			len = 0;
			for (int i = 0; i < n; i++) {
				len = (len << 8) | (data[pos++] & 0xff);
			}
		}
		if( len < 0 || pos+len > end ) {
			throw new IOException("DER: element longer than its container");
		}
		int start = pos;
		pos += len;
		return new int[] {start, len};
	}

	public Der sequence() throws IOException {
		int[] e = element(SEQUENCE);
		return new Der(data, e[0], e[1]);
	}

	/**
	 * @return the contents of the context specific constructed element [n], or null if the
	 * next element isn't one (it is optional)
	 */
	public Der explicit(int n) throws IOException {
		if( !hasMore() || peekTag() != (0xa0 | n) ) {
			return null;
		}
		int[] e = element(0xa0 | n);
		return new Der(data, e[0], e[1]);
	}

	public BigInteger integer() throws IOException {
		int[] e = element(INTEGER);
		return new BigInteger(Arrays.copyOfRange(data, e[0], e[0]+e[1]));
	}

	public byte[] octetString() throws IOException {
		int[] e = element(OCTET_STRING);
		return Arrays.copyOfRange(data, e[0], e[0]+e[1]);
	}

	/**
	 * @return the bits, without the unused-bits byte (which must be 0)
	 */
	public byte[] bitString() throws IOException {
		int[] e = element(BIT_STRING);
		if( e[1] < 1 || data[e[0]] != 0 ) {
			throw new IOException("DER: unsupported BIT STRING");
		}
		return Arrays.copyOfRange(data, e[0]+1, e[0]+e[1]);
	}

	/**
	 * @return the object identifier in dotted form, e.g. "1.2.840.113549.1.1.1"
	 */
	public String oid() throws IOException {
		int[] e = element(OID);
		StringBuilder sb = new StringBuilder();
		long v = 0;
		boolean first = true;
		for (int i = e[0]; i < e[0]+e[1]; i++) {
			int b = data[i] & 0xff;
			v = (v << 7) | (b & 0x7f);
			if( (b & 0x80) == 0 ) {
				if( first ) {
					long a = Math.min(v/40, 2);
					sb.append(a).append('.').append(v-a*40);
					first = false;
				} else {
					sb.append('.').append(v);
				}
				v = 0;
			}
		}
		return sb.toString();
	}

	/**
	 * Skip the next element, whatever it is.
	 */
	public void skip() throws IOException {
		element(peekTag());
	}
}
