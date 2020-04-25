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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

public class MixinTargetScanner {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Project project;

	private HashMap<String, List<MixinTargetInfo>> classMixins = new HashMap<>();
	private List<String> scannedMods = new ArrayList<>();

	public MixinTargetScanner(Project project) {
		this.project = project;
	}

	public void scan(Configuration configuration) {
		Set<File> filesToScan = configuration.getResolvedConfiguration().getFiles();
		filesToScan.forEach(this::scanFile);
	}

	private void scanFile(File file) {
		try (ZipFile zipFile = new ZipFile(file)) {
			ZipEntry modJsonEntry = zipFile.getEntry("fabric.mod.json");

			if (modJsonEntry != null) {
				try (InputStream is = zipFile.getInputStream(modJsonEntry)) {
					JsonObject jsonObject = GSON.fromJson(new InputStreamReader(is), JsonObject.class);
					scanMod(zipFile, jsonObject);
				}
			} else {
				project.getLogger().lifecycle("Could not find mod json in " + file.getName());
			}
		} catch (ZipException e) {
			// Ignore this, most likely an invalid zip
		} catch (IOException e) {
			throw new RuntimeException("Failed to scan zip file ", e);
		}
	}

	private void scanMod(ZipFile zipFile, JsonObject modInfo) throws IOException {
		if (!modInfo.has("mixins")) return;

		JsonArray mixinsJsonArray = modInfo.getAsJsonArray("mixins");
		String modId = modInfo.get("id").getAsString();
		List<String> mixins = new ArrayList<>();
		List<String> mixinClasses = new ArrayList<>();

		//Make sure we dont scan the same mod twice, I dont think this is possible just want to be sure
		if (scannedMods.contains(modId)) return;
		scannedMods.add(modId);

		for (int i = 0; i < mixinsJsonArray.size(); i++) {
			mixins.add(mixinsJsonArray.get(i).getAsString());
		}

		//Find all the mixins in the jar
		for (String mixin : mixins) {
			ZipEntry mixinZipEntry = zipFile.getEntry(mixin);
			if (mixinZipEntry == null) continue;

			try (InputStream is = zipFile.getInputStream(mixinZipEntry)) {
				JsonObject jsonObject = GSON.fromJson(new InputStreamReader(is), JsonObject.class);
				readMixinClasses(jsonObject, mixinClasses);
			}
		}

		for (String mixinClass : mixinClasses) {
			ZipEntry mixinZipEntry = zipFile.getEntry(mixinClass.replaceAll("\\.", "/") + ".class");

			if (mixinZipEntry == null) {
				project.getLogger().info("Failed to find mixin class: " + mixinClass);
				continue;
			}

			try (InputStream is = zipFile.getInputStream(mixinZipEntry)) {
				byte[] classBytes = IOUtils.toByteArray(is);
				List<String> mixinTargets = readMixinClass(classBytes);

				for (String mixinTarget : mixinTargets) {
					classMixins.computeIfAbsent(mixinTarget, s -> new ArrayList<>())
							.add(new MixinTargetInfo(mixinClass, modId));
				}
			}
		}
	}

	private void readMixinClasses(JsonObject jsonObject, List<String> mixinClasses) {
		String[] sides = new String[]{"mixins", "client", "server"};

		if (!jsonObject.has("package")) return;
		String mixinPackage = jsonObject.getAsJsonPrimitive("package").getAsString();

		for (String side : sides) {
			if (!jsonObject.has(side)) continue;
			JsonArray jsonArray = jsonObject.getAsJsonArray(side);

			for (int i = 0; i < jsonArray.size(); i++) {
				String mixinClass = jsonArray.get(i).getAsString();

				mixinClasses.add(String.format("%s.%s", mixinPackage, mixinClass));
			}
		}
	}

	private List<String> readMixinClass(byte[] bytes) {
		ClassNode classNode = new ClassNode();
		ClassReader classReader = new ClassReader(bytes);
		classReader.accept(classNode, 0);

		if (classNode.invisibleAnnotations == null) return Collections.emptyList();

		List<String> mixinTargets = new ArrayList<>();

		for (AnnotationNode annotationNode : classNode.invisibleAnnotations) {
			if (annotationNode.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
				List<Object> values = annotationNode.values;

				for (int i = 0; i < values.size(); i++) {
					if (values.get(i).equals("value")) {
						//noinspection unchecked
						List<Type> types = (List<Type>) values.get(i + 1);

						for (Type type : types) {
							mixinTargets.add(type.getInternalName());
						}
					}
				}

				break;
			}
		}

		return mixinTargets;
	}

	public Map<String, List<MixinTargetInfo>> getClassMixins() {
		return classMixins;
	}

	public String getClassMixinsJson() {
		return GSON.toJson(getClassMixins());
	}

	public static Map<String, List<MixinTargetInfo>> fromJson(String input) {
		return GSON.fromJson(input, new TypeToken<Map<String, List<MixinTargetInfo>>>() {
		}.getType());
	}

	public static class MixinTargetInfo {
		private final String mixinClass;
		private final String modid;

		public MixinTargetInfo(String mixinClass, String modid) {
			this.mixinClass = mixinClass;
			this.modid = modid;
		}

		public String getMixinClass() {
			return mixinClass;
		}

		public String getModid() {
			return modid;
		}
	}
}
