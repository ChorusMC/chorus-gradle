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

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import net.fabricmc.loom.util.StaticPathWatcher;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;

public final class MappingsCache {
	public static final MappingsCache INSTANCE = new MappingsCache();

	private final Map<Path, SoftReference<TinyTree>> mappingsCache = new HashMap<>();

	// TODO: loom doesn't actually use new mappings when the mappings change until the gradle daemons are stopped
	public TinyTree get(Path mappingsPath) throws IOException {
		mappingsPath = mappingsPath.toAbsolutePath();

		if (StaticPathWatcher.INSTANCE.hasFileChanged(mappingsPath)) {
			mappingsCache.remove(mappingsPath);
		}

		SoftReference<TinyTree> ref = mappingsCache.get(mappingsPath);

		if (ref != null && ref.get() != null) {
			return ref.get();
		} else {
			try (BufferedReader reader = Files.newBufferedReader(mappingsPath)) {
				TinyTree mappings = TinyMappingFactory.loadWithDetection(reader);
				ref = new SoftReference<>(mappings);
				mappingsCache.put(mappingsPath, ref);
				return mappings;
			}
		}
	}

	public void invalidate() {
		mappingsCache.clear();
	}
}
