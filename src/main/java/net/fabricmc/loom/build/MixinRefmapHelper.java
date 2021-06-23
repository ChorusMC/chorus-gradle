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

package net.fabricmc.loom.build;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;

import com.google.gson.JsonObject;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.StringZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import net.fabricmc.loom.LoomGradlePlugin;

public final class MixinRefmapHelper {
	private MixinRefmapHelper() { }

	public static boolean addRefmapName(String filename, Path outputPath) {
		File output = outputPath.toFile();
		Set<String> mixinFilenames = findMixins(output, true);

		if (mixinFilenames.size() > 0) {
			return ZipUtil.transformEntries(output, mixinFilenames.stream().map((f) -> new ZipEntryTransformerEntry(f, new StringZipEntryTransformer("UTF-8") {
				@Override
				protected String transform(ZipEntry zipEntry, String input) throws IOException {
					JsonObject json = LoomGradlePlugin.GSON.fromJson(input, JsonObject.class);

					if (!json.has("refmap")) {
						json.addProperty("refmap", filename);
					}

					return LoomGradlePlugin.GSON.toJson(json);
				}
			})).toArray(ZipEntryTransformerEntry[]::new));
		} else {
			return false;
		}
	}

	private static Set<String> findMixins(File output, boolean onlyWithoutRefmap) {
		// first, identify all of the mixin files
		Set<String> mixinFilename = new HashSet<>();
		// TODO: this is a lovely hack
		ZipUtil.iterate(output, (stream, entry) -> {
			if (!entry.isDirectory() && entry.getName().endsWith(".json") && !entry.getName().contains("/") && !entry.getName().contains("\\")) {
				// JSON file in root directory
				try (InputStreamReader inputStreamReader = new InputStreamReader(stream)) {
					JsonObject json = LoomGradlePlugin.GSON.fromJson(inputStreamReader, JsonObject.class);

					if (json != null) {
						boolean hasMixins = json.has("mixins") && json.get("mixins").isJsonArray();
						boolean hasClient = json.has("client") && json.get("client").isJsonArray();
						boolean hasServer = json.has("server") && json.get("server").isJsonArray();

						if (json.has("package") && (hasMixins || hasClient || hasServer)) {
							if (!onlyWithoutRefmap || !json.has("refmap") || !json.has("minVersion")) {
								mixinFilename.add(entry.getName());
							}
						}
					}
				} catch (Exception ignored) {
					// ...
				}
			}
		});
		return mixinFilename;
	}
}
