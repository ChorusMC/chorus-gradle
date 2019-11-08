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

package net.fabricmc.loom.providers;

import java.io.File;
import java.util.Collection;
import java.util.function.Consumer;

import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyProvider;
import net.fabricmc.loom.util.MapJarsTiny;

public class MinecraftMappedProvider extends DependencyProvider {
	public File MINECRAFT_MAPPED_JAR;
	public File MINECRAFT_INTERMEDIARY_JAR;

	private boolean mappedByV2;

	public MinecraftMappedProvider(boolean mappedByV2) {
		this.mappedByV2 = mappedByV2;
	}

	private MinecraftProvider minecraftProvider;

	@Override
	public void provide(DependencyInfo dependency, Project project, LoomGradleExtension extension, Consumer<Runnable> postPopulationScheduler) throws Exception {
		if (!extension.getMappingsProvider().tinyMappings.exists()) {
			throw new RuntimeException("mappings file not found");
		}

		if (!extension.getMinecraftProvider().getMergedJar().exists()) {
			throw new RuntimeException("input merged jar not found");
		}

		if (!getMappedJar().exists() || !getIntermediaryJar().exists()) {
			if (getMappedJar().exists()) {
				getMappedJar().delete();
			}

			if (getIntermediaryJar().exists()) {
				getIntermediaryJar().delete();
			}

			new MapJarsTiny().mapJars(minecraftProvider, this, project);
		}

		if (!MINECRAFT_MAPPED_JAR.exists()) {
			throw new RuntimeException("mapped jar not found");
		}

		String mappingsName = extension.getMappingsProvider().mappingsName;
		project.getDependencies().add(Constants.MINECRAFT_NAMED,
						project.getDependencies().module("net.minecraft:minecraft:" + getNamedJarVersionString(mappingsName)));
		project.getDependencies().add(Constants.MINECRAFT_INTERMEDIARY,
						project.getDependencies().module("net.minecraft:minecraft:" + getIntermediaryJarVersionString(mappingsName)));
	}

	public void initFiles(Project project, MinecraftProvider minecraftProvider, MappingsProvider mappingsProvider) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		this.minecraftProvider = minecraftProvider;
		MINECRAFT_INTERMEDIARY_JAR = new File(extension.getUserCache(),
						"minecraft-" + getIntermediaryJarVersionString(mappingsProvider.mappingsName) + ".jar");
		MINECRAFT_MAPPED_JAR = new File(extension.getUserCache(),
						"minecraft-" + getNamedJarVersionString(mappingsProvider.mappingsName) + ".jar");
	}

	private String getNamedJarVersionString(String mappingsName) {
		return minecraftProvider.minecraftVersion + "-mapped-" + mappingsName + (mappedByV2 ? "-v2" : "");
	}

	private String getIntermediaryJarVersionString(String mappingsName) {
		return minecraftProvider.minecraftVersion + "-intermediary-" + mappingsName + (mappedByV2 ? "-v2" : "");
	}

	public Collection<File> getMapperPaths() {
		return minecraftProvider.libraryProvider.getLibraries();
	}

	public File getIntermediaryJar() {
		return MINECRAFT_INTERMEDIARY_JAR;
	}

	public File getMappedJar() {
		return MINECRAFT_MAPPED_JAR;
	}

	@Override
	public String getTargetConfig() {
		return Constants.MINECRAFT_NAMED;
	}
}
