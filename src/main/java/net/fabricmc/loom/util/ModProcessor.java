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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.commons.io.IOUtils;
import org.apache.tools.ant.util.StreamUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.commons.FileUtils;
import org.zeroturnaround.zip.transform.StringZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftMappedProvider;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public class ModProcessor {
	private static final Gson GSON = new Gson();

	public static void processMod(Project project, File input, Function<String, File> outputFileProducer, Iterable<File> dependencies) throws IOException {
		final File output = outputFileProducer.apply(input.getName());
	    remapJar(project, input, output, dependencies);

		//Enable this if you want your nested jars to be extracted, this will extract **all** jars
		if (project.getExtensions().getByType(LoomGradleExtension.class).extractJars) {
			handleNestedJars(project, input, outputFileProducer);
		}

		//Always strip the nested jars
		stripNestedJars(output);
	}

    private static void handleNestedJars(
                    Project project,
                    File input,
                    Function<String, File> outputFileProducer) throws IOException {
        JarFile jarFile = new JarFile(input);
        JarEntry modJsonEntry = jarFile.getJarEntry("fabric.mod.json");

        if (modJsonEntry == null) {
            return;
        }

        try (InputStream inputStream = jarFile.getInputStream(modJsonEntry)) {
            JsonObject json = GSON.fromJson(new InputStreamReader(inputStream), JsonObject.class);

            if (json == null || !json.has("jars")) {
                return;
            }

            JsonArray jsonArray = json.getAsJsonArray("jars");

            for (int i = 0; i < jsonArray.size(); i++) {
                JsonObject jsonObject = jsonArray.get(i).getAsJsonObject();
                String fileName = jsonObject.get("file").getAsString();
                project.getLogger().lifecycle(String.format("Found %s nested in %s", fileName, input.getName()));
                processNestedJar(project, jarFile, fileName, outputFileProducer);
            }
        }
    }

    private static void processNestedJar(
                    Project project,
                    JarFile parentJar,
                    String fileName,
                    Function<String, File> outputFileProducer) throws IOException {
        JarEntry entry = parentJar.getJarEntry(fileName);

        if (entry == null) {
            throw new RuntimeException(String.format("%s was not found in %s", fileName, parentJar.getName()));
        }

        File nestedFile = File.createTempFile(String.format("nested_%s", fileName), "jar");
        nestedFile.deleteOnExit();

        try (InputStream jarStream = parentJar.getInputStream(entry)) {
            FileUtils.copy(jarStream, nestedFile);
        }

        processMod(project, nestedFile, outputFileProducer, Lists.newArrayList());
    }

	private static void stripNestedJars(File file) {
		//Strip out all contained jar info as we dont want loader to try and load the jars contained in dev.
		ZipUtil.transformEntries(file, new ZipEntryTransformerEntry[] {(new ZipEntryTransformerEntry("fabric.mod.json", new StringZipEntryTransformer() {
			@Override
			protected String transform(ZipEntry zipEntry, String input) throws IOException {
				JsonObject json = GSON.fromJson(input, JsonObject.class);
				json.remove("jars");
				return GSON.toJson(json);
			}
		}))});
	}

	private static void remapJar(Project project, File input, File output, Iterable<File> dependencies) throws IOException {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		String fromM = "intermediary";
		String toM = "named";

		MinecraftMappedProvider mappedProvider = extension.getMinecraftMappedProvider();
		MappingsProvider mappingsProvider = extension.getMappingsProvider();

		Path inputPath = input.getAbsoluteFile().toPath();
		Path mc = mappedProvider.MINECRAFT_INTERMEDIARY_JAR.toPath();
		Path[] mcDeps = mappedProvider.getMapperPaths().stream().map(File::toPath).toArray(Path[]::new);

		TinyRemapper remapper = TinyRemapper.newRemapper()
						.withMappings(TinyRemapperMappingsHelper.create(mappingsProvider.getMappings(), fromM, toM, false))
						.build();

		try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(Paths.get(output.getAbsolutePath())).build()) {
			outputConsumer.addNonClassFiles(inputPath);
			remapper.readClassPath(StreamUtils.iteratorAsStream(dependencies.iterator()).map(File::toPath).toArray(Path[]::new));
			remapper.readClassPath(mc);
			remapper.readClassPath(mcDeps);
			remapper.readInputs(inputPath);
			remapper.apply(outputConsumer);
		} finally {
			remapper.finish();
		}

		if (!output.exists()) {
			throw new RuntimeException("Failed to remap JAR to " + toM + " file not found: " + output.getAbsolutePath());
		}
	}
}
