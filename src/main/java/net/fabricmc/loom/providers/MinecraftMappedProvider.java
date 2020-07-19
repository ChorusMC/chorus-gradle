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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;

import org.gradle.api.Project;

import net.fabricmc.loom.util.TinyRemapperMappingsHelper;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DependencyProvider;

public class MinecraftMappedProvider extends DependencyProvider {
	private File minecraftMappedJar;
	private File minecraftIntermediaryJar;

	private MinecraftProvider minecraftProvider;

	public MinecraftMappedProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		if (!getExtension().getMappingsProvider().tinyMappings.exists()) {
			throw new RuntimeException("mappings file not found");
		}

		if (!getExtension().getMinecraftProvider().getMergedJar().exists()) {
			throw new RuntimeException("input merged jar not found");
		}

		if (!minecraftMappedJar.exists() || !getIntermediaryJar().exists() || isRefreshDeps()) {
			if (minecraftMappedJar.exists()) {
				minecraftMappedJar.delete();
			}

			minecraftMappedJar.getParentFile().mkdirs();

			if (minecraftIntermediaryJar.exists()) {
				minecraftIntermediaryJar.delete();
			}

			try {
				mapMinecraftJar();
			} catch (Throwable t) {
				//Cleanup some some things that may be in a bad state now
				minecraftMappedJar.delete();
				minecraftIntermediaryJar.delete();
				getExtension().getMappingsProvider().cleanFiles();
				throw new RuntimeException("Failed to remap minecraft", t);
			}
		}

		if (!minecraftMappedJar.exists()) {
			throw new RuntimeException("mapped jar not found");
		}

		addDependencies(dependency, postPopulationScheduler);
	}

	private void mapMinecraftJar() throws IOException {
		String fromM = "official";

		MappingsProvider mappingsProvider = getExtension().getMappingsProvider();

		Path input = minecraftProvider.getMergedJar().toPath();
		Path outputMapped = minecraftMappedJar.toPath();
		Path outputIntermediary = minecraftIntermediaryJar.toPath();

		for (String toM : Arrays.asList("named", "intermediary")) {
			Path output = "named".equals(toM) ? outputMapped : outputIntermediary;

			getProject().getLogger().lifecycle(":remapping minecraft (TinyRemapper, " + fromM + " -> " + toM + ")");

			TinyRemapper remapper = getTinyRemapper(fromM, toM);

			try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(output).build()) {
				outputConsumer.addNonClassFiles(input);
				remapper.readClassPath(getRemapClasspath());
				remapper.readInputs(input);
				remapper.apply(outputConsumer);
			} catch (Exception e) {
				throw new RuntimeException("Failed to remap JAR " + input + " with mappings from " + mappingsProvider.tinyMappings, e);
			} finally {
				remapper.finish();
			}
		}
	}

	public TinyRemapper getTinyRemapper(String fromM, String toM) throws IOException {
		TinyTree annotationMappingsTree;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(MinecraftMappedProvider.class.getClassLoader().getResourceAsStream("jsrToJetbrains.tiny")))) {
			annotationMappingsTree = TinyMappingFactory.loadWithDetection(reader);
		}

		return TinyRemapper.newRemapper()
				.withMappings(TinyRemapperMappingsHelper.create(getExtension().getMappingsProvider().getMappings(), fromM, toM, true))
				.withMappings(TinyRemapperMappingsHelper.create(annotationMappingsTree, "jsr", "jetbrains", false))
				.renameInvalidLocals(true)
				.rebuildSourceFilenames(true)
				.build();
	}

	public Path[] getRemapClasspath() {
		return getMapperPaths().stream().map(File::toPath).toArray(Path[]::new);
	}

	protected void addDependencies(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) {
		getProject().getRepositories().flatDir(repository -> repository.dir(getJarDirectory(getExtension().getUserCache(), "mapped")));

		getProject().getDependencies().add(Constants.MINECRAFT_NAMED,
				getProject().getDependencies().module("net.minecraft:minecraft:" + getJarVersionString("mapped")));
	}

	public void initFiles(MinecraftProvider minecraftProvider, MappingsProvider mappingsProvider) {
		this.minecraftProvider = minecraftProvider;
		minecraftIntermediaryJar = new File(getExtension().getUserCache(), "minecraft-" + getJarVersionString("intermediary") + ".jar");
		minecraftMappedJar = new File(getJarDirectory(getExtension().getUserCache(), "mapped"), "minecraft-" + getJarVersionString("mapped") + ".jar");
	}

	protected File getJarDirectory(File parentDirectory, String type) {
		return new File(parentDirectory, getJarVersionString(type));
	}

	protected String getJarVersionString(String type) {
		return String.format("%s-%s-%s-%s", minecraftProvider.getMinecraftVersion(), type, getExtension().getMappingsProvider().mappingsName, getExtension().getMappingsProvider().mappingsVersion);
	}

	public Collection<File> getMapperPaths() {
		return minecraftProvider.getLibraryProvider().getLibraries();
	}

	public File getIntermediaryJar() {
		return minecraftIntermediaryJar;
	}

	public File getMappedJar() {
		return minecraftMappedJar;
	}

	@Override
	public String getTargetConfig() {
		return Constants.MINECRAFT_NAMED;
	}
}
