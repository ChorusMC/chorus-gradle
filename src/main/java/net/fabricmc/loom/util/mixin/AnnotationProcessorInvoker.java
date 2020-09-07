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

package net.fabricmc.loom.util.mixin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskCollection;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.task.RemapJarTask;
import net.fabricmc.loom.util.Constants;

/**
 * Normally javac invokes annotation processors, but when the scala or kapt plugin are installed they will want to invoke
 * the annotation processor themselves.
 * See Java and Kapt implementations for a more deep understanding of the things passed by the children.
 */
public abstract class AnnotationProcessorInvoker<T extends Task> {
	protected final Project project;
	private final Collection<Configuration> annotationProcessorConfigurations;
	protected final TaskCollection<T> invokerTasks;

	protected AnnotationProcessorInvoker(Project project,
										Collection<Configuration> annotationProcessorConfigurations,
										TaskCollection<T> invokerTasks) {
		this.project = project;
		this.annotationProcessorConfigurations = annotationProcessorConfigurations;
		this.invokerTasks = invokerTasks;
	}

	protected abstract void passArgument(T compileTask, String key, String value);

	protected abstract File getDestinationDir(T task);

	protected final String getRefmapDestination(T task, LoomGradleExtension extension) throws IOException {
		return new File(getDestinationDir(task), extension.getRefmapName()).getCanonicalPath();
	}

	private void passMixinArguments(T task, String fromM, String toM) {
		try {
			LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
			Map<String, String> args = new HashMap<String, String>() {{
					put("inMapFileNamedIntermediary", extension.getMappingsProvider().tinyMappings.getCanonicalPath());
					put("outMapFileNamedIntermediary", extension.getNextMixinMappings().getCanonicalPath());
					put("outRefMapFile", getRefmapDestination(task, extension));
					put("defaultObfuscationEnv", fromM + ":" + toM);
				}};

			project.getLogger().debug("Outputting refmap to dir: " + getDestinationDir(task) + " for compile task: " + task);
			args.forEach((k, v) -> passArgument(task, k, v));
		} catch (IOException e) {
			project.getLogger().error("Could not configure mixin annotation processors", e);
		}
	}

	public void configureMixin() {
		ConfigurationContainer configs = project.getConfigurations();
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);

		if (!extension.ideSync()) {
			for (Configuration processorConfig : annotationProcessorConfigurations) {
				project.getLogger().info("Adding mixin to classpath of AP config: " + processorConfig.getName());
				// Pass named MC classpath to mixin AP classpath
				processorConfig.extendsFrom(
						configs.getByName(Constants.MINECRAFT_NAMED),
						configs.getByName(Constants.MOD_COMPILE_CLASSPATH_MAPPED),
						configs.getByName(Constants.MAPPINGS_FINAL)
				);

				// Add Mixin and mixin extensions (fabric-mixin-compile-extensions pulls mixin itself too)
				project.getDependencies().add(processorConfig.getName(),
						"net.fabricmc:fabric-mixin-compile-extensions:" + Constants.MIXIN_COMPILE_EXTENSIONS_VERSION);
			}
		}

		for (T task : invokerTasks) {
			if (task instanceof RemapJarTask) {
				passMixinArguments(task, ((RemapJarTask) task).getFromM().get(), ((RemapJarTask) task).getToM().get());
			} else {
				// TODO: Correct approach?
				// Assume named -> intermediary is desired for non-remapJar - Probably in dev?
				passMixinArguments(task, "named", "intermediary");
			}
		}
	}

	static Stream<SourceSet> getNonTestSourceSets(Project project) {
		return project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets()
						.stream()
						.filter(sourceSet -> !sourceSet.getName().equals("test"));
	}
}
