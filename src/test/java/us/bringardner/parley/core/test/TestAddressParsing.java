package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.util.AddressMatcher;

/**
 * AddressMatcher reads its lists without regular expressions. These tests compare it, on a wide set of
 * strings, with the regular expression version it replaced.
 */
public class TestAddressParsing {

	/** What AddressMatcher.toBytes did when it used String.matches and split. */
	private static byte[] reference(String addr) {
		if( addr.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}") ) {
			String[] parts = addr.split("\\.");
			byte[] ret = new byte[4];
			for(int i = 0; i < 4; i++) {
				int v = Integer.parseInt(parts[i]);
				if( v > 255 ) {
					return null;
				}
				ret[i] = (byte) v;
			}
			return ret;
		}
		if( addr.indexOf(':') >= 0 && addr.matches("[0-9A-Fa-f:.]+") ) {
			try {
				return InetAddress.getByName(addr).getAddress();
			} catch(UnknownHostException e) {
				return null;
			}
		}
		return null;
	}

	private static List<String> corpus() {
		List<String> ret = new ArrayList<>();
		String[] fixed = {
			"1.2.3.4", "0.0.0.0", "255.255.255.255", "256.1.1.1", "1.2.3.999", "1.2.3", "1.2.3.4.5", "1..3.4", ".1.2.3",
			"1.2.3.", "01.02.03.04", "001.2.3.4", "0001.2.3.4", "1.2.3.4a", "a.b.c.d", "1.2.3. 4", "", " ", ".", "...",
			"::", "::1", "1::", "2001:db8::1", "2001:db8:0:0:0:0:0:1", "1:2:3:4:5:6:7:8", "1:2:3:4:5:6:7:8:9", ":::",
			"::ffff:1.2.3.4", "::ffff:1.2.3.999", "fe80::1%eth0", "g::1", "12345::1", "1:2", ":", "a", "abc", "localhost",
			"example.com", "1.2.3.4:80", "[::1]", "١.٢.٣.٤", "1.2.3.\u0661", "0x7f.0.0.1", "1.2.3.4/8", "-1.2.3.4",
		};
		for(String s : fixed) {
			ret.add(s);
		}
		java.util.Random r = new java.util.Random(7);
		String alphabet = "0123456789abcdefABCDEFxg:./ -";
		for(int i = 0; i < 3000; i++) {
			int n = 1 + r.nextInt(20);
			StringBuilder sb = new StringBuilder();
			for(int j = 0; j < n; j++) {
				sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
			}
			ret.add(sb.toString());
		}
		for(int i = 0; i < 1000; i++) {
			ret.add(r.nextInt(300)+"."+r.nextInt(300)+"."+r.nextInt(300)+"."+r.nextInt(300));
		}
		return ret;
	}

	@Test
	public void testIsAddressLiteralMatchesTheRegexVersion() {
		for(String s : corpus()) {
			boolean expected = reference(s.trim()) != null;
			assertEquals(expected, AddressMatcher.isAddressLiteral(s), "isAddressLiteral('"+s+"')");
		}
	}

	@Test
	public void testParseAcceptsAndRejectsTheSameEntries() {
		for(String s : corpus()) {
			String t = s.trim();
			if( t.isEmpty() || t.indexOf('/') >= 0 || t.chars().anyMatch(c -> c == ',' || Character.isWhitespace(c)) ) {
				continue;	//  a separator inside would make it more than one entry; prefixes have their own test
			}
			boolean expected = reference(t) != null;
			boolean actual;
			try {
				AddressMatcher.parse(t);
				actual = true;
			} catch(IllegalArgumentException e) {
				actual = false;
			}
			assertEquals(expected, actual, "parse('"+t+"')");
		}
	}

	@Test
	public void testPrefixLengthsMatchTheRegexVersion() {
		String[] prefixes = {"0", "8", "24", "32", "33", "128", "129", "999", "1000", "", "a", "-1", "+1", "01", "007"};
		for(String p : prefixes) {
			boolean shape = p.matches("[0-9]{1,3}");
			for(String net : new String[] {"10.0.0.0", "2001:db8::"}) {
				int bits = net.indexOf(':') >= 0 ? 128 : 32;
				boolean expected = shape && Integer.parseInt(p) <= bits;
				boolean actual;
				try {
					AddressMatcher.parse(net+"/"+p);
					actual = true;
				} catch(IllegalArgumentException e) {
					actual = false;
				}
				assertEquals(expected, actual, net+"/"+p);
			}
		}
	}

	@Test
	public void testListSeparators() throws Exception {
		AddressMatcher m = AddressMatcher.parse(" ,10.0.0.1,\t192.168.0.0/16 ,\n::1,, 172.16.0.0/12\r\n");
		for(String ok : new String[] {"10.0.0.1", "192.168.5.5", "::1", "172.20.1.1"}) {
			assertEquals(true, m.matches(InetAddress.getByName(ok)), ok);
		}
		assertEquals(false, m.matches(InetAddress.getByName("10.0.0.2")));
		//  control characters at either end are trimmed, as before
		assertEquals(true, AddressMatcher.parse("\u0001 10.0.0.1 \u0002").matches(InetAddress.getByName("10.0.0.1")));
	}
}
