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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.ide.RunConfig;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;

// Recommended vscode plugins:
// https://marketplace.visualstudio.com/items?itemName=redhat.java
// https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-debug
// https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack
public class GenVsCodeProjectTask extends AbstractLoomTask {
	@TaskAction
	public void genRuns() {
		Project project = getProject();
		File projectDir = project.file(".vscode");

		if (!projectDir.exists()) {
			projectDir.mkdir();
		}

		File launchJson = new File(projectDir, "launch.json");

		if (launchJson.exists()) {
			launchJson.delete();
		}

		VsCodeLaunch launch = new VsCodeLaunch();

		for (RunConfigSettings settings : getExtension().getRunConfigs()) {
			if (!settings.isIdeConfigGenerated()) {
				continue;
			}

			launch.add(RunConfig.runConfig(project, settings));
			settings.makeRunDir();
		}

		String json = LoomGradlePlugin.GSON.toJson(launch);

		try {
			FileUtils.writeStringToFile(launchJson, json, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException("Failed to write launch.json", e);
		}
	}

	private static class VsCodeLaunch {
		public String version = "0.2.0";
		public List<VsCodeConfiguration> configurations = new ArrayList<>();

		public void add(RunConfig runConfig) {
			configurations.add(new VsCodeConfiguration(runConfig));
		}
	}

	@SuppressWarnings("unused")
	private static class VsCodeConfiguration {
		public String type = "java";
		public String name;
		public String request = "launch";
		public String cwd;
		public String console = "internalConsole";
		public boolean stopOnEntry = false;
		public String mainClass;
		public String vmArgs;
		public String args;

		VsCodeConfiguration(RunConfig runConfig) {
			this.name = runConfig.configName;
			this.mainClass = runConfig.mainClass;
			this.vmArgs = runConfig.vmArgs;
			this.args = runConfig.programArgs;
			this.cwd = "${workspaceFolder}/" + runConfig.runDir;
		}
	}
}
