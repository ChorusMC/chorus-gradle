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

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.tasks.Jar;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.util.MixinRefmapHelper;
import net.fabricmc.loom.util.NestedJars;
import net.fabricmc.loom.util.TinyRemapperMappingsHelper;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;

public class RemapJarTask extends Jar {
	private RegularFileProperty input;
	private Property<Boolean> addNestedDependencies;

	public RemapJarTask() {
		super();
		input = getProject().getObjects().fileProperty();
		addNestedDependencies = getProject().getObjects().property(Boolean.class);
	}

	@TaskAction
	public void doTask() throws Throwable {
		Project project = getProject();
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		Path input = this.getInput().getAsFile().get().toPath();
		Path output = this.getArchivePath().toPath();

		if (!Files.exists(input)) {
			throw new FileNotFoundException(input.toString());
		}

		MappingsProvider mappingsProvider = extension.getMappingsProvider();

		String fromM = "named";
		String toM = "intermediary";

		Set<File> classpathFiles = new LinkedHashSet<>(
						project.getConfigurations().getByName("compileClasspath").getFiles()
		);
		Path[] classpath = classpathFiles.stream().map(File::toPath).filter((p) -> !input.equals(p) && Files.exists(p)).toArray(Path[]::new);

		File mixinMapFile = mappingsProvider.mappingsMixinExport;
		Path mixinMapPath = mixinMapFile.toPath();

		TinyRemapper.Builder remapperBuilder = TinyRemapper.newRemapper();

		remapperBuilder = remapperBuilder.withMappings(TinyRemapperMappingsHelper.create(mappingsProvider.getMappings(), fromM, toM, false));

		if (mixinMapFile.exists()) {
			remapperBuilder = remapperBuilder.withMappings(TinyUtils.createTinyMappingProvider(mixinMapPath, fromM, toM));
		}

		project.getLogger().lifecycle(":remapping " + input.getFileName());

		StringBuilder rc = new StringBuilder("Remap classpath: ");

		for (Path p : classpath) {
			rc.append("\n - ").append(p.toString());
		}

		project.getLogger().debug(rc.toString());

		TinyRemapper remapper = remapperBuilder.build();

		try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(output).build()) {
			outputConsumer.addNonClassFiles(input);
			remapper.readClassPath(classpath);
			remapper.readInputs(input);
			remapper.apply(outputConsumer);
		} catch (Exception e) {
			throw new RuntimeException("Failed to remap " + input + " to " + output, e);
		} finally {
			remapper.finish();
		}

		if (!Files.exists(output)) {
			throw new RuntimeException("Failed to remap " + input + " to " + output + " - file missing!");
		}

		if (MixinRefmapHelper.addRefmapName(extension.getRefmapName(), extension.getMixinJsonVersion(), output)) {
			project.getLogger().debug("Transformed mixin reference maps in output JAR!");
		}

		if (getAddNestedDependencies().getOrElse(false)) {
			if (NestedJars.addNestedJars(project, output)) {
				project.getLogger().debug("Added nested jar paths to mod json");
			}
		}

		/*try {
			if (modJar.exists()) {
				Files.move(modJar, modJarUnmappedCopy);
				extension.addUnmappedMod(modJarUnmappedCopy);
			}

			Files.move(modJarOutput, modJar);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}*/
	}

	@InputFile
	public RegularFileProperty getInput() {
		return input;
	}

	@Input
	public Property<Boolean> getAddNestedDependencies() {
		return addNestedDependencies;
	}
}
