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

package net.fabricmc.loom.configuration.processors;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;

import net.fabricmc.loom.configuration.providers.MinecraftProviderImpl;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftMappedProvider;
import net.fabricmc.loom.util.Constants;

public class MinecraftProcessedProvider extends MinecraftMappedProvider {
	public final String projectMappedClassifier;
	public final String projectMappedCompileClassifier;
	public final String projectMappedRuntimeClassifier;

	private File projectMappedCommonJar;
	private File projectMappedCompileJar;
	private File projectMappedRuntimeJar;

	private boolean split;

	private final JarProcessorManager jarProcessorManager;

	public MinecraftProcessedProvider(Project project, JarProcessorManager jarProcessorManager) {
		super(project);
		this.jarProcessorManager = jarProcessorManager;
		this.projectMappedClassifier = "project-" + project.getPath().replace(':', '@')
				+ "-mapped";
		this.projectMappedCompileClassifier = this.projectMappedClassifier + "-compile";
		this.projectMappedRuntimeClassifier = this.projectMappedClassifier + "-runtime";
	}

	@Override
	protected void addDependencies(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) {
		boolean invalid = this.split
				? this.jarProcessorManager.isInvalid(Environment.COMPILE, this.projectMappedCompileJar) || this.jarProcessorManager.isInvalid(Environment.RUNTIME, this.projectMappedRuntimeJar)
				: this.jarProcessorManager.isInvalid(Environment.BOTH, this.projectMappedCommonJar);

		if (invalid || isRefreshDeps()) {
			getProject().getLogger().lifecycle(":processing mapped jar");
			invalidateJars();

			try {
				File mappedJar = super.getMappedJar();
				File firstProcessedJar = this.getMappedJar();

				FileUtils.copyFile(mappedJar, firstProcessedJar);

				this.jarProcessorManager.process(Environment.BOTH, firstProcessedJar);

				if (this.split) {
					FileUtils.copyFile(firstProcessedJar, this.projectMappedRuntimeJar);

					this.jarProcessorManager.process(Environment.COMPILE, this.projectMappedCompileJar);
					this.jarProcessorManager.process(Environment.RUNTIME, this.projectMappedRuntimeJar);
				}
			} catch (IOException e) {
				String message = "Failed to copy or process source jar";

				if (this.split) {
					message += "s";
				}

				throw new UncheckedIOException(message, e);
			}
		}

		String compileClassifier = this.split ? projectMappedCompileClassifier : projectMappedClassifier;
		String runtimeClassifier = this.split ? projectMappedRuntimeClassifier : projectMappedClassifier;

		getProject().getDependencies().add(Constants.Configurations.MINECRAFT_NAMED_COMPILE,
				getProject().getDependencies().module("net.minecraft:minecraft:" + getJarVersionString(compileClassifier))
		);
		getProject().getDependencies().add(Constants.Configurations.MINECRAFT_NAMED_RUNTIME,
				getProject().getDependencies().module("net.minecraft:minecraft:" + getJarVersionString(runtimeClassifier))
		);
	}

	private void invalidateJars() {
		File dir = getJarDirectory(getExtension().getUserCache(), projectMappedClassifier);

		if (dir.exists()) {
			getProject().getLogger().warn("Invalidating project jars");

			try {
				FileUtils.cleanDirectory(dir);
			} catch (IOException e) {
				throw new RuntimeException("Failed to invalidate jars, try stopping gradle daemon or closing the game", e);
			}
		}
	}

	@Override
	public void initFiles(MinecraftProviderImpl minecraftProvider, MappingsProviderImpl mappingsProvider) {
		super.initFiles(minecraftProvider, mappingsProvider);

		this.projectMappedCommonJar = new File(getJarDirectory(getExtension().getRootProjectPersistentCache(), projectMappedClassifier), "minecraft-" + getJarVersionString(projectMappedClassifier) + ".jar");
		this.projectMappedCompileJar = new File(getJarDirectory(getExtension().getRootProjectPersistentCache(), projectMappedClassifier), "minecraft-" + getJarVersionString(projectMappedCompileClassifier) + ".jar");
		this.projectMappedRuntimeJar = new File(getJarDirectory(getExtension().getRootProjectPersistentCache(), projectMappedClassifier), "minecraft-" + getJarVersionString(projectMappedRuntimeClassifier) + ".jar");

		this.split = this.jarProcessorManager.hasEnvironment(Environment.COMPILE) || this.jarProcessorManager.hasEnvironment(Environment.RUNTIME);
	}

	@Override
	public File getMappedJar() {
		return this.split ? this.projectMappedCompileJar : this.projectMappedCommonJar;
	}

	public File getMappedCompileJar() {
		return this.projectMappedCompileJar;
	}

	public File getMappedRuntimeJar() {
		return this.projectMappedRuntimeJar;
	}
}
