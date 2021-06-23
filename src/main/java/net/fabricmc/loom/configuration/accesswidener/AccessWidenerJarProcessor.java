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

package net.fabricmc.loom.configuration.accesswidener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.Remapper;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.ByteArrayZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerRemapper;
import net.fabricmc.accesswidener.AccessWidenerVisitor;
import net.fabricmc.accesswidener.AccessWidenerWriter;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.processors.Environment;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.tinyremapper.TinyRemapper;

public class AccessWidenerJarProcessor implements JarProcessor {
	private AccessWidener accessWidener = new AccessWidener();
	private AccessWidenerReader accessWidenerReader = new AccessWidenerReader(accessWidener);
	private final Project project;
	private byte[] inputHash;

	public AccessWidenerJarProcessor(Project project) {
		this.project = project;
	}

	@Override
	public Environment getEnvironment() {
		return Environment.BOTH;
	}

	@Override
	public void setup() {
		LoomGradleExtension loomGradleExtension = project.getExtensions().getByType(LoomGradleExtension.class);

		if (!loomGradleExtension.accessWidener.exists()) {
			throw new RuntimeException("Could not find access widener file @ " + loomGradleExtension.accessWidener.getAbsolutePath());
		}

		inputHash = Checksum.sha256(loomGradleExtension.accessWidener);

		try (BufferedReader reader = new BufferedReader(new FileReader(loomGradleExtension.accessWidener))) {
			accessWidenerReader.read(reader);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read project access widener file");
		}

		//Remap accessWidener if its not named, allows for AE's to be written in intermediary
		if (!accessWidener.getNamespace().equals("named")) {
			try {
				List<String> validNamespaces = loomGradleExtension.getMappingsProvider().getMappings().getMetadata().getNamespaces();

				if (!validNamespaces.contains(accessWidener.getNamespace())) {
					throw new UnsupportedOperationException(String.format("Access Widener namespace '%s' is not a valid namespace, it must be one of: '%s'", accessWidener.getNamespace(), String.join(", ", validNamespaces)));
				}

				TinyRemapper tinyRemapper = loomGradleExtension.getMinecraftMappedProvider().getTinyRemapper("official", "named");
				tinyRemapper.readClassPath(loomGradleExtension.getMinecraftMappedProvider().getRemapClasspath());

				AccessWidenerRemapper remapper = new AccessWidenerRemapper(accessWidener, tinyRemapper.getRemapper(), "named");
				accessWidener = remapper.remap();

				tinyRemapper.finish();
			} catch (IOException e) {
				throw new RuntimeException("Failed to remap access widener", e);
			}
		}
	}

	@Override
	public void process(File file) {
		project.getLogger().lifecycle("Processing file: " + file.getName());
		ZipUtil.transformEntries(file, getTransformers(accessWidener.getTargets()));
		ZipUtil.addEntry(file, "aw.sha256", inputHash);
	}

	private ZipEntryTransformerEntry[] getTransformers(Set<String> classes) {
		return classes.stream()
				.map(string -> new ZipEntryTransformerEntry(string.replaceAll("\\.", "/") + ".class", getTransformer(string)))
				.toArray(ZipEntryTransformerEntry[]::new);
	}

	private ZipEntryTransformer getTransformer(String className) {
		return new ByteArrayZipEntryTransformer() {
			@Override
			protected byte[] transform(ZipEntry zipEntry, byte[] input) {
				ClassReader reader = new ClassReader(input);
				ClassWriter writer = new ClassWriter(0);
				ClassVisitor classVisitor = AccessWidenerVisitor.createClassVisitor(Constants.ASM_VERSION, writer, accessWidener);

				project.getLogger().lifecycle("Applying access widener to " + className);

				reader.accept(classVisitor, 0);
				return writer.toByteArray();
			}
		};
	}

	//Called when remapping the mod
	public void remapAccessWidener(Path modJarPath, Remapper asmRemapper) throws IOException {
		byte[] bytes = getRemappedAccessWidener(asmRemapper);

		String path = getAccessWidenerPath(modJarPath);

		if (path == null) {
			throw new RuntimeException("Failed to find accessWidener in fabric.mod.json");
		}

		boolean replaced = ZipUtil.replaceEntry(modJarPath.toFile(), path, bytes);

		if (!replaced) {
			project.getLogger().warn("Failed to replace access widener file at " + path);
		}
	}

	public byte[] getRemappedAccessWidener(Remapper asmRemapper) throws IOException {
		AccessWidenerRemapper remapper = new AccessWidenerRemapper(accessWidener, asmRemapper, "intermediary");
		AccessWidener remapped = remapper.remap();
		AccessWidenerWriter accessWidenerWriter = new AccessWidenerWriter(remapped);

		try (StringWriter writer = new StringWriter()) {
			accessWidenerWriter.write(writer);
			return writer.toString().getBytes();
		}
	}

	public String getAccessWidenerPath(Path modJarPath) {
		byte[] modJsonBytes = ZipUtil.unpackEntry(modJarPath.toFile(), "fabric.mod.json");

		if (modJsonBytes == null) {
			return null;
		}

		JsonObject jsonObject = new Gson().fromJson(new String(modJsonBytes, StandardCharsets.UTF_8), JsonObject.class);

		if (!jsonObject.has("accessWidener")) {
			return null;
		}

		return jsonObject.get("accessWidener").getAsString();
	}

	@Override
	public boolean isInvalid(File file) {
		byte[] hash = ZipUtil.unpackEntry(file, "aw.sha256");

		if (hash == null) {
			return true;
		}

		return !Arrays.equals(inputHash, hash); // TODO how do we know if the current jar as the correct access applied? save the hash of the input?
	}
}
