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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.tasks.Jar;
import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.util.GradleSupport;
import net.fabricmc.loom.util.MixinRefmapHelper;
import net.fabricmc.loom.util.NestedJars;
import net.fabricmc.loom.util.TinyRemapperMappingsHelper;
import net.fabricmc.loom.util.accesswidener.AccessWidenerJarProcessor;
import net.fabricmc.loom.util.JarRemapper;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.stitch.util.Pair;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;

public class RemapJarTask extends Jar {
	private final RegularFileProperty input;
	private final Property<Boolean> addNestedDependencies;
	private final Property<Boolean> remapAccessWidener;
	private final Property<String> fromM;
	private final Property<String> toM;
	public JarRemapper jarRemapper;

	public RemapJarTask() {
		super();
		input = GradleSupport.getfileProperty(getProject());
		addNestedDependencies = getProject().getObjects().property(Boolean.class);
		remapAccessWidener = getProject().getObjects().property(Boolean.class);
		fromM = getProject().getObjects().property(String.class);
		toM = getProject().getObjects().property(String.class);
		// Set the defaults
		fromM.set("named");
		toM.set("intermediary");
		// false by default, I have no idea why I have to do it for this property and not the other one
		remapAccessWidener.set(false);
	}

	@TaskAction
	public void doTask() throws Throwable {
		if (jarRemapper == null) {
			doSingleRemap();
		} else {
			scheduleRemap();
		}
	}

	public void doSingleRemap() throws Throwable {
		Project project = getProject();
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		Path input = this.getInput().getAsFile().get().toPath();
		Path output = this.getArchivePath().toPath();

		if (!Files.exists(input)) {
			throw new FileNotFoundException(input.toString());
		}

		MappingsProvider mappingsProvider = extension.getMappingsProvider();

		String fromM = this.fromM.get();
		String toM = this.toM.get();

		TinyTree mappings = mappingsProvider.getMappings();
		List<String> namespaces = mappings.getMetadata().getNamespaces();

		// Validate the namespaces are supported in the tiny tree the namespaces are supported in the tiny tree
		if (!namespaces.contains(fromM)) {
			throwMissingNamespaceException(project, fromM, namespaces);
		}

		if (!namespaces.contains(toM)) {
			throwMissingNamespaceException(project, toM, namespaces);
		}

		Set<File> classpathFiles = new LinkedHashSet<>(
						project.getConfigurations().getByName("compileClasspath").getFiles()
		);
		Path[] classpath = classpathFiles.stream().map(File::toPath).filter((p) -> !input.equals(p) && Files.exists(p)).toArray(Path[]::new);

		TinyRemapper.Builder remapperBuilder = TinyRemapper.newRemapper();

		remapperBuilder = remapperBuilder.withMappings(TinyRemapperMappingsHelper.create(mappings, fromM, toM, false));

		for (File mixinMapFile : extension.getAllMixinMappings()) {
			if (mixinMapFile.exists()) {
				remapperBuilder = remapperBuilder.withMappings(TinyUtils.createTinyMappingProvider(mixinMapFile.toPath(), fromM, toM));
			}
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
			remapper.finish();
			throw new RuntimeException("Failed to remap " + input + " to " + output, e);
		}

		if (getRemapAccessWidener().getOrElse(false) && extension.accessWidener != null) {
			extension.getJarProcessorManager().getByType(AccessWidenerJarProcessor.class).remapAccessWidener(output, remapper.getRemapper(), toM);
		}

		remapper.finish();

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

	public void scheduleRemap() throws Throwable {
		Project project = getProject();
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		Path input = this.getInput().getAsFile().get().toPath();
		Path output = this.getArchivePath().toPath();

		if (!Files.exists(input)) {
			throw new FileNotFoundException(input.toString());
		}

		MappingsProvider mappingsProvider = extension.getMappingsProvider();

		String fromM = this.fromM.get();
		String toM = this.toM.get();

		TinyTree mappings = mappingsProvider.getMappings();
		List<String> namespaces = mappings.getMetadata().getNamespaces();

		// Validate the namespaces are supported in the tiny tree
		if (!namespaces.contains(fromM)) {
			throwMissingNamespaceException(project, fromM, namespaces);
		}

		if (!namespaces.contains(toM)) {
			throwMissingNamespaceException(project, toM, namespaces);
		}

		if (extension.isRootProject()) {
			Set<File> classpathFiles = new LinkedHashSet<>(
					project.getConfigurations().getByName("compileClasspath").getFiles()
			);

			Path[] classpath = classpathFiles.stream()
					.map(File::toPath)
					.filter(Files::exists)
					.toArray(Path[]::new);

			jarRemapper.addToClasspath(classpath);

			jarRemapper.addMappings(TinyRemapperMappingsHelper.create(mappings, fromM, toM, false));
		}

		for (File mixinMapFile : extension.getAllMixinMappings()) {
			if (mixinMapFile.exists()) {
				jarRemapper.addMappings(TinyUtils.createTinyMappingProvider(mixinMapFile.toPath(), fromM, toM));
			}
		}

		jarRemapper.scheduleRemap(input, output)
				.supplyAccessWidener((remapData, remapper) -> {
					if (getRemapAccessWidener().getOrElse(false) && extension.accessWidener != null) {
						AccessWidenerJarProcessor accessWidenerJarProcessor = extension.getJarProcessorManager().getByType(AccessWidenerJarProcessor.class);
						byte[] data;

						try {
							data = accessWidenerJarProcessor.getRemappedAccessWidener(remapper, toM);
						} catch (IOException e) {
							throw new RuntimeException("Failed to remap access widener");
						}

						String awPath = accessWidenerJarProcessor.getAccessWidenerPath(remapData.input);
						Preconditions.checkNotNull(awPath, "Failed to find accessWidener in fabric.mod.json: " + remapData.input);

						return Pair.of(awPath, data);
					}

					return null;
				})
				.complete((data, accessWidener) -> {
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

					if (accessWidener != null) {
						boolean replaced = ZipUtil.replaceEntry(data.output.toFile(), accessWidener.getLeft(), accessWidener.getRight());
						Preconditions.checkArgument(replaced, "Failed to remap access widener");
					}
				});
	}

	private static void throwMissingNamespaceException(Project project, String namespace, List<String> supportedNamespaces) {
		project.getLogger().error(String.format("Namespace \"%s\" is not present in the mappings.", namespace));
		project.getLogger().error("The following namespaces are supported:");

		for (String supportedNamespace : supportedNamespaces) {
			project.getLogger().error(String.format("- %s", supportedNamespace));
		}

		throw new RuntimeException();
	}

	@InputFile
	public RegularFileProperty getInput() {
		return input;
	}

	@Input
	public Property<Boolean> getAddNestedDependencies() {
		return addNestedDependencies;
	}

	@Input
	public Property<Boolean> getRemapAccessWidener() {
		return remapAccessWidener;
	}

	/**
	 * Specifies the input namespace to use to remap a built jar file.
	 * The value of this property should match the namespace of the mappings used in sources.
	 *
	 * <p>The default value of this property is {@code named}.
	 *
	 * @return the input namespace
	 */
	@Input
	public Property<String> getFromM() {
		return this.fromM;
	}

	/**
	 * Specifies the output namespace to use to remap a built jar file to.
	 * The value of this property should match the namespace of the mappings to remap this jar file to.
	 *
	 * <p>The default value of this property is {@code intermediary}.
	 *
	 * @return the output namespace to remap to
	 */
	@Input
	public Property<String> getToM() {
		return this.toM;
	}
}
