package us.bringardner.parley.core.util;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.SignatureException;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.util.Arrays;

/**
 * Elliptic curve helpers that protocols need around the JDK's EC support: curve
 * points as bytes (with the on-curve check an untrusted point must pass), and ECDSA
 * signatures in the JDK's DER form, as the two numbers r and s, or as r || s (DNSSEC,
 * RFC 6605).
 * <p>
 * From parley-ssh (point checks, DER) and parley-dns (raw signatures).
 *
 * @author Tony Bringardner
 */
public final class Ec {

	private Ec() {
	}

	/** @return the size in bytes of a coordinate (and of r and s) on this curve */
	public static int fieldSize(ECParameterSpec params) {
		return (params.getCurve().getField().getFieldSize()+7)/8;
	}

	// ------------------------------------------------------------------ points

	/**
	 * Check a point from outside (a peer's public key, a DNSKEY): an invalid point is a
	 * known attack on ECDH, and a key that isn't on the curve must not be used.
	 *
	 * @throws InvalidKeyException unless y^2 = x^3 + ax + b (mod p) with x and y in the field
	 */
	public static void checkOnCurve(ECPoint w, ECParameterSpec params) throws InvalidKeyException {
		if( w == null || w.equals(ECPoint.POINT_INFINITY) ) {
			throw new InvalidKeyException("EC point is the point at infinity");
		}
		BigInteger p = ((ECFieldFp) params.getCurve().getField()).getP();
		BigInteger x = w.getAffineX();
		BigInteger y = w.getAffineY();
		if( x.signum() < 0 || x.compareTo(p) >= 0 || y.signum() < 0 || y.compareTo(p) >= 0 ) {
			throw new InvalidKeyException("EC point is outside the field");
		}
		BigInteger left = y.multiply(y).mod(p);
		BigInteger right = x.pow(3).add(params.getCurve().getA().multiply(x)).add(params.getCurve().getB()).mod(p);
		if( !left.equals(right) ) {
			throw new InvalidKeyException("EC point is not on the curve");
		}
	}

	/** @return the uncompressed point encoding (SEC1): 0x04 || x || y, each the field size */
	public static byte[] encodePoint(ECPoint w, ECParameterSpec params) {
		int size = fieldSize(params);
		byte[] ret = new byte[1+2*size];
		ret[0] = 4;
		unsigned(w.getAffineX(), ret, 1, size);
		unsigned(w.getAffineY(), ret, 1+size, size);
		return ret;
	}

	/**
	 * @param data an uncompressed point (SEC1): 0x04 || x || y
	 * @return the point, checked to be on the curve
	 * @throws InvalidKeyException if it isn't an uncompressed point on the curve
	 */
	public static ECPoint decodePoint(byte[] data, ECParameterSpec params) throws InvalidKeyException {
		int size = fieldSize(params);
		if( data == null || data.length != 1+2*size || data[0] != 4 ) {
			throw new InvalidKeyException("Invalid EC point (only uncompressed points are supported)");
		}
		return rawPoint(Arrays.copyOfRange(data, 1, data.length), params);
	}

	/** @return x || y, each the field size (a DNSSEC ECDSA public key, RFC 6605 4) */
	public static byte[] encodeRawPoint(ECPoint w, ECParameterSpec params) {
		byte[] p = encodePoint(w, params);
		return Arrays.copyOfRange(p, 1, p.length);
	}

	/**
	 * @param data x || y, each the field size
	 * @return the point, checked to be on the curve
	 * @throws InvalidKeyException if it has the wrong length or isn't on the curve
	 */
	public static ECPoint rawPoint(byte[] data, ECParameterSpec params) throws InvalidKeyException {
		int size = fieldSize(params);
		if( data == null || data.length != 2*size ) {
			throw new InvalidKeyException("Invalid EC point: "+(data == null ? 0 : data.length)+" bytes, not "+2*size);
		}
		ECPoint ret = new ECPoint(new BigInteger(1, Arrays.copyOfRange(data, 0, size)), new BigInteger(1, Arrays.copyOfRange(data, size, 2*size)));
		checkOnCurve(ret, params);
		return ret;
	}

	/**
	 * Write v as exactly len unsigned big endian bytes.
	 * @throws IllegalArgumentException if v is negative or doesn't fit
	 */
	public static void unsigned(BigInteger v, byte[] out, int off, int len) {
		if( v.signum() < 0 ) {
			throw new IllegalArgumentException("Negative value");
		}
		byte[] b = v.toByteArray();
		int start = (b.length > 1 && b[0] == 0) ? 1 : 0;
		int n = b.length-start;
		if( n > len ) {
			throw new IllegalArgumentException("Value too large for "+len+" bytes");
		}
		Arrays.fill(out, off, off+len-n, (byte) 0);
		System.arraycopy(b, start, out, off+len-n, n);
	}

	/** @return v as exactly len unsigned big endian bytes */
	public static byte[] unsigned(BigInteger v, int len) {
		byte[] ret = new byte[len];
		unsigned(v, ret, 0, len);
		return ret;
	}

	// ------------------------------------------------------------------ ECDSA signatures

	/** @return the DER form (SEQUENCE { INTEGER r, INTEGER s }) the JDK's Signature uses */
	public static byte[] signatureToDer(BigInteger r, BigInteger s) {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		derItem(body, Der.INTEGER, r.toByteArray());
		derItem(body, Der.INTEGER, s.toByteArray());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		derItem(out, Der.SEQUENCE, body.toByteArray());
		return out.toByteArray();
	}

	/**
	 * @param raw r || s, two numbers of the same size
	 * @return the DER form
	 * @throws SignatureException if raw has an odd length (or none)
	 */
	public static byte[] signatureToDer(byte[] raw) throws SignatureException {
		if( raw == null || raw.length == 0 || raw.length % 2 != 0 ) {
			throw new SignatureException("Bad ECDSA signature: "+(raw == null ? 0 : raw.length)+" bytes");
		}
		int size = raw.length/2;
		return signatureToDer(new BigInteger(1, Arrays.copyOfRange(raw, 0, size)), new BigInteger(1, Arrays.copyOfRange(raw, size, raw.length)));
	}

	/**
	 * @return {r, s} from the DER form; the encoding must be exactly one SEQUENCE of two
	 *  positive INTEGERs
	 * @throws SignatureException if it isn't
	 */
	public static BigInteger[] signatureFromDer(byte[] der) throws SignatureException {
		int[] pos = {0};
		if( der == null || der.length < 2 || (der[pos[0]++] & 0xff) != Der.SEQUENCE ) {
			throw bad();
		}
		int seqLen = derLength(der, pos);
		if( pos[0]+seqLen != der.length ) {
			throw bad();
		}
		BigInteger r = derInteger(der, pos);
		BigInteger s = derInteger(der, pos);
		if( pos[0] != der.length ) {
			throw bad();
		}
		return new BigInteger[] {r, s};
	}

	/**
	 * @return r || s, each size bytes, from the DER form
	 * @throws SignatureException if the DER is malformed or r or s doesn't fit size bytes
	 */
	public static byte[] signatureFromDer(byte[] der, int size) throws SignatureException {
		BigInteger[] rs = signatureFromDer(der);
		byte[] ret = new byte[2*size];
		try {
			unsigned(rs[0], ret, 0, size);
			unsigned(rs[1], ret, size, size);
		} catch (IllegalArgumentException e) {
			throw new SignatureException("Bad ECDSA signature: "+e.getMessage());
		}
		return ret;
	}

	private static SignatureException bad() {
		return new SignatureException("Bad ECDSA signature encoding");
	}

	private static void derItem(ByteArrayOutputStream out, int tag, byte[] value) {
		out.write(tag);
		int len = value.length;
		if( len < 128 ) {
			out.write(len);
		} else if( len < 256 ) {
			out.write(0x81);
			out.write(len);
		} else {
			out.write(0x82);
			out.write(len >>> 8);
			out.write(len);
		}
		out.write(value, 0, value.length);
	}

	private static BigInteger derInteger(byte[] der, int[] pos) throws SignatureException {
		if( pos[0] >= der.length || (der[pos[0]++] & 0xff) != Der.INTEGER ) {
			throw bad();
		}
		int len = derLength(der, pos);
		if( len <= 0 || pos[0]+len > der.length ) {
			throw bad();
		}
		BigInteger v = new BigInteger(Arrays.copyOfRange(der, pos[0], pos[0]+len));
		pos[0] += len;
		if( v.signum() <= 0 ) {
			throw bad();
		}
		return v;
	}

	private static int derLength(byte[] der, int[] pos) throws SignatureException {
		if( pos[0] >= der.length ) {
			throw bad();
		}
		int b = der[pos[0]++] & 0xff;
		if( b < 128 ) {
			return b;
		}
		int n = b & 0x7f;
		if( n < 1 || n > 2 || pos[0]+n > der.length ) {
			throw bad();
		}
		int len = 0;
		for (int i = 0; i < n; i++) {
			len = (len << 8) | (der[pos[0]++] & 0xff);
		}
		return len;
	}
}
