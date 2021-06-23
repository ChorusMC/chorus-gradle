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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.StructRecordComponent;
import org.objectweb.asm.Opcodes;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.ParameterDef;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.mappings.EntryTriple;

public class TinyJavadocProvider implements IFabricJavadocProvider {
	private final Map<String, ClassDef> classes = new HashMap<>();
	private final Map<EntryTriple, FieldDef> fields = new HashMap<>();
	private final Map<EntryTriple, MethodDef> methods = new HashMap<>();

	private final String namespace = "named";

	public TinyJavadocProvider(File tinyFile) {
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
	}

	@Override
	public String getClassDoc(StructClass structClass) {
		ClassDef classDef = classes.get(structClass.qualifiedName);

		if (classDef == null) {
			return null;
		}

		if (!isRecord(structClass)) {
			return classDef.getComment();
		}

		/**
		 * Handle the record component docs here.
		 *
		 * Record components are mapped via the field name, thus take the docs from the fields and display them on then class.
		 */
		List<String> parts = new ArrayList<>();

		if (classDef.getComment() != null) {
			parts.add(classDef.getComment());
		}

		boolean addedParam = false;

		for (StructRecordComponent component : structClass.getRecordComponents()) {
			// The component will always match the field name and descriptor
			FieldDef fieldDef = fields.get(new EntryTriple(structClass.qualifiedName, component.getName(), component.getDescriptor()));

			if (fieldDef == null) {
				continue;
			}

			String comment = fieldDef.getComment();

			if (comment != null) {
				if (!addedParam && classDef.getComment() != null) {
					//Add a blank line before components when the class has a comment
					parts.add("");
					addedParam = true;
				}

				parts.add(String.format("@param %s %s", fieldDef.getName(namespace), comment));
			}
		}

		if (parts.isEmpty()) {
			return null;
		}

		return String.join("\n", parts);
	}

	@Override
	public String getFieldDoc(StructClass structClass, StructField structField) {
		// None static fields in records are handled in the class javadoc.
		if (isRecord(structClass) && !isStatic(structField)) {
			return null;
		}

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

	public static boolean isRecord(StructClass structClass) {
		return (structClass.getAccessFlags() & Opcodes.ACC_RECORD) != 0;
	}

	public static boolean isStatic(StructField structField) {
		return (structField.getAccessFlags() & Opcodes.ACC_STATIC) != 0;
	}
}
