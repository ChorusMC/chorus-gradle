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

package net.fabricmc.loom;

import java.io.File;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.PluginAware;

public class LoomRepositoryPlugin implements Plugin<PluginAware> {
	@Override
	public void apply(PluginAware target) {
		RepositoryHandler repositories = null;

		if (target instanceof Settings settings) {
			repositories = settings.getDependencyResolutionManagement().getRepositories();

			// leave a marker so projects don't try to override these
			settings.getGradle().getPluginManager().apply(LoomRepositoryPlugin.class);
		} else if (target instanceof Project project) {
			if (project.getGradle().getPlugins().hasPlugin(LoomRepositoryPlugin.class)) {
				return;
			}

			repositories = project.getRepositories();
		} else if (target instanceof Gradle) {
			return;
		} else {
			throw new IllegalArgumentException("Expected target to be a Project or Settings, but was a " + target.getClass());
		}

		Cache cache = new Cache(target);

		// MavenConfiguration.java
		repositories.flatDir(repo -> {
			repo.setName("UserLocalCacheFiles");
			repo.dir(cache.getRootBuildCache());
		});
		repositories.maven(repo -> {
			repo.setName("UserLocalRemappedMods");
			repo.setUrl(cache.getRemappedModCache());
		});
		repositories.maven(repo -> {
			repo.setName("Fabric");
			repo.setUrl("https://maven.fabricmc.net/");
		});
		repositories.maven(repo -> {
			repo.setName("Mojang");
			repo.setUrl("https://libraries.minecraft.net/");
		});
		repositories.mavenCentral();

		// MinecraftMappedProvider.java
		repositories.ivy(repo -> {
			repo.setUrl(cache.getUserCache());
			repo.patternLayout(layout -> {
				layout.artifact("[revision]/[artifact]-[revision](-[classifier])(.[ext])");
			});
			repo.metadataSources(IvyArtifactRepository.MetadataSources::artifact);
		});

		// MinecraftProcessedProvider.java
		repositories.ivy(repo -> {
			repo.setUrl(cache.getRootPersistentCache());
			repo.patternLayout(layout -> {
				layout.artifact("[revision]/[artifact]-[revision](-[classifier])(.[ext])");
			});
			repo.metadataSources(IvyArtifactRepository.MetadataSources::artifact);
		});
	}
}

final class Cache {
	private PluginAware target;

	Cache(PluginAware target) {
		if (target instanceof Project || target instanceof Settings) {
			this.target = target;
		} else {
			throw new IllegalArgumentException("Expected target to be a Project or Settings, but was a " + target.getClass());
		}
	}

	File getUserCache() {
		File gradleUserHomeDir = null;

		if (target instanceof Settings settings) {
			gradleUserHomeDir = settings.getGradle().getGradleUserHomeDir();
		} else if (target instanceof Project project) {
			gradleUserHomeDir = project.getGradle().getGradleUserHomeDir();
		} else {
			throw new IllegalArgumentException("Expected target to be a Project or Settings, but was a " + target.getClass());
		}

		File userCache = new File(gradleUserHomeDir, "caches" + File.separator + "fabric-loom");

		if (!userCache.exists()) {
			userCache.mkdirs();
		}

		return userCache;
	}

	public File getRootPersistentCache() {
		File rootDir = null;

		if (target instanceof Settings settings) {
			rootDir = settings.getRootDir();
		} else if (target instanceof Project project) {
			rootDir = project.getRootDir();
		} else {
			throw new IllegalArgumentException("Expected target to be a Project or Settings, but was a " + target.getClass());
		}

		File persistentCache = new File(rootDir, ".gradle" + File.separator + "loom-cache");

		if (!persistentCache.exists()) {
			persistentCache.mkdirs();
		}

		return persistentCache;
	}

	public File getRootBuildCache() {
		File rootDir = null;

		if (target instanceof Settings settings) {
			rootDir = settings.getRootDir();
		} else if (target instanceof Project project) {
			rootDir = project.getRootDir();
		} else {
			throw new IllegalArgumentException("Expected target to be a Project or Settings, but was a " + target.getClass());
		}

		File buildCache = new File(rootDir, "build" + File.separator + "loom-cache");

		if (!buildCache.exists()) {
			buildCache.mkdirs();
		}

		return buildCache;
	}

	public File getRemappedModCache() {
		File remappedModCache = new File(getRootPersistentCache(), "remapped_mods");

		if (!remappedModCache.exists()) {
			remappedModCache.mkdir();
		}

		return remappedModCache;
	}

	public File getNestedModCache() {
		File nestedModCache = new File(getRootPersistentCache(), "nested_mods");

		if (!nestedModCache.exists()) {
			nestedModCache.mkdir();
		}

		return nestedModCache;
	}

	public File getNativesJarStore() {
		File natives = new File(getUserCache(), "natives/jars");

		if (!natives.exists()) {
			natives.mkdirs();
		}

		return natives;
	}
}
