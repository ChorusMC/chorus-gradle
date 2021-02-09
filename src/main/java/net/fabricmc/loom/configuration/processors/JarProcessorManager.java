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

package net.fabricmc.loom.configuration.processors;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JarProcessorManager {
	private final List<JarProcessor> jarProcessors;

	public JarProcessorManager(List<JarProcessor> jarProcessors) {
		this.jarProcessors = jarProcessors;
	}

	public void setupProcessors() {
		jarProcessors.forEach(JarProcessor::setup);
	}

	public boolean active() {
		return !jarProcessors.isEmpty();
	}

	public List<JarProcessor> filter(Environment environment) {
		List<JarProcessor> processors = new ArrayList<>();

		for (JarProcessor processor : jarProcessors) {
			if (processor.getEnvironment() == environment) {
				processors.add(processor);
			}
		}

		return processors;
	}

	public boolean hasEnvironment(Environment environment) {
		return !this.filter(environment).isEmpty();
	}

	public boolean isInvalid(Environment environment, File file) {
		if (!file.exists()) {
			return true;
		}

		return this.filter(environment).stream().anyMatch(jarProcessor -> jarProcessor.isInvalid(file));
	}

	public void process(Environment environment, File file) {
		for (JarProcessor jarProcessor : this.filter(environment)) {
			jarProcessor.process(file);
		}
	}

	public <T extends JarProcessor> T getByType(Class<T> tClass) {
		//noinspection unchecked
		return (T) jarProcessors.stream().filter(jarProcessor -> jarProcessor.getClass().equals(tClass)).findFirst().orElse(null);
	}
}
