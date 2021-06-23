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

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import groovy.util.Node;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.GroovyXmlUtil;

public final class MavenPublication {
	private MavenPublication() {
	}

	public static void configure(Project project) {
		project.afterEvaluate((p) -> {
			for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
				if (!entry.hasMavenScope()) {
					continue;
				}

				Configuration compileModsConfig = p.getConfigurations().getByName(entry.sourceConfiguration());

				// add modsCompile to maven-publish
				PublishingExtension mavenPublish = p.getExtensions().findByType(PublishingExtension.class);

				if (mavenPublish != null) {
					processEntry(entry, compileModsConfig, mavenPublish);
				}
			}
		});
	}

	private static void processEntry(RemappedConfigurationEntry entry, Configuration compileModsConfig, PublishingExtension mavenPublish) {
		mavenPublish.publications((publications) -> {
			for (Publication publication : publications) {
				if (!(publication instanceof org.gradle.api.publish.maven.MavenPublication)) {
					continue;
				}

				((org.gradle.api.publish.maven.MavenPublication) publication).pom((pom) -> pom.withXml((xml) -> {
					Node dependencies = GroovyXmlUtil.getOrCreateNode(xml.asNode(), "dependencies");
					Set<String> foundArtifacts = new HashSet<>();

					GroovyXmlUtil.childrenNodesStream(dependencies).filter((n) -> "dependency".equals(n.name())).forEach((n) -> {
						Optional<Node> groupId = GroovyXmlUtil.getNode(n, "groupId");
						Optional<Node> artifactId = GroovyXmlUtil.getNode(n, "artifactId");

						if (groupId.isPresent() && artifactId.isPresent()) {
							foundArtifacts.add(groupId.get().text() + ":" + artifactId.get().text());
						}
					});

					for (Dependency dependency : compileModsConfig.getAllDependencies()) {
						if (foundArtifacts.contains(dependency.getGroup() + ":" + dependency.getName())) {
							continue;
						}

						Node depNode = dependencies.appendNode("dependency");
						depNode.appendNode("groupId", dependency.getGroup());
						depNode.appendNode("artifactId", dependency.getName());
						depNode.appendNode("version", dependency.getVersion());
						depNode.appendNode("scope", entry.mavenScope());

						if (!(dependency instanceof ModuleDependency)) {
							continue;
						}

						final Set<ExcludeRule> exclusions = ((ModuleDependency) dependency).getExcludeRules();

						if (exclusions.isEmpty()) {
							continue;
						}

						Node exclusionsNode = depNode.appendNode("exclusions");

						for (ExcludeRule rule : exclusions) {
							Node exclusionNode = exclusionsNode.appendNode("exclusion");
							exclusionNode.appendNode("groupId", rule.getGroup() == null ? "*" : rule.getGroup());
							exclusionNode.appendNode("artifactId", rule.getModule() == null ? "*" : rule.getModule());
						}
					}
				}));
			}
		});
	}
}
