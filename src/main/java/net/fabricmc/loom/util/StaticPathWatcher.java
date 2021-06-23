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

package net.fabricmc.loom.util;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;

public final class StaticPathWatcher {
	public static final StaticPathWatcher INSTANCE = new StaticPathWatcher();

	private final Map<Path, Boolean> changeCache = new HashMap<>();
	private final WatchService service;
	private final Map<Path, WatchKey> pathsObserved = new HashMap<>();

	private StaticPathWatcher() {
		try {
			service = FileSystems.getDefault().newWatchService();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public boolean hasFileChanged(Path filePath) {
		if (!Files.exists(filePath)) {
			return true;
		}

		WatchKey key;

		while ((key = service.poll()) != null) {
			for (WatchEvent<?> event : key.pollEvents()) {
				Object ctx = event.context();

				if (ctx instanceof Path path) {
					changeCache.put(path.toAbsolutePath(), true);
				}
			}
		}

		filePath = filePath.toAbsolutePath();
		Path parentPath = filePath.getParent();

		if (changeCache.containsKey(filePath)) {
			return true;
		} else {
			if (!pathsObserved.containsKey(parentPath)) {
				try {
					pathsObserved.put(parentPath, parentPath.register(service, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE));

					return true;
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			} else {
				return false;
			}
		}
	}
}
