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
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import us.bringardner.parley.core.ILogger;
import us.bringardner.parley.core.JulLogger;
import us.bringardner.parley.core.Log4JLogger;
import us.bringardner.parley.core.ParleyLogger;
import us.bringardner.parley.core.SystemLogger;

/**
 * Compares the performance of the four ILogger implementations in parley-core:
 * ParleyLogger, JulLogger, Log4JLogger and SystemLogger.
 * <p>
 * It is a JMH benchmark, not a unit test: surefire doesn't run it. Run it with
 * <pre>
 *   mvn -Pbenchmark test
 * </pre>
 * (it builds in target/benchmark, apart from Eclipse's and the normal build's classes)
 * and pass JMH options with -Djmh.args, for example a quick run of only the disabled-debug cases:
 * <pre>
 *   mvn -Pbenchmark test -Djmh.args="LoggerBenchmark.debug -wi 2 -i 3 -w 1s -r 1s"
 * </pre>
 * The results are printed at the end and written to target/benchmark/logger-benchmark.json
 * (which <a href="https://jmh.morethan.io">jmh.morethan.io</a> can chart).
 * After JMH's own table, {@link LoggerBenchmarkRunner} prints how the loggers compare (the fastest
 * for each benchmark is 1.00, the others multiples of its time), also saved to target/benchmark/relative.txt.
 * <p>
 * Each logger is set up the same way, in its own JVM (fork): level INFO, writing to a file in a
 * temporary folder with a similar one-line layout, flushed after every entry, rotated at 50 MB with
 * one old file kept (so a long run doesn't fill the disk). SystemLogger has no file settings of its
 * own; with nothing else installed behind System.Logger it goes to java.util.logging, which is set up
 * as it is for JulLogger. The folder is removed at the end.
 * <p>
 * What is measured (average time per call):
 * <ol>
 * <li>isDebugEnabled: the level check alone.</li>
 * <li>debugDisabled: debug(String) with a constant message, when debug is off.</li>
 * <li>debugDisabledConcat: debug("value="+n) when debug is off; the caller pays for building the message.</li>
 * <li>debugDisabledSupplier: debug(() -&gt; "value="+n) when debug is off; the message is never built.</li>
 * <li>infoEnabled: info("value="+n), written to the file.</li>
 * <li>infoEnabled4Threads: the same from four threads at once, sharing one logger and one file.</li>
 * <li>warnWithThrowable: warn(msg, exception), written to the file with its stack trace.</li>
 * </ol>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class LoggerBenchmark {

	/** The name every logger is created with. */
	static final String LOGGER_NAME = LoggerBenchmark.class.getName();

	/** Each log file is rotated when it reaches this size (bytes), keeping one old file. */
	static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

	/** The layout of an entry: like ParleyLogger's (time, thread where available, level, logger name, message). */
	static final String LOG4J_PATTERN = "%d{MM-dd-yyyy HH:mm:ss.SSS} [%t] %p %c - %m%n";
	static final String JUL_FORMAT = "%1$tm-%1$td-%1$tY %1$tH:%1$tM:%1$tS.%1$tL %4$s %3$s - %5$s%6$s%n";

	@Param({"ParleyLogger", "JulLogger", "Log4JLogger", "SystemLogger"})
	public String loggerType;

	ILogger logger;
	Path dir;
	Exception error;
	private java.util.logging.Handler julHandler;

	/** A counter per thread, so the messages differ without the threads sharing a variable. */
	@State(Scope.Thread)
	public static class Counter {
		int n;
	}

	@Setup(Level.Trial)
	public void setup() throws IOException {
		dir = Files.createTempDirectory("parley-logger-benchmark");
		Path file = dir.resolve(loggerType + ".log");
		error = new IOException("Benchmark exception (not a real error)");

		switch (loggerType) {
		case "ParleyLogger":
			System.setProperty(ParleyLogger.PROPERTY_LOG_FILE, file.toString());
			System.setProperty(ParleyLogger.PROPERTY_LOG_FILE_MAX_SIZE, Long.toString(MAX_FILE_SIZE));
			System.setProperty(ParleyLogger.PROPERTY_LOG_FILE_COUNT, "1");
			logger = new ParleyLogger();
			break;
		case "JulLogger":
			logger = new JulLogger();
			break;
		case "Log4JLogger":
			System.setProperty("log4j2.configurationFile", writeLog4jConfig(file).toString());
			logger = new Log4JLogger();
			break;
		case "SystemLogger":
			logger = new SystemLogger();
			break;
		default:
			throw new IllegalArgumentException("Unknown logger type " + loggerType);
		}

		logger.init(LOGGER_NAME);

		if( logger instanceof JulLogger || logger instanceof SystemLogger ) {
			//  After init: JulLogger reads its JulLogging.properties there, which adds a console handler
			useJulFile(file);
		}

		logger.setLevel(ILogger.Level.INFO);

		if( logger.isDebugEnabled() || !logger.isInfoEnabled() ) {
			throw new IllegalStateException(loggerType + " isn't at level INFO (it's " + logger.getLevel() + ")");
		}
	}

	/** Send every java.util.logging message to the file (and nowhere else). */
	private void useJulFile(Path file) throws IOException {
		System.setProperty("java.util.logging.SimpleFormatter.format", JUL_FORMAT);
		java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
		for (java.util.logging.Handler h : root.getHandlers()) {
			root.removeHandler(h);
			h.close();
		}
		//  count 2 is the current file and one old one; FileHandler flushes after every entry
		julHandler = new java.util.logging.FileHandler(file.toString(), (int) MAX_FILE_SIZE, 2, true);
		julHandler.setFormatter(new java.util.logging.SimpleFormatter());
		julHandler.setLevel(java.util.logging.Level.ALL);
		root.addHandler(julHandler);
		root.setLevel(java.util.logging.Level.INFO);
	}

	private Path writeLog4jConfig(Path file) throws IOException {
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<Configuration status=\"WARN\">\n"
				+ "  <Appenders>\n"
				+ "    <RollingFile name=\"file\" fileName=\"" + file + "\" filePattern=\"" + file + ".%i\""
				+ " immediateFlush=\"true\">\n"
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

	@TearDown(Level.Trial)
	public void tearDown() throws IOException {
		switch (loggerType) {
		case "ParleyLogger":
			ParleyLogger.closeLogFiles();
			break;
		case "Log4JLogger":
			org.apache.logging.log4j.LogManager.shutdown();
			break;
		default:
			if( julHandler != null ) {
				julHandler.close();
			}
		}
		try (Stream<Path> paths = Files.walk(dir)) {
			paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
	}

	@Benchmark
	public boolean isDebugEnabled() {
		return logger.isDebugEnabled();
	}

	@Benchmark
	public void debugDisabled() {
		logger.debug("A debug message that is never written");
	}

	@Benchmark
	public void debugDisabledConcat(Counter c) {
		logger.debug("value=" + c.n++);
	}

	@Benchmark
	public void debugDisabledSupplier(Counter c) {
		int n = c.n++;
		logger.debug(() -> "value=" + n);
	}

	@Benchmark
	public void infoEnabled(Counter c) {
		logger.info("value=" + c.n++);
	}

	@Benchmark
	@Threads(4)
	public void infoEnabled4Threads(Counter c) {
		logger.info("value=" + c.n++);
	}

	@Benchmark
	public void warnWithThrowable() {
		logger.warn("Something failed", error);
	}
}
