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

package net.fabricmc.loom.configuration.providers.minecraft;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.util.HashedDownloadUtil;

public class MinecraftNativesProvider {
	private final Project project;
	private final LoomGradleExtension extension;
	private final File nativesDir;
	private final File jarStore;

	public MinecraftNativesProvider(Project project) {
		this.project = project;
		extension = project.getExtensions().getByType(LoomGradleExtension.class);

		nativesDir = extension.getNativesDirectory();
		jarStore = extension.getNativesJarStore();
	}

	public static void provide(Project project) throws IOException {
		new MinecraftNativesProvider(project).provide();
	}

	private void provide() throws IOException {
		if (extension.hasCustomNatives()) {
			if (!nativesDir.exists()) {
				throw new RuntimeException("Could no find custom natives directory at " + nativesDir.getAbsolutePath());
			}

			return;
		}

		if (!LoomGradlePlugin.refreshDeps && !requiresExtract()) {
			project.getLogger().info("Natives do no need extracting, skipping");
			return;
		}

		extractNatives();
	}

	private void extractNatives() throws IOException {
		boolean offline = project.getGradle().getStartParameter().isOffline();

		if (nativesDir.exists()) {
			try {
				FileUtils.deleteDirectory(nativesDir);
			} catch (IOException e) {
				throw new IOException("Failed to delete the natives directory, is the game running?", e);
			}
		}

		nativesDir.mkdirs();

		for (MinecraftVersionMeta.Download library : getNatives()) {
			File libJarFile = library.relativeFile(jarStore);

			if (!offline) {
				HashedDownloadUtil.downloadIfInvalid(new URL(library.url()), libJarFile, library.sha1(), project.getLogger(), false);
			}

			if (!libJarFile.exists()) {
				throw new GradleException("Native jar not found at " + libJarFile.getAbsolutePath());
			}

			ZipUtil.unpack(libJarFile, nativesDir);

			// Store a file containing the hash of the extracted natives, used on subsequent runs to skip extracting all the natives if they haven't changed
			File libSha1File = new File(nativesDir, libJarFile.getName() + ".sha1");
			FileUtils.writeStringToFile(libSha1File, library.sha1(), StandardCharsets.UTF_8);
		}
	}

	private boolean requiresExtract() {
		List<MinecraftVersionMeta.Download> natives = getNatives();

		if (natives.isEmpty()) {
			throw new IllegalStateException("No natives found for the current system");
		}

		for (MinecraftVersionMeta.Download library : natives) {
			File libJarFile = library.relativeFile(jarStore);
			File libSha1File = new File(nativesDir, libJarFile.getName() + ".sha1");

			if (!libSha1File.exists()) {
				return true;
			}

			try {
				String sha1 = FileUtils.readFileToString(libSha1File, StandardCharsets.UTF_8);

				if (!sha1.equalsIgnoreCase(library.sha1())) {
					return true;
				}
			} catch (IOException e) {
				project.getLogger().error("Failed to read " + libSha1File.getAbsolutePath(), e);
				return true;
			}
		}

		// All looks good, no need to re-extract
		return false;
	}

	private List<MinecraftVersionMeta.Download> getNatives() {
		return extension.getMinecraftProvider().getVersionInfo().libraries().stream()
				.filter((MinecraftVersionMeta.Library::hasNativesForOS))
				.map(MinecraftVersionMeta.Library::classifierForOS)
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
	}
}
