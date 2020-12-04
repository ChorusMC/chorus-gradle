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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.GradleException;
import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.jetbrains.annotations.NotNull;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftMappedProvider;
import net.fabricmc.loom.util.SourceRemapper;
import net.fabricmc.loom.util.mappings.MojangMappingsDependency;
import net.fabricmc.lorenztiny.TinyMappingsJoiner;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;

public class MigrateMappingsTask extends AbstractLoomTask {
	private List<String> sourceSets;
	private String mappings;
	private boolean inPlace;

	public MigrateMappingsTask() {
		sourceSets = new ArrayList<>();
		sourceSets.add(SourceSet.MAIN_SOURCE_SET_NAME);
	}

	@Option(option = "sourceSets", description = "List of the source sets that should be remapped")
	public void setSourceSets(List<String> sourceSets) {
		this.sourceSets = new ArrayList<>(sourceSets);
	}

	@Option(option = "inPlace", description = "Overwrites source files after remapping")
	public void setInPlace(boolean inPlace) {
		this.inPlace = inPlace;
	}

	@Option(option = "mappings", description = "Target mappings")
	public void setMappings(String mappings) {
		this.mappings = mappings;
	}

	@TaskAction
	public void doTask() throws Throwable {
		Project project = getProject();
		LoomGradleExtension extension = getExtension();

		getLogger().lifecycle(":loading mappings");

		File mappings = loadMappings();
		MappingsProvider mappingsProvider = extension.getMappingsProvider();

		MappingSet mappingSet = loadMappingSet(mappings, mappingsProvider);

		Path projectDir = project.getProjectDir().toPath();
		Path outputBaseDir = project.getBuildDir().toPath().resolve("remapped");

		cleanOutputDir(outputBaseDir);

		List<RemapTaskRecord> remappingTasks = getRemappingTasks(project, outputBaseDir);

		for (RemapTaskRecord remappingTask : remappingTasks) {
			getLogger().lifecycle(":remapping {}", projectDir.relativize(remappingTask.inputDir));

			Files.createDirectories(remappingTask.outputDir);

			migrateMappings(project, extension.getMinecraftMappedProvider(), mappingSet, remappingTask.inputDir, remappingTask.outputDir, remappingTask.sourcePath);
		}

		if (!inPlace) {
			getLogger().lifecycle(":remapped project written to " + outputBaseDir.toAbsolutePath());
			return;
		}

		copyRemappedFiles(remappingTasks);
	}

	/**
	 * Output directories need to be cleaned before use because they might contained outdated sources which
	 * would be copied back later when inPlace is used.
	 */
	private void cleanOutputDir(Path outputBaseDir) throws IOException {
		if (!Files.exists(outputBaseDir)) {
			return;
		}

		getLogger().lifecycle(":cleaning output directory");
		// ALLOW_INSECURE is needed for Windows, sadly.
		MoreFiles.deleteDirectoryContents(outputBaseDir, RecursiveDeleteOption.ALLOW_INSECURE);
	}

	private MappingSet loadMappingSet(File mappings, MappingsProvider mappingsProvider) throws IOException {
		TinyTree currentMappings, targetMappings;

		try {
			currentMappings = mappingsProvider.getMappings();
			targetMappings = getMappings(mappings);
		} catch (IOException e) {
			throw new GradleException("Error while loading mappings", e);
		}

		getLogger().lifecycle(":joining mappings");

		return new TinyMappingsJoiner(
				currentMappings, "named",
				targetMappings, "named",
				"intermediary"
		).read();
	}

	private File loadMappings() {
		Project project = getProject();

		if (mappings == null || mappings.isEmpty()) {
			throw new IllegalArgumentException("No mappings were specified. Use --mappings=\"\" to specify target mappings");
		}

		Set<File> files;

		try {
			if (mappings.startsWith(MojangMappingsDependency.GROUP + ':' + MojangMappingsDependency.MODULE + ':') || mappings.startsWith("net.mojang.minecraft:mappings:")) {
				if (!mappings.endsWith(":" + project.getExtensions().getByType(LoomGradleExtension.class).getMinecraftProvider().getMinecraftVersion())) {
					throw new UnsupportedOperationException("Migrating Mojang mappings is currently only supported for the specified minecraft version");
				}

				files = new MojangMappingsDependency(project, getExtension()).resolve();
			} else {
				Dependency dependency = project.getDependencies().create(mappings);
				files = project.getConfigurations().detachedConfiguration(dependency).resolve();
			}
		} catch (IllegalDependencyNotation ignored) {
			getLogger().info("Could not locate mappings, presuming V2 Yarn");

			try {
				files = project.getConfigurations().detachedConfiguration(project.getDependencies().module(ImmutableMap.of("group", "net.fabricmc", "name", "yarn", "version", mappings, "classifier", "v2"))).resolve();
			} catch (GradleException ignored2) {
				getLogger().info("Could not locate mappings, presuming V1 Yarn");
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

	/**
	 * When inPlace is used, this will copy back the remapped files over the original sources.
	 */
	private void copyRemappedFiles(List<RemapTaskRecord> remappingTasks) throws IOException {
		AtomicInteger fileCounter = new AtomicInteger();

		getLogger().lifecycle(":copying back remapped files");

		for (RemapTaskRecord remappingTask : remappingTasks) {
			Stream<Path> files = Files.walk(remappingTask.outputDir);

			files.parallel()
					.filter(Files::isRegularFile)
					.forEach(file -> {
						Path relativePath = remappingTask.outputDir.relativize(file);
						Path originalPath = remappingTask.inputDir.resolve(relativePath);

						try {
							Files.copy(file, originalPath, StandardCopyOption.REPLACE_EXISTING);
						} catch (IOException e) {
							throw new GradleException("Failed to copy " + file + " to " + originalPath);
						}

						fileCounter.incrementAndGet();
					});

			files.close();
		}

		getLogger().lifecycle(":remapped {} files", fileCounter.get());
	}

	/**
	 * Collect all individual source directories that we want to run through the remapper,
	 * and also collect their respective source directory dependencies.
	 */
	private List<RemapTaskRecord> getRemappingTasks(Project project, Path outputBaseDir) {
		Path projectDir = project.getProjectDir().toPath();

		List<RemapTaskRecord> result = new ArrayList<>();

		Logger logger = getLogger();

		Map<String, SourceSet> projectSourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets()
				.getAsMap();

		for (String sourceSetName : sourceSets) {
			SourceSet sourceSet = projectSourceSets.get(sourceSetName);

			if (sourceSet == null) {
				throw new GradleException("Unable to find source set '" + sourceSetName + "'");
			}

			List<Path> sourcePath = collectSourcePath(projectSourceSets, sourceSet);
			logger.debug("Determined source path for source set {}: {}", sourceSetName, sourcePath);

			SourceDirectorySet allJava = sourceSet.getAllJava();

			for (File sourceDirectory : allJava.getSourceDirectories()) {
				Path inputPath = sourceDirectory.toPath();
				Path outputPath;

				// We need to build a sensible directory for outputting the files to
				try {
					outputPath = outputBaseDir.resolve(projectDir.relativize(inputPath));
				} catch (IllegalArgumentException e) {
					logger.warn("Not remapping source directory outside of project: {}", inputPath);
					continue;
				}

				result.add(new RemapTaskRecord(
						inputPath,
						outputPath,
						sourcePath
				));
			}
		}

		return result;
	}

	/**
	 * Collect the source path for Mercury by inspecting the compile classpath of the given source set
	 * and finding any directories in it that correspond to the output path of another source set.
	 */
	@NotNull
	private List<Path> collectSourcePath(Map<String, SourceSet> projectSourceSets, SourceSet sourceSet) {
		Set<Path> sourcePathSet = new HashSet<>();

		for (File file : sourceSet.getCompileClasspath()) {
			if (!file.isDirectory()) {
				continue;
			}

			for (SourceSet otherSourceSet : projectSourceSets.values()) {
				// Check if the other source set's output is this compile classpath directory
				if (otherSourceSet.getOutput().getClassesDirs().contains(file)) {
					for (File otherSourceSetSrcDir : otherSourceSet.getAllJava().getSrcDirs()) {
						sourcePathSet.add(otherSourceSetSrcDir.toPath());
					}
				}
			}
		}

		return new ArrayList<>(sourcePathSet);
	}

	private void migrateMappings(Project project, MinecraftMappedProvider minecraftMappedProvider,
								MappingSet mappingSet,
								Path inputDir, Path outputDir,
								List<Path> sourcePath
	) {
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

		mercury.getSourcePath().addAll(sourcePath);

		mercury.getProcessors().add(MercuryRemapper.create(mappingSet));

		try {
			mercury.rewrite(inputDir, outputDir);
		} catch (Exception e) {
			getLogger().warn("Could not remap fully!", e);
		}

		getLogger().lifecycle(":cleaning file descriptors");
		System.gc();
	}

	private static class RemapTaskRecord {
		private final Path inputDir;

		private final Path outputDir;

		private final List<Path> sourcePath;

		RemapTaskRecord(Path inputDir, Path outputDir, List<Path> sourcePath) {
			this.inputDir = inputDir;
			this.outputDir = outputDir;
			this.sourcePath = sourcePath;
		}
	}
}
