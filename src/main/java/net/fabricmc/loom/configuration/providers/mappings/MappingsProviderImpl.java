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

package net.fabricmc.loom.configuration.providers.mappings;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;
import com.google.common.net.UrlEscapers;
import com.google.gson.JsonObject;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.util.StringUtils;
import org.gradle.api.Project;
import org.zeroturnaround.zip.FileSource;
import org.zeroturnaround.zip.ZipEntrySource;
import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.DependencyProvider;
import net.fabricmc.loom.configuration.accesswidener.AccessWidenerJarProcessor;
import net.fabricmc.loom.configuration.enumwidener.EnumWidenerJarProcessor;
import net.fabricmc.loom.configuration.processors.JarProcessorManager;
import net.fabricmc.loom.configuration.processors.MinecraftProcessedProvider;
import net.fabricmc.loom.configuration.providers.MinecraftProviderImpl;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftMappedProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DeletingFileVisitor;
import net.fabricmc.loom.util.DownloadUtil;
import net.fabricmc.mapping.reader.v2.TinyV2Factory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.stitch.Command;
import net.fabricmc.stitch.commands.CommandProposeFieldNames;
import net.fabricmc.stitch.commands.tinyv2.CommandMergeTinyV2;
import net.fabricmc.stitch.commands.tinyv2.CommandReorderTinyV2;

public class MappingsProviderImpl extends DependencyProvider implements MappingsProvider {
	public MinecraftMappedProvider mappedProvider;

	public String mappingsName;
	public String minecraftVersion;
	public String mappingsVersion;

	private final Path mappingsDir;
	private final Path mappingsStepsDir;
	private Path intermediaryTiny;
	private boolean hasRefreshed = false;
	// The mappings that gradle gives us
	private Path baseTinyMappings;
	// The mappings we use in practice
	public File tinyMappings;
	public File tinyMappingsJar;
	private File unpickDefinitionsFile;
	private boolean hasUnpickDefinitions;
	private UnpickMetadata unpickMetadata;

	public MappingsProviderImpl(Project project) {
		super(project);
		mappingsDir = getExtension().getUserCache().toPath().resolve("mappings");
		mappingsStepsDir = mappingsDir.resolve("steps");
	}

	public void clean() throws IOException {
		FileUtils.deleteDirectory(mappingsDir.toFile());
	}

	public TinyTree getMappings() throws IOException {
		return MappingsCache.INSTANCE.get(tinyMappings.toPath());
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		MinecraftProviderImpl minecraftProvider = getDependencyManager().getProvider(MinecraftProviderImpl.class);

		getProject().getLogger().info(":setting up mappings (" + dependency.getDependency().getName() + " " + dependency.getResolvedVersion() + ")");

		String version = dependency.getResolvedVersion();
		File mappingsJar = dependency.resolveFile().orElseThrow(() -> new RuntimeException("Could not find yarn mappings: " + dependency));

		this.mappingsName = StringUtils.removeSuffix(dependency.getDependency().getGroup() + "." + dependency.getDependency().getName(), "-unmerged");
		this.minecraftVersion = minecraftProvider.minecraftVersion();

		boolean isV2;

		// Only do this for official yarn, there isn't really a way we can get the mc version for all mappings
		if (dependency.getDependency().getGroup() != null && dependency.getDependency().getGroup().equals("net.fabricmc") && dependency.getDependency().getName().equals("yarn") && dependency.getDependency().getVersion() != null) {
			String yarnVersion = dependency.getDependency().getVersion();
			char separator = yarnVersion.contains("+build.") ? '+' : yarnVersion.contains("-") ? '-' : '.';
			String yarnMinecraftVersion = yarnVersion.substring(0, yarnVersion.lastIndexOf(separator));

			if (!yarnMinecraftVersion.equalsIgnoreCase(minecraftVersion)) {
				throw new RuntimeException(String.format("Minecraft Version (%s) does not match yarn's minecraft version (%s)", minecraftVersion, yarnMinecraftVersion));
			}

			// We can save reading the zip file + header by checking the file name
			isV2 = mappingsJar.getName().endsWith("-v2.jar");
		} else {
			isV2 = doesJarContainV2Mappings(mappingsJar.toPath());
		}

		this.mappingsVersion = version + (isV2 ? "-v2" : "");

		initFiles();

		if (isRefreshDeps()) {
			cleanFiles();
		}

		Files.createDirectories(mappingsDir);
		Files.createDirectories(mappingsStepsDir);

		String[] depStringSplit = dependency.getDepString().split(":");
		String jarClassifier = "final";

		if (depStringSplit.length >= 4) {
			jarClassifier = jarClassifier + depStringSplit[3];
		}

		tinyMappings = mappingsDir.resolve(StringUtils.removeSuffix(mappingsJar.getName(), ".jar") + ".tiny").toFile();
		unpickDefinitionsFile = mappingsDir.resolve(StringUtils.removeSuffix(mappingsJar.getName(), ".jar") + ".unpick").toFile();
		tinyMappingsJar = new File(getExtension().getUserCache(), mappingsJar.getName().replace(".jar", "-" + jarClassifier + ".jar"));

		if (!tinyMappings.exists() || isRefreshDeps()) {
			storeMappings(getProject(), minecraftProvider, mappingsJar.toPath());
		} else {
			try (FileSystem fileSystem = FileSystems.newFileSystem(mappingsJar.toPath(), (ClassLoader) null)) {
				extractUnpickDefinitions(fileSystem, unpickDefinitionsFile.toPath());
			}
		}

		if (!tinyMappingsJar.exists() || isRefreshDeps()) {
			ZipUtil.pack(new ZipEntrySource[] {new FileSource("mappings/mappings.tiny", tinyMappings)}, tinyMappingsJar);
		}

		if (hasUnpickDefinitions()) {
			String notation = String.format("%s:%s:%s:constants",
					dependency.getDependency().getGroup(),
					dependency.getDependency().getName(),
					dependency.getDependency().getVersion()
			);

			getProject().getDependencies().add(Constants.Configurations.MAPPING_CONSTANTS, notation);
			populateUnpickClasspath();
		}

		addDependency(tinyMappingsJar, Constants.Configurations.MAPPINGS_FINAL);

		LoomGradleExtension extension = getExtension();

		if (extension.accessWidener != null) {
			extension.addJarProcessor(new AccessWidenerJarProcessor(getProject()));
		}

		if (!extension.enumWidener.isEmpty()) {
			extension.addJarProcessor(new EnumWidenerJarProcessor(getProject()));
		}

		JarProcessorManager processorManager = new JarProcessorManager(extension.getJarProcessors());
		extension.setJarProcessorManager(processorManager);
		processorManager.setupProcessors();

		if (processorManager.active()) {
			mappedProvider = new MinecraftProcessedProvider(getProject(), processorManager);
			getProject().getLogger().lifecycle("Using project based jar storage");
		} else {
			mappedProvider = new MinecraftMappedProvider(getProject());
		}

		mappedProvider.initFiles(minecraftProvider, this);
		mappedProvider.provide(dependency, postPopulationScheduler);
	}

	private void storeMappings(Project project, MinecraftProviderImpl minecraftProvider, Path yarnJar) throws IOException {
		project.getLogger().info(":extracting " + yarnJar.getFileName());

		try (FileSystem fileSystem = FileSystems.newFileSystem(yarnJar, (ClassLoader) null)) {
			extractMappings(fileSystem, baseTinyMappings);
			extractUnpickDefinitions(fileSystem, unpickDefinitionsFile.toPath());
		}

		if (baseMappingsAreV2()) {
			// These are unmerged v2 mappings
			mergeAndSaveMappings(project, yarnJar);
		} else {
			// These are merged v1 mappings
			if (tinyMappings.exists()) {
				tinyMappings.delete();
			}

			project.getLogger().lifecycle(":populating field names");
			suggestFieldNames(minecraftProvider, baseTinyMappings, tinyMappings.toPath());
		}
	}

	private boolean baseMappingsAreV2() throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(baseTinyMappings)) {
			TinyV2Factory.readMetadata(reader);
			return true;
		} catch (IllegalArgumentException e) {
			// TODO: just check the mappings version when Parser supports V1 in readMetadata()
			return false;
		}
	}

	private boolean doesJarContainV2Mappings(Path path) throws IOException {
		try (FileSystem fs = FileSystems.newFileSystem(path, (ClassLoader) null)) {
			try (BufferedReader reader = Files.newBufferedReader(fs.getPath("mappings", "mappings.tiny"))) {
				TinyV2Factory.readMetadata(reader);
				return true;
			} catch (IllegalArgumentException e) {
				return false;
			}
		}
	}

	public static void extractMappings(FileSystem jar, Path extractTo) throws IOException {
		Files.copy(jar.getPath("mappings/mappings.tiny"), extractTo, StandardCopyOption.REPLACE_EXISTING);
	}

	private void extractUnpickDefinitions(FileSystem jar, Path extractTo) throws IOException {
		Path unpickPath = jar.getPath("extras/definitions.unpick");
		Path unpickMetadataPath = jar.getPath("extras/unpick.json");

		if (!Files.exists(unpickPath) || !Files.exists(unpickMetadataPath)) {
			return;
		}

		Files.copy(unpickPath, extractTo, StandardCopyOption.REPLACE_EXISTING);

		unpickMetadata = parseUnpickMetadata(unpickMetadataPath);
		hasUnpickDefinitions = true;
	}

	private UnpickMetadata parseUnpickMetadata(Path input) throws IOException {
		JsonObject jsonObject = LoomGradlePlugin.GSON.fromJson(Files.readString(input), JsonObject.class);

		if (!jsonObject.has("version") || jsonObject.get("version").getAsInt() != 1) {
			throw new UnsupportedOperationException("Unsupported unpick version");
		}

		return new UnpickMetadata(
				jsonObject.get("unpickGroup").getAsString(),
				jsonObject.get("unpickVersion").getAsString()
		);
	}

	private void populateUnpickClasspath() {
		String unpickCliName = "unpick-cli";
		getProject().getDependencies().add(Constants.Configurations.UNPICK_CLASSPATH,
				String.format("%s:%s:%s", unpickMetadata.unpickGroup, unpickCliName, unpickMetadata.unpickVersion)
		);
	}

	private void extractIntermediary(Path intermediaryJar, Path intermediaryTiny) throws IOException {
		getProject().getLogger().info(":extracting " + intermediaryJar.getFileName());

		try (FileSystem unmergedIntermediaryFs = FileSystems.newFileSystem(intermediaryJar, (ClassLoader) null)) {
			extractMappings(unmergedIntermediaryFs, intermediaryTiny);
		}
	}

	private void mergeAndSaveMappings(Project project, Path unmergedYarnJar) throws IOException {
		Path unmergedYarn = Paths.get(mappingsStepsDir.toString(), "unmerged-yarn.tiny");
		project.getLogger().info(":extracting " + unmergedYarnJar.getFileName());

		try (FileSystem unmergedYarnJarFs = FileSystems.newFileSystem(unmergedYarnJar, (ClassLoader) null)) {
			extractMappings(unmergedYarnJarFs, unmergedYarn);
		}

		Path invertedIntermediary = Paths.get(mappingsStepsDir.toString(), "inverted-intermediary.tiny");
		reorderMappings(getIntermediaryTiny(), invertedIntermediary, "intermediary", "official");
		Path unorderedMergedMappings = Paths.get(mappingsStepsDir.toString(), "unordered-merged.tiny");
		project.getLogger().info(":merging");
		mergeMappings(invertedIntermediary, unmergedYarn, unorderedMergedMappings);
		reorderMappings(unorderedMergedMappings, tinyMappings.toPath(), "official", "intermediary", "named");
	}

	private void reorderMappings(Path oldMappings, Path newMappings, String... newOrder) {
		Command command = new CommandReorderTinyV2();
		String[] args = new String[2 + newOrder.length];
		args[0] = oldMappings.toAbsolutePath().toString();
		args[1] = newMappings.toAbsolutePath().toString();
		System.arraycopy(newOrder, 0, args, 2, newOrder.length);
		runCommand(command, args);
	}

	private void mergeMappings(Path intermediaryMappings, Path yarnMappings, Path newMergedMappings) {
		try {
			Command command = new CommandMergeTinyV2();
			runCommand(command, intermediaryMappings.toAbsolutePath().toString(),
							yarnMappings.toAbsolutePath().toString(),
							newMergedMappings.toAbsolutePath().toString(),
							"intermediary", "official");
		} catch (Exception e) {
			throw new RuntimeException("Could not merge mappings from " + intermediaryMappings.toString()
							+ " with mappings from " + yarnMappings, e);
		}
	}

	private void suggestFieldNames(MinecraftProviderImpl minecraftProvider, Path oldMappings, Path newMappings) {
		Command command = new CommandProposeFieldNames();
		runCommand(command, minecraftProvider.getMergedJar().getAbsolutePath(),
						oldMappings.toAbsolutePath().toString(),
						newMappings.toAbsolutePath().toString());
	}

	private void runCommand(Command command, String... args) {
		try {
			command.run(args);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void initFiles() {
		baseTinyMappings = mappingsDir.resolve(mappingsName + "-tiny-" + minecraftVersion + "-" + mappingsVersion + "-base");
	}

	public void cleanFiles() {
		try {
			if (Files.exists(mappingsStepsDir)) {
				Files.walkFileTree(mappingsStepsDir, new DeletingFileVisitor());
			}

			if (Files.exists(baseTinyMappings)) {
				Files.deleteIfExists(baseTinyMappings);
			}

			if (tinyMappings != null) {
				tinyMappings.delete();
			}

			if (tinyMappingsJar != null) {
				tinyMappingsJar.delete();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public String getTargetConfig() {
		return Constants.Configurations.MAPPINGS;
	}

	@Override
	public Path getMappingsDir() {
		return mappingsDir;
	}

	public Path getIntermediaryTiny() throws IOException {
		if (intermediaryTiny == null) {
			minecraftVersion = getExtension().getMinecraftProvider().minecraftVersion();
			Preconditions.checkNotNull(minecraftVersion, "Minecraft version cannot be null");

			intermediaryTiny = mappingsDir.resolve(String.format("intermediary-%s-v2.tiny", minecraftVersion));

			if (!Files.exists(intermediaryTiny) || (isRefreshDeps() && !hasRefreshed)) {
				hasRefreshed = true;

				// Download and extract intermediary
				String encodedMinecraftVersion = UrlEscapers.urlFragmentEscaper().escape(minecraftVersion);
				String intermediaryArtifactUrl = getExtension().getIntermediaryUrl().apply(encodedMinecraftVersion);
				Path intermediaryJar = mappingsDir.resolve("v2-intermediary-" + minecraftVersion + ".jar");
				DownloadUtil.downloadIfChanged(new URL(intermediaryArtifactUrl), intermediaryJar.toFile(), getProject().getLogger());

				extractIntermediary(intermediaryJar, intermediaryTiny);
			}
		}

		return intermediaryTiny;
	}

	public String getMappingsKey() {
		return mappingsName + "." + minecraftVersion.replace(' ', '_').replace('.', '_').replace('-', '_') + "." + mappingsVersion;
	}

	public File getUnpickDefinitionsFile() {
		return unpickDefinitionsFile;
	}

	public boolean hasUnpickDefinitions() {
		return hasUnpickDefinitions;
	}

	@Override
	public File intermediaryTinyFile() {
		try {
			return getIntermediaryTiny().toFile();
		} catch (IOException e) {
			throw new RuntimeException("Failed to get intermediary", e);
		}
	}

	public record UnpickMetadata(String unpickGroup, String unpickVersion) {
	}
}
