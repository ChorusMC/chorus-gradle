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

import com.google.common.collect.ImmutableMap;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.mappings.*;
import net.fabricmc.stitch.util.Pair;
import net.fabricmc.stitch.util.StitchUtil;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingsReader;
import org.cadixdev.lorenz.io.TextMappingsReader;
import org.cadixdev.lorenz.model.Mapping;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.Project;
import org.gradle.internal.impldep.aQute.bnd.build.Run;
import org.objectweb.asm.commons.Remapper;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.commons.FileUtils;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SourceRemapper {
	public static void remapSources(Project project, File source, File destination, boolean toNamed) throws Exception {
		remapSourcesInner(project, source, destination, toNamed);
		// TODO: FIXME - WORKAROUND https://github.com/FabricMC/fabric-loom/issues/45
		System.gc();
	}

	private static void remapSourcesInner(Project project, File source, File destination, boolean toNamed) throws Exception {
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		MappingsProvider mappingsProvider = extension.getMappingsProvider();

		MappingSet mappings = extension.getOrCreateSrcMappingCache(toNamed ? 1 : 0, () -> {
			try {
				Mappings m = mappingsProvider.getMappings();
				project.getLogger().lifecycle(":loading " + (toNamed ? "intermediary -> named" : "named -> intermediary") + " source mappings");
				return new TinyReader(m, toNamed ? "intermediary" : "named", toNamed ? "named" : "intermediary").read();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});

		project.getLogger().lifecycle(":remapping source jar");

		Mercury mercury = extension.getOrCreateSrcMercuryCache(toNamed ? 1 : 0, () -> {
			Mercury m = new Mercury();

			for (File file : project.getConfigurations().getByName(Constants.MINECRAFT_DEPENDENCIES).getFiles()) {
				m.getClassPath().add(file.toPath());
			}
			if (!toNamed) {
				for (File file : project.getConfigurations().getByName("compileClasspath").getFiles()) {
					m.getClassPath().add(file.toPath());
				}
			}
			for (Path file : extension.getUnmappedMods()) {
				if (Files.isRegularFile(file)) {
					m.getClassPath().add(file);
				}
			}

			m.getClassPath().add(extension.getMinecraftMappedProvider().MINECRAFT_MAPPED_JAR.toPath());
			m.getClassPath().add(extension.getMinecraftMappedProvider().MINECRAFT_INTERMEDIARY_JAR.toPath());

			m.getProcessors().add(MercuryRemapper.create(mappings));

			return m;
		});

		if (source.equals(destination)) {
			if (source.isDirectory()) {
				throw new RuntimeException("Directories must differ!");
			}

			source = new File(destination.getAbsolutePath().substring(0, destination.getAbsolutePath().lastIndexOf('.')) + "-dev.jar");
			try {
				com.google.common.io.Files.move(destination, source);
			} catch (IOException e) {
				throw new RuntimeException("Could not rename " + destination.getName() + "!", e);
			}
		}

		Path srcPath = source.toPath();
		boolean isSrcTmp = false;
		if (!source.isDirectory()) {
			// create tmp directory
			isSrcTmp = true;
			srcPath = Files.createTempDirectory("fabric-loom-src");
			ZipUtil.unpack(source, srcPath.toFile());
		}

		if (!destination.isDirectory() && destination.exists()) {
			if (!destination.delete()) {
				throw new RuntimeException("Could not delete " + destination.getName() + "!");
			}
		}

		StitchUtil.FileSystemDelegate dstFs = destination.isDirectory() ? null : StitchUtil.getJarFileSystem(destination, true);
		Path dstPath = dstFs != null ? dstFs.get().getPath("/") : destination.toPath();

		try {
			mercury.rewrite(srcPath, dstPath);
		} catch (Exception e) {
			project.getLogger().warn("Could not remap " + source.getName() + " fully!", e);
		}

		copyNonJavaFiles(srcPath.toFile(), dstPath);

		if (dstFs != null) {
			dstFs.close();
		}

		if (isSrcTmp) {
			Files.walkFileTree(srcPath, new DeletingFileVisitor());
		}

	}

	private static void copyNonJavaFiles(File from, Path to) throws IOException {
        for (File file : from.listFiles()) {
            Path path = to.resolve(file.getName());
            if (file.isDirectory()) {
                Files.createDirectories(path);
                copyNonJavaFiles(file, path);
            } else if (!isJavaFile(file)) {
                Files.copy(file.toPath(), path, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static boolean isJavaFile(File file) {
		String name = file.getName();
		return name.endsWith(".java") && name.lastIndexOf('.') > 0;
    }

	public static class TinyReader extends MappingsReader {
		private final Mappings m;
		private final String from, to;

		public TinyReader(Mappings m, String from, String to) {
			this.m = m;
			this.from = from;
			this.to = to;
		}

		@Override
		public MappingSet read(final MappingSet mappings) {
			for (ClassEntry entry : m.getClassEntries()) {
				mappings.getOrCreateClassMapping(entry.get(from))
						.setDeobfuscatedName(entry.get(to));
			}

			for (FieldEntry entry : m.getFieldEntries()) {
				EntryTriple fromEntry = entry.get(from);
				EntryTriple toEntry = entry.get(to);

				mappings.getOrCreateClassMapping(fromEntry.getOwner())
						.getOrCreateFieldMapping(fromEntry.getName(), fromEntry.getDesc())
						.setDeobfuscatedName(toEntry.getName());
			}

			for (MethodEntry entry : m.getMethodEntries()) {
				EntryTriple fromEntry = entry.get(from);
				EntryTriple toEntry = entry.get(to);

				mappings.getOrCreateClassMapping(fromEntry.getOwner())
						.getOrCreateMethodMapping(fromEntry.getName(), fromEntry.getDesc())
						.setDeobfuscatedName(toEntry.getName());
			}

			return mappings;
		}

		@Override
		public void close() throws IOException {

		}
	}

}
