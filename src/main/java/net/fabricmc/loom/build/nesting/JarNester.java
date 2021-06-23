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

package net.fabricmc.loom.build.nesting;

import java.io.File;
import java.util.Collection;
import java.util.zip.ZipEntry;

import com.google.common.base.Preconditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.gradle.api.logging.Logger;
import org.zeroturnaround.zip.FileSource;
import org.zeroturnaround.zip.ZipEntrySource;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.StringZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.util.ModUtils;

public class JarNester {
	public static void nestJars(Collection<File> jars, File modJar, Logger logger) {
		if (jars.isEmpty()) {
			logger.debug("Nothing to nest into " + modJar.getName());
			return;
		}

		Preconditions.checkArgument(ModUtils.isMod(modJar), "Cannot nest jars into none mod jar " + modJar.getName());

		ZipUtil.addEntries(modJar, jars.stream().map(file -> new FileSource("META-INF/jars/" + file.getName(), file)).toArray(ZipEntrySource[]::new));

		boolean didNest = ZipUtil.transformEntries(modJar, single(new ZipEntryTransformerEntry("fabric.mod.json", new StringZipEntryTransformer() {
			@Override
			protected String transform(ZipEntry zipEntry, String input) {
				JsonObject json = LoomGradlePlugin.GSON.fromJson(input, JsonObject.class);
				JsonArray nestedJars = json.getAsJsonArray("jars");

				if (nestedJars == null || !json.has("jars")) {
					nestedJars = new JsonArray();
				}

				for (File file : jars) {
					String nestedJarPath = "META-INF/jars/" + file.getName();

					for (JsonElement nestedJar : nestedJars) {
						JsonObject jsonObject = nestedJar.getAsJsonObject();

						if (jsonObject.has("file") && jsonObject.get("file").getAsString().equals(nestedJarPath)) {
							throw new IllegalStateException("Cannot nest 2 jars at the same path: " + nestedJarPath);
						}
					}

					JsonObject jsonObject = new JsonObject();
					jsonObject.addProperty("file", nestedJarPath);
					nestedJars.add(jsonObject);

					logger.debug("Nested " + nestedJarPath + " into " + modJar.getName());
				}

				json.add("jars", nestedJars);

				return LoomGradlePlugin.GSON.toJson(json);
			}
		})));
		Preconditions.checkArgument(didNest, "Failed to nest jars into " + modJar.getName());
	}

	private static ZipEntryTransformerEntry[] single(ZipEntryTransformerEntry element) {
		return new ZipEntryTransformerEntry[]{element};
	}
}
