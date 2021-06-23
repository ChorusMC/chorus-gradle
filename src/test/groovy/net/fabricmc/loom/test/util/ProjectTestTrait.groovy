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

package net.fabricmc.loom.test.util

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

trait ProjectTestTrait {
	final static String DEFAULT_GRADLE = "7.0.1"
	final static String PRE_RELEASE_GRADLE = "7.2-20210612220215+0000"

	static File gradleHome = File.createTempDir()
	File testProjectDir = File.createTempDir()

	abstract String name()

	def copyInputFiles() {
		println "Project directory: ${testProjectDir}"

		def baseProjectDir = new File("src/test/resources/projects/" + name())

		if (!baseProjectDir.exists()) {
			throw new FileNotFoundException("Failed to find project directory at:" + baseProjectDir.absolutePath)
		}

		baseProjectDir.eachFileRecurse { file ->
			if (file.isDirectory()) {
				return
			}

			def path = file.path.replace(baseProjectDir.path, "")

			File tempFile = new File(testProjectDir, path)

			if (tempFile.exists()) {
				tempFile.delete()
			}

			tempFile.parentFile.mkdirs()
			tempFile << file.text
		}

		// Disable the CI checks to ensure nothing is skipped
		System.setProperty("fabric.loom.ci", "false")
	}

	@SuppressWarnings('unused')
	def cleanup() {
		// Clean after each test
		new File(testProjectDir, "build").deleteDir()
		new File(testProjectDir, ".gradle").deleteDir()
	}

	@SuppressWarnings('unused')
	def cleanupSpec() {
		testProjectDir.deleteDir()
		gradleHome.deleteDir()
	}

	BuildResult create(String task, String gradleVersion = DEFAULT_GRADLE) {
		System.setProperty("fabric.loom.test", "true")
		copyInputFiles()

		GradleRunner.create()
			.withProjectDir(testProjectDir)
			.withArguments(task, "--stacktrace", "--warning-mode", warningMode(gradleVersion), "--gradle-user-home", gradleHomeDirectory(gradleVersion))
			.withPluginClasspath()
			.withGradleVersion(gradleVersion)
			.forwardOutput()
			.withDebug(true)
			.build()
	}

	String warningMode(String gradleVersion) {
		'fail'
	}

	String gradleHomeDirectory(String gradleVersion) {
		// Each gradle version gets its own dir to run on, to ensure that a full run is done.
		new File(gradleHome, gradleVersion).absolutePath
	}

	File getOutputFile(String name, String project = "") {
		def file = new File(testProjectDir, "${project}build/libs/${name}")

		if (!file.exists()) {
			throw new FileNotFoundException("Could not find ${name} at ${file.absolutePath}")
		}

		return file
	}

	File getGeneratedSources(String mappings, String gradleVersion = DEFAULT_GRADLE) {
		return new File(gradleHomeDirectory(gradleVersion), "caches/fabric-loom/${mappings}/minecraft-${mappings}-sources.jar")
	}
}