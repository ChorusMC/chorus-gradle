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

public class OperatingSystem {
	public static String getOS() {
		String osName = System.getProperty("os.name").toLowerCase();

		if (osName.contains("win")) {
			return "windows";
		} else if (osName.contains("mac")) {
			return "osx";
		} else {
			return "linux";
		}
	}

	public static String getArch() {
		if (is64Bit()) {
			return "64";
		} else {
			return "32";
		}
	}

	public static boolean is64Bit() {
		return System.getProperty("sun.arch.data.model").contains("64");
	}

	public static boolean isWindows() {
		return getOS().equals("windows");
	}

	public static boolean isCIBuild() {
		String loomProperty = System.getProperty("fabric.loom.ci");

		if (loomProperty != null) {
			return loomProperty.equalsIgnoreCase("true");
		}

		// CI seems to be set by most popular CI services
		return System.getenv("CI") != null;
	}
}
