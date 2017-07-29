/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016 FabricMC
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

package net.fabricmc.loom.task;

import cuchaz.enigma.Deobfuscator;
import cuchaz.enigma.TranslatingTypeLoader;
import cuchaz.enigma.mapping.MappingsEnigmaReader;
import cuchaz.enigma.mapping.TranslationDirection;
import cuchaz.enigma.throwables.MappingParseException;
import javassist.CtClass;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.proccessing.AccessManipulatorUtils;
import net.fabricmc.loom.util.proccessing.ClassAccessManipulator;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.commons.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;

public class MapJarsTask extends DefaultTask {

	Deobfuscator deobfuscator;

	@TaskAction
	public void mapJars() throws IOException, MappingParseException {
		LoomGradleExtension extension = this.getProject().getExtensions().getByType(LoomGradleExtension.class);
		if (!Constants.MINECRAFT_MAPPED_JAR.get(extension).exists() || extension.localMappings) {
			if(extension.localMappings && Constants.MINECRAFT_MAPPED_JAR.get(extension).exists()){
				//Always remap the jar when using local mappings.
				Constants.MINECRAFT_MAPPED_JAR.get(extension).delete();
			}
			if(!extension.hasPomf()){
				this.getLogger().lifecycle("POMF version not set, skipping mapping!");
				FileUtils.copyFile(Constants.MINECRAFT_MERGED_JAR.get(extension), Constants.MINECRAFT_MAPPED_JAR.get(extension));
				return;
			}
			if (!Constants.MAPPINGS_DIR.get(extension).exists() || extension.localMappings) {
				this.getLogger().lifecycle(":unpacking mappings");
				FileUtils.deleteDirectory(Constants.MAPPINGS_DIR.get(extension));
				ZipUtil.unpack(Constants.MAPPINGS_ZIP.get(extension), Constants.MAPPINGS_DIR.get(extension));
			}

			this.getLogger().lifecycle(":remapping jar");
			deobfuscator = new Deobfuscator(new JarFile(Constants.MINECRAFT_MERGED_JAR.get(extension)));
			this.deobfuscator.setMappings(new MappingsEnigmaReader().read(Constants.MAPPINGS_DIR.get(extension)));
			writeJar(Constants.MINECRAFT_MAPPED_JAR.get(extension), new ProgressListener(), deobfuscator, extension);

			File tempAssests = new File(Constants.CACHE_FILES, "tempAssets");
			if (tempAssests.exists()) {
				FileUtils.deleteDirectory(tempAssests);
			}
			tempAssests.mkdir();

			ZipUtil.unpack(Constants.MINECRAFT_CLIENT_JAR.get(extension), tempAssests, name -> {
				if (name.startsWith("assets") || name.startsWith("log4j2.xml") || name.startsWith("pack.png")) {
					return name;
				} else {
					return null;
				}
			});
			ZipUtil.unpack(Constants.MINECRAFT_MAPPED_JAR.get(extension), tempAssests);

			ZipUtil.pack(tempAssests, Constants.MINECRAFT_MAPPED_JAR.get(extension));
			FileUtils.deleteDirectory(tempAssests);
		} else {
			this.getLogger().lifecycle(Constants.MINECRAFT_MAPPED_JAR.get(extension).getAbsolutePath());
			this.getLogger().lifecycle(":mapped jar found, skipping mapping");
		}
	}

	public void writeJar(File out, Deobfuscator.ProgressListener progress, Deobfuscator deobfuscator, LoomGradleExtension extension) throws IOException {
		TranslatingTypeLoader loader = new TranslatingTypeLoader(deobfuscator.getJar(), deobfuscator.getJarIndex(), deobfuscator.getTranslator(TranslationDirection.Obfuscating), deobfuscator.getTranslator(TranslationDirection.Deobfuscating));
		CustomClassTransformer transformer = new CustomClassTransformer(loader);
		if(extension != null && extension.accessManipulator != null && !extension.accessManipulator.isEmpty()){
			File amFile = new File(extension.accessManipulator);
			transformer.setClassAccessManipulator(new ClassAccessManipulator(AccessManipulatorUtils.readAMFromFile(amFile)));
		}
		deobfuscator.transformJar(out, progress, transformer);
	}

	private class CustomClassTransformer implements Deobfuscator.ClassTransformer {

		TranslatingTypeLoader loader;
		ClassAccessManipulator classAccessManipulator;

		public CustomClassTransformer(TranslatingTypeLoader loader) {
			this.loader = loader;
		}

		@Override
		public CtClass transform(CtClass ctClass) throws Exception {
			if(classAccessManipulator != null){
				return classAccessManipulator.transform(loader.transformClass(ctClass));
			}
			return loader.transformClass(ctClass);
		}

		public void setClassAccessManipulator(ClassAccessManipulator classAccessManipulator) {
			this.classAccessManipulator = classAccessManipulator;
		}
	}

	public static class ProgressListener implements Deobfuscator.ProgressListener {
		@Override
		public void init(int i, String s) {

		}

		@Override
		public void onProgress(int i, String s) {

		}
	}

}
