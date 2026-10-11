package us.bringardner.parley.core.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <PRE>
 * A list of IP addresses and networks (CIDR), e.g. "192.0.2.10, 10.0.0.0/8, 2001:db8::/32",
 * for allow lists: which clients may relay mail, transfer a zone, connect at all ...
 *
 * Only address literals are accepted, never a host name, so parsing never does a DNS lookup
 * and a typo can't turn into a lookup of a name nobody meant. A prefix length must be 0 to 32
 * (IPv4) or 0 to 128 (IPv6); anything else is rejected rather than matching too much or too little.
 * An IPv4 address and an IPv6 address never match each other.
 *
 * Instances are immutable and thread safe. {@link #matches} compares the address with each entry in
 * turn (about 2-4 ns per entry), which suits the lists this is meant for, up to a few hundred
 * entries. A list of thousands of networks (an imported blocklist, say) needs a different structure.
 *
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
 *  @author Tony Bringardner
 */
public final class AddressMatcher {

	/** Matches nothing. */
	public static final AddressMatcher NONE = new AddressMatcher(Collections.<byte[]>emptyList(), Collections.<Integer>emptyList(), "");

	private final List<byte[]> networks;
	private final List<Integer> prefixes;
	private final String text;

	private AddressMatcher(List<byte[]> networks, List<Integer> prefixes, String text) {
		this.networks = networks;
		this.prefixes = prefixes;
		this.text = text;
	}

	/**
	 * @param list comma or space separated addresses / networks; null or empty matches nothing
	 * @return a matcher for the list
	 * @throws IllegalArgumentException for an entry that is not an address or network
	 */
	public static AddressMatcher parse(String list) {
		if( list == null || list.trim().isEmpty() ) {
			return NONE;
		}
		List<byte[]> nets = new ArrayList<byte[]>();
		List<Integer> prefixes = new ArrayList<Integer>();
		for(String item : items(list.trim())) {
			String addr = item;
			int prefix = -1;
			int slash = item.indexOf('/');
			if( slash >= 0 ) {
				addr = item.substring(0, slash);
				String p = item.substring(slash+1);
				if( !isDigits(p, 0, p.length(), 3) ) {
					throw new IllegalArgumentException("Invalid prefix length in '"+item+"'");
				}
				prefix = Integer.parseInt(p);
			}
			byte [] b = toBytes(addr);
			if( b == null ) {
				throw new IllegalArgumentException("Not an IP address: '"+item+"'");
			}
			if( prefix < 0 ) {
				prefix = b.length*8;
			}
			if( prefix > b.length*8 ) {
				throw new IllegalArgumentException("Prefix too long in '"+item+"'");
			}
			nets.add(b);
			prefixes.add(prefix);
		}
		return new AddressMatcher(Collections.unmodifiableList(nets), Collections.unmodifiableList(prefixes), list.trim());
	}

	/**
	 * @param host a host name or address
	 * @return true if it is an IPv4 or IPv6 address literal (checked without a DNS lookup)
	 */
	public static boolean isAddressLiteral(String host) {
		return host != null && toBytes(host.trim()) != null;
	}

	/**
	 * @return the address's bytes, or null if it isn't an IPv4 or IPv6 literal. Never a name lookup:
	 * IPv4 is parsed here (InetAddress.getByName would look up "1.2.3.999" as a host name), and
	 * InetAddress only gets strings with a ':', which can't be host names.
	 */
	private static byte [] toBytes(String addr) {
		byte [] v4 = ipv4(addr);
		if( v4 != null ) {
			return v4;
		}
		if( isIpv4Shape(addr) ) {
			return null;	//  four numbers, one more than 255
		}
		if( addr.indexOf(':') >= 0 && isAddressChars(addr) ) {
			try {
				return InetAddress.getByName(addr).getAddress();
			} catch(UnknownHostException e) {
				return null;
			}
		}
		return null;
	}

	//  The list is parsed with plain loops rather than String.matches / split: those compile a regular
	//  expression for every call, which was most of the time spent in parse.

	/** @return the entries of a list separated by commas and white space (ASCII, like the regex \\s) */
	private static List<String> items(String list) {
		List<String> ret = new ArrayList<String>();
		int start = -1;
		int n = list.length();
		for(int i = 0; i <= n; i++) {
			boolean sep = i == n || list.charAt(i) == ',' || isSpace(list.charAt(i));
			if( sep ) {
				if( start >= 0 ) {
					ret.add(list.substring(start, i));
					start = -1;
				}
			} else if( start < 0 ) {
				start = i;
			}
		}
		return ret;
	}

	private static boolean isSpace(char c) {
		return c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r';
	}

	/** @return true if s[from, to) is 1 to max ASCII digits */
	private static boolean isDigits(String s, int from, int to, int max) {
		int n = to - from;
		if( n < 1 || n > max ) {
			return false;
		}
		for(int i = from; i < to; i++) {
			char c = s.charAt(i);
			if( c < '0' || c > '9' ) {
				return false;
			}
		}
		return true;
	}

	/** @return true for exactly four dot separated numbers of 1 to 3 digits (they may be over 255) */
	private static boolean isIpv4Shape(String s) {
		int from = 0;
		for(int part = 0; part < 4; part++) {
			int dot = part < 3 ? s.indexOf('.', from) : s.length();
			if( dot < 0 || !isDigits(s, from, dot, 3) ) {
				return false;
			}
			from = dot + 1;
		}
		return true;
	}

	/** @return the four bytes of a dotted address, or null if it isn't one or has a number over 255 */
	private static byte [] ipv4(String s) {
		if( !isIpv4Shape(s) ) {
			return null;
		}
		byte [] ret = new byte[4];
		int from = 0;
		for(int part = 0; part < 4; part++) {
			int dot = part < 3 ? s.indexOf('.', from) : s.length();
			int v = 0;
			for(int i = from; i < dot; i++) {
				v = v*10 + (s.charAt(i) - '0');
			}
			if( v > 255 ) {
				return null;
			}
			ret[part] = (byte) v;
			from = dot + 1;
		}
		return ret;
	}

	/** @return true if every character is a hex digit, ':' or '.' (and there is at least one) */
	private static boolean isAddressChars(String s) {
		if( s.isEmpty() ) {
			return false;
		}
		for(int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == ':' || c == '.';
			if( !ok ) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @param a an address
	 * @return true if the address is in one of the networks
	 */
	public boolean matches(InetAddress a) {
		if( a == null ) {
			return false;
		}
		byte [] b = a.getAddress();
		for(int i=0; i < networks.size(); i++ ) {
			byte [] n = networks.get(i);
			if( n.length == b.length && samePrefix(n, b, prefixes.get(i)) ) {
				return true;
			}
		}
		return false;
	}

	private static boolean samePrefix(byte [] a, byte [] b, int bits) {
		int full = bits / 8;
		for(int i=0; i < full; i++ ) {
			if( a[i] != b[i] ) {
				return false;
			}
		}
		int rest = bits % 8;
		if( rest == 0 ) {
			return true;
		}
		int mask = (0xff << (8-rest)) & 0xff;
		return (a[full] & mask) == (b[full] & mask);
	}

	/**
	 * @return a matcher for this list and the other one (either may be empty)
	 */
	public AddressMatcher and(AddressMatcher other) {
		if( other == null || other.isEmpty() ) {
			return this;
		}
		if( isEmpty() ) {
			return other;
		}
		List<byte[]> nets = new ArrayList<byte[]>(networks);
		nets.addAll(other.networks);
		List<Integer> p = new ArrayList<Integer>(prefixes);
		p.addAll(other.prefixes);
		return new AddressMatcher(Collections.unmodifiableList(nets), Collections.unmodifiableList(p), text+", "+other.text);
	}

	/** @return true if this matches nothing */
	public boolean isEmpty() {
		return networks.isEmpty();
	}

	/** @return the list it was parsed from */
	@Override
	public String toString() {
		return text;
	}
}
