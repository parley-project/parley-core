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
 * The file-writing cases of {@link LoggerBenchmark} again, with each logger collecting entries and
 * writing them together (batched) instead of writing every entry as soon as it is logged. How each
 * logger does that is described in {@link LoggerFixture}. The level checks and disabled debug calls
 * don't write anything, so they aren't repeated here.
 * <p>
 * It runs with LoggerBenchmark (mvn -Pbenchmark test), and {@link LoggerBenchmarkRunner} prints how
 * much faster batching is for each logger.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class BatchedLoggerBenchmark {

	@Param({LoggerFixture.PARLEY, LoggerFixture.JUL, LoggerFixture.LOG4J, LoggerFixture.SYSTEM})
	public String loggerType;

	LoggerFixture fixture;
	ILogger logger;
	Exception error;

	@Setup(Level.Trial)
	public void setup() throws IOException {
		fixture = new LoggerFixture(loggerType, true);
		logger = fixture.logger;
		error = new IOException("Benchmark exception (not a real error)");
	}

	@TearDown(Level.Trial)
	public void tearDown() throws IOException {
		fixture.close();
	}

	@Benchmark
	public void infoEnabled(LoggerBenchmark.Counter c) {
		logger.info("value=" + c.n++);
	}

	@Benchmark
	@Threads(4)
	public void infoEnabled4Threads(LoggerBenchmark.Counter c) {
		logger.info("value=" + c.n++);
	}

	@Benchmark
	public void warnWithThrowable() {
		logger.warn("Something failed", error);
	}
}
