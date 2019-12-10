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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import net.fabricmc.loom.LoomGradleExtension;

public final class InstallerUtils
{

    private static final Gson GSON = new Gson();

    private InstallerUtils()
    {
        throw new IllegalStateException("Tried to initialize: InstallerUtils but this is a Utility class.");
    }

    static JsonObject readInstallerJson(File file, Project project) {
        try {
            LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
            String launchMethod = extension.getLoaderLaunchMethod();

            String jsonStr;
            int priority = 0;

            try (JarFile jarFile = new JarFile(file)) {
                ZipEntry entry = null;

                if (!launchMethod.isEmpty()) {
                    entry = jarFile.getEntry("fabric-installer." + launchMethod + ".json");

                    if (entry == null) {
                        project.getLogger().warn("Could not find loader launch method '" + launchMethod + "', falling back");
                    }
                }

                if (entry == null) {
                    entry = jarFile.getEntry("fabric-installer.json");
                    priority++;

                    if (entry == null) {
                        return null;
                    }
                }

                try (InputStream inputstream = jarFile.getInputStream(entry)) {
                    jsonStr = IOUtils.toString(inputstream, StandardCharsets.UTF_8);
                }
            }

            JsonObject jsonObject = GSON.fromJson(jsonStr, JsonObject.class);
            return jsonObject;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}
