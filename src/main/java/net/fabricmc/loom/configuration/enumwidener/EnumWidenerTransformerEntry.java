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

import java.util.zip.ZipEntry;

import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.zeroturnaround.zip.transform.ByteArrayZipEntryTransformer;

import net.fabricmc.loom.util.Constants;

public class EnumWidenerTransformerEntry extends ByteArrayZipEntryTransformer {
	private final Project project;
	private final String klass;

	public EnumWidenerTransformerEntry(Project project, String klass) {
		this.project = project;
		this.klass = klass;
	}

	@Override
	protected byte[] transform(ZipEntry zipEntry, byte[] input) {
		ClassWriter writer = new ClassWriter(0);
		ClassVisitor node = new EnumWidenerClassVisitor(Constants.ASM_VERSION, writer);

		this.project.getLogger().lifecycle(String.format("Applying EnumWidener to %s.", klass));

		new ClassReader(input).accept(node, 0);

		return writer.toByteArray();
	}
}
