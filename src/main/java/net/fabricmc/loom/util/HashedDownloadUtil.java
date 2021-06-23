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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;

import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.gradle.api.logging.Logger;

import net.fabricmc.loom.LoomGradlePlugin;

public class HashedDownloadUtil {
	public static void downloadIfInvalid(URL from, File to, String expectedHash, Logger logger, boolean quiet) throws IOException {
		downloadIfInvalid(from, to, expectedHash, logger, quiet, () -> { });
	}

	public static void downloadIfInvalid(URL from, File to, String expectedHash, Logger logger, boolean quiet, Runnable startDownload) throws IOException {
		if (LoomGradlePlugin.refreshDeps) {
			delete(to);
		}

		String sha1 = getSha1(to, logger);

		if (expectedHash.equals(sha1)) {
			// The hash in the sha1 file matches
			return;
		}

		startDownload.run();

		HttpURLConnection connection = (HttpURLConnection) from.openConnection();
		connection.setRequestProperty("Accept-Encoding", "gzip");
		connection.connect();

		int code = connection.getResponseCode();

		if ((code < 200 || code > 299) && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
			//Didn't get what we expected
			delete(to);
			throw new IOException(connection.getResponseMessage() + " for " + from);
		}

		long contentLength = connection.getContentLengthLong();

		if (!quiet && contentLength >= 0) {
			logger.info("'{}' Changed, downloading {}", to, DownloadUtil.toNiceSize(contentLength));
		}

		try { // Try download to the output
			FileUtils.copyInputStreamToFile(connection.getInputStream(), to);
		} catch (IOException e) {
			delete(to); // Probably isn't good if it fails to copy/save
			throw e;
		}

		if (!Checksum.equals(to, expectedHash)) {
			String actualHash = Files.asByteSource(to).hash(Hashing.sha1()).toString();
			delete(to);

			throw new IOException(String.format("Downloaded file from %s to %s and got unexpected hash of %s expected %s", from, to, actualHash, expectedHash));
		}

		saveSha1(to, expectedHash, logger);
	}

	private static File getSha1File(File file) {
		return new File(file.getAbsoluteFile().getParentFile(), file.getName() + ".sha1");
	}

	@Nullable
	private static String getSha1(File to, Logger logger) {
		if (!to.exists()) {
			delete(to);
			return null;
		}

		File sha1File = getSha1File(to);

		try {
			return Files.asCharSource(sha1File, StandardCharsets.UTF_8).read();
		} catch (FileNotFoundException ignored) {
			// Quicker to catch this than do an exists check before.
			return null;
		} catch (IOException e) {
			logger.warn("Error reading sha1 file '{}'.", sha1File);
			return null;
		}
	}

	private static void saveSha1(File to, String sha1, Logger logger) {
		File sha1File = getSha1File(to);

		try {
			if (!sha1File.exists()) {
				sha1File.createNewFile();
			}

			Files.asCharSink(sha1File, StandardCharsets.UTF_8).write(sha1);
		} catch (IOException e) {
			logger.warn("Error saving sha1 file '{}'.", sha1File, e);
		}
	}

	public static void delete(File file) {
		if (file.exists()) {
			file.delete();
		}

		File sha1File = getSha1File(file);

		if (sha1File.exists()) {
			sha1File.delete();
		}
	}
}
