package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.BaseObject;

/**
 * Every example in the "Properties" section of the README, so the documentation can't drift from the code.
 * ExampleMailer is the README's com.example.Mailer, ExampleBulkMailer is BulkMailer, ExampleDatabase is Database.
 */
public class TestReadmePropertyExamples {

	private static final String MAILER = ExampleMailer.class.getName();
	private static final String BULK = ExampleBulkMailer.class.getName();

	@BeforeEach
	@AfterEach
	public void clean() {
		for(String key : new String[] {"SmtpHost", MAILER+".SmtpHost", BULK+".SmtpHost", "primary.Url", "Retries"}) {
			System.clearProperty(key);
		}
		BaseObject.clearPropertyCache();
	}

	@Test
	public void mailerUsesItsOwnFile() {
		assertEquals("mail.example.com", new ExampleMailer().getProperty("SmtpHost"));
	}

	@Test
	public void subClassFileWinsOverSuperClassFile() {
		assertEquals("bulk.example.com", new ExampleBulkMailer().getProperty("SmtpHost"));
	}

	@Test
	public void subClassFallsBackToSuperClassFile() {
		assertEquals(587, new ExampleBulkMailer().getIntProperty("SmtpPort", 25));
		assertTrue(new ExampleBulkMailer().getBooleanProperty("UseTls", false));
	}

	@Test
	public void defaultIsUsedWhenNotDefined() {
		assertEquals("3", new ExampleMailer().getProperty("Retries", "3"));
		assertNull(new ExampleMailer().getProperty("Retries"));
		assertEquals(25, new ExampleMailer().getIntProperty("NoSuchPort", 25));
	}

	@Test
	public void systemPropertyBeatsTheFile() {
		System.setProperty("SmtpHost", "relay");
		assertEquals("relay", new ExampleMailer().getProperty("SmtpHost"));
		assertEquals("relay", new ExampleBulkMailer().getProperty("SmtpHost"), "for every object that asks for the name");
	}

	@Test
	public void nameIsLookedUpAsGiven() {
		System.setProperty(MAILER+".SmtpHost", "relay");
		assertEquals("mail.example.com", new ExampleMailer().getProperty("SmtpHost"), "a prefixed name is another name");
		assertEquals("relay", new ExampleMailer().getProperty(MAILER+".SmtpHost"));
	}

	@Test
	public void prefixPerInstance() {
		assertEquals("jdbc:reports", new ExampleDatabase("reports").url());
		assertNull(new ExampleDatabase("other").url());
		System.setProperty("primary.Url", "jdbc:primary");
		assertEquals("jdbc:primary", new ExampleDatabase("primary").url());
	}
}

class ExampleMailer extends TestCoreBase {
}

class ExampleBulkMailer extends ExampleMailer {
}

class ExampleDatabase extends TestCoreBase {

	ExampleDatabase(String name) {
		setPropertyPrefix(name);
	}

	String url() {
		return getProperty(propertyName("Url"));
	}
}
