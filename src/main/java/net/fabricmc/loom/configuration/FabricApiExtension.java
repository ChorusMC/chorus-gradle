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

package net.fabricmc.loom.configuration;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.DownloadUtil;

public class FabricApiExtension {
	private final Project project;

	public FabricApiExtension(Project project) {
		this.project = project;
	}

	private static final HashMap<String, Map<String, String>> moduleVersionCache = new HashMap<>();

	public Dependency module(String moduleName, String fabricApiVersion) {
		return project.getDependencies()
				.create(getDependencyNotation(moduleName, fabricApiVersion));
	}

	public String moduleVersion(String moduleName, String fabricApiVersion) {
		String moduleVersion = moduleVersionCache
				.computeIfAbsent(fabricApiVersion, this::populateModuleVersionMap)
				.get(moduleName);

		if (moduleVersion == null) {
			throw new RuntimeException("Failed to find module version for module: " + moduleName);
		}

		return moduleVersion;
	}

	private String getDependencyNotation(String moduleName, String fabricApiVersion) {
		return String.format("net.fabricmc.fabric-api:%s:%s", moduleName, moduleVersion(moduleName, fabricApiVersion));
	}

	private Map<String, String> populateModuleVersionMap(String fabricApiVersion) {
		File pomFile = getApiMavenPom(fabricApiVersion);

		try {
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			Document pom = docBuilder.parse(pomFile);

			Map<String, String> versionMap = new HashMap<>();

			NodeList dependencies = ((Element) pom.getElementsByTagName("dependencies").item(0)).getElementsByTagName("dependency");

			for (int i = 0; i < dependencies.getLength(); i++) {
				Element dep = (Element) dependencies.item(i);
				Element artifact = (Element) dep.getElementsByTagName("artifactId").item(0);
				Element version = (Element) dep.getElementsByTagName("version").item(0);

				if (artifact == null || version == null) {
					throw new RuntimeException("Failed to find artifact or version");
				}

				versionMap.put(artifact.getTextContent(), version.getTextContent());
			}

			return versionMap;
		} catch (Exception e) {
			throw new RuntimeException("Failed to parse " + pomFile.getName(), e);
		}
	}

	private File getApiMavenPom(String fabricApiVersion) {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);

		File mavenPom = new File(extension.getUserCache(), "fabric-api/" + fabricApiVersion + ".pom");

		try {
			URL url = new URL(String.format("https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/%1$s/fabric-api-%1$s.pom", fabricApiVersion));
			DownloadUtil.downloadIfChanged(url, mavenPom, project.getLogger());
		} catch (IOException e) {
			throw new RuntimeException("Failed to download maven info for " + fabricApiVersion);
		}

		return mavenPom;
	}
}
