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

package net.fabricmc.loom.task;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.GradleException;
import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftMappedProvider;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingSpecBuilder;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingsDependency;
import net.fabricmc.loom.util.SourceRemapper;
import net.fabricmc.lorenztiny.TinyMappingsJoiner;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;

public class MigrateMappingsTask extends AbstractLoomTask {
	private Path inputDir;
	private Path outputDir;
	private String mappings;

	public MigrateMappingsTask() {
		inputDir = getProject().file("src/main/java").toPath();
		outputDir = getProject().file("remappedSrc").toPath();
	}

	@Option(option = "input", description = "Java source file directory")
	public void setInputDir(String inputDir) {
		this.inputDir = getProject().file(inputDir).toPath();
	}

	@Option(option = "output", description = "Remapped source output directory")
	public void setOutputDir(String outputDir) {
		this.outputDir = getProject().file(outputDir).toPath();
	}

	@Option(option = "mappings", description = "Target mappings")
	public void setMappings(String mappings) {
		this.mappings = mappings;
	}

	@TaskAction
	public void doTask() throws Throwable {
		Project project = getProject();
		LoomGradleExtension extension = getExtension();

		project.getLogger().info(":loading mappings");

		if (!Files.exists(inputDir) || !Files.isDirectory(inputDir)) {
			throw new IllegalArgumentException("Could not find input directory: " + inputDir.toAbsolutePath());
		}

		Files.createDirectories(outputDir);

		File mappings = loadMappings();
		MappingsProviderImpl mappingsProvider = extension.getMappingsProvider();

		try {
			TinyTree currentMappings = mappingsProvider.getMappings();
			TinyTree targetMappings = getMappings(mappings);
			migrateMappings(project, extension.getMinecraftMappedProvider(), inputDir, outputDir, currentMappings, targetMappings);
			project.getLogger().lifecycle(":remapped project written to " + outputDir.toAbsolutePath());
		} catch (IOException e) {
			throw new IllegalArgumentException("Error while loading mappings", e);
		}
	}

	private File loadMappings() {
		Project project = getProject();

		if (mappings == null || mappings.isEmpty()) {
			throw new IllegalArgumentException("No mappings were specified. Use --mappings=\"\" to specify target mappings");
		}

		Set<File> files;

		try {
			if (mappings.startsWith("net.minecraft:mappings:") || mappings.startsWith("net.mojang.minecraft:mappings:")) {
				if (!mappings.endsWith(":" + project.getExtensions().getByType(LoomGradleExtension.class).getMinecraftProvider().minecraftVersion())) {
					throw new UnsupportedOperationException("Migrating Mojang mappings is currently only supported for the specified minecraft version");
				}

				LayeredMappingsDependency dep = (LayeredMappingsDependency) getExtension().layered(LayeredMappingSpecBuilder::officialMojangMappings);
				files = dep.resolve();
			} else {
				Dependency dependency = project.getDependencies().create(mappings);
				files = project.getConfigurations().detachedConfiguration(dependency).resolve();
			}
		} catch (IllegalDependencyNotation ignored) {
			project.getLogger().info("Could not locate mappings, presuming V2 Yarn");

			try {
				files = project.getConfigurations().detachedConfiguration(project.getDependencies().module(ImmutableMap.of("group", "net.fabricmc", "name", "yarn", "version", mappings, "classifier", "v2"))).resolve();
			} catch (GradleException ignored2) {
				project.getLogger().info("Could not locate mappings, presuming V1 Yarn");
				files = project.getConfigurations().detachedConfiguration(project.getDependencies().module(ImmutableMap.of("group", "net.fabricmc", "name", "yarn", "version", mappings))).resolve();
			}
		}

		if (files.isEmpty()) {
			throw new IllegalArgumentException("Mappings could not be found");
		}

		return Iterables.getOnlyElement(files);
	}

	private static TinyTree getMappings(File mappings) throws IOException {
		Path temp = Files.createTempFile("mappings", ".tiny");

		try (FileSystem fileSystem = FileSystems.newFileSystem(mappings.toPath(), (ClassLoader) null)) {
			Files.copy(fileSystem.getPath("mappings/mappings.tiny"), temp, StandardCopyOption.REPLACE_EXISTING);
		}

		try (BufferedReader reader = Files.newBufferedReader(temp)) {
			return TinyMappingFactory.loadWithDetection(reader);
		}
	}

	private static void migrateMappings(Project project, MinecraftMappedProvider minecraftMappedProvider,
										Path inputDir, Path outputDir, TinyTree currentMappings, TinyTree targetMappings
	) throws IOException {
		project.getLogger().info(":joining mappings");

		MappingSet mappingSet = new TinyMappingsJoiner(
				currentMappings, "named",
				targetMappings, "named",
				"intermediary"
		).read();

		project.getLogger().lifecycle(":remapping");
		Mercury mercury = SourceRemapper.createMercuryWithClassPath(project, false);

		final JavaPluginConvention convention = project.getConvention().findPlugin(JavaPluginConvention.class);
		final JavaVersion javaVersion = convention != null
				?
				convention.getSourceCompatibility()
				:
				JavaVersion.current();
		mercury.setSourceCompatibility(javaVersion.toString());

		mercury.getClassPath().add(minecraftMappedProvider.getMappedJar().toPath());
		mercury.getClassPath().add(minecraftMappedProvider.getIntermediaryJar().toPath());

		mercury.getProcessors().add(MercuryRemapper.create(mappingSet));

		try {
			mercury.rewrite(inputDir, outputDir);
		} catch (Exception e) {
			project.getLogger().warn("Could not remap fully!", e);
		}

		project.getLogger().info(":cleaning file descriptors");
		System.gc();
	}
}
