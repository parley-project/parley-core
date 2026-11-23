package us.bringardner.parley.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The parley.include setting of a properties file: where it is read, what comes of it and what is an error.
 * The files are in memory, so there is nothing to close or clean up.
 */
public class TestPropertiesReader {

	private static final String VAR = "parley.test.include.dir";

	private final Map<String, String> files = new HashMap<>();
	private final List<String> asked = new ArrayList<>();

	/** Files by name, a name that starts with a slash is as it is, any other is next to the file that names it */
	private final IncludeResolver resolver = (from, name) -> {
		String n = name.startsWith("/") ? name : from.substring(0, from.lastIndexOf('/') + 1) + name;
		asked.add(from + " -> " + n);
		String text = files.get(n);
		return text == null ? null : new IncludeResolver.Found(n, new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1)));
	};

	@AfterEach
	public void clear() {
		System.clearProperty(VAR);
	}

	private Properties read(String main) throws IOException {
		return PropertiesReader.read(new ByteArrayInputStream(main.getBytes(StandardCharsets.ISO_8859_1)), "/d/Main.properties", resolver);
	}

	@Test
	public void aFileWithoutTheSettingIsReadAsProperiesLoadReadsIt() throws IOException {
		String text = "a=1\nb = two words\n# comment\nc:3\nd\\ e=x\\\n  y\n";
		Properties expected = new Properties();
		expected.load(new java.io.StringReader(text));
		assertEquals(expected, read(text));
		assertTrue(asked.isEmpty());
	}

	@Test
	public void theIncludeIsReadWhereItStands() throws IOException {
		files.put("/d/more.properties", "a=included\nc=included\nd=included\n");
		Properties p = read("a=before\nb=before\nparley.include=more.properties\nc=after\n");
		assertEquals("included", p.getProperty("a"), "what came before is overridden by the included file");
		assertEquals("before", p.getProperty("b"));
		assertEquals("after", p.getProperty("c"), "what comes after overrides the included file");
		assertEquals("included", p.getProperty("d"));
	}

	@Test
	public void theSettingItselfIsNotKept() throws IOException {
		files.put("/d/more.properties", "x=1\n");
		Properties p = read("parley.include=more.properties\n");
		assertFalse(p.containsKey(PropertiesReader.INCLUDE));
		assertEquals(1, p.size());
	}

	@Test
	public void severalSettingsAreReadEachWhereItStands() throws IOException {
		files.put("/d/one.properties", "a=one\nb=one\n");
		files.put("/d/two.properties", "a=two\n");
		Properties p = read("parley.include=one.properties\nb=main\nparley.include=two.properties\n");
		assertEquals("two", p.getProperty("a"));
		assertEquals("main", p.getProperty("b"));
	}

	@Test
	public void anIncludeOfAnIncludeIsFoundNextToTheFileThatNamesIt() throws IOException {
		files.put("/d/sub/one.properties", "parley.include=two.properties\nfrom=one\n");
		files.put("/d/sub/two.properties", "from=two\nonly=two\n");
		Properties p = read("parley.include=sub/one.properties\n");
		assertEquals("one", p.getProperty("from"));
		assertEquals("two", p.getProperty("only"));
		assertTrue(asked.contains("/d/sub/one.properties -> /d/sub/two.properties"), asked.toString());
	}

	@Test
	public void aNameThatStartsWithASlashIsAsItIs() throws IOException {
		files.put("/etc/host.properties", "h=host\n");
		assertEquals("host", read("parley.include=/etc/host.properties\n").getProperty("h"));
	}

	@Test
	public void aVariableIsTakenFromTheSettingsReadSoFar() throws IOException {
		files.put("/d/mx2.properties", "h=mx2\n");
		assertEquals("mx2", read("machine=mx2\nparley.include=${machine}.properties\n").getProperty("h"));
	}

	@Test
	public void aVariableIsTakenFromTheSystemProperties() throws IOException {
		System.setProperty(VAR, "/etc");
		files.put("/etc/host.properties", "h=host\n");
		assertEquals("host", read("parley.include=${" + VAR + "}/host.properties\n").getProperty("h"));
	}

	@Test
	public void aSettingReadSoFarWinsOverASystemProperty() throws IOException {
		System.setProperty(VAR, "/wrong");
		files.put("/etc/host.properties", "h=host\n");
		assertEquals("host", read(VAR + "=/etc\nparley.include=${" + VAR + "}/host.properties\n").getProperty("h"));
	}

	@Test
	public void aVariableOfTheIncludingFileIsSeenByItsIncludes() throws IOException {
		files.put("/d/one.properties", "parley.include=${machine}.properties\n");
		files.put("/d/mx2.properties", "h=mx2\n");
		assertEquals("mx2", read("machine=mx2\nparley.include=one.properties\n").getProperty("h"));
	}

	@Test
	public void aVariableWithoutAValueIsAnError() {
		IllegalStateException e = assertThrows(IllegalStateException.class, () -> read("parley.include=${no.such.setting}.properties\n"));
		assertTrue(e.getMessage().contains("no.such.setting") && e.getMessage().contains("/d/Main.properties"), e.getMessage());
		e = assertThrows(IllegalStateException.class, () -> read("parley.include=${env.PARLEY_NO_SUCH_VARIABLE_X}\n"));
		assertTrue(e.getMessage().contains("env.PARLEY_NO_SUCH_VARIABLE_X"), e.getMessage());
	}

	@Test
	public void anUnclosedVariableIsAnError() {
		assertThrows(IllegalStateException.class, () -> read("parley.include=${abc\n"));
	}

	@Test
	public void aTextWithADollarButNoVariableIsAsItIs() throws IOException {
		files.put("/d/a$b.properties", "h=dollar\n");
		assertEquals("dollar", read("parley.include=a$b.properties\n").getProperty("h"));
	}

	@Test
	public void aFileThatIsNotThereIsAnErrorThatNamesBothFiles() {
		IllegalStateException e = assertThrows(IllegalStateException.class, () -> read("parley.include=host.properties\n"));
		assertTrue(e.getMessage().contains("host.properties") && e.getMessage().contains("/d/Main.properties"), e.getMessage());
	}

	@Test
	public void anEmptyNameIsAnError() {
		assertThrows(IllegalStateException.class, () -> read("parley.include=\n"));
	}

	@Test
	public void aFileThatIncludesItselfIsAnError() {
		files.put("/d/Main.properties", "parley.include=Main.properties\n");
		IllegalStateException e = assertThrows(IllegalStateException.class, () -> read("parley.include=Main.properties\n"));
		assertTrue(e.getMessage().contains("comes back"), e.getMessage());
	}

	@Test
	public void twoFilesThatIncludeEachOtherAreAnError() {
		files.put("/d/a.properties", "parley.include=b.properties\n");
		files.put("/d/b.properties", "parley.include=a.properties\n");
		IllegalStateException e = assertThrows(IllegalStateException.class, () -> read("parley.include=a.properties\n"));
		assertTrue(e.getMessage().contains("comes back"), e.getMessage());
	}

	@Test
	public void theSameFileIncludedTwiceIsNotALoop() throws IOException {
		files.put("/d/one.properties", "a=1\n");
		assertEquals("1", read("parley.include=one.properties\nparley.include=one.properties\n").getProperty("a"));
	}

	@Test
	public void aChainThatIsTooDeepIsAnError() {
		for(int i = 0; i < 40; i++) {
			files.put("/d/f" + i + ".properties", "parley.include=f" + (i + 1) + ".properties\n");
		}
		IllegalStateException e = assertThrows(IllegalStateException.class, () -> read("parley.include=f0.properties\n"));
		assertTrue(e.getMessage().contains("too deep"), e.getMessage());
	}

	@Test
	public void aResolverThatCantReadTheFileIsAnError() {
		IncludeResolver bad = (from, name) -> {
			throw new IOException("disk gone");
		};
		IllegalStateException e = assertThrows(IllegalStateException.class, () -> PropertiesReader
				.read(new ByteArrayInputStream("parley.include=x\n".getBytes(StandardCharsets.ISO_8859_1)), "/d/Main.properties", bad));
		assertTrue(e.getMessage().contains("disk gone"), e.getMessage());
	}

	@Test
	public void propertiesLoadCallsPutInTheOrderOfTheFile() {
		// the reader depends on this: an include is read where its setting stands
		List<String> keys = new ArrayList<>();
		Properties p = new Properties() {
			private static final long serialVersionUID = 1L;

			@Override
			public synchronized Object put(Object key, Object value) {
				keys.add((String) key);
				return super.put(key, value);
			}
		};
		try {
			p.load(new java.io.StringReader("z=1\na=2\nm=3\nb=4\nz=5\n"));
		} catch(IOException e) {
			throw new AssertionError(e);
		}
		assertEquals(List.of("z", "a", "m", "b", "z"), keys);
		assertNull(p.getProperty("none"));
	}
}
