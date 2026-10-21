package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.util.Ec;
import us.bringardner.parley.core.util.PrivateKeys;

public class TestEc {

	private static KeyPair pair(String curve) throws Exception {
		KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
		g.initialize(new ECGenParameterSpec(curve));
		return g.generateKeyPair();
	}

	@Test
	public void pointsRoundTripAndAreChecked() throws Exception {
		for(String c : new String[] {"secp256r1", "secp384r1", "secp521r1"}) {
			ECPublicKey pub = (ECPublicKey) pair(c).getPublic();
			ECParameterSpec params = pub.getParams();
			byte[] enc = Ec.encodePoint(pub.getW(), params);
			assertEquals(1+2*Ec.fieldSize(params), enc.length);
			assertEquals(pub.getW(), Ec.decodePoint(enc, params));
			assertEquals(pub.getW(), Ec.rawPoint(Ec.encodeRawPoint(pub.getW(), params), params));
			enc[enc.length-1] ^= 1;
			assertThrows(InvalidKeyException.class, () -> Ec.decodePoint(enc, params), "not on the curve");
		}
	}

	@Test
	public void badPointsAreRefused() throws Exception {
		ECParameterSpec params = PrivateKeys.curve("secp256r1");
		assertThrows(InvalidKeyException.class, () -> Ec.decodePoint(new byte[65], params), "no 0x04");
		assertThrows(InvalidKeyException.class, () -> Ec.decodePoint(new byte[3], params));
		assertThrows(InvalidKeyException.class, () -> Ec.decodePoint(null, params));
		assertThrows(InvalidKeyException.class, () -> Ec.rawPoint(new byte[63], params));
		assertThrows(InvalidKeyException.class, () -> Ec.rawPoint(null, params));
		assertThrows(InvalidKeyException.class, () -> Ec.checkOnCurve(ECPoint.POINT_INFINITY, params));
		assertThrows(InvalidKeyException.class, () -> Ec.checkOnCurve(null, params));
		BigInteger p = ((java.security.spec.ECFieldFp) params.getCurve().getField()).getP();
		assertThrows(InvalidKeyException.class, () -> Ec.checkOnCurve(new ECPoint(p, BigInteger.ONE), params));
		assertThrows(InvalidKeyException.class, () -> Ec.checkOnCurve(new ECPoint(BigInteger.ONE, BigInteger.ONE.negate()), params));
		assertThrows(InvalidKeyException.class, () -> Ec.checkOnCurve(new ECPoint(BigInteger.ONE.negate(), BigInteger.ONE), params));
		assertThrows(InvalidKeyException.class, () -> Ec.checkOnCurve(new ECPoint(BigInteger.ONE, p), params));
	}

	@Test
	public void signaturesConvertBothWays() throws Exception {
		for(String c : new String[] {"secp256r1", "secp384r1", "secp521r1"}) {
			KeyPair kp = pair(c);
			int size = Ec.fieldSize(((ECPublicKey) kp.getPublic()).getParams());
			Signature s = Signature.getInstance("SHA256withECDSA");
			s.initSign(kp.getPrivate());
			s.update(new byte[] {1, 2, 3});
			byte[] der = s.sign();
			byte[] raw = Ec.signatureFromDer(der, size);
			assertEquals(2*size, raw.length);
			byte[] back = Ec.signatureToDer(raw);
			s.initVerify(kp.getPublic());
			s.update(new byte[] {1, 2, 3});
			assertTrue(s.verify(back), c);
			BigInteger[] rs = Ec.signatureFromDer(der);
			assertArrayEquals(back, Ec.signatureToDer(rs[0], rs[1]));
		}
	}

	@Test
	public void longNumbersUseLongLengths() throws Exception {
		BigInteger big = BigInteger.ONE.shiftLeft(8*130);
		byte[] der = Ec.signatureToDer(big, big);
		BigInteger[] rs = Ec.signatureFromDer(der);
		assertEquals(big, rs[0]);
		assertEquals(big, rs[1]);
		BigInteger mid = BigInteger.ONE.shiftLeft(8*60);
		assertEquals(mid, Ec.signatureFromDer(Ec.signatureToDer(mid, mid))[1]);
	}

	@Test
	public void malformedSignaturesAreRefused() {
		assertThrows(SignatureException.class, () -> Ec.signatureFromDer(null));
		assertThrows(SignatureException.class, () -> Ec.signatureFromDer(new byte[] {0x30}));
		assertThrows(SignatureException.class, () -> Ec.signatureFromDer(new byte[] {0x31, 0}));
		assertThrows(SignatureException.class, () -> Ec.signatureFromDer(new byte[] {0x30, 5, 2, 1, 1}), "sequence length");
		assertThrows(SignatureException.class, () -> Ec.signatureFromDer(new byte[] {0x30, 3, 2, 1, 1}), "one integer");
		assertThrows(SignatureException.class, () -> Ec.signatureFromDer(new byte[] {0x30, 6, 2, 1, 1, 4, 1, 1}), "not an integer");
		assertThrows(SignatureException.class, () -> Ec.signatureFromDer(new byte[] {0x30, 6, 2, 1, 0, 2, 1, 1}), "zero");
		assertThrows(SignatureException.class, () -> Ec.signatureFromDer(new byte[] {0x30, 6, 2, 1, (byte)0xff, 2, 1, 1}), "negative");
		assertThrows(SignatureException.class, () -> Ec.signatureFromDer(new byte[] {0x30, 6, 2, 0, 2, 1, 1, 0}), "empty integer");
		assertThrows(SignatureException.class, () -> Ec.signatureFromDer(new byte[] {0x30, 6, 2, 1, 1, 2, 2, 1}), "runs past the end");
		assertThrows(SignatureException.class, () -> Ec.signatureFromDer(new byte[] {0x30, (byte)0x83, 0, 0, 0}), "3 byte length");
		assertThrows(SignatureException.class, () -> Ec.signatureFromDer(new byte[] {0x30, (byte)0x81}), "missing length byte");
		assertThrows(SignatureException.class, () -> Ec.signatureFromDer(new byte[] {0x30, 7, 2, 1, 1, 2, 1, 1, 0}), "trailing byte");
		assertThrows(SignatureException.class, () -> Ec.signatureFromDer(new byte[] {0x30, 4, 2, 1, 1, 2}), "no length");
		byte[] big = Ec.signatureToDer(BigInteger.ONE.shiftLeft(300), BigInteger.ONE);
		assertThrows(SignatureException.class, () -> Ec.signatureFromDer(big, 32), "r doesn't fit");
		assertThrows(SignatureException.class, () -> Ec.signatureToDer(new byte[3]));
		assertThrows(SignatureException.class, () -> Ec.signatureToDer((byte[]) null));
		assertThrows(SignatureException.class, () -> Ec.signatureToDer(new byte[0]));
	}

	@Test
	public void unsignedPadsAndRefuses() {
		assertArrayEquals(new byte[] {0, 0, 1}, Ec.unsigned(BigInteger.ONE, 3));
		assertArrayEquals(new byte[] {(byte)0xff}, Ec.unsigned(BigInteger.valueOf(255), 1), "sign byte dropped");
		assertThrows(IllegalArgumentException.class, () -> Ec.unsigned(BigInteger.valueOf(256), 1));
		assertThrows(IllegalArgumentException.class, () -> Ec.unsigned(BigInteger.ONE.negate(), 1));
	}

	@Test
	public void exposureOfAPrivateKeyFile() throws Exception {
		File f = File.createTempFile("key", ".private");
		try {
			try {
				Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("rw-------"));
			} catch(UnsupportedOperationException ex) {
				Assumptions.assumeTrue(false, "no POSIX permissions");
			}
			assertNull(PrivateKeys.exposure(f));
			Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("rw-r-----"));
			assertEquals("readable by its group", PrivateKeys.exposure(f));
			Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("rw-r--r--"));
			assertEquals("readable by everyone", PrivateKeys.exposure(f));
			assertNull(PrivateKeys.exposure(new File(f.getPath()+".missing")));
		} finally {
			f.delete();
		}
	}
}
