/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018, 2020 FabricMC
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

package net.fabricmc.loom.util;

import java.io.File;
import java.io.IOException;

import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.jetbrains.gradle.ext.ActionDelegationConfig;
import org.jetbrains.gradle.ext.Application;
import org.jetbrains.gradle.ext.ProjectSettings;
import org.jetbrains.gradle.ext.RunConfiguration;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MinecraftAssetsProvider;
import net.fabricmc.loom.providers.MinecraftNativesProvider;

public class SetupIdeaSettings {

	@SuppressWarnings("unchecked")
	public static void setup(Project project, boolean generateRuns) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);

		IdeaModel ideaModel = ((IdeaModel) project.getExtensions().findByName("idea"));

		if (ideaModel == null) return;

		if (ideaModel.getProject() != null) {
			ProjectSettings settings = ((ExtensionAware) ideaModel.getProject()).getExtensions().getByType(ProjectSettings.class);
			NamedDomainObjectContainer<RunConfiguration> runConfigurations = (NamedDomainObjectContainer<RunConfiguration>)
					((ExtensionAware) settings).getExtensions().getByName("runConfigurations");
			ActionDelegationConfig delegateActions = ((ExtensionAware) settings).getExtensions().getByType(ActionDelegationConfig.class);

			// Only apply these if no values are set
			if (delegateActions.getDelegateBuildRunToGradle() == null) {
				delegateActions.setDelegateBuildRunToGradle(false);
			}
			if (delegateActions.getTestRunner() == null) {
				delegateActions.setTestRunner(ActionDelegationConfig.TestRunner.PLATFORM);
			}

			if (generateRuns) {
				setupRunConfigurations(project, extension, runConfigurations);
			}

		}
	}

	private static void setupRunConfigurations(Project project, LoomGradleExtension extension, NamedDomainObjectContainer<RunConfiguration> runConfigurations) {
		try {
			generate(project, runConfigurations);
		} catch (IOException e) {
			throw new RuntimeException("Failed to generate run configs", e);
		}

		File runDir = new File(project.getRootDir(), extension.runDir);

		if (!runDir.exists()) {
			runDir.mkdirs();
		}
	}

	private static void generate(Project project, NamedDomainObjectCollection<RunConfiguration> runConfigurations) throws IOException {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);

		if (extension.ideSync()) {
			// Ensures the assets are downloaded when idea is syncing a project
			MinecraftAssetsProvider.provide(extension.getMinecraftProvider(), project);
			MinecraftNativesProvider.provide(extension.getMinecraftProvider(), project);
		}

		Application clientRunConfig = createApplication(RunConfig.clientRunConfig(project), project);
		Application serverRunConfig = createApplication(RunConfig.serverRunConfig(project), project);

		runConfigurations.add(clientRunConfig);
		runConfigurations.add(serverRunConfig);
	}

	private static Application createApplication(RunConfig template, Project project) {
		Application a = new Application(template.configName, project);
		a.setMainClass(template.mainClass);
		a.setModuleName(String.format("%s.main", template.projectName));
		a.setProgramParameters(template.programArgs);
		a.setJvmArgs(template.vmArgs);
		a.setWorkingDirectory("$PROJECT_DIR$/run/");
		return a;
	}

}
