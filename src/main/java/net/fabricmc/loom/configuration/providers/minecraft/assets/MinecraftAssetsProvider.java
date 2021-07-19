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

package net.fabricmc.loom.configuration.providers.minecraft.assets;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.providers.MinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Mirrors;
import net.fabricmc.loom.util.HashedDownloadUtil;
import net.fabricmc.loom.util.gradle.ProgressLogger;

public class MinecraftAssetsProvider {
	public static void provide(MinecraftProvider minecraftProvider, Project project) throws IOException {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		boolean offline = project.getGradle().getStartParameter().isOffline();

		MinecraftVersionMeta versionInfo = minecraftProvider.getVersionInfo();
		MinecraftVersionMeta.AssetIndex assetIndex = versionInfo.getAssetIndex();

		// get existing cache files
		File assets = new File(extension.getUserCache(), "assets");

		if (!assets.exists()) {
			assets.mkdirs();
		}

		File assetsInfo = new File(assets, "indexes" + File.separator + assetIndex.getFabricId(minecraftProvider.getMinecraftVersion()) + ".json");

		project.getLogger().info(":downloading asset index");

		if (offline) {
			if (assetsInfo.exists()) {
				//We know it's outdated but can't do anything about it, oh well
				project.getLogger().warn("Asset index outdated");
			} else {
				//We don't know what assets we need, just that we don't have any
				throw new GradleException("Asset index not found at " + assetsInfo.getAbsolutePath());
			}
		} else {
			HashedDownloadUtil.downloadIfInvalid(new URL(assetIndex.getUrl()), assetsInfo, assetIndex.getSha1(), project.getLogger(), false);
		}

		Deque<ProgressLogger> loggers = new ConcurrentLinkedDeque<>();
		ExecutorService executor = Executors.newFixedThreadPool(Math.min(10, Math.max(Runtime.getRuntime().availableProcessors() / 2, 1)));

		AssetIndex index;

		try (FileReader fileReader = new FileReader(assetsInfo)) {
			index = LoomGradlePlugin.GSON.fromJson(fileReader, AssetIndex.class);
		}

		Stopwatch stopwatch = Stopwatch.createStarted();

		Map<String, AssetObject> parent = index.getFileMap();

		for (Map.Entry<String, AssetObject> entry : parent.entrySet()) {
			AssetObject object = entry.getValue();
			String sha1 = object.getHash();
			String filename = "objects" + File.separator + sha1.substring(0, 2) + File.separator + sha1;
			File file = new File(assets, filename);

			if (offline) {
				if (file.exists()) {
					project.getLogger().warn("Outdated asset " + entry.getKey());
				} else {
					throw new GradleException("Asset " + entry.getKey() + " not found at " + file.getAbsolutePath());
				}
			} else {
				executor.execute(() -> {
					final String[] assetName = {entry.getKey()};
					int end = assetName[0].lastIndexOf("/") + 1;

					if (end > 0) {
						assetName[0] = assetName[0].substring(end);
					}

					project.getLogger().debug("validating asset " + assetName[0]);

					final ProgressLogger[] progressLogger = new ProgressLogger[1];

					try {
						HashedDownloadUtil.downloadIfInvalid(new URL(Mirrors.getResourcesBase() + sha1.substring(0, 2) + "/" + sha1), file, sha1, project.getLogger(), true, () -> {
							ProgressLogger logger = loggers.pollFirst();

							if (logger == null) {
								//Create a new logger if we need one
								progressLogger[0] = ProgressLogger.getProgressFactory(project, MinecraftAssetsProvider.class.getName());
								progressLogger[0].start("Downloading assets...", "assets");
							} else {
								// use a free logger if we can
								progressLogger[0] = logger;
							}

							project.getLogger().debug("downloading asset " + assetName[0]);
							progressLogger[0].progress(String.format("%-30.30s", assetName[0]) + " - " + sha1);
						});
					} catch (IOException e) {
						throw new RuntimeException("Failed to download: " + assetName[0], e);
					}

					if (progressLogger[0] != null) {
						//Give this logger back if we used it
						loggers.add(progressLogger[0]);
					}
				});
			}
		}

		project.getLogger().info("Took " + stopwatch.stop() + " to iterate " + parent.size() + " asset index.");

		//Wait for the assets to all download
		executor.shutdown();

		try {
			if (executor.awaitTermination(2, TimeUnit.HOURS)) {
				executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		loggers.forEach(ProgressLogger::completed);
	}
}
