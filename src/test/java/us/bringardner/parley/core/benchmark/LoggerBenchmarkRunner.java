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
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.CommandLineOptions;

/**
 * Runs the benchmarks (taking the same options as JMH's own Main) and then prints how the loggers
 * compare: for each benchmark the fastest logger is 1.00 and the others are shown as multiples of
 * its time (2.50 = takes two and a half times as long). Lower is better.
 * <p>
 * The comparison is also written to relative.txt in the build folder (target/benchmark), given by
 * the system property benchmark.dir.
 */
public class LoggerBenchmarkRunner {

	public static void main(String[] args) throws Exception {
		CommandLineOptions options = new CommandLineOptions(args);
		if( options.shouldHelp() || options.shouldList() || options.shouldListWithParams()
				|| options.shouldListProfilers() || options.shouldListResultFormats() ) {
			//  Not a run: let JMH answer
			org.openjdk.jmh.Main.main(args);
			return;
		}

		Collection<RunResult> results = new Runner(options).run();

		String table = relativeTable(results);
		System.out.println();
		System.out.print(table);

		String dir = System.getProperty("benchmark.dir");
		if( dir != null ) {
			Path file = Paths.get(dir, "relative.txt");
			writeFile(file, table);
			System.out.println("Comparison saved to " + file);
		}
	}

	/**
	 * @return a table with a row for each benchmark and a column for each logger, each logger's
	 *         time divided by the fastest logger's time for that benchmark
	 */
	static String relativeTable(Collection<RunResult> results) {
		//  benchmark method -> logger -> score (ns/op)
		Map<String, Map<String, Double>> scores = new TreeMap<>();
		List<String> loggers = new ArrayList<>();

		for (RunResult r : results) {
			String benchmark = r.getParams().getBenchmark();
			benchmark = benchmark.substring(benchmark.lastIndexOf('.') + 1);
			String logger = r.getParams().getParam("loggerType");
			if( !loggers.contains(logger) ) {
				loggers.add(logger);
			}
			scores.computeIfAbsent(benchmark, k -> new LinkedHashMap<>()).put(logger, r.getPrimaryResult().getScore());
		}

		int nameWidth = "Benchmark".length();
		for (String b : scores.keySet()) {
			nameWidth = Math.max(nameWidth, b.length());
		}
		int colWidth = 8;
		for (String l : loggers) {
			colWidth = Math.max(colWidth, l.length());
		}

		StringBuilder out = new StringBuilder();
		out.append("Relative time (1.00 = the fastest logger for that benchmark; lower is better)\n\n");
		out.append(pad("Benchmark", nameWidth, false));
		for (String l : loggers) {
			out.append("  ").append(pad(l, colWidth, true));
		}
		out.append('\n');

		for (Map.Entry<String, Map<String, Double>> row : scores.entrySet()) {
			double fastest = Double.MAX_VALUE;
			for (double score : row.getValue().values()) {
				fastest = Math.min(fastest, score);
			}
			out.append(pad(row.getKey(), nameWidth, false));
			for (String l : loggers) {
				Double score = row.getValue().get(l);
				String cell = score == null ? "-" : String.format(Locale.ROOT, "%.2f", score / fastest);
				out.append("  ").append(pad(cell, colWidth, true));
			}
			out.append('\n');
		}
		return out.toString();
	}

	private static String pad(String s, int width, boolean right) {
		StringBuilder b = new StringBuilder();
		if( !right ) {
			b.append(s);
		}
		for (int i = s.length(); i < width; i++) {
			b.append(' ');
		}
		if( right ) {
			b.append(s);
		}
		return b.toString();
	}

	private static void writeFile(Path file, String text) throws IOException {
		Files.createDirectories(file.getParent());
		try (PrintStream ps = new PrintStream(Files.newOutputStream(file), true, StandardCharsets.UTF_8.name())) {
			ps.print(text);
		}
	}
}
