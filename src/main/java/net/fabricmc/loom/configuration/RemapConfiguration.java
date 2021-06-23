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

package net.fabricmc.loom.configuration;

import java.io.IOException;

import com.google.common.base.Preconditions;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.build.JarRemapper;
import net.fabricmc.loom.build.nesting.NestedDependencyProvider;
import net.fabricmc.loom.task.AbstractLoomTask;
import net.fabricmc.loom.task.RemapAllSourcesTask;
import net.fabricmc.loom.task.RemapJarTask;
import net.fabricmc.loom.task.RemapSourcesJarTask;
import net.fabricmc.loom.util.SourceRemapper;

public class RemapConfiguration {
	private static final String DEFAULT_JAR_TASK_NAME = JavaPlugin.JAR_TASK_NAME;
	private static final String DEFAULT_SOURCES_JAR_TASK_NAME = "sourcesJar";
	private static final String DEFAULT_REMAP_JAR_TASK_NAME = "remapJar";
	private static final String DEFAULT_REMAP_SOURCES_JAR_TASK_NAME = "remapSourcesJar";
	private static final String DEFAULT_REMAP_ALL_JARS_TASK_NAME = "remapAllJars";
	private static final String DEFAULT_REMAP_ALL_SOURCES_TASK_NAME = "remapAllSources";

	public static void setupDefaultRemap(Project project) {
		setupRemap(project, true, DEFAULT_JAR_TASK_NAME, DEFAULT_SOURCES_JAR_TASK_NAME, DEFAULT_REMAP_JAR_TASK_NAME, DEFAULT_REMAP_SOURCES_JAR_TASK_NAME, DEFAULT_REMAP_ALL_JARS_TASK_NAME, DEFAULT_REMAP_ALL_SOURCES_TASK_NAME);
	}

	@ApiStatus.Experimental // This is only an api if you squint really hard, expect it to explode every 5 mins. If you must call in afterEvaluate on all projects
	public static void setupRemap(Project project, String jarTaskName, String sourcesJarTaskName, String remapJarTaskName, String remapSourcesJarTaskName, String remapAllJarsTaskName, String remapAllSourcesTaskName) {
		setupRemap(project, false, jarTaskName, sourcesJarTaskName, remapJarTaskName, remapSourcesJarTaskName, remapAllJarsTaskName, remapAllSourcesTaskName);
	}

	// isDefaultRemap is set to true for the standard remap task, some defaults are left out when this is false.
	private static void setupRemap(Project project, boolean isDefaultRemap, String jarTaskName, String sourcesJarTaskName, String remapJarTaskName, String remapSourcesJarTaskName, String remapAllJarsTaskName, String remapAllSourcesTaskName) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		AbstractArchiveTask jarTask = (AbstractArchiveTask) project.getTasks().getByName(jarTaskName);
		RemapJarTask remapJarTask = (RemapJarTask) project.getTasks().findByName(remapJarTaskName);

		assert remapJarTask != null;

		if (!remapJarTask.getInput().isPresent() && isDefaultRemap) {
			jarTask.setClassifier("dev");
			remapJarTask.setClassifier("");
			remapJarTask.getInput().set(jarTask.getArchivePath());
		}

		if (isDefaultRemap) {
			extension.getUnmappedModCollection().from(jarTask);
			remapJarTask.getAddNestedDependencies().set(true);
			remapJarTask.getRemapAccessWidener().set(true);

			project.getArtifacts().add("archives", remapJarTask);
		}

		remapJarTask.dependsOn(jarTask);
		project.getTasks().getByName("build").dependsOn(remapJarTask);

		// TODO this might be wrong?
		project.getTasks().withType(RemapJarTask.class).forEach(task -> {
			if (task.getAddNestedDependencies().getOrElse(false)) {
				NestedDependencyProvider.getRequiredTasks(project).forEach(task::dependsOn);
			}
		});

		SourceRemapper remapper = null;
		// TODO what is this for?
		Task parentTask = project.getTasks().getByName("build");

		if (extension.isShareCaches()) {
			Project rootProject = project.getRootProject();

			if (extension.isRootProject()) {
				SourceRemapper sourceRemapper = new SourceRemapper(rootProject, false);
				JarRemapper jarRemapper = new JarRemapper();

				remapJarTask.jarRemapper = jarRemapper;

				rootProject.getTasks().register(remapAllSourcesTaskName, RemapAllSourcesTask.class, task -> {
					task.sourceRemapper = sourceRemapper;
					task.doLast(t -> sourceRemapper.remapAll());
				});

				parentTask = rootProject.getTasks().getByName(remapAllSourcesTaskName);

				rootProject.getTasks().register(remapAllJarsTaskName, AbstractLoomTask.class, task -> {
					task.doLast(t -> {
						try {
							jarRemapper.remap();
						} catch (IOException e) {
							throw new RuntimeException("Failed to remap jars", e);
						}
					});
				});
			} else {
				parentTask = rootProject.getTasks().getByName(remapAllSourcesTaskName);
				remapper = ((RemapAllSourcesTask) parentTask).sourceRemapper;
				Preconditions.checkNotNull(remapper);

				remapJarTask.jarRemapper = ((RemapJarTask) rootProject.getTasks().getByName(remapJarTaskName)).jarRemapper;

				project.getTasks().getByName("build").dependsOn(parentTask);
				project.getTasks().getByName("build").dependsOn(rootProject.getTasks().getByName(remapAllJarsTaskName));
				rootProject.getTasks().getByName(remapAllJarsTaskName).dependsOn(project.getTasks().getByName(remapJarTaskName));
			}
		}

		try {
			AbstractArchiveTask sourcesTask = (AbstractArchiveTask) project.getTasks().getByName(sourcesJarTaskName);

			RemapSourcesJarTask remapSourcesJarTask = (RemapSourcesJarTask) project.getTasks().findByName(remapSourcesJarTaskName);
			Preconditions.checkNotNull(remapSourcesJarTask, "Could not find " + remapSourcesJarTaskName + " in " + project.getName());
			remapSourcesJarTask.setInput(sourcesTask.getArchivePath());
			remapSourcesJarTask.setOutput(sourcesTask.getArchivePath());
			remapSourcesJarTask.dependsOn(project.getTasks().getByName(sourcesJarTaskName));

			if (isDefaultRemap) {
				// Do not use lambda here, see: https://github.com/gradle/gradle/pull/17087
				//noinspection Convert2Lambda
				remapSourcesJarTask.doLast(new Action<>() {
					@Override
					public void execute(Task task) {
						project.getArtifacts().add("archives", remapSourcesJarTask.getOutput());
					}
				});
			}

			if (extension.isShareCaches()) {
				remapSourcesJarTask.setSourceRemapper(remapper);
			}

			parentTask.dependsOn(remapSourcesJarTask);
		} catch (UnknownTaskException ignored) {
			// pass
		}
	}
}
