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

package net.fabricmc.loom.util;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.compile.JavaCompile;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.util.DependencyProvider.DependencyInfo;

public class LoomDependencyManager {
	private static class ProviderList {
		private final String key;
		private final List<DependencyProvider> providers = new ArrayList<>();

		ProviderList(String key) {
			this.key = key;
		}
	}

	private List<DependencyProvider> dependencyProviderList = new ArrayList<>();

	public void addProvider(DependencyProvider provider) {
		if (dependencyProviderList.contains(provider)) {
			throw new RuntimeException("Provider is already registered");
		}

		if (getProvider(provider.getClass()) != null) {
			throw new RuntimeException("Provider of this type is already registered");
		}

		provider.register(this);
		dependencyProviderList.add(provider);
	}

	public <T> T getProvider(Class<T> clazz) {
		for (DependencyProvider provider : dependencyProviderList) {
			if (provider.getClass() == clazz) {
				return (T) provider;
			}
		}

		return null;
	}

	public void handleDependencies(Project project) {
		List<Runnable> afterTasks = new ArrayList<>();

		MappingsProvider mappingsProvider = null;

		project.getLogger().lifecycle(":setting up loom dependencies");
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		Map<String, ProviderList> providerListMap = new HashMap<>();
		List<ProviderList> targetProviders = new ArrayList<>();

		for (DependencyProvider provider : dependencyProviderList) {
			providerListMap.computeIfAbsent(provider.getTargetConfig(), (k) -> {
				ProviderList list = new ProviderList(k);
				targetProviders.add(list);
				return list;
			}).providers.add(provider);

			if (provider instanceof MappingsProvider) {
				mappingsProvider = (MappingsProvider) provider;
			}
		}

		if (mappingsProvider == null) {
			throw new RuntimeException("Could not find MappingsProvider instance!");
		}

		for (ProviderList list : targetProviders) {
			Configuration configuration = project.getConfigurations().getByName(list.key);
			configuration.getDependencies().forEach(dependency -> {
				for (DependencyProvider provider : list.providers) {
					DependencyProvider.DependencyInfo info = DependencyInfo.create(project, dependency, configuration);

					try {
						provider.provide(info, project, extension, afterTasks::add);
					} catch (Exception e) {
						throw new RuntimeException("Failed to provide " + dependency.getGroup() + ":" + dependency.getName() + ":" + dependency.getVersion(), e);
					}
				}
			});
		}

		if (extension.getInstallerJson() == null) {
			//If we've not found the installer JSON we've probably skipped remapping Fabric loader, let's go looking
			project.getLogger().info("Searching through modCompileClasspath for installer JSON");
			CheatyFabricInstallerFinder.handleFabricInstaller(project);
		}

		if (extension.getInstallerJson() != null) {
			handleInstallerJson(extension.getInstallerJson(), project);
		} else {
			project.getLogger().warn("fabric-installer.json not found in classpath!");
		}

		for (Runnable runnable : afterTasks) {
			runnable.run();
		}
	}

	private static void handleInstallerJson(JsonObject jsonObject, Project project) {
		JsonObject libraries = jsonObject.get("libraries").getAsJsonObject();
		Configuration mcDepsConfig = project.getConfigurations().getByName(Constants.MINECRAFT_DEPENDENCIES);
		Configuration apDepsConfig = project.getConfigurations().getByName("annotationProcessor");

		libraries.get("common").getAsJsonArray().forEach(jsonElement -> {
			String name = jsonElement.getAsJsonObject().get("name").getAsString();

			ExternalModuleDependency modDep = (ExternalModuleDependency) project.getDependencies().create(name);
			modDep.setTransitive(false);
			mcDepsConfig.getDependencies().add(modDep);
			apDepsConfig.getDependencies().add(modDep);

			project.getLogger().debug("Loom adding " + name + " from installer JSON");

			if (jsonElement.getAsJsonObject().has("url")) {
				String url = jsonElement.getAsJsonObject().get("url").getAsString();
				long count = project.getRepositories().stream().filter(artifactRepository -> artifactRepository instanceof MavenArtifactRepository)
						.map(artifactRepository -> (MavenArtifactRepository) artifactRepository)
						.filter(mavenArtifactRepository -> mavenArtifactRepository.getUrl().toString().equalsIgnoreCase(url)).count();

				if (count == 0) {
				    project.getLogger().lifecycle("[FabricInstaller]: Adding: " + url + " as repository for installer dependencies.");
					project.getRepositories().maven(mavenArtifactRepository -> mavenArtifactRepository.setUrl(jsonElement.getAsJsonObject().get("url").getAsString()));
				}
			}
		});
	}
}
