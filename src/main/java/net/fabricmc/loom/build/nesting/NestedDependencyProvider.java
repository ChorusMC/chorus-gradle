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

package net.fabricmc.loom.build.nesting;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.task.RemapJarTask;
import net.fabricmc.loom.util.Constants;

public final class NestedDependencyProvider implements NestedJarProvider {
	final Project project;
	final List<DependencyInfo<?>> files;

	private NestedDependencyProvider(Project project, List<DependencyInfo<?>> files) {
		this.project = project;
		this.files = files;
	}

	public static NestedDependencyProvider createNestedDependencyProviderFromConfiguration(Project project, Configuration configuration) {
		List<DependencyInfo<?>> fileList = new ArrayList<>();
		Set<String> visited = new HashSet<>();

		fileList.addAll(populateProjectDependencies(configuration, visited));
		fileList.addAll(populateResolvedDependencies(configuration, visited));

		return new NestedDependencyProvider(project, fileList);
	}

	// Looks for any deps that require a sub project to be built first
	public static List<RemapJarTask> getRequiredTasks(Project project) {
		List<RemapJarTask> remapTasks = new ArrayList<>();

		Configuration configuration = project.getConfigurations().getByName(Constants.Configurations.INCLUDE);
		DependencySet dependencies = configuration.getDependencies();

		for (Dependency dependency : dependencies) {
			if (dependency instanceof ProjectDependency projectDependency) {
				Project dependencyProject = projectDependency.getDependencyProject();

				for (Task task : dependencyProject.getTasksByName("remapJar", false)) {
					if (task instanceof RemapJarTask remapJarTask) {
						remapTasks.add(remapJarTask);
					}
				}
			}
		}

		return remapTasks;
	}

	private static List<DependencyInfo<ProjectDependency>> populateProjectDependencies(Configuration configuration, Set<String> visited) {
		List<DependencyInfo<ProjectDependency>> fileList = new ArrayList<>();

		for (Dependency dependency : configuration.getDependencies()) {
			if (dependency instanceof ProjectDependency projectDependency) {
				Project dependencyProject = projectDependency.getDependencyProject();

				visited.add(dependency.getGroup() + ":" + dependency.getName() + ":" + dependency.getVersion());

				Collection<Task> remapJarTasks = dependencyProject.getTasksByName("remapJar", false);
				Collection<Task> jarTasks = dependencyProject.getTasksByName("jar", false);

				for (Task task : remapJarTasks.isEmpty() ? jarTasks : remapJarTasks) {
					if (task instanceof AbstractArchiveTask abstractArchiveTask) {
						fileList.add(new DependencyInfo<>(
								projectDependency,
								new ProjectDependencyMetaExtractor(),
								abstractArchiveTask.getArchiveFile().get().getAsFile(),
								abstractArchiveTask.getArchiveClassifier().getOrNull()
						));
					}
				}
			}
		}

		return fileList;
	}

	private static List<DependencyInfo<ResolvedDependency>> populateResolvedDependencies(Configuration configuration, Set<String> visited) {
		ResolvedConfiguration resolvedConfiguration = configuration.getResolvedConfiguration();
		Set<ResolvedDependency> dependencies = resolvedConfiguration.getFirstLevelModuleDependencies();

		List<DependencyInfo<ResolvedDependency>> fileList = new ArrayList<>();

		for (ResolvedDependency dependency : dependencies) {
			if (visited.contains(dependency.getModuleGroup() + ":" + dependency.getModuleName() + ":" + dependency.getModuleVersion())) {
				continue;
			}

			for (var artifact : dependency.getModuleArtifacts()) {
				fileList.add(new DependencyInfo<>(
						dependency,
						new ResolvedDependencyMetaExtractor(),
						artifact.getFile(),
						artifact.getClassifier()
				));
			}
		}

		return fileList;
	}

	@Override
	public List<File> provide() {
		List<File> fileList = new ArrayList<>();

		for (DependencyInfo<?> metaFile : files) {
			metaFile.validateInputs();

			File file = metaFile.file;

			//A lib that doesnt have a mod.json, we turn it into a fake mod
			if (!ZipUtil.containsEntry(file, "fabric.mod.json")) {
				LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
				File tempDir = new File(extension.getUserCache(), "temp/modprocessing");

				if (!tempDir.exists()) {
					tempDir.mkdirs();
				}

				File tempFile = new File(tempDir, file.getName());

				if (tempFile.exists()) {
					tempFile.delete();
				}

				try {
					FileUtils.copyFile(file, tempFile);
				} catch (IOException e) {
					throw new RuntimeException("Failed to copy file", e);
				}

				ZipUtil.addEntry(tempFile, "fabric.mod.json", generateModForDependency(metaFile).getBytes());
				fileList.add(tempFile);
			} else {
				// Default copy the jar right in
				fileList.add(file);
			}
		}

		return fileList;
	}

	// Generates a barebones mod for a dependency
	private static <D> String generateModForDependency(DependencyInfo<D> info) {
		DependencyMetaExtractor<D> metaExtractor = info.metaExtractor;
		D dependency = info.dependency;

		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("schemaVersion", 1);

		jsonObject.addProperty("id",
				(metaExtractor.group(dependency) + "_" + metaExtractor.name(dependency) + info.getClassifierSuffix())
						.replaceAll("\\.", "_")
						.toLowerCase(Locale.ENGLISH)
		);
		jsonObject.addProperty("version", metaExtractor.version(dependency));
		jsonObject.addProperty("name", metaExtractor.name(dependency));

		JsonObject custom = new JsonObject();
		custom.addProperty("fabric-loom:generated", true);
		jsonObject.add("custom", custom);

		return LoomGradlePlugin.GSON.toJson(jsonObject);
	}

	private record DependencyInfo<D>(D dependency, DependencyMetaExtractor<D> metaExtractor, File file, @Nullable String classifier) {
		void validateInputs() {
			if (!file.exists()) {
				throw new RuntimeException("Failed to include nested jars, as it could not be found @ " + file.getAbsolutePath());
			}

			if (file.isDirectory() || !file.getName().endsWith(".jar")) {
				throw new RuntimeException("Failed to include nested jars, as file was not a jar: " + file.getAbsolutePath());
			}
		}

		String getClassifierSuffix() {
			if (classifier == null) {
				return "";
			} else {
				return "_" + classifier;
			}
		}
	}

	private interface DependencyMetaExtractor<D> {
		String group(D dependency);

		String version(D dependency);

		String name(D dependency);
	}

	private static final class ProjectDependencyMetaExtractor implements DependencyMetaExtractor<ProjectDependency> {
		@Override
		public String group(ProjectDependency dependency) {
			return dependency.getGroup();
		}

		@Override
		public String version(ProjectDependency dependency) {
			return dependency.getVersion();
		}

		@Override
		public String name(ProjectDependency dependency) {
			return dependency.getName();
		}
	}

	private static final class ResolvedDependencyMetaExtractor implements DependencyMetaExtractor<ResolvedDependency> {
		@Override
		public String group(ResolvedDependency dependency) {
			return dependency.getModuleGroup();
		}

		@Override
		public String version(ResolvedDependency dependency) {
			return dependency.getModuleVersion();
		}

		@Override
		public String name(ResolvedDependency dependency) {
			return dependency.getModuleName();
		}
	}
}
