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
import java.util.concurrent.TimeUnit;

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
 * Each logger runs in its own JVM (fork), set up as {@link LoggerFixture} describes, with every
 * entry written to the file as soon as it is logged (each logger's default). {@link BatchedLoggerBenchmark}
 * measures the file-writing cases again with entries collected and written together.
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

	@Param({LoggerFixture.PARLEY, LoggerFixture.JUL, LoggerFixture.LOG4J, LoggerFixture.SYSTEM})
	public String loggerType;

	LoggerFixture fixture;
	ILogger logger;
	Exception error;

	/** A counter per thread, so the messages differ without the threads sharing a variable. */
	@State(Scope.Thread)
	public static class Counter {
		int n;
	}

	@Setup(Level.Trial)
	public void setup() throws IOException {
		fixture = new LoggerFixture(loggerType, false);
		logger = fixture.logger;
		error = new IOException("Benchmark exception (not a real error)");
	}

	@TearDown(Level.Trial)
	public void tearDown() throws IOException {
		fixture.close();
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
