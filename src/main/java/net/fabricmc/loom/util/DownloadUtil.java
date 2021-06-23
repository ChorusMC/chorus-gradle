/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019 Chocohead
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import net.fabricmc.loom.LoomGradlePlugin;

public class DownloadUtil {
	/**
	 * Download from the given {@link URL} to the given {@link File} so long as there are differences between them.
	 *
	 * @param from The URL of the file to be downloaded
	 * @param to The destination to be saved to, and compared against if it exists
	 * @param logger The logger to print everything to, typically from {@link Project#getLogger()}
	 * @throws IOException If an exception occurs during the process
	 */
	public static void downloadIfChanged(URL from, File to, Logger logger) throws IOException {
		downloadIfChanged(from, to, logger, false);
	}

	/**
	 * Download from the given {@link URL} to the given {@link File} so long as there are differences between them.
	 *
	 * @param from The URL of the file to be downloaded
	 * @param to The destination to be saved to, and compared against if it exists
	 * @param logger The logger to print information to, typically from {@link Project#getLogger()}
	 * @param quiet Whether to only print warnings (when <code>true</code>) or everything
	 * @throws IOException If an exception occurs during the process
	 */
	public static void downloadIfChanged(URL from, File to, Logger logger, boolean quiet) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) from.openConnection();

		if (LoomGradlePlugin.refreshDeps) {
			getETagFile(to).delete();
			to.delete();
		}

		// If the output already exists we'll use it's last modified time
		if (to.exists()) {
			connection.setIfModifiedSince(to.lastModified());
		}

		//Try use the ETag if there's one for the file we're downloading
		String etag = loadETag(to, logger);

		if (etag != null) {
			connection.setRequestProperty("If-None-Match", etag);
		}

		// We want to download gzip compressed stuff
		connection.setRequestProperty("Accept-Encoding", "gzip");

		// Try make the connection, it will hang here if the connection is bad
		connection.connect();

		int code = connection.getResponseCode();

		if ((code < 200 || code > 299) && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
			//Didn't get what we expected
			delete(to);
			throw new IOException(connection.getResponseMessage() + " for " + from);
		}

		long modifyTime = connection.getHeaderFieldDate("Last-Modified", -1);

		if (to.exists() && (code == HttpURLConnection.HTTP_NOT_MODIFIED || modifyTime > 0 && to.lastModified() >= modifyTime)) {
			if (!quiet) {
				logger.info("'{}' Not Modified, skipping.", to);
			}

			return; //What we've got is already fine
		}

		long contentLength = connection.getContentLengthLong();

		if (!quiet && contentLength >= 0) {
			logger.info("'{}' Changed, downloading {}", to, toNiceSize(contentLength));
		}

		try { // Try download to the output
			FileUtils.copyInputStreamToFile(connection.getInputStream(), to);
		} catch (IOException e) {
			delete(to); // Probably isn't good if it fails to copy/save
			throw e;
		}

		//Set the modify time to match the server's (if we know it)
		if (modifyTime > 0) {
			to.setLastModified(modifyTime);
		}

		//Save the ETag (if we know it)
		String eTag = connection.getHeaderField("ETag");

		if (eTag != null) {
			//Log if we get a weak ETag and we're not on quiet
			if (!quiet && eTag.startsWith("W/")) {
				logger.warn("Weak ETag found.");
			}

			saveETag(to, eTag, logger);
		}
	}

	/**
	 * Creates a new file in the same directory as the given file with <code>.etag</code> on the end of the name.
	 *
	 * @param file The file to produce the ETag for
	 * @return The (uncreated) ETag file for the given file
	 */
	private static File getETagFile(File file) {
		return new File(file.getAbsoluteFile().getParentFile(), file.getName() + ".etag");
	}

	/**
	 * Attempt to load an ETag for the given file, if it exists.
	 *
	 * @param to The file to load an ETag for
	 * @param logger The logger to print errors to if it goes wrong
	 * @return The ETag for the given file, or <code>null</code> if it doesn't exist
	 */
	private static String loadETag(File to, Logger logger) {
		File eTagFile = getETagFile(to);

		if (!eTagFile.exists()) {
			return null;
		}

		try {
			return Files.asCharSource(eTagFile, StandardCharsets.UTF_8).read();
		} catch (IOException e) {
			logger.warn("Error reading ETag file '{}'.", eTagFile);
			return null;
		}
	}

	/**
	 * Saves the given ETag for the given file, replacing it if it already exists.
	 *
	 * @param to The file to save the ETag for
	 * @param eTag The ETag to be saved
	 * @param logger The logger to print errors to if it goes wrong
	 */
	private static void saveETag(File to, String eTag, Logger logger) {
		File eTagFile = getETagFile(to);

		try {
			if (!eTagFile.exists()) {
				eTagFile.createNewFile();
			}

			Files.asCharSink(eTagFile, StandardCharsets.UTF_8).write(eTag);
		} catch (IOException e) {
			logger.warn("Error saving ETag file '{}'.", eTagFile, e);
		}
	}

	/**
	 * Format the given number of bytes as a more human readable string.
	 *
	 * @param bytes The number of bytes
	 * @return The given number of bytes formatted to kilobytes, megabytes or gigabytes if appropriate
	 */
	public static String toNiceSize(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		} else if (bytes < 1024 * 1024) {
			return bytes / 1024 + " KB";
		} else if (bytes < 1024 * 1024 * 1024) {
			return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
		} else {
			return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
		}
	}

	/**
	 * Delete the file along with the corresponding ETag, if it exists.
	 *
	 * @param file The file to delete.
	 */
	public static void delete(File file) {
		if (file.exists()) {
			file.delete();
		}

		File etagFile = getETagFile(file);

		if (etagFile.exists()) {
			etagFile.delete();
		}
	}
}
