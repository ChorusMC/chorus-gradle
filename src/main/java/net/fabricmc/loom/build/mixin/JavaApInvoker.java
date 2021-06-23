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

package net.fabricmc.loom.build.mixin;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.compile.JavaCompile;

public class JavaApInvoker extends AnnotationProcessorInvoker<JavaCompile> {
	public JavaApInvoker(Project project) {
		super(project, getConfigurations(project), project.getTasks().withType(JavaCompile.class));
	}

	@Override
	protected void passArgument(JavaCompile compileTask, String key, String value) {
		compileTask.getOptions().getCompilerArgs().add("-A" + key + "=" + value);
	}

	@Override
	protected File getDestinationDir(JavaCompile task) {
		return task.getDestinationDir();
	}

	private static List<Configuration> getConfigurations(Project project) {
		// java plugin generates an AP configuration for every source set based off of the getAptConfigurationName method.
		return AnnotationProcessorInvoker.getNonTestSourceSets(project)
						.map(sourceSet -> project.getConfigurations()
										.getByName(getAptConfigurationName(sourceSet.getName()))
						).collect(Collectors.toList());
	}

	private static String getAptConfigurationName(String sourceSet) {
		// This is documented by the gradle 4.6 release notes https://docs.gradle.org/4.6/release-notes.html#potential-breaking-changes
		return sourceSet.equals("main") ? JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME : sourceSet + "AnnotationProcessor";
	}
}
