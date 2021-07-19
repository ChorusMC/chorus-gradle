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

package net.fabricmc.loom.util;

public class Mirrors {
	public static String LIBRARIES_BASE_MIRRORS;
	public static String RESOURCES_BASE_MIRRORS;
	public static String VERSION_MANIFESTS;

	public static void changeMirror(String lib, String res, String manifest) {
		LIBRARIES_BASE_MIRRORS = lib;
		RESOURCES_BASE_MIRRORS = res;
		VERSION_MANIFESTS = manifest;
	}

	public static String getLibrariesBase() {
		return LIBRARIES_BASE_MIRRORS == null ? Constants.LIBRARIES_BASE : LIBRARIES_BASE_MIRRORS;
	}

	public static String getResourcesBase() {
		return RESOURCES_BASE_MIRRORS == null ? Constants.RESOURCES_BASE : RESOURCES_BASE_MIRRORS;
	}

	public static String getVersionManifests() {
		return VERSION_MANIFESTS == null ? Constants.VERSION_MANIFESTS : VERSION_MANIFESTS;
	}
}
