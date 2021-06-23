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

package net.fabricmc.loom.configuration.enumwidener;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;

import org.gradle.api.Project;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.processors.Environment;
import net.fabricmc.loom.configuration.processors.JarProcessor;

public class EnumWidenerJarProcessor implements JarProcessor {
	private static final String HASH_FILE_NAME = "ew.hash";

	private final Project project;
	private final LoomGradleExtension loom;

	private List<String> classes;

	public EnumWidenerJarProcessor(Project project) {
		this.project = project;
		this.loom = project.getExtensions().getByType(LoomGradleExtension.class);
	}

	@Override
	public Environment getEnvironment() {
		return Environment.COMPILE;
	}

	@Override
	public void setup() {
		this.classes = this.loom.enumWidener;
	}

	@Override
	public void process(File file) {
		if (!this.classes.isEmpty()) {
			ZipUtil.transformEntries(file, this.classes.stream()
					.map(klass -> new ZipEntryTransformerEntry(klass.replace('.', '/') + ".class", new EnumWidenerTransformerEntry(this.project, klass)))
					.toArray(ZipEntryTransformerEntry[]::new)
			);

			ZipUtil.addEntry(file, HASH_FILE_NAME, ByteBuffer.allocate(4).putInt(this.classes.hashCode()).array());
		}
	}

	@Override
	public boolean isInvalid(File file) {
		byte[] hash = ZipUtil.unpackEntry(file, HASH_FILE_NAME);

		if (hash == null) {
			return !this.classes.isEmpty();
		}

		return ByteBuffer.wrap(hash).getInt() != this.classes.hashCode();
	}
}
