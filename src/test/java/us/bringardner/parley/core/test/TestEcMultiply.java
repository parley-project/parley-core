package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.util.Random;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.util.PrivateKeys;

/**
 * PrivateKeys.multiply (the public key of an EC private key) uses Jacobian coordinates. The reference is
 * the JDK itself: the public key of a key pair the JDK generated must be what multiply gives.
 */
public class TestEcMultiply {

	private static final String[] CURVES = {"secp256r1", "secp384r1", "secp521r1"};

	private static ECParameterSpec curve(String name) throws Exception {
		return PrivateKeys.curve(name);
	}

	/** The textbook affine addition, as the code was before. */
	private static ECPoint affineAdd(ECPoint q, ECPoint r, ECParameterSpec params) {
		BigInteger p = ((ECFieldFp) params.getCurve().getField()).getP();
		BigInteger a = params.getCurve().getA();
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

	private static ECPoint affineMultiply(ECPoint point, BigInteger k, ECParameterSpec params) {
		ECPoint result = ECPoint.POINT_INFINITY;
		ECPoint addend = point;
		for(int i = 0; i < k.bitLength(); i++) {
			if( k.testBit(i) ) {
				result = affineAdd(result, addend, params);
			}
			addend = affineAdd(addend, addend, params);
		}
		return result;
	}

	@Test
	public void testMatchesTheJdksPublicKeys() throws Exception {
		for(String name : CURVES) {
			KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
			g.initialize(new ECGenParameterSpec(name));
			for(int i = 0; i < 8; i++) {
				KeyPair kp = g.generateKeyPair();
				ECPrivateKey priv = (ECPrivateKey) kp.getPrivate();
				ECPublicKey pub = (ECPublicKey) kp.getPublic();
				ECPoint w = PrivateKeys.multiply(priv.getParams().getGenerator(), priv.getS(), priv.getParams());
				assertEquals(pub.getW(), w, name+" key "+i);
			}
		}
	}

	@Test
	public void testEdgeScalars() throws Exception {
		for(String name : CURVES) {
			ECParameterSpec c = curve(name);
			ECPoint g = c.getGenerator();
			BigInteger n = c.getOrder();
			BigInteger p = ((ECFieldFp) c.getCurve().getField()).getP();
			assertEquals(ECPoint.POINT_INFINITY, PrivateKeys.multiply(g, BigInteger.ZERO, c), name+" 0");
			assertEquals(g, PrivateKeys.multiply(g, BigInteger.ONE, c), name+" 1");
			assertEquals(affineAdd(g, g, c), PrivateKeys.multiply(g, BigInteger.TWO, c), name+" 2");
			assertEquals(ECPoint.POINT_INFINITY, PrivateKeys.multiply(g, n, c), name+" n");
			assertEquals(g, PrivateKeys.multiply(g, n.add(BigInteger.ONE), c), name+" n+1");
			ECPoint minusG = new ECPoint(g.getAffineX(), p.subtract(g.getAffineY()));
			assertEquals(minusG, PrivateKeys.multiply(g, n.subtract(BigInteger.ONE), c), name+" n-1");
			assertEquals(minusG, PrivateKeys.multiply(g, BigInteger.ONE.negate(), c), name+" -1");
			assertEquals(ECPoint.POINT_INFINITY, PrivateKeys.multiply(ECPoint.POINT_INFINITY, BigInteger.TEN, c), name+" infinity");
		}
	}

	@Test
	public void testSameAsTheAffineVersionForAnyPoint() throws Exception {
		Random r = new Random(11);
		for(String name : CURVES) {
			ECParameterSpec c = curve(name);
			ECPoint g = c.getGenerator();
			ECPoint other = affineMultiply(g, BigInteger.valueOf(7919), c);
			for(int i = 0; i < 6; i++) {
				BigInteger k = new BigInteger(c.getOrder().bitLength() + (i % 2 == 0 ? 0 : 20), r);
				assertEquals(affineMultiply(g, k, c), PrivateKeys.multiply(g, k, c), name+" G*k "+i);
				assertEquals(affineMultiply(other, k, c), PrivateKeys.multiply(other, k, c), name+" P*k "+i);
			}
			//  k * (m * G) = (k * m) * G
			BigInteger k = BigInteger.valueOf(104729);
			assertEquals(PrivateKeys.multiply(g, k.multiply(BigInteger.valueOf(7919)), c), PrivateKeys.multiply(other, k, c), name);
			//  a small run of consecutive scalars crosses the case where the running point equals the added one
			for(int s = 1; s < 40; s++) {
				assertEquals(affineMultiply(g, BigInteger.valueOf(s), c), PrivateKeys.multiply(g, BigInteger.valueOf(s), c), name+" "+s);
			}
			assertNotEquals(g, PrivateKeys.multiply(g, BigInteger.valueOf(2), c));
		}
	}
}
