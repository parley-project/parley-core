package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.util.Base32Hex;
import us.bringardner.parley.core.util.RenamedProperty;

public class TestBase32HexAndRenamedProperty {

	private static byte[] b(String s) {
		return s.getBytes(StandardCharsets.US_ASCII);
	}

	/** RFC 4648 section 10 test vectors (lower case, no padding) */
	@Test
	public void rfc4648Vectors() {
		String[][] v = {{"", ""}, {"f", "co"}, {"fo", "cpng"}, {"foo", "cpnmu"}, {"foob", "cpnmuog"},
				{"fooba", "cpnmuoj1"}, {"foobar", "cpnmuoj1e8"}};
		for (String[] x : v) {
			assertEquals(x[1], Base32Hex.encode(b(x[0])), x[0]);
			assertArrayEquals(b(x[0]), Base32Hex.decode(x[1]), x[1]);
		}
		assertArrayEquals(b("foobar"), Base32Hex.decode("CPNMUOJ1E8======"), "upper case and padding");
		assertArrayEquals(new byte[0], Base32Hex.decode("===="));
		assertThrows(IllegalArgumentException.class, () -> Base32Hex.decode("w"));
	}

	@Test
	public void renamedProperty() {
		String n = "TestRenamed.new", o = "TestRenamed.old";
		try {
			assertNull(RenamedProperty.get(n, o));
			assertEquals("d", RenamedProperty.get(n, o, "d"));
			System.setProperty(o, "old");
			assertEquals("old", RenamedProperty.get(n, o));
			assertEquals("old", RenamedProperty.get(n, o), "warned once, still read");
			System.setProperty(n, "new");
			assertEquals("new", RenamedProperty.get(n, o, "d"));
			assertEquals("new", RenamedProperty.get(n, null));
			System.clearProperty(n);
			assertNull(RenamedProperty.get(n, null));
		} finally {
			System.clearProperty(n);
			System.clearProperty(o);
		}
	}
}
