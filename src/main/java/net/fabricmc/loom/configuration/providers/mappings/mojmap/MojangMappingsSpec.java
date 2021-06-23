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

package net.fabricmc.loom.configuration.providers.mappings.mojmap;

import net.fabricmc.loom.configuration.providers.mappings.MappingContext;
import net.fabricmc.loom.configuration.providers.mappings.MappingsSpec;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;

public record MojangMappingsSpec() implements MappingsSpec<MojangMappingLayer> {
	// Keys in dependency manifest
	private static final String MANIFEST_CLIENT_MAPPINGS = "client_mappings";
	private static final String MANIFEST_SERVER_MAPPINGS = "server_mappings";

	@Override
	public MojangMappingLayer createLayer(MappingContext context) {
		MinecraftVersionMeta versionInfo = context.minecraftProvider().getVersionInfo();

		if (versionInfo.download(MANIFEST_CLIENT_MAPPINGS) == null) {
			throw new RuntimeException("Failed to find official mojang mappings for " + context.minecraftVersion());
		}

		return new MojangMappingLayer(
				versionInfo.download(MANIFEST_CLIENT_MAPPINGS),
				versionInfo.download(MANIFEST_SERVER_MAPPINGS),
				context.workingDirectory("mojang"),
				context.getLogger()
		);
	}
}
