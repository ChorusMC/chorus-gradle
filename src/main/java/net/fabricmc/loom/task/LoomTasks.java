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

import com.google.common.base.Preconditions;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskContainer;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.loom.decompilers.fernflower.FabricFernFlowerDecompiler;

public final class LoomTasks {
	private LoomTasks() {
	}

	public static void registerTasks(Project project) {
		TaskContainer tasks = project.getTasks();

		tasks.register("migrateMappings", MigrateMappingsTask.class, t -> {
			t.setDescription("Migrates mappings to a new version.");
			t.getOutputs().upToDateWhen(o -> false);
		});

		tasks.register("remapJar", RemapJarTask.class, t -> {
			t.setDescription("Remaps the built project jar to intermediary mappings.");
			t.setGroup("fabric");
		});

		tasks.register("downloadAssets", DownloadAssetsTask.class, t -> t.setDescription("Downloads required assets for Fabric."));
		tasks.register("remapSourcesJar", RemapSourcesJarTask.class, t -> t.setDescription("Remaps the project sources jar to intermediary names."));

		registerIDETasks(tasks);
		registerRunTasks(tasks, project);
		registerDecompileTasks(tasks, project);
	}

	private static void registerIDETasks(TaskContainer tasks) {
		tasks.register("genIdeaWorkspace", GenIdeaProjectTask.class, t -> {
			t.setDescription("Generates an IntelliJ IDEA workspace from this project.");
			t.dependsOn("idea", "downloadAssets");
			t.setGroup("ide");
		});

		tasks.register("genEclipseRuns", GenEclipseRunsTask.class, t -> {
			t.setDescription("Generates Eclipse run configurations for this project.");
			t.dependsOn("downloadAssets");
			t.setGroup("ide");
		});

		tasks.register("cleanEclipseRuns", CleanEclipseRunsTask.class, t -> {
			t.setDescription("Removes Eclipse run configurations for this project.");
			t.setGroup("ide");
		});

		tasks.register("vscode", GenVsCodeProjectTask.class, t -> {
			t.setDescription("Generates VSCode launch configurations.");
			t.dependsOn("downloadAssets");
			t.setGroup("ide");
		});
	}

	private static void registerRunTasks(TaskContainer tasks, Project project) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);

		Preconditions.checkArgument(extension.getRunConfigs().size() == 0, "Run configurations must not be registered before loom");

		extension.getRunConfigs().whenObjectAdded(config -> {
			String configName = config.getName();
			String taskName = "run" + configName.substring(0, 1).toUpperCase() + configName.substring(1);

			tasks.register(taskName, RunGameTask.class, config).configure(t -> {
				t.setDescription("Starts the '" + config.getConfigName() + "' run configuration");
				t.setGroup("fabric");

				if (config.getEnvironment().equals("client")) {
					t.dependsOn("downloadAssets");
				}
			});
		});

		extension.getRunConfigs().create("client", RunConfigSettings::client);
		extension.getRunConfigs().create("server", RunConfigSettings::server);
	}

	private static void registerDecompileTasks(TaskContainer tasks, Project project) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);

		project.afterEvaluate(p -> {
			MappingsProviderImpl mappingsProvider = extension.getMappingsProvider();
			File inputJar = mappingsProvider.mappedProvider.getMappedJar();

			if (mappingsProvider.hasUnpickDefinitions()) {
				File outputJar = mappingsProvider.mappedProvider.getUnpickedJar();

				tasks.register("unpickJar", UnpickJarTask.class, unpickJarTask -> {
					unpickJarTask.setUnpickDefinition(mappingsProvider.getUnpickDefinitionsFile());
					unpickJarTask.setInputJar(mappingsProvider.mappedProvider.getMappedJar());
					unpickJarTask.setOutputJar(outputJar);
				});

				inputJar = outputJar;
			}

			for (LoomDecompiler decompiler : extension.getDecompilers()) {
				String taskName = decompiler instanceof FabricFernFlowerDecompiler ? "genSources" : "genSourcesWith" + decompiler.name();
				// decompiler will be passed to the constructor of GenerateSourcesTask
				GenerateSourcesTask generateSourcesTask = tasks.register(taskName, GenerateSourcesTask.class, decompiler).get();
				generateSourcesTask.setInputJar(inputJar);

				if (mappingsProvider.hasUnpickDefinitions()) {
					generateSourcesTask.dependsOn(tasks.getByName("unpickJar"));
				}
			}
		});
	}
}
