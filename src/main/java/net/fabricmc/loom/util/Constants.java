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

import java.util.List;

import com.google.common.collect.ImmutableList;

public class Constants {
	public static final String DEFAULT_FABRIC_CLIENT_TWEAKER = "net.fabricmc.loader.launch.FabricClientTweaker";
	public static final String DEFAULT_FABRIC_SERVER_TWEAKER = "net.fabricmc.loader.launch.FabricServerTweaker";

	public static final String LIBRARIES_BASE = "https://libraries.minecraft.net/";
	public static final String RESOURCES_BASE = "http://resources.download.minecraft.net/";

	public static final String SYSTEM_ARCH = System.getProperty("os.arch").equals("64") ? "64" : "32";

	public static final String INCLUDE = "include";
	public static final String MINECRAFT = "minecraft";
	public static final String MINECRAFT_DEPENDENCIES = "minecraftLibraries";
	public static final String MINECRAFT_INTERMEDIARY = "minecraftIntermediary";
	public static final String MINECRAFT_NAMED = "minecraftNamed";
	public static final String MINECRAFT_LINEMAPPED = "minecraftLinemapped";
	public static final String MAPPINGS = "mappings";
	public static final String MAPPINGS_FINAL = "mappingsFinal";

	public static final String MIXIN_COMPILE_EXTENSIONS_VERSION = "0.3.0.4";
	public static final String DEV_LAUNCH_INJECTOR_VERSION = "0.1.0+build.3";
}
