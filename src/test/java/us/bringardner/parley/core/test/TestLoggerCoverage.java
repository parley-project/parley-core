package us.bringardner.parley.core.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.core.ParleyLogger;
import us.bringardner.parley.core.ILogger.Level;
import us.bringardner.parley.core.JulLogger;
import us.bringardner.parley.core.Log4JLogger;

/**
 * Covers the logger code paths the original tests did not reach: the ILogger Supplier
 * defaults, ParleyLogger configuration (System.out / System.err / log file / per name level),
 * JulLogger lazy init and configuration, and the Log4JLogger fallback to ParleyLogger.
 */
public class TestLoggerCoverage {

	private static final String LOGGER_PREFIX = ParleyLogger.class.getName()+".";

	@Test
	public void testSupplierDefaults() {
		RecordingLogger rec = new RecordingLogger();
		rec.setLevel(Level.DEBUG);
		rec.debug(() -> "d");
		rec.info(() -> "i");
		rec.warn(() -> "w");
		rec.error(() -> "e");
		assertEquals(Arrays.asList("DEBUG d", "INFO i", "WARN w", "ERROR e"), rec.messages);

		rec.messages.clear();
		rec.setLevel(Level.NONE);
		AtomicBoolean called = new AtomicBoolean();
		rec.debug(() -> { called.set(true); return "d"; });
		rec.info(() -> { called.set(true); return "i"; });
		rec.warn(() -> { called.set(true); return "w"; });
		rec.error(() -> { called.set(true); return "e"; });
		assertFalse(called.get(), "Suppliers must not be called when the level is disabled");
		assertTrue(rec.messages.isEmpty());
	}

	@Test
	public void testParseLevelAliases() {
		for(String name : new String[] {"TRACE","ALL","FINE","FINER","FINEST"}) {
			assertEquals(Level.DEBUG, ParleyLogger.parseLevel(name, Level.ERROR), name);
		}
		assertEquals(Level.ERROR, ParleyLogger.parseLevel("severe", Level.NONE));
		assertEquals(Level.INFO, ParleyLogger.parseLevel("   ", Level.INFO));
	}

	@Test
	public void testParleyLoggerNullLevelAndErrStream() {
		ParleyLogger logger = new ParleyLogger();
		logger.init(null);
		logger.setLevel(null);
		assertEquals(Level.NONE, logger.getLevel(), "A null level turns logging off");
		assertFalse(logger.isErrorEnabled());

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		logger.init("test.parley.streams");
		logger.setLevel(Level.DEBUG);
		logger.setOut(new PrintStream(out, true));
		logger.setErr(new PrintStream(err, true));

		logger.info("to out");
		logger.debug("error is null", null);
		logger.error("to err", new IOException("err exception"));

		String o = out.toString();
		String e = err.toString();
		assertTrue(o.contains("INFO test.parley.streams - to out"), o);
		assertTrue(e.contains("DEBUG test.parley.streams - error is null"), "Messages with a (null) Throwable go to err");
		assertTrue(e.contains("java.io.IOException: err exception"));
		assertFalse(o.contains("to err"));
	}

	@Test
	public void testParleyLoggerLevelByName() {
		System.setProperty("test.parley.named.LogLevel", "debug");
		try {
			ParleyLogger logger = new ParleyLogger();
			logger.init("test.parley.named");
			assertEquals(Level.DEBUG, logger.getLevel());

			ParleyLogger other = new ParleyLogger();
			other.init("");
			assertEquals(ParleyLogger.DEFAULT_LEVEL, other.getLevel(), "An empty name uses the default level");
		} finally {
			System.clearProperty("test.parley.named.LogLevel");
		}
	}

	@Test
	public void testParleyLoggerLogFileSystemStreams() {
		String key = LOGGER_PREFIX+ParleyLogger.PROPERTY_LOG_FILE;
		try {
			System.setProperty(key, "System.err");
			ParleyLogger toErr = new ParleyLogger();
			toErr.init("test.parley.system.err");
			assertSame(System.err, toErr.getOut());
			assertSame(System.err, toErr.getErr());

			System.setProperty(key, " System.out ");
			ParleyLogger toOut = new ParleyLogger();
			toOut.setOut(new PrintStream(new ByteArrayOutputStream()));
			toOut.init("test.parley.system.out");
			assertSame(System.out, toOut.getOut(), "System.out resets any previous stream");
			assertSame(System.out, toOut.getErr());
		} finally {
			System.clearProperty(key);
		}
	}

	@Test
	public void testParleyLoggerLogFileInNewDirectory() throws IOException {
		File dir = Files.createTempDirectory("parley-core-logs").toFile();
		File file = new File(new File(dir, "sub"), "test.log");
		String key = LOGGER_PREFIX+ParleyLogger.PROPERTY_LOG_FILE;
		System.setProperty(key, file.getPath());
		try {
			ParleyLogger logger = new ParleyLogger();
			logger.init("test.parley.newdir");
			logger.error("written to a new directory");
		} finally {
			System.clearProperty(key);
		}
		assertTrue(file.exists(), "Missing parent directories are created");
		assertTrue(new String(Files.readAllBytes(file.toPath())).contains("written to a new directory"));
	}

	@Test
	public void testParleyLoggerLogFileCanNotBeOpened() throws IOException {
		//  a directory can't be opened as a file, so the logger keeps using System.out
		File dir = Files.createTempDirectory("parley-core-not-a-file").toFile();
		String key = LOGGER_PREFIX+ParleyLogger.PROPERTY_LOG_FILE;
		System.setProperty(key, dir.getAbsolutePath());
		PrintStream err = System.err;
		System.setErr(new PrintStream(new ByteArrayOutputStream()));
		try {
			ParleyLogger logger = new ParleyLogger();
			logger.init("test.parley.bad.file");
			assertSame(System.out, logger.getOut());
		} finally {
			System.setErr(err);
			System.clearProperty(key);
			dir.delete();
		}
	}

	@Test
	public void testJulLoggerLazyInitAndNullLevel() {
		JulLogger logger = new JulLogger();
		//  getLogger() before init uses the class name
		Logger jul = logger.getLogger();
		assertNotNull(jul);
		assertEquals(JulLogger.class.getName(), jul.getName());

		logger.setLevel(null);
		assertEquals(java.util.logging.Level.OFF, jul.getLevel());
		assertEquals(Level.NONE, logger.getLevel());

		JulLogger empty = new JulLogger();
		empty.init("");
		assertEquals(JulLogger.class.getName(), empty.getLogger().getName());
	}

	@Test
	public void testJulLoggerConfiguration() throws Exception {
		//  JulLogger configures java.util.logging once per JVM. Reset that flag so the
		//  "config file does not exist, use the class path resource" branch can run.
		Field field = JulLogger.class.getDeclaredField("configured");
		field.setAccessible(true);
		AtomicBoolean configured = (AtomicBoolean) field.get(null);
		boolean was = configured.get();
		String key = "java.util.logging.config.file";
		String original = System.getProperty(key);
		try {
			configured.set(false);
			System.setProperty(key, new File("no-such-dir/NoSuchLogging.properties").getAbsolutePath());
			JulLogger logger = new JulLogger();
			logger.init("test.jul.configuration");
			assertTrue(configured.get());
			assertNotNull(logger.getLevel());
		} finally {
			configured.set(was);
			if( original == null ) {
				System.clearProperty(key);
			} else {
				System.setProperty(key, original);
			}
		}
	}

	/** Force a Log4JLogger to use its ParleyLogger fallback (what happens when log4j is not on the class path). */
	private static ParleyLogger useFallback(Log4JLogger logger, PrintStream out) throws Exception {
		ParleyLogger fallback = new ParleyLogger();
		fallback.init("test.log4j.fallback");
		fallback.setOut(out);
		Field field = Log4JLogger.class.getDeclaredField("fallback");
		field.setAccessible(true);
		field.set(logger, fallback);
		return fallback;
	}

	@Test
	public void testLog4JLoggerFallback() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		Log4JLogger logger = new Log4JLogger();
		ParleyLogger fallback = useFallback(logger, new PrintStream(bytes, true));

		logger.init("test.log4j.fallback.renamed");
		logger.setLevel(Level.DEBUG);
		assertEquals(Level.DEBUG, logger.getLevel());
		assertEquals(Level.DEBUG, fallback.getLevel(), "The level is set on the fallback logger");
		assertTrue(logger.isDebugEnabled());
		assertTrue(logger.isInfoEnabled());
		assertTrue(logger.isWarnEnabled());
		assertTrue(logger.isErrorEnabled());

		IOException err = new IOException("fallback exception");
		logger.debug("d1");
		logger.debug("d2", err);
		logger.info("i1");
		logger.info("i2", err);
		logger.warn("w1");
		logger.warn("w2", err);
		logger.error("e1");
		logger.error("e2", err);

		String text = bytes.toString();
		for(String expect : new String[] {
				"DEBUG test.log4j.fallback.renamed - d1", "DEBUG test.log4j.fallback.renamed - d2",
				"INFO test.log4j.fallback.renamed - i1", "INFO test.log4j.fallback.renamed - i2",
				"WARN test.log4j.fallback.renamed - w1", "WARN test.log4j.fallback.renamed - w2",
				"ERROR test.log4j.fallback.renamed - e1", "ERROR test.log4j.fallback.renamed - e2",
				"java.io.IOException: fallback exception"}) {
			assertTrue(text.contains(expect), "Missing '"+expect+"' in "+text);
		}
	}

	@Test
	public void testLog4JLoggerLazyInit() {
		Assumptions.assumeTrue(Log4JLogger.isLog4jAvailable(), "log4j is not on the class path");
		Log4JLogger logger = new Log4JLogger();
		//  no init() - the logger is created on first use with the default name
		logger.setLevel(Level.WARN);
		assertEquals(Level.WARN, logger.getLevel());
		assertTrue(logger.isErrorEnabled());
		assertFalse(logger.isInfoEnabled());
		logger.setLevel(null);
		assertEquals(Level.NONE, logger.getLevel(), "A null level turns logging off");
		logger.setLevel(Level.ERROR);
	}
}
