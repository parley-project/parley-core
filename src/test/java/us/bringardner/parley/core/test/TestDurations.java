package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.BaseObject;
import us.bringardner.parley.core.util.Durations;

public class TestDurations {

	@Test
	public void units() {
		assertEquals(250, Durations.parse("250ms"));
		assertEquals(250, Durations.parse("250"), "a bare number is ms");
		assertEquals(30_000, Durations.parse("30s"));
		assertEquals(300_000, Durations.parse("5m"));
		assertEquals(3_600_000, Durations.parse("1h"));
		assertEquals(5L * 24 * 3_600_000, Durations.parse("5d"));
		assertEquals(0, Durations.parse("0"));
		assertEquals(30_000, Durations.parse(" 30 S "), "a space, and any case");
	}

	@Test
	public void notDurations() {
		for(String bad : new String[] { "", " ", "s", "-5s", "1.5h", "5 minutes", "5x", "1e3", "99999999999999999999d" }) {
			assertThrows(IllegalArgumentException.class, () -> Durations.parse(bad), bad);
		}
		assertThrows(IllegalArgumentException.class, () -> Durations.parse(null));
	}

	@Test
	public void lists() {
		assertArrayEquals(new long[] { 60_000, 300_000, 900_000, 1_800_000, 3_600_000, 7_200_000 }, Durations.parseList("1m,5m,15m,30m,1h,2h"));
		assertArrayEquals(new long[] { 500, 1000 }, Durations.parseList(" 500 , 1s "));
		assertThrows(IllegalArgumentException.class, () -> Durations.parseList(""));
		assertThrows(IllegalArgumentException.class, () -> Durations.parseList("1m,,5m"));
	}

	@Test
	public void format() {
		assertEquals("0", Durations.format(0));
		assertEquals("1500ms", Durations.format(1500));
		assertEquals("30s", Durations.format(30_000));
		assertEquals("90m", Durations.format(5_400_000));
		assertEquals("2h", Durations.format(7_200_000));
		assertEquals("5d", Durations.format(5L * 24 * 3_600_000));
		assertEquals("1m,5m,1h", Durations.formatList(60_000, 300_000, 3_600_000));
		for(String v : new String[] { "250ms", "30s", "5m", "1h", "5d" }) {
			assertEquals(v, Durations.format(Durations.parse(v)), "format writes what parse reads");
		}
	}

	@Test
	public void baseObject() {
		BaseObject o = new BaseObject();
		String key = "test.durations.timeout";
		String list = "test.durations.retry";
		try {
			assertEquals(4000, o.getDuration(key, 4000), "not set: the default");
			System.setProperty(key, "4s");
			assertEquals(4000, o.getDuration(key, 1));
			System.setProperty(key, "soon");
			assertEquals(7, o.getDuration(key, 7), "invalid: the default");
			assertArrayEquals(new long[] { 1, 2 }, o.getDurations(list, 1, 2));
			System.setProperty(list, "1m,1h");
			assertArrayEquals(new long[] { 60_000, 3_600_000 }, o.getDurations(list, 1));
		} finally {
			System.clearProperty(key);
			System.clearProperty(list);
		}
	}
}
