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

package net.fabricmc.loom;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.BasePluginConvention;
import org.jetbrains.annotations.ApiStatus;

import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.configuration.LoomDependencyManager;
import net.fabricmc.loom.configuration.LoomProjectData;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.configuration.processors.JarProcessor;
import net.fabricmc.loom.configuration.processors.JarProcessorManager;
import net.fabricmc.loom.configuration.providers.MinecraftProviderImpl;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftMappedProvider;
import net.fabricmc.loom.configuration.providers.mappings.GradleMappingContext;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingSpec;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingSpecBuilder;
import net.fabricmc.loom.configuration.providers.mappings.LayeredMappingsDependency;
import net.fabricmc.loom.configuration.providers.mappings.MappingsProviderImpl;

public class LoomGradleExtension {
	public String refmapName;
	public String loaderLaunchMethod;
	public boolean remapMod = true;
	public String customManifest = null;
	public File accessWidener = null;
	public List<String> enumWidener = new ArrayList<>();
	public Function<String, Object> intermediaryUrl = mcVer -> "https://maven.fabricmc.net/net/fabricmc/intermediary/" + mcVer + "/intermediary-" + mcVer + "-v2.jar";
	public boolean shareCaches = false;

	private final ConfigurableFileCollection unmappedMods;
	private final ConfigurableFileCollection log4jConfigs;

	final List<LoomDecompiler> decompilers = new ArrayList<>();
	private final List<JarProcessor> jarProcessors = new ArrayList<>();

	// Not to be set in the build.gradle
	private final Project project;
	private LoomDependencyManager dependencyManager;
	private JarProcessorManager jarProcessorManager;
	private JsonObject installerJson;
	private MappingSet[] srcMappingCache = new MappingSet[2];
	private Mercury[] srcMercuryCache = new Mercury[2];
	private Set<File> mixinMappings = Collections.synchronizedSet(new HashSet<>());

	@ApiStatus.Internal
	private final LoomProjectData projectData;

	private NamedDomainObjectContainer<RunConfigSettings> runConfigs;

	/**
	 * Loom will generate a new genSources task (with a new name, based off of {@link LoomDecompiler#name()})
	 * that uses the specified decompiler instead.
	 */
	public void addDecompiler(LoomDecompiler decompiler) {
		decompilers.add(decompiler);
	}

	/**
	 * Add a transformation over the mapped mc jar.
	 * Adding any jar processor will cause mapped mc jars to be stored per-project so that
	 * different transformation can be applied in different projects.
	 * This means remapping will need to be done individually per-project, which is slower when developing
	 * more than one project using the same minecraft version.
	 */
	public void addJarProcessor(JarProcessor processor) {
		jarProcessors.add(processor);
	}

	public MappingSet getOrCreateSrcMappingCache(int id, Supplier<MappingSet> factory) {
		return srcMappingCache[id] != null ? srcMappingCache[id] : (srcMappingCache[id] = factory.get());
	}

	public Mercury getOrCreateSrcMercuryCache(int id, Supplier<Mercury> factory) {
		return srcMercuryCache[id] != null ? srcMercuryCache[id] : (srcMercuryCache[id] = factory.get());
	}

	public Dependency officialMojangMappings() {
		return layered(LayeredMappingSpecBuilder::officialMojangMappings);
	}

	public Dependency layered(Action<LayeredMappingSpecBuilder> action) {
		LayeredMappingSpecBuilder builder = new LayeredMappingSpecBuilder();
		action.execute(builder);
		LayeredMappingSpec builtSpec = builder.build();
		return new LayeredMappingsDependency(new GradleMappingContext(project, "layers_" + builtSpec.getVersion().replace("+", "_").replace(".", "_")), builtSpec, builtSpec.getVersion());
	}

	public LoomGradleExtension(Project project) {
		this.project = project;
		this.unmappedMods = project.files();
		this.runConfigs = project.container(RunConfigSettings.class,
				baseName -> new RunConfigSettings(project, baseName));
		this.log4jConfigs = project.files(getDefaultLog4jConfigFile());
		projectData = new LoomProjectData(project);
	}

	/**
	 * @see ConfigurableFileCollection#from(Object...)
	 * @deprecated use {@link #getUnmappedModCollection()}{@code .from()} instead
	 */
	@Deprecated
	public void addUnmappedMod(Path file) {
		getUnmappedModCollection().from(file);
	}

	/**
	 * @deprecated use {@link #getUnmappedModCollection()} instead
	 */
	@Deprecated
	public List<Path> getUnmappedMods() {
		return unmappedMods.getFiles().stream()
				.map(File::toPath)
				.collect(Collectors.toList());
	}

	public ConfigurableFileCollection getUnmappedModCollection() {
		return unmappedMods;
	}

	public void setInstallerJson(JsonObject object) {
		this.installerJson = object;
	}

	public JsonObject getInstallerJson() {
		return installerJson;
	}

	public void enumWidener(String klass) {
		this.enumWidener.add(klass);
	}

	public void accessWidener(Object file) {
		this.accessWidener = project.file(file);
	}

	public File getUserCache() {
		File userCache = new File(project.getGradle().getGradleUserHomeDir(), "caches" + File.separator + "fabric-loom");

		if (!userCache.exists()) {
			userCache.mkdirs();
		}

		return userCache;
	}

	public File getRootProjectPersistentCache() {
		File projectCache = new File(project.getRootProject().file(".gradle"), "loom-cache");

		if (!projectCache.exists()) {
			projectCache.mkdirs();
		}

		return projectCache;
	}

	public File getProjectPersistentCache() {
		File projectCache = new File(project.file(".gradle"), "loom-cache");

		if (!projectCache.exists()) {
			projectCache.mkdirs();
		}

		return projectCache;
	}

	public File getRootProjectBuildCache() {
		File projectCache = new File(project.getRootProject().getBuildDir(), "loom-cache");

		if (!projectCache.exists()) {
			projectCache.mkdirs();
		}

		return projectCache;
	}

	public File getProjectBuildCache() {
		File projectCache = new File(project.getBuildDir(), "loom-cache");

		if (!projectCache.exists()) {
			projectCache.mkdirs();
		}

		return projectCache;
	}

	public File getRemappedModCache() {
		File remappedModCache = new File(getRootProjectPersistentCache(), "remapped_mods");

		if (!remappedModCache.exists()) {
			remappedModCache.mkdir();
		}

		return remappedModCache;
	}

	public File getNestedModCache() {
		File nestedModCache = new File(getRootProjectPersistentCache(), "nested_mods");

		if (!nestedModCache.exists()) {
			nestedModCache.mkdir();
		}

		return nestedModCache;
	}

	public File getNativesJarStore() {
		File natives = new File(getUserCache(), "natives/jars");

		if (!natives.exists()) {
			natives.mkdirs();
		}

		return natives;
	}

	public File getNativesDirectory() {
		if (project.hasProperty("fabric.loom.natives.dir")) {
			return new File((String) project.property("fabric.loom.natives.dir"));
		}

		File natives = new File(getUserCache(), "natives/" + getMinecraftProvider().minecraftVersion());

		if (!natives.exists()) {
			natives.mkdirs();
		}

		return natives;
	}

	public boolean hasCustomNatives() {
		return project.getProperties().get("fabric.loom.natives.dir") != null;
	}

	public File getDevLauncherConfig() {
		return new File(getProjectPersistentCache(), "launch.cfg");
	}

	public String getLoaderLaunchMethod() {
		return loaderLaunchMethod != null ? loaderLaunchMethod : "";
	}

	public LoomDependencyManager getDependencyManager() {
		return dependencyManager;
	}

	public MinecraftProviderImpl getMinecraftProvider() {
		return getDependencyManager().getProvider(MinecraftProviderImpl.class);
	}

	public MinecraftMappedProvider getMinecraftMappedProvider() {
		return getMappingsProvider().mappedProvider;
	}

	public MappingsProviderImpl getMappingsProvider() {
		return getDependencyManager().getProvider(MappingsProviderImpl.class);
	}

	public void setDependencyManager(LoomDependencyManager dependencyManager) {
		this.dependencyManager = dependencyManager;
	}

	public JarProcessorManager getJarProcessorManager() {
		return jarProcessorManager;
	}

	public void setJarProcessorManager(JarProcessorManager jarProcessorManager) {
		this.jarProcessorManager = jarProcessorManager;
	}

	public List<JarProcessor> getJarProcessors() {
		return jarProcessors;
	}

	public String getRefmapName() {
		if (refmapName == null || refmapName.isEmpty()) {
			String defaultRefmapName = project.getConvention().getPlugin(BasePluginConvention.class).getArchivesBaseName() + "-refmap.json";
			project.getLogger().info("Could not find refmap definition, will be using default name: " + defaultRefmapName);
			refmapName = defaultRefmapName;
		}

		return refmapName;
	}

	public boolean ideSync() {
		return Boolean.parseBoolean(System.getProperty("idea.sync.active", "false"));
	}

	// Ideally this should use maven, but this is a lot easier
	public Function<String, String> getIntermediaryUrl() {
		// Done like this to work around this possibly not being a java string...
		return s -> intermediaryUrl.apply(s).toString();
	}

	public boolean isRootProject() {
		return project.getRootProject() == project;
	}

	public LoomGradleExtension getRootGradleExtension() {
		if (isRootProject()) {
			return this;
		}

		return project.getRootProject().getExtensions().getByType(LoomGradleExtension.class);
	}

	public LoomGradleExtension getSharedGradleExtension() {
		return isShareCaches() ? getRootGradleExtension() : this;
	}

	public boolean isShareCaches() {
		return shareCaches;
	}

	// Creates a new file each time its called, this is then held onto later when remapping the output jar
	// Required as now when using parallel builds the old single file could be written by another sourceset compile task
	public synchronized File getNextMixinMappings() {
		File mixinMapping = new File(getProjectBuildCache(), "mixin-map-" + getMinecraftProvider().minecraftVersion() + "-" + getMappingsProvider().mappingsVersion + "." + mixinMappings.size() + ".tiny");
		mixinMappings.add(mixinMapping);
		return mixinMapping;
	}

	public Set<File> getAllMixinMappings() {
		return Collections.unmodifiableSet(mixinMappings);
	}

	public List<LoomDecompiler> getDecompilers() {
		return decompilers;
	}

	public File getDefaultLog4jConfigFile() {
		return new File(getProjectPersistentCache(), "log4j.xml");
	}

	public File getUnpickLoggingConfigFile() {
		return new File(getProjectPersistentCache(), "unpick-logging.properties");
	}

	public ConfigurableFileCollection getLog4jConfigs() {
		return log4jConfigs;
	}

	public void runs(Action<NamedDomainObjectContainer<RunConfigSettings>> action) {
		action.execute(runConfigs);
	}

	public NamedDomainObjectContainer<RunConfigSettings> getRunConfigs() {
		return runConfigs;
	}

	@ApiStatus.Internal
	public LoomProjectData getProjectData() {
		return projectData;
	}
}
