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

package net.fabricmc.loom.processors.side;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.zeroturnaround.zip.ByteSource;
import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.loom.processors.JarProcessor;

public enum SideStripperJarProcessor implements JarProcessor {
	CLIENT,
	SERVER;

	@Override
	public void setup() {
	}

	@Override
	public void process(File file) {
		Set<String> toRemove = new HashSet<>();
		Set<ByteSource> toTransform = new HashSet<>();

		ZipUtil.iterate(file, (in, zipEntry) -> {
			String name = zipEntry.getName();

			if (!zipEntry.isDirectory() && name.endsWith(".class")) {
				ClassNode original = new ClassNode();
				new ClassReader(in).accept(original, 0);

				EnvironmentStrippingData stripData = new EnvironmentStrippingData(Opcodes.ASM8, name());
				original.accept(stripData);

				if (stripData.stripEntireClass()) {
					toRemove.add(name);
				} else if (!stripData.isEmpty()) {
					ClassWriter classWriter = new ClassWriter(0);
					original.accept(new ClassStripper(Opcodes.ASM8, classWriter, stripData.getStripInterfaces(), stripData.getStripFields(), stripData.getStripMethods()));
					toTransform.add(new ByteSource(name, classWriter.toByteArray()));
				}
			}
		});

		ZipUtil.replaceEntries(file, toTransform.toArray(new ByteSource[0]));
		ZipUtil.removeEntries(file, toRemove.toArray(new String[0]));
		ZipUtil.addEntry(file, "side.txt", name().getBytes());
	}

	@Override
	public boolean isInvalid(File file) {
		return !Arrays.equals(ZipUtil.unpackEntry(file, "side.txt"), name().getBytes());
	}
}
