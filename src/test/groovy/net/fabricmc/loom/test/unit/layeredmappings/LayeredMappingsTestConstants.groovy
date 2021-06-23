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

package net.fabricmc.loom.test.unit.layeredmappings

import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta

interface LayeredMappingsTestConstants {
    public static final String INTERMEDIARY_1_17_URL = "https://maven.fabricmc.net/net/fabricmc/intermediary/1.17/intermediary-1.17-v2.jar"
    public static final String INTERMEDIARY_1_16_5_URL = "https://maven.fabricmc.net/net/fabricmc/intermediary/1.16.5/intermediary-1.16.5-v2.jar"

    public static final Map<String, MinecraftVersionMeta.Download> DOWNLOADS_1_17 = [
            client_mappings:new MinecraftVersionMeta.Download(null, "227d16f520848747a59bef6f490ae19dc290a804", 6431705, "https://launcher.mojang.com/v1/objects/227d16f520848747a59bef6f490ae19dc290a804/client.txt"),
            server_mappings:new MinecraftVersionMeta.Download(null, "84d80036e14bc5c7894a4fad9dd9f367d3000334", 4948536, "https://launcher.mojang.com/v1/objects/84d80036e14bc5c7894a4fad9dd9f367d3000334/server.txt")
    ]
    public static final MinecraftVersionMeta VERSION_META_1_17 = new MinecraftVersionMeta(null, null, null, 0, DOWNLOADS_1_17, null, null, null, null, 0, null, null, null)

    public static final Map<String, MinecraftVersionMeta.Download> DOWNLOADS_1_16_5 = [
            client_mappings:new MinecraftVersionMeta.Download(null, "e3dfb0001e1079a1af72ee21517330edf52e6192", 5746047, "https://launcher.mojang.com/v1/objects/e3dfb0001e1079a1af72ee21517330edf52e6192/client.txt"),
            server_mappings:new MinecraftVersionMeta.Download(null, "81d5c793695d8cde63afddb40dde88e3a88132ac", 4400926, "https://launcher.mojang.com/v1/objects/81d5c793695d8cde63afddb40dde88e3a88132ac/server.txt")
    ]
    public static final MinecraftVersionMeta VERSION_META_1_16_5 = new MinecraftVersionMeta(null, null, null, 0, DOWNLOADS_1_16_5, null, null, null, null, 0, null, null, null)

    public static final String PARCHMENT_NOTATION = "org.parchmentmc.data:parchment-1.16.5:20210608-SNAPSHOT@zip"
    public static final String PARCHMENT_URL = "https://ldtteam.jfrog.io/artifactory/parchmentmc-snapshots/org/parchmentmc/data/parchment-1.16.5/20210608-SNAPSHOT/parchment-1.16.5-20210608-SNAPSHOT.zip"
}