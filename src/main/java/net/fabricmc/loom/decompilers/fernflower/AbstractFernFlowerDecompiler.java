/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.decompilers.fernflower;

import static java.text.MessageFormat.format;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.function.Supplier;

import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.process.ExecResult;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import net.fabricmc.loom.api.decompilers.DecompilationMetadata;
import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.util.ConsumingOutputStream;
import net.fabricmc.loom.util.OperatingSystem;

public abstract class AbstractFernFlowerDecompiler implements LoomDecompiler {
	private final Project project;

	protected AbstractFernFlowerDecompiler(Project project) {
		this.project = project;
	}

	public abstract Class<? extends AbstractForkedFFExecutor> fernFlowerExecutor();

	@Override
	public void decompile(Path compiledJar, Path sourcesDestination, Path linemapDestination, DecompilationMetadata metaData) {
		if (!OperatingSystem.is64Bit()) {
			throw new UnsupportedOperationException("FernFlower decompiler requires a 64bit JVM to run due to the memory requirements");
		}

		project.getLogging().captureStandardOutput(LogLevel.LIFECYCLE);

		Map<String, Object> options = new HashMap<>() {{
				put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
				put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1");
				put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
				put(IFernflowerPreferences.LOG_LEVEL, "trace");
				put(IFernflowerPreferences.THREADS, metaData.numberOfThreads());
				put(IFernflowerPreferences.INDENT_STRING, "\t");
			}};

		List<String> args = new ArrayList<>();

		options.forEach((k, v) -> args.add(format("-{0}={1}", k, v)));
		args.add(absolutePathOf(compiledJar));
		args.add("-o=" + absolutePathOf(sourcesDestination));
		args.add("-l=" + absolutePathOf(linemapDestination));
		args.add("-m=" + absolutePathOf(metaData.javaDocs()));

		// TODO, Decompiler breaks on jemalloc, J9 module-info.class?
		for (Path library : metaData.libraries()) {
			args.add("-e=" + absolutePathOf(library));
		}

		ServiceRegistry registry = ((ProjectInternal) project).getServices();
		ProgressLoggerFactory factory = registry.get(ProgressLoggerFactory.class);
		ProgressLogger progressGroup = factory.newOperation(getClass()).setDescription("Decompile");
		Supplier<ProgressLogger> loggerFactory = () -> {
			ProgressLogger pl = factory.newOperation(getClass(), progressGroup);
			pl.setDescription("decompile worker");
			pl.started();
			return pl;
		};
		Stack<ProgressLogger> freeLoggers = new Stack<>();
		Map<String, ProgressLogger> inUseLoggers = new HashMap<>();

		progressGroup.started();
		ExecResult result = ForkingJavaExec.javaexec(
				project,
				spec -> {
					spec.getMainClass().set(fernFlowerExecutor().getName());
					spec.jvmArgs("-Xms200m", "-Xmx3G");
					spec.setArgs(args);
					spec.setErrorOutput(new ConsumingOutputStream(line -> {
						if (line.startsWith("Inconsistent inner class entries")) {
							// Suppress this
							return;
						}

						System.err.println(line);
					}));
					spec.setStandardOutput(new ConsumingOutputStream(line -> {
						if (line.startsWith("Listening for transport") || !line.contains("::")) {
							System.out.println(line);
							return;
						}

						int sepIdx = line.indexOf("::");
						String id = line.substring(0, sepIdx).trim();
						String data = line.substring(sepIdx + 2).trim();

						ProgressLogger logger = inUseLoggers.get(id);

						String[] segs = data.split(" ");

						if (segs[0].equals("waiting")) {
							if (logger != null) {
								logger.progress("Idle..");
								inUseLoggers.remove(id);
								freeLoggers.push(logger);
							}
						} else {
							if (logger == null) {
								if (!freeLoggers.isEmpty()) {
									logger = freeLoggers.pop();
								} else {
									logger = loggerFactory.get();
								}

								inUseLoggers.put(id, logger);
							}

							logger.progress(data);
						}
					}));
				});
		inUseLoggers.values().forEach(ProgressLogger::completed);
		freeLoggers.forEach(ProgressLogger::completed);
		progressGroup.completed();

		result.rethrowFailure();
		result.assertNormalExitValue();
	}

	private static String absolutePathOf(Path path) {
		return path.toAbsolutePath().toString();
	}
}
