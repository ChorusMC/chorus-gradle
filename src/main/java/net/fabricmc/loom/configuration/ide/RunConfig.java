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

package net.fabricmc.loom.configuration.ide;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.OperatingSystem;

public class RunConfig {
	public String configName;
	public String eclipseProjectName;
	public String ideaModuleName;
	public String mainClass;
	public String runDirIdeaUrl;
	public String runDir;
	public String vmArgs;
	public String programArgs;
	public SourceSet sourceSet;

	public Element genRuns(Element doc) {
		Element root = this.addXml(doc, "component", ImmutableMap.of("name", "ProjectRunConfigurationManager"));
		root = addXml(root, "configuration", ImmutableMap.of("default", "false", "name", configName, "type", "Application", "factoryName", "Application"));

		this.addXml(root, "module", ImmutableMap.of("name", ideaModuleName));
		this.addXml(root, "option", ImmutableMap.of("name", "MAIN_CLASS_NAME", "value", mainClass));
		this.addXml(root, "option", ImmutableMap.of("name", "WORKING_DIRECTORY", "value", runDirIdeaUrl));

		if (!Strings.isNullOrEmpty(vmArgs)) {
			this.addXml(root, "option", ImmutableMap.of("name", "VM_PARAMETERS", "value", vmArgs));
		}

		if (!Strings.isNullOrEmpty(programArgs)) {
			this.addXml(root, "option", ImmutableMap.of("name", "PROGRAM_PARAMETERS", "value", programArgs));
		}

		return root;
	}

	public Element addXml(Node parent, String name, Map<String, String> values) {
		Document doc = parent.getOwnerDocument();

		if (doc == null) {
			doc = (Document) parent;
		}

		Element e = doc.createElement(name);

		for (Map.Entry<String, String> entry : values.entrySet()) {
			e.setAttribute(entry.getKey(), entry.getValue());
		}

		parent.appendChild(e);
		return e;
	}

	private static String getIdeaModuleName(Project project, SourceSet srcs) {
		String module = project.getName() + "." + srcs.getName();

		while ((project = project.getParent()) != null) {
			module = project.getName() + "." + module;
		}

		return module;
	}

	private static void populate(Project project, LoomGradleExtension extension, RunConfig runConfig, String environment) {
		runConfig.configName += extension.isRootProject() ? "" : " (" + project.getPath() + ")";
		runConfig.eclipseProjectName = project.getExtensions().getByType(EclipseModel.class).getProject().getName();
		runConfig.vmArgs = "";
		runConfig.programArgs = "";

		if ("launchwrapper".equals(extension.getLoaderLaunchMethod())) {
			runConfig.mainClass = "net.minecraft.launchwrapper.Launch"; // TODO What about custom tweakers for run configs?
			runConfig.programArgs += "--tweakClass " + ("client".equals(environment) ? Constants.LaunchWrapper.DEFAULT_FABRIC_CLIENT_TWEAKER : Constants.LaunchWrapper.DEFAULT_FABRIC_SERVER_TWEAKER);
		} else {
			runConfig.mainClass = "net.fabricmc.devlaunchinjector.Main";
			runConfig.vmArgs = "-XX:+ShowCodeDetailsInExceptionMessages -Dfabric.dli.config=" + encodeEscaped(extension.getDevLauncherConfig().getAbsolutePath()) + " -Dfabric.dli.env=" + environment.toLowerCase();
		}

		if (extension.getLoaderLaunchMethod().equals("launchwrapper")) {
			// if installer.json found...
			JsonObject installerJson = extension.getInstallerJson();

			if (installerJson != null) {
				List<String> sideKeys = ImmutableList.of(environment, "common");

				// copy launchwrapper tweakers
				if (installerJson.has("launchwrapper")) {
					JsonObject launchwrapperJson = installerJson.getAsJsonObject("launchwrapper");

					if (launchwrapperJson.has("tweakers")) {
						JsonObject tweakersJson = launchwrapperJson.getAsJsonObject("tweakers");
						StringBuilder builder = new StringBuilder();

						for (String s : sideKeys) {
							if (tweakersJson.has(s)) {
								for (JsonElement element : tweakersJson.getAsJsonArray(s)) {
									builder.append(" --tweakClass ").append(element.getAsString());
								}
							}
						}

						runConfig.programArgs += builder.toString();
					}
				}
			}
		}
	}

	// Turns camelCase/PascalCase into Capital Case
	// caseConversionExample -> Case Conversion Example
	private static String capitalizeCamelCaseName(String name) {
		if (name.length() == 0) {
			return "";
		}

		return name.substring(0, 1).toUpperCase() + name.substring(1).replaceAll("([^A-Z])([A-Z])", "$1 $2");
	}

	public static RunConfig runConfig(Project project, RunConfigSettings settings) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		String name = settings.getName();

		String configName = settings.getConfigName();
		String environment = settings.getEnvironment();
		SourceSet sourceSet = settings.getSource(project);

		String defaultMain = settings.getDefaultMainClass();

		if (defaultMain == null) {
			throw new IllegalArgumentException("Run configuration '" + name + "' must specify 'defaultMainClass'");
		}

		if (configName == null) {
			configName = "";
			String srcName = sourceSet.getName();

			if (!srcName.equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
				configName += capitalizeCamelCaseName(srcName) + " ";
			}

			configName += "Minecraft " + capitalizeCamelCaseName(name);
		}

		Objects.requireNonNull(environment, "No environment set for run config");

		String runDir = settings.getRunDir();

		if (runDir == null) {
			runDir = "run";
		}

		RunConfig runConfig = new RunConfig();
		runConfig.configName = configName;
		populate(project, extension, runConfig, environment);
		runConfig.ideaModuleName = getIdeaModuleName(project, sourceSet);
		runConfig.runDirIdeaUrl = "file://$PROJECT_DIR$/" + runDir;
		runConfig.runDir = runDir;
		runConfig.sourceSet = sourceSet;

		// Custom parameters
		for (String progArg : settings.getProgramArgs()) {
			runConfig.programArgs += " " + progArg;
		}

		for (String vmArg : settings.getVmArgs()) {
			runConfig.vmArgs += " " + vmArg;
		}

		runConfig.vmArgs += " -Dfabric.dli.main=" + getMainClass(environment, extension, defaultMain);

		// Remove unnecessary leading/trailing whitespaces we might have generated
		runConfig.programArgs = runConfig.programArgs.trim();
		runConfig.vmArgs = runConfig.vmArgs.trim();

		return runConfig;
	}

	public String fromDummy(String dummy) throws IOException {
		String dummyConfig;

		try (InputStream input = SetupIntelijRunConfigs.class.getClassLoader().getResourceAsStream(dummy)) {
			dummyConfig = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}

		dummyConfig = dummyConfig.replace("%NAME%", configName);
		dummyConfig = dummyConfig.replace("%MAIN_CLASS%", mainClass);
		dummyConfig = dummyConfig.replace("%ECLIPSE_PROJECT%", eclipseProjectName);
		dummyConfig = dummyConfig.replace("%IDEA_MODULE%", ideaModuleName);
		dummyConfig = dummyConfig.replace("%RUN_DIRECTORY%", runDir);
		dummyConfig = dummyConfig.replace("%PROGRAM_ARGS%", programArgs.replaceAll("\"", "&quot;"));
		dummyConfig = dummyConfig.replace("%VM_ARGS%", vmArgs.replaceAll("\"", "&quot;"));

		return dummyConfig;
	}

	public static String getOSClientJVMArgs() {
		if (OperatingSystem.getOS().equalsIgnoreCase("osx")) {
			return " -XstartOnFirstThread";
		}

		return "";
	}

	private static String getMainClass(String side, LoomGradleExtension extension, String defaultMainClass) {
		JsonObject installerJson = extension.getInstallerJson();

		if (installerJson != null && installerJson.has("mainClass")) {
			JsonElement mainClassJson = installerJson.get("mainClass");

			String mainClassName = "";

			if (mainClassJson.isJsonObject()) {
				JsonObject mainClassesJson = mainClassJson.getAsJsonObject();

				if (mainClassesJson.has(side)) {
					mainClassName = mainClassesJson.get(side).getAsString();
				}
			} else {
				mainClassName = mainClassJson.getAsString();
			}

			return mainClassName;
		}

		// Fallback to default class names, happens when in a loader dev env
		if ("launchwrapper".equals(extension.getLoaderLaunchMethod())) {
			return "net.minecraft.launchwrapper.Launch";
		}

		return defaultMainClass;
	}

	private static String encodeEscaped(String s) {
		StringBuilder ret = new StringBuilder();

		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);

			if (c == '@' && i > 0 && s.charAt(i - 1) == '@' || c == ' ') {
				ret.append(String.format("@@%04x", (int) c));
			} else {
				ret.append(c);
			}
		}

		return ret.toString();
	}
}
