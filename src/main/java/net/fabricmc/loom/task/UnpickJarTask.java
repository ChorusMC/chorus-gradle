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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.OutputFile;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.configuration.providers.LaunchProvider;
import net.fabricmc.loom.util.Constants;

public class UnpickJarTask extends JavaExec {
	File inputJar;
	File unpickDefinition;

	File outputJar;

	public UnpickJarTask() {
		getOutputs().upToDateWhen(e -> false);
		classpath(getProject().getConfigurations().getByName(Constants.Configurations.UNPICK_CLASSPATH));
		setMain("daomephsta.unpick.cli.Main");
	}

	@Override
	public void exec() {
		fileArg(getInputJar(), getOutputJar(), getUnpickDefinition());
		fileArg(getConstantJar());

		// Classpath
		fileArg(getExtension().getMinecraftMappedProvider().getMappedJar());
		fileArg(getMinecraftDependencies());

		writeUnpickLogConfig();
		systemProperty("java.util.logging.config.file", getExtension().getUnpickLoggingConfigFile().getAbsolutePath());

		super.exec();
	}

	private void writeUnpickLogConfig() {
		try (InputStream is = LaunchProvider.class.getClassLoader().getResourceAsStream("unpick-logging.properties")) {
			Files.deleteIfExists(getExtension().getUnpickLoggingConfigFile().toPath());
			Files.copy(is, getExtension().getUnpickLoggingConfigFile().toPath());
		} catch (IOException e) {
			throw new RuntimeException("Failed to copy unpick logging config", e);
		}
	}

	private File[] getMinecraftDependencies() {
		return getProject().getConfigurations().getByName(Constants.Configurations.MINECRAFT_DEPENDENCIES)
				.resolve().toArray(new File[0]);
	}

	private File getConstantJar() {
		return getProject().getConfigurations().getByName(Constants.Configurations.MAPPING_CONSTANTS).getSingleFile();
	}

	@InputFile
	public File getInputJar() {
		return inputJar;
	}

	public UnpickJarTask setInputJar(File inputJar) {
		this.inputJar = inputJar;
		return this;
	}

	@InputFile
	public File getUnpickDefinition() {
		return unpickDefinition;
	}

	public UnpickJarTask setUnpickDefinition(File unpickDefinition) {
		this.unpickDefinition = unpickDefinition;
		return this;
	}

	@OutputFile
	public File getOutputJar() {
		return outputJar;
	}

	public UnpickJarTask setOutputJar(File outputJar) {
		this.outputJar = outputJar;
		return this;
	}

	private void fileArg(File... files) {
		for (File file : files) {
			args(file.getAbsolutePath());
		}
	}

	@Internal
	protected LoomGradleExtension getExtension() {
		return getProject().getExtensions().getByType(LoomGradleExtension.class);
	}
}
