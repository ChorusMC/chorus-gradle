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

package net.fabricmc.loom.test.integration

import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.google.common.io.Files
import net.fabricmc.loom.test.util.ProjectTestTrait
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class ReproducibleBuildTest extends Specification implements ProjectTestTrait {
	@Override
	String name() {
		"reproducible"
	}

	@Unroll
	def "build (gradle #gradle)"() {
		when:
			def result = create("build", gradle)
		then:
			result.task(":build").outcome == SUCCESS
			getOutputHash("fabric-example-mod-1.0.0.jar") == modHash
			getOutputHash("fabric-example-mod-1.0.0-sources.jar") in sourceHash // Done for different line endings.
		where:
			gradle              | modHash                               | sourceHash
			DEFAULT_GRADLE      | "6132ffb4117adb7e258f663110552952"    | ["be31766e6cafbe4ae3bca9e35ba63169", "7348b0bd87d36d7ec6f3bca9c2b66062"]
			PRE_RELEASE_GRADLE  | "6132ffb4117adb7e258f663110552952"    | ["be31766e6cafbe4ae3bca9e35ba63169", "7348b0bd87d36d7ec6f3bca9c2b66062"]
	}

	String getOutputHash(String name) {
		generateMD5(getOutputFile(name))
	}

	String generateMD5(File file) {
		HashCode hash = Files.asByteSource(file).hash(Hashing.md5())
		return hash.asBytes().encodeHex() as String
	}
}
