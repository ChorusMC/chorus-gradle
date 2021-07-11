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
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.StringZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.MixinAnnotationProcessorExtension;

public final class MixinRefmapHelper {
	private MixinRefmapHelper() { }

	public static boolean addRefmapName(Project project, Path outputPath) {
		MixinAnnotationProcessorExtension mixin = project.getExtensions().getByType(MixinAnnotationProcessorExtension.class);
		File output = outputPath.toFile();

		return mixin.getMixinSourceSetsStream().map(sourceSet -> {
			MixinAnnotationProcessorExtension.MixinInformationContainer container = Objects.requireNonNull(
					MixinAnnotationProcessorExtension.getMixinInformationContainer(sourceSet)
			);
			Stream<String> mixinJsonNames = container.getMixinJsonNames();
			String refmapName = container.getRefmapName();

			return ZipUtil.transformEntries(output, mixinJsonNames.map(f -> new ZipEntryTransformerEntry(f, new StringZipEntryTransformer("UTF-8") {
				@Override
				protected String transform(ZipEntry zipEntry, String input) {
					JsonObject json = LoomGradlePlugin.GSON.fromJson(input, JsonObject.class);

					if (!json.has("refmap")) {
						json.addProperty("refmap", refmapName);
					}

					return LoomGradlePlugin.GSON.toJson(json);
				}
			})).toArray(ZipEntryTransformerEntry[]::new));
		}).reduce(false, Boolean::logicalOr);
	}
}
