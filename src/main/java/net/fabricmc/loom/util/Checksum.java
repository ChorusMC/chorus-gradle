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

import java.io.File;
import java.io.IOException;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public class Checksum {
	private static final Logger log = Logging.getLogger(Checksum.class);

	public static boolean equals(File file, String checksum) {
		if (file == null || !file.exists()) {
			return false;
		}

		try {
			HashCode hash = Files.asByteSource(file).hash(Hashing.sha1());
			log.debug("Checksum check: '" + hash.toString() + "' == '" + checksum + "'?");
			return hash.toString().equals(checksum);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return false;
	}

	public static byte[] sha256(File file) {
		try {
			HashCode hash = Files.asByteSource(file).hash(Hashing.sha256());
			return hash.asBytes();
		} catch (IOException e) {
			throw new RuntimeException("Failed to get file hash");
		}
	}
}
