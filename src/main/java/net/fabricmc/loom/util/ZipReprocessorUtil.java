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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.attribute.FileTime;
import java.util.Calendar;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ZipReprocessorUtil {
	/**
	 * See {@link org.gradle.api.internal.file.archive.ZipCopyAction} about this.
	 */
	private static final long CONSTANT_TIME_FOR_ZIP_ENTRIES = new GregorianCalendar(1980, Calendar.FEBRUARY, 1, 0, 0, 0).getTimeInMillis();

	private ZipReprocessorUtil() { }

	public static void reprocessZip(File file, boolean reproducibleFileOrder, boolean preserveFileTimestamps) throws IOException {
		if (!reproducibleFileOrder && preserveFileTimestamps) {
			return;
		}

		try (ZipFile zipFile = new ZipFile(file)) {
			ZipEntry[] entries;

			if (reproducibleFileOrder) {
				entries = zipFile.stream().sorted(Comparator.comparing(ZipEntry::getName)).toArray(ZipEntry[]::new);
			} else {
				entries = zipFile.stream().toArray(ZipEntry[]::new);
			}

			ByteArrayOutputStream outZip = new ByteArrayOutputStream(zipFile.size());

			try (ZipOutputStream zipOutputStream = new ZipOutputStream(outZip)) {
				for (ZipEntry entry : entries) {
					ZipEntry newEntry = entry;

					if (!preserveFileTimestamps) {
						newEntry = new ZipEntry(entry.getName());
						newEntry.setTime(CONSTANT_TIME_FOR_ZIP_ENTRIES);
						newEntry.setLastModifiedTime(FileTime.fromMillis(CONSTANT_TIME_FOR_ZIP_ENTRIES));
						newEntry.setLastAccessTime(FileTime.fromMillis(CONSTANT_TIME_FOR_ZIP_ENTRIES));
					}

					zipOutputStream.putNextEntry(newEntry);
					InputStream inputStream = zipFile.getInputStream(entry);
					byte[] buf = new byte[1024];
					int length;

					while ((length = inputStream.read(buf)) > 0) {
						zipOutputStream.write(buf, 0, length);
					}

					zipOutputStream.closeEntry();
				}
			}

			try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
				outZip.writeTo(fileOutputStream);
			}
		}
	}
}
