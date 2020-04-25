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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMethod;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.ParameterDef;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.mappings.EntryTriple;
import net.fabricmc.loom.util.MixinTargetScanner;

public class TinyJavadocProvider implements IFabricJavadocProvider {
	private final Map<String, ClassDef> classes = new HashMap<>();
	private final Map<EntryTriple, FieldDef> fields = new HashMap<>();
	private final Map<EntryTriple, MethodDef> methods = new HashMap<>();

	private final Map<String, List<MixinTargetScanner.MixinTargetInfo>> mixins;

	private final String namespace = "named";

	public TinyJavadocProvider(File tinyFile, File mixinTargetFile) {
		final TinyTree mappings = readMappings(tinyFile);

		for (ClassDef classDef : mappings.getClasses()) {
			final String className = classDef.getName(namespace);
			classes.put(className, classDef);

			for (FieldDef fieldDef : classDef.getFields()) {
				fields.put(new EntryTriple(className, fieldDef.getName(namespace), fieldDef.getDescriptor(namespace)), fieldDef);
			}

			for (MethodDef methodDef : classDef.getMethods()) {
				methods.put(new EntryTriple(className, methodDef.getName(namespace), methodDef.getDescriptor(namespace)), methodDef);
			}
		}

		try {
			mixins = MixinTargetScanner.fromJson(FileUtils.readFileToString(mixinTargetFile, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new RuntimeException("Failed to read mixin target file", e);
		}
	}

	@Override
	public String getClassDoc(StructClass structClass) {
		StringBuilder comment = new StringBuilder();
		ClassDef classDef = classes.get(structClass.qualifiedName);

		if (classDef != null && classDef.getComment() != null) {
			comment.append(classDef.getComment());
		}

		if (mixins.containsKey(structClass.qualifiedName)) {
			List<MixinTargetScanner.MixinTargetInfo> mixinList = mixins.get(structClass.qualifiedName);

			if (classDef != null && classDef.getComment() != null) {
				comment.append("\n");
			}

			comment.append("Mixins:");

			for (MixinTargetScanner.MixinTargetInfo info : mixinList) {
				comment.append(String.format("\n\t%s - {@link %s}", info.getModid(), info.getMixinClass().replaceAll("\\$", ".")));
			}
		}

		return comment.length() == 0 ? null : comment.toString();
	}

	@Override
	public String getFieldDoc(StructClass structClass, StructField structField) {
		FieldDef fieldDef = fields.get(new EntryTriple(structClass.qualifiedName, structField.getName(), structField.getDescriptor()));
		return fieldDef != null ? fieldDef.getComment() : null;
	}

	@Override
	public String getMethodDoc(StructClass structClass, StructMethod structMethod) {
		MethodDef methodDef = methods.get(new EntryTriple(structClass.qualifiedName, structMethod.getName(), structMethod.getDescriptor()));

		if (methodDef != null) {
			List<String> parts = new ArrayList<>();

			if (methodDef.getComment() != null) {
				parts.add(methodDef.getComment());
			}

			boolean addedParam = false;

			for (ParameterDef param : methodDef.getParameters()) {
				String comment = param.getComment();

				if (comment != null) {
					if (!addedParam && methodDef.getComment() != null) {
						//Add a blank line before params when the method has a comment
						parts.add("");
						addedParam = true;
					}

					parts.add(String.format("@param %s %s", param.getName(namespace), comment));
				}
			}

			if (parts.isEmpty()) {
				return null;
			}

			return String.join("\n", parts);
		}

		return null;
	}

	private static TinyTree readMappings(File input) {
		try (BufferedReader reader = Files.newBufferedReader(input.toPath())) {
			return TinyMappingFactory.loadWithDetection(reader);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read mappings", e);
		}
	}
}
