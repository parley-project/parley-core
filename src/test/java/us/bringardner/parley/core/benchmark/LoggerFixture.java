/**
 * Copyright 1998-2026 Tony Bringardner
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *
 *	@author Tony Bringardner
 */
package us.bringardner.parley.core.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import us.bringardner.parley.core.ILogger;
import us.bringardner.parley.core.JulLogger;
import us.bringardner.parley.core.Log4JLogger;
import us.bringardner.parley.core.ParleyLogger;
import us.bringardner.parley.core.SystemLogger;

/**
 * Creates one of the ILogger implementations for the benchmarks, set up the same way as the
 * others, and removes everything it made when it is closed.
 * <p>
 * Every logger is at level INFO and writes to a file in a new temporary folder with a similar one-line
 * layout. The file is rotated at {@link #MAX_FILE_SIZE} with one old file kept, so a long run can't fill the disk.
 * <p>
 * How entries reach the file:
 * <ol>
 * <li>Not batched: every entry is written to the file as soon as it is logged (each logger's default).</li>
 * <li>Batched: entries are collected and written together.
 *   <ol>
 *   <li>ParleyLogger: its parley.log.flushInterval setting ({@link #FLUSH_INTERVAL_MS}).</li>
 *   <li>Log4JLogger: log4j's immediateFlush="false"; log4j writes when its 8 KB buffer is full (it has no timer).</li>
 *   <li>JulLogger and SystemLogger: java.util.logging has no setting for it (its FileHandler writes every
 *       entry), so a FileHandler that leaves the writing to a timer ({@link BatchedFileHandler}) is used.</li>
 *   </ol></li>
 * </ol>
 * SystemLogger has no file settings of its own; with nothing else installed behind System.Logger it goes
 * to java.util.logging, which is set up as it is for JulLogger.
 */
final class LoggerFixture implements AutoCloseable {

	/** The logger types, the values of the benchmarks' loggerType parameter. */
	static final String PARLEY = "ParleyLogger";
	static final String JUL = "JulLogger";
	static final String LOG4J = "Log4JLogger";
	static final String SYSTEM = "SystemLogger";

	/** The name every logger is created with. */
	static final String LOGGER_NAME = LoggerBenchmark.class.getName();

	/** Each log file is rotated when it reaches this size (bytes), keeping one old file. */
	static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

	/** How often batched entries are written, for the loggers that write on a timer. */
	static final long FLUSH_INTERVAL_MS = 500;

	/** The layout of an entry: like ParleyLogger's (time, thread where available, level, logger name, message). */
	static final String LOG4J_PATTERN = "%d{MM-dd-yyyy HH:mm:ss.SSS} [%t] %p %c - %m%n";
	static final String JUL_FORMAT = "%1$tm-%1$td-%1$tY %1$tH:%1$tM:%1$tS.%1$tL %4$s %3$s - %5$s%6$s%n";

	final ILogger logger;
	private final String type;
	private final Path dir;
	private java.util.logging.Handler julHandler;
	private ScheduledExecutorService julFlusher;

	/**
	 * @param type one of {@link #PARLEY}, {@link #JUL}, {@link #LOG4J} or {@link #SYSTEM}
	 * @param batched true to collect entries and write them together, false to write each one as it is logged
	 */
	LoggerFixture(String type, boolean batched) throws IOException {
		this.type = type;
		dir = Files.createTempDirectory("parley-logger-benchmark");
		try {
			Path file = dir.resolve(type + ".log");

			switch (type) {
			case PARLEY:
				System.setProperty(ParleyLogger.PROPERTY_LOG_FILE, file.toString());
				System.setProperty(ParleyLogger.PROPERTY_LOG_FILE_MAX_SIZE, Long.toString(MAX_FILE_SIZE));
				System.setProperty(ParleyLogger.PROPERTY_LOG_FILE_COUNT, "1");
				if( batched ) {
					System.setProperty(ParleyLogger.PROPERTY_LOG_FLUSH_INTERVAL, FLUSH_INTERVAL_MS + "ms");
				}
				logger = new ParleyLogger();
				break;
			case JUL:
				logger = new JulLogger();
				break;
			case LOG4J:
				System.setProperty("log4j2.configurationFile", writeLog4jConfig(file, batched).toString());
				logger = new Log4JLogger();
				break;
			case SYSTEM:
				logger = new SystemLogger();
				break;
			default:
				throw new IllegalArgumentException("Unknown logger type " + type);
			}

			logger.init(LOGGER_NAME);

			if( logger instanceof JulLogger || logger instanceof SystemLogger ) {
				//  After init: JulLogger reads its JulLogging.properties there, which adds a console handler
				useJulFile(file, batched);
			}

			logger.setLevel(ILogger.Level.INFO);

			if( logger.isDebugEnabled() || !logger.isInfoEnabled() ) {
				throw new IllegalStateException(type + " isn't at level INFO (it's " + logger.getLevel() + ")");
			}
		} catch (IOException | RuntimeException e) {
			close();
			throw e;
		}
	}

	/** Send every java.util.logging message to the file (and nowhere else). */
	private void useJulFile(Path file, boolean batched) throws IOException {
		System.setProperty("java.util.logging.SimpleFormatter.format", JUL_FORMAT);
		java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
		for (java.util.logging.Handler h : root.getHandlers()) {
			root.removeHandler(h);
			h.close();
		}
		//  count 2 is the current file and one old one
		if( batched ) {
			BatchedFileHandler handler = new BatchedFileHandler(file.toString(), (int) MAX_FILE_SIZE, 2);
			julHandler = handler;
			julFlusher = Executors.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "benchmark-jul-flush");
				t.setDaemon(true);
				return t;
			});
			julFlusher.scheduleWithFixedDelay(handler::write, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
		} else {
			julHandler = new java.util.logging.FileHandler(file.toString(), (int) MAX_FILE_SIZE, 2, true);
		}
		julHandler.setFormatter(new java.util.logging.SimpleFormatter());
		julHandler.setLevel(java.util.logging.Level.ALL);
		root.addHandler(julHandler);
		root.setLevel(java.util.logging.Level.INFO);
	}

	private Path writeLog4jConfig(Path file, boolean batched) throws IOException {
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<Configuration status=\"WARN\">\n"
				+ "  <Appenders>\n"
				+ "    <RollingFile name=\"file\" fileName=\"" + file + "\" filePattern=\"" + file + ".%i\""
				+ " immediateFlush=\"" + !batched + "\" bufferedIO=\"true\">\n"
				+ "      <PatternLayout pattern=\"" + LOG4J_PATTERN + "\"/>\n"
				+ "      <SizeBasedTriggeringPolicy size=\"" + MAX_FILE_SIZE + "\"/>\n"
				+ "      <DefaultRolloverStrategy max=\"1\"/>\n"
				+ "    </RollingFile>\n"
				+ "  </Appenders>\n"
				+ "  <Loggers>\n"
				+ "    <Root level=\"INFO\"><AppenderRef ref=\"file\"/></Root>\n"
				+ "  </Loggers>\n"
				+ "</Configuration>\n";
		Path config = dir.resolve("log4j2-benchmark.xml");
		Files.writeString(config, xml);
		return config;
	}

	/**
	 * Write anything still waiting, close the log file and stop the timer, and remove the temporary folder.
	 */
	@Override
	public void close() throws IOException {
		try {
			switch (type) {
			case PARLEY:
				ParleyLogger.closeLogFiles();
				break;
			case LOG4J:
				org.apache.logging.log4j.LogManager.shutdown();
				break;
			default:
				if( julFlusher != null ) {
					julFlusher.shutdownNow();
					try {
						julFlusher.awaitTermination(5, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
				if( julHandler != null ) {
					java.util.logging.Logger.getLogger("").removeHandler(julHandler);
					julHandler.close();
				}
			}
		} finally {
			deleteDir();
		}
	}

	private void deleteDir() throws IOException {
		try (Stream<Path> paths = Files.walk(dir)) {
			paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
	}

	/**
	 * A java.util.logging FileHandler that collects entries and leaves writing them to the file to
	 * {@link #write()}, called by a timer. FileHandler calls flush() after every entry; here that does
	 * nothing. close() still writes everything (it doesn't go through flush()), and the file is still
	 * rotated by size, a little later than with every entry written at once.
	 */
	static final class BatchedFileHandler extends java.util.logging.FileHandler {

		BatchedFileHandler(String pattern, int limit, int count) throws IOException {
			super(pattern, limit, count, true);
		}

		/** Called by FileHandler after every entry: the entries are written by {@link #write()} instead. */
		@Override
		public void flush() {
		}

		/** Write the entries collected since the last call to the file. */
		void write() {
			super.flush();
		}
	}
}
