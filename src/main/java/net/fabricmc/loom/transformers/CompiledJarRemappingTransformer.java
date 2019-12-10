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

package net.fabricmc.loom.transformers;

import java.io.File;
import java.io.IOException;

import org.zeroturnaround.zip.commons.FileUtils;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.InputArtifactDependencies;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.Project;
import org.gradle.api.tasks.CompileClasspath;

import net.fabricmc.loom.transformers.parameters.ProjectReferencingParameters;
import net.fabricmc.loom.util.FabricModUtils;
import net.fabricmc.loom.util.FileNameUtils;
import net.fabricmc.loom.util.ModProcessor;

@CacheableTransform
public abstract class CompiledJarRemappingTransformer implements TransformAction<ProjectReferencingParameters> {
	@CompileClasspath
	@InputArtifact
	public abstract Provider<FileSystemLocation> getInput();

	@CompileClasspath
	@InputArtifactDependencies
	public abstract FileCollection getDependencies();

	@Override
	public void transform(final TransformOutputs outputs) {
		final FileSystemLocation resolvedInput = getInput().getOrNull();
		if (resolvedInput == null) {
			lifecycle("Can not transform a jar who has no input.");
			return;
		}

		final File inputFile = resolvedInput.getAsFile();
		if (!inputFile.exists()) {
			lifecycle("Can not transform jar who does not exists on disk.");
			return;
		}

        //Rudimentary check for sources. Which need to be remapped separately.
        //Potentially we need to check zip file contents.
        if (inputFile.getName().contains("-sources"))
        {
            getProject().getLogger().lifecycle("[Not remapping artifact, sources jar]: " + inputFile.getName());
            final File outputFile = outputs.file("not-remapped-sources" + File.separator + FileNameUtils.appendToNameBeForeExtension(inputFile, "-compiled_remapped"));
            try {
                FileUtils.copyFile(inputFile, outputFile);
            } catch (Exception e) {
                lifecycle("Failed to copy not remappable file to output.", e);
            }
            return;
        }

		if (!FabricModUtils.isFabricMod(
						getProject(),
						getProject().getLogger(),
						inputFile,
						inputFile.getName()
		)) {
            getProject().getLogger().lifecycle("[Not remapping artifact, no mod]: " + inputFile.getName());
			final File outputFile = outputs.file("not-remapped-no-mod" + File.separator + FileNameUtils.appendToNameBeForeExtension(inputFile, "-compiled_remapped"));
			try {
				FileUtils.copyFile(inputFile, outputFile);
			} catch (Exception e) {
				lifecycle("Failed to copy not remappable file to output.", e);
			}
            return;
		}

		try {
		    getProject().getLogger().lifecycle("[Remapping artifact]: " + inputFile.getName());
            ModProcessor.processMod(getProject(), inputFile, (fileName) -> outputs.file(String.format("remapped%s%s", File.separator, FileNameUtils.appendToNameBeForeExtension(fileName, "-compiled_remapped"))), getDependencies());
		} catch (IOException e) {
			lifecycle("Failed to remap file.", e);
		}
	}

	/**
	 * Short circuit method to handle logging a lifecycle message to the projects logger.
	 *
	 * @param message The message to log, formatted.
	 * @param objects The formatting parameters.
	 */
	private void lifecycle(String message, Object... objects) {
		getProject().getLogger().lifecycle("[ContainedJarRemapping]: " + message, objects);
	}

	/**
	 * Short circuit method to handle logging a lifecycle message to the projects logger.
	 *
	 * @param message   The message to log, formatted.
	 * @param exception The exception to log.
	 */
	private void lifecycle(String message, Throwable exception) {
		getProject().getLogger().lifecycle("[ContainedJarRemapping]: " + message, exception);
	}

	/**
	 * Returns the project that is referenced by the parameters.
	 *
	 * @return The project that this transformer is operating on.
	 */
	private Project getProject() {
		return TransformerProjectManager.getInstance().get(getParameters().getProjectPathParameter().get());
	}
}
