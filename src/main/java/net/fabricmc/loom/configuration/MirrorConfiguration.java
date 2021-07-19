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

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Mirrors;

import org.gradle.api.Project;

public class MirrorConfiguration {
	public static void setup(Project project){
		if (!(project.hasProperty("loom_libraries_base")
				|| project.hasProperty("loom_resources_base")
		        || project.hasProperty("loom_version_manifests"))){
			return;
		}

		String LIBRARIES_BASE_MIRRORS = project.hasProperty("loom_libraries_base")
				? String.valueOf(project.property("loom_libraries_base")) : Constants.LIBRARIES_BASE;
		String RESOURCES_BASE_MIRRORS = project.hasProperty("loom_resources_base")
				? String.valueOf(project.property("loom_resources_base")) : Constants.RESOURCES_BASE;
		String VERSION_MANIFESTS = project.hasProperty("loom_version_manifests")
				? String.valueOf(project.property("loom_version_manifests")) : Constants.VERSION_MANIFESTS;

		Mirrors.changeMirror(LIBRARIES_BASE_MIRRORS
				,RESOURCES_BASE_MIRRORS
				,VERSION_MANIFESTS);
	}
}
