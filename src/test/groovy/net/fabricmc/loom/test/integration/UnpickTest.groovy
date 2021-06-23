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

import net.fabricmc.loom.test.util.ProjectTestTrait
import org.zeroturnaround.zip.ZipUtil
import spock.lang.Specification

import java.nio.charset.StandardCharsets

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class UnpickTest extends Specification implements ProjectTestTrait {
	static final String MAPPINGS = "21w13a-mapped-net.fabricmc.yarn-21w13a+build.30-v2"

	@Override
	String name() {
		"unpick"
	}

	def "unpick decompile"() {
		when:
			def result = create("genSources")
		then:
			result.task(":genSources").outcome == SUCCESS
			getClassSource("net/minecraft/block/CakeBlock.java").contains("Block.DEFAULT_SET_BLOCK_STATE_FLAG")
	}

	def "unpick build"() {
		when:
			def result = create("build")
		then:
			result.task(":build").outcome == SUCCESS
	}

	String getClassSource(String classname, String mappings = MAPPINGS) {
		File sourcesJar = getGeneratedSources(mappings)
		return new String(ZipUtil.unpackEntry(sourcesJar, classname), StandardCharsets.UTF_8)
	}
}
