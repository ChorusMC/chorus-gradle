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

package net.fabricmc.loom.configuration.providers.mappings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public class LayeredMappingsProcessor {
	private final LayeredMappingSpec layeredMappingSpec;

	public LayeredMappingsProcessor(LayeredMappingSpec spec) {
		this.layeredMappingSpec = spec;
	}

	public MemoryMappingTree getMappings(MappingContext context) throws IOException {
		MemoryMappingTree mappingTree = new MemoryMappingTree();

		List<Class<? extends MappingLayer>> visitedLayers = new ArrayList<>();

		for (MappingsSpec<?> spec : layeredMappingSpec.layers()) {
			MappingLayer layer = spec.createLayer(context);

			for (Class<? extends MappingLayer> dependentLayer : layer.dependsOn()) {
				if (!visitedLayers.contains(dependentLayer)) {
					throw new RuntimeException("Layer %s depends on %s".formatted(layer.getClass().getName(), dependentLayer.getName()));
				}
			}

			visitedLayers.add(layer.getClass());

			// We have to rebuild a new tree to work on when a layer doesnt merge into layered
			boolean rebuild = layer.getSourceNamespace() != MappingNamespace.NAMED;
			MemoryMappingTree workingTree;

			if (rebuild) {
				var tempTree = new MemoryMappingTree();

				// This can be null on the first layer
				if (mappingTree.getSrcNamespace() != null) {
					var sourceNsSwitch = new MappingSourceNsSwitch(tempTree, layer.getSourceNamespace().stringValue());
					mappingTree.accept(sourceNsSwitch);
				}

				workingTree = tempTree;
			} else {
				workingTree = mappingTree;
			}

			try {
				layer.visit(workingTree);
			} catch (IOException e) {
				throw new IOException("Failed to visit: " + layer.getClass(), e);
			}

			if (rebuild) {
				mappingTree = new MemoryMappingTree();
				workingTree.accept(new MappingSourceNsSwitch(mappingTree, MappingNamespace.NAMED.stringValue()));
			}
		}

		return mappingTree;
	}
}
