package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * getInheritedProperty: a setting the servers of a protocol share (ftp and ftps, say) is found under the
 * prefix of the server, else the one of its protocol, else the one of its suite, and nowhere else.
 */
public class TestPropertyPrefixes {

	private static final String[] FTP = { "parley.test.ftp", "parley.test" };
	private static final String[] FTPS = { "parley.test.ftps", "parley.test.ftp", "parley.test" };

	private static final String[] SYSTEM_NAMES = { "parley.test.shared", "parley.test.ftp.shared", "parley.test.ftps.shared",
			"parley.test.port", "parley.test.fromFileGeneral" };

	@AfterEach
	public void clear() {
		for(String n : SYSTEM_NAMES) {
			System.clearProperty(n);
		}
	}

	private static PrefixTarget server(Properties settings, String... prefixes) {
		PrefixTarget ret = new PrefixTarget(prefixes);
		ret.setConfiguration(settings);
		return ret;
	}

	private static Properties settings(String... pairs) {
		Properties ret = new Properties();
		for(int i = 0; i < pairs.length; i += 2) {
			ret.setProperty(pairs[i], pairs[i + 1]);
		}
		return ret;
	}

	@Test
	public void oneLineServesBothServers() {
		Properties p = settings("parley.test.externalAddress", "3.20.74.200");
		PrefixTarget ftp = server(p, FTP);
		PrefixTarget ftps = server(p, FTPS);
		assertEquals("3.20.74.200", ftp.getInheritedProperty("externalAddress"));
		assertEquals("3.20.74.200", ftps.getInheritedProperty("externalAddress"));
		assertEquals("parley.test.externalAddress", ftps.getInheritedPropertyName("externalAddress"));
	}

	@Test
	public void aValueForOneServerChangesOnlyThatServer() {
		Properties p = settings("parley.test.externalAddress", "3.20.74.200", "parley.test.ftps.externalAddress", "9.9.9.9");
		assertEquals("3.20.74.200", server(p, FTP).getInheritedProperty("externalAddress"));
		PrefixTarget ftps = server(p, FTPS);
		assertEquals("9.9.9.9", ftps.getInheritedProperty("externalAddress"));
		assertEquals("parley.test.ftps.externalAddress", ftps.getInheritedPropertyName("externalAddress"));
	}

	@Test
	public void theProtocolsValueIsBetweenTheServersAndTheSuites() {
		Properties p = settings("parley.test.externalAddress", "suite", "parley.test.ftp.externalAddress", "protocol");
		assertEquals("protocol", server(p, FTPS).getInheritedProperty("externalAddress"));
		assertEquals("protocol", server(p, FTP).getInheritedProperty("externalAddress"));
		assertEquals("suite", server(p, "parley.test.imap", "parley.test").getInheritedProperty("externalAddress"));
	}

	@Test
	public void aPrefixWithoutTheSettingIsSkipped() {
		Properties p = settings("parley.test.ftp.other", "x", "parley.test.shared", "suite");
		assertEquals("suite", server(p, FTPS).getInheritedProperty("shared"));
	}

	@Test
	public void noPrefixHavingItIsNullOrTheDefault() {
		PrefixTarget s = server(settings(), FTPS);
		assertNull(s.getInheritedProperty("shared"));
		assertNull(s.getInheritedPropertyName("shared"));
		assertEquals("none", s.getInheritedProperty("shared", "none"));
	}

	@Test
	public void theFirstPrefixIsTheOneThePrefixMethodsUse() {
		PrefixTarget s = server(settings(), FTPS);
		assertEquals("parley.test.ftps", s.getPropertyPrefix());
		assertEquals("parley.test.ftps.port", s.propertyName("port"));
	}

	@Test
	public void aSettingReadWithPropertyNameNeverFallsBack() {
		Properties p = settings("parley.test.port", "21");
		PrefixTarget ftps = server(p, FTPS);
		assertNull(ftps.getProperty(ftps.propertyName("port")), "a port is the server's own");
		assertEquals("21", ftps.getInheritedProperty("port"), "only a setting asked for as shared falls back");
	}

	@Test
	public void oneOrNoPrefixesIsAsBefore() {
		Properties p = settings("parley.test.ftp.shared", "own", "parley.test.shared", "other");
		PrefixTarget one = server(p, "parley.test.ftp");
		assertEquals("own", one.getInheritedProperty("shared"));
		assertEquals(one.getProperty(one.propertyName("shared")), one.getInheritedProperty("shared"));
		PrefixTarget none = server(settings());
		assertEquals(none.getProperty(none.propertyName("zzz")), none.getInheritedProperty("zzz"));
		none.setConfiguration(settings(none.getClass().getName() + ".zzz", "by class"));
		assertEquals("by class", none.getInheritedProperty("zzz"), "the default prefix is the class name, as it was");
	}

	@Test
	public void theMoreSpecificPrefixWinsWhereverItsValueComes() {
		// the specific value is in the properties file, the general one is a system property
		System.setProperty("parley.test.fromFile", "unused");
		PrefixTarget ftps = new PrefixTarget(FTPS);
		assertEquals("file-ftp", ftps.getInheritedProperty("fromFile"));
		assertEquals("parley.test.ftp.fromFile", ftps.getInheritedPropertyName("fromFile"));
		System.clearProperty("parley.test.fromFile");
		System.setProperty("parley.test.shared", "sys");
		System.setProperty("parley.test.ftp.shared", "sys-ftp");
		assertEquals("sys-ftp", ftps.getInheritedProperty("shared"));
	}

	@Test
	public void aSettingOfTheSuiteIsFoundInAFile() {
		assertEquals("file-general", new PrefixTarget(FTPS).getInheritedProperty("fromFileGeneral"));
	}

	@Test
	public void settingOnePrefixAgainEndsTheFallBack() {
		Properties p = settings("parley.test.shared", "suite");
		PrefixTarget s = server(p, FTPS);
		assertEquals("suite", s.getInheritedProperty("shared"));
		s.prefix("parley.test.ftps");
		assertNull(s.getInheritedProperty("shared"));
	}

	@Test
	public void thePrefixesAreNotChangedByTheCallersArray() {
		String[] mine = { "parley.test.ftps", "parley.test" };
		PrefixTarget s = server(settings("parley.test.shared", "suite"), mine);
		mine[1] = "elsewhere";
		assertEquals("suite", s.getInheritedProperty("shared"));
	}

	@Test
	public void noPrefixesOrANullOneIsRefused() {
		PrefixTarget s = new PrefixTarget();
		assertThrows(IllegalArgumentException.class, () -> s.prefixes());
		assertThrows(IllegalArgumentException.class, () -> s.prefixes("parley.test", null));
		assertThrows(IllegalArgumentException.class, () -> s.prefixes((String[]) null));
	}

	@Test
	public void anObjectWithoutPrefixesUsesTheNameAlone() {
		PrefixTarget s = server(settings("shared", "bare"));
		s.setPropertyPrefix("");
		assertEquals("bare", s.getInheritedProperty("shared"));
	}

	/** a class that does more when its prefix changes */
	private static class Watching extends PrefixTarget {
		int changes;

		@Override
		public void setPropertyPrefix(String propertyPrefix) {
			changes++;
			super.setPropertyPrefix(propertyPrefix);
		}
	}

	@Test
	public void theSettersArePublic() {
		// called from outside the class and its package
		PrefixTarget t = new PrefixTarget();
		t.setPropertyPrefix("parley.test.one");
		assertEquals("parley.test.one", t.getPropertyPrefix());
		t.setPropertyPrefixes("parley.test.two", "parley.test.one");
		assertEquals("parley.test.two", t.getPropertyPrefix());
	}

	@Test
	public void aNewFirstPrefixIsSetThroughSetPropertyPrefix() {
		Watching t = new Watching();
		t.setPropertyPrefixes("parley.test.ftps", "parley.test.ftp");
		assertEquals(1, t.changes);
		assertEquals("parley.test.ftps", t.getPropertyPrefix());
	}

	@Test
	public void theSameFirstPrefixIsNotSetAgain() {
		Watching t = new Watching();
		t.setPropertyPrefix("parley.test.ftps");
		t.setPropertyPrefixes("parley.test.ftps", "parley.test.ftp");
		assertEquals(1, t.changes, "only the first call");
	}

	@Test
	public void aNullPrefixGoesBackToTheClassName() {
		PrefixTarget t = new PrefixTarget();
		t.setPropertyPrefixes("parley.test.two", "parley.test.one");
		t.setPropertyPrefix(null);
		assertEquals(PrefixTarget.class.getName(), t.getPropertyPrefix());
		assertEquals(null, t.getInheritedPropertyName("nothing.here"));
	}
}
