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

package net.fabricmc.loom.task.fernflower;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;

/**
 * Entry point for Forked FernFlower task.
 * Takes one parameter, a single file, each line is treated as command line input.
 * Forces one input file.
 * Forces one output file using '-o=/path/to/output'
 * Created by covers1624 on 11/02/19.
 */
public class ForkedFFExecutor {
	public static void main(String[] args) throws IOException {
		Map<String, Object> options = new HashMap<>();
		File input = null;
		File output = null;
		File lineMap = null;
		File mappings = null;
		File mixins = null;
		List<File> libraries = new ArrayList<>();
		int numThreads = 0;

		boolean isOption = true;

		for (String arg : args) {
			if (isOption && arg.length() > 5 && arg.charAt(0) == '-' && arg.charAt(4) == '=') {
				String value = arg.substring(5);

				if ("true".equalsIgnoreCase(value)) {
					value = "1";
				} else if ("false".equalsIgnoreCase(value)) {
					value = "0";
				}

				options.put(arg.substring(1, 4), value);
			} else {
				isOption = false;

				if (arg.startsWith("-e=")) {
					libraries.add(new File(arg.substring(3)));
				} else if (arg.startsWith("-o=")) {
					if (output != null) {
						throw new RuntimeException("Unable to set more than one output.");
					}

					output = new File(arg.substring(3));
				} else if (arg.startsWith("-l=")) {
					if (lineMap != null) {
						throw new RuntimeException("Unable to set more than one lineMap file.");
					}

					lineMap = new File(arg.substring(3));
				} else if (arg.startsWith("-m=")) {
					if (mappings != null) {
						throw new RuntimeException("Unable to use more than one mappings file.");
					}

					mappings = new File(arg.substring(3));
				} else if (arg.startsWith("-j=")) {
					if (mixins != null) {
						throw new RuntimeException("Unable to use more than one mixin file.");
					}

					mixins = new File(arg.substring(3));
				} else if (arg.startsWith("-t=")) {
					numThreads = Integer.parseInt(arg.substring(3));
				} else {
					if (input != null) {
						throw new RuntimeException("Unable to set more than one input.");
					}

					input = new File(arg);
				}
			}
		}

		Objects.requireNonNull(input, "Input not set.");
		Objects.requireNonNull(output, "Output not set.");
		Objects.requireNonNull(mappings, "Mappings not set.");

		options.put(IFabricJavadocProvider.PROPERTY_NAME, new TinyJavadocProvider(mappings, mixins));
		runFF(options, libraries, input, output, lineMap);
	}

	public static void runFF(Map<String, Object> options, List<File> libraries, File input, File output, File lineMap) {
		IResultSaver saver = new ThreadSafeResultSaver(() -> output, () -> lineMap);
		IFernflowerLogger logger = new ThreadIDFFLogger();
		Fernflower ff = new Fernflower(FernFlowerUtils::getBytecode, saver, options, logger);

		for (File library : libraries) {
			ff.addLibrary(library);
		}

		ff.addSource(input);
		ff.decompileContext();
	}
}
