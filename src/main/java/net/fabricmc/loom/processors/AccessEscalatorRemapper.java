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

package net.fabricmc.loom.processors;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.mappings.EntryTriple;

public class AccessEscalatorRemapper {
	private final AccessEscalator input;
	private final String from, to;

	private Map<String, String> classNames = new HashMap<>();
	private Map<EntryTriple, EntryTriple> fieldNames = new HashMap<>();
	private Map<EntryTriple, EntryTriple> methodNames = new HashMap<>();

	public AccessEscalatorRemapper(AccessEscalator input, TinyTree tinyTree, String to) {
		this.input = input;
		this.from = input.namespace;
		this.to = to;
		populateMappings(tinyTree);
	}

	private void populateMappings(TinyTree tinyTree) {
		for (ClassDef classDef : tinyTree.getClasses()) {
			classNames.put(classDef.getName(from), classDef.getName(to));

			for (FieldDef fieldDef : classDef.getFields()) {
				EntryTriple fromEntry = new EntryTriple(classDef.getName(from), fieldDef.getName(from), fieldDef.getDescriptor(from));
				EntryTriple toEntry = new EntryTriple(classDef.getName(to), fieldDef.getName(to), fieldDef.getDescriptor(to));
				fieldNames.put(fromEntry, toEntry);
			}

			for (MethodDef methodDef : classDef.getMethods()) {
				EntryTriple fromEntry = new EntryTriple(classDef.getName(from), methodDef.getName(from), methodDef.getDescriptor(from));
				EntryTriple toEntry = new EntryTriple(classDef.getName(to), methodDef.getName(to), methodDef.getDescriptor(to));
				methodNames.put(fromEntry, toEntry);
			}
		}
	}

	public AccessEscalator remap() {
		//Dont remap if we dont need to
		if (input.namespace.equals(to)) {
			return input;
		}

		AccessEscalator remapped = new AccessEscalator();
		remapped.namespace = to;

		for (Map.Entry<String, AccessEscalator.Access> entry : input.classAccess.entrySet()) {
			String remappedName = classNames.get(entry.getKey());

			if (remappedName == null) {
				throw new RuntimeException("Failed to remap access escalator file, missing class mapping for: " + entry.getKey());
			}

			remapped.classAccess.put(remappedName, entry.getValue());
		}

		for (Map.Entry<EntryTriple, AccessEscalator.Access> entry : input.methodAccess.entrySet()) {
			remapped.addOrMerge(remapped.methodAccess, methodNames.get(entry.getKey()), entry.getValue());
		}

		for (Map.Entry<EntryTriple, AccessEscalator.Access> entry : input.fieldAccess.entrySet()) {
			remapped.addOrMerge(remapped.fieldAccess, fieldNames.get(entry.getKey()), entry.getValue());
		}

		return remapped;
	}
}
