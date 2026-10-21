package us.bringardner.parley.core.util;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

/**
 * Base32 with the extended hex alphabet (RFC 4648 section 7), lower case and without
 * padding, as DNSSEC NSEC3 owner names and next hashes use it (RFC 5155). Decoding
 * accepts upper case and trailing '=' padding.
 * <p>
 * From parley-dns.
 *
 * @author Tony Bringardner
 */
public final class Base32Hex {

	private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuv";

	private Base32Hex() {
	}

	/** @return data in base32hex, lower case, no padding */
	public static String encode(byte[] data) {
		StringBuilder b = new StringBuilder((data.length*8+4)/5);
		int buffer = 0;
		int bits = 0;
		for (byte x : data) {
			buffer = (buffer << 8) | (x & 0xff);
			bits += 8;
			while( bits >= 5 ) {
				b.append(ALPHABET.charAt((buffer >> (bits-5)) & 0x1f));
				bits -= 5;
			}
		}
		if( bits > 0 ) {
			b.append(ALPHABET.charAt((buffer << (5-bits)) & 0x1f));
		}
		return b.toString();
	}

	/**
	 * @return the bytes of base32hex text (upper or lower case, padding optional)
	 * @throws IllegalArgumentException for a character outside the alphabet
	 */
	public static byte[] decode(String text) {
		String t = text.toLowerCase(Locale.ROOT);
		int end = t.length();
		while( end > 0 && t.charAt(end-1) == '=' ) {
			end--;
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int buffer = 0;
		int bits = 0;
		for (int i = 0; i < end; i++) {
			int v = ALPHABET.indexOf(t.charAt(i));
			if( v < 0 ) {
				throw new IllegalArgumentException("Invalid base32hex '"+text+"'");
			}
			buffer = (buffer << 5) | v;
			bits += 5;
			if( bits >= 8 ) {
				out.write((buffer >> (bits-8)) & 0xff);
				bits -= 8;
			}
		}
		return out.toByteArray();
	}
}
