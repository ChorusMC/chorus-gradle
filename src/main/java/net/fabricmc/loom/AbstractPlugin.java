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
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import groovy.util.Node;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.idea.model.IdeaModel;

import net.fabricmc.loom.ide.ToolingModelBuilderRegistryProcessor;
import net.fabricmc.loom.providers.LaunchProvider;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftProvider;
import net.fabricmc.loom.task.RemapJarTask;
import net.fabricmc.loom.task.RemapSourcesJarTask;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.LoomDependencyManager;
import net.fabricmc.loom.util.NestedJars;
import net.fabricmc.loom.util.RepositoryUtils;
import net.fabricmc.loom.util.SetupIntelijRunConfigs;

public class AbstractPlugin implements Plugin<Project> {
	protected Project project;

	public static boolean isRootProject(Project project) {
		return project.getRootProject() == project;
	}

	private void extendsFrom(String a, String b) {
		project.getConfigurations().getByName(a).extendsFrom(project.getConfigurations().getByName(b));
	}

	@Override
	public void apply(Project target) {
        this.project = target;
        ToolingModelBuilderRegistryProcessor.attach(project);

		project.getLogger().lifecycle("Fabric Loom: " + AbstractPlugin.class.getPackage().getImplementationVersion());

		// Apply default plugins
		project.apply(ImmutableMap.of("plugin", "java"));
		project.apply(ImmutableMap.of("plugin", "eclipse"));
		project.apply(ImmutableMap.of("plugin", "idea"));

		project.getExtensions().create("minecraft", LoomGradleExtension.class, project);

		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		// Force add Mojang repository
        project.getLogger().lifecycle("[Loom]: Adding MC as repository.");
        RepositoryUtils.ensureRepositoriesExist(project);

		Configuration minecraftNamedConfig = project.getConfigurations().maybeCreate(Constants.MINECRAFT_NAMED);
		minecraftNamedConfig.setTransitive(false); // The launchers do not recurse dependencies
		Configuration minecraftIntermediaryConfig = project.getConfigurations().maybeCreate(Constants.MINECRAFT_INTERMEDIARY);
		minecraftIntermediaryConfig.setTransitive(false);
		Configuration minecraftDependenciesConfig = project.getConfigurations().maybeCreate(Constants.MINECRAFT_DEPENDENCIES);
		minecraftDependenciesConfig.setTransitive(false);
		Configuration minecraftConfig = project.getConfigurations().maybeCreate(Constants.MINECRAFT);
		minecraftConfig.setTransitive(false);

		Configuration includeConfig = project.getConfigurations().maybeCreate(Constants.INCLUDE);
		includeConfig.setTransitive(false); // Dont get transitive deps

		project.getConfigurations().maybeCreate(Constants.MAPPINGS);
		project.getConfigurations().maybeCreate(Constants.MAPPINGS_FINAL);

		extendsFrom("compile", Constants.MINECRAFT_NAMED);
		extendsFrom("annotationProcessor", Constants.MINECRAFT_NAMED);

		extendsFrom(Constants.MINECRAFT_NAMED, Constants.MINECRAFT_DEPENDENCIES);
		extendsFrom(Constants.MINECRAFT_INTERMEDIARY, Constants.MINECRAFT_DEPENDENCIES);

		extendsFrom("compile", Constants.MAPPINGS_FINAL);
		extendsFrom("annotationProcessor", Constants.MAPPINGS_FINAL);

		configureIDEs();
		configureCompile();
		configureScala();

		Map<Project, Set<Task>> taskMap = project.getAllTasks(true);

		for (Map.Entry<Project, Set<Task>> entry : taskMap.entrySet()) {
			Project project = entry.getKey();
			Set<Task> taskSet = entry.getValue();

			for (Task task : taskSet) {
				if (task instanceof JavaCompile && !(task.getName().contains("Test")) && !(task.getName().contains("test"))) {
					JavaCompile javaCompileTask = (JavaCompile) task;
					javaCompileTask.doFirst(task1 -> {
						project.getLogger().lifecycle(":setting java compiler args");

						try {
							javaCompileTask.getOptions().getCompilerArgs().add("-AinMapFileNamedIntermediary=" + extension.getMappingsProvider().tinyMappings.getCanonicalPath());
							javaCompileTask.getOptions().getCompilerArgs().add("-AoutMapFileNamedIntermediary=" + extension.getMappingsProvider().mappingsMixinExport.getCanonicalPath());
							javaCompileTask.getOptions().getCompilerArgs().add("-AoutRefMapFile=" + new File(javaCompileTask.getDestinationDir(), extension.getRefmapName()).getCanonicalPath());
							javaCompileTask.getOptions().getCompilerArgs().add("-AdefaultObfuscationEnv=named:intermediary");
						} catch (IOException e) {
							e.printStackTrace();
						}
					});
				}
			}
		}
	}

	public Project getProject() {
		return project;
	}

	protected void configureScala() {
		project.afterEvaluate(proj -> {
			if (project.getPluginManager().hasPlugin("scala")) {
				ScalaCompile task = (ScalaCompile) project.getTasks().getByName("compileScala");
				LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
				project.getLogger().warn(":configuring scala compilation processing");

				try {
					task.getOptions().getCompilerArgs().add("-AinMapFileNamedIntermediary=" + extension.getMappingsProvider().tinyMappings.getCanonicalPath());
					task.getOptions().getCompilerArgs().add("-AoutMapFileNamedIntermediary=" + extension.getMappingsProvider().mappingsMixinExport.getCanonicalPath());
					task.getOptions().getCompilerArgs().add("-AoutRefMapFile=" + new File(task.getDestinationDir(), extension.getRefmapName()).getCanonicalPath());
					task.getOptions().getCompilerArgs().add("-AdefaultObfuscationEnv=named:intermediary");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Add Minecraft dependencies to IDE dependencies.
	 */
	protected void configureIDEs() {
		// IDEA
		IdeaModel ideaModel = (IdeaModel) project.getExtensions().getByName("idea");

		ideaModel.getModule().getExcludeDirs().addAll(project.files(".gradle", "build", ".idea", "out").getFiles());
		ideaModel.getModule().setDownloadJavadoc(true);
		ideaModel.getModule().setDownloadSources(true);
		ideaModel.getModule().setInheritOutputDirs(true);

        ideaModel.getModule().iml(iml -> {
            iml.withXml(xml -> {
                getProject().getLogger().error(xml.asString().toString());
            });
        });

		// ECLIPSE
		EclipseModel eclipseModel = (EclipseModel) project.getExtensions().getByName("eclipse");
	}

	/**
	 * Add Minecraft dependencies to compile time.
	 */
	protected void configureCompile() {
		JavaPluginConvention javaModule = (JavaPluginConvention) project.getConvention().getPlugins().get("java");

		SourceSet main = javaModule.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
		SourceSet test = javaModule.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);

		Javadoc javadoc = (Javadoc) project.getTasks().getByName(JavaPlugin.JAVADOC_TASK_NAME);
		javadoc.setClasspath(main.getOutput().plus(main.getCompileClasspath()));

		// Add Mixin dependencies
		project.getDependencies().add(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME, "net.fabricmc:fabric-mixin-compile-extensions:" + Constants.MIXIN_COMPILE_EXTENSIONS_VERSION);

		project.afterEvaluate(project1 -> {
			LoomGradleExtension extension = project1.getExtensions().getByType(LoomGradleExtension.class);

			project1.getRepositories().flatDir(flatDirectoryArtifactRepository -> {
				flatDirectoryArtifactRepository.dir(extension.getUserCache());
				flatDirectoryArtifactRepository.setName("UserCacheFiles");
			});

			project1.getRepositories().flatDir(flatDirectoryArtifactRepository -> {
				flatDirectoryArtifactRepository.dir(extension.getRootProjectBuildCache());
				flatDirectoryArtifactRepository.setName("UserLocalCacheFiles");
			});

			project1.getRepositories().flatDir(flatDirectoryArtifactRepository -> {
				flatDirectoryArtifactRepository.dir(extension.getRemappedModCache());
				flatDirectoryArtifactRepository.setName("UserLocalRemappedMods");
			});

			project1.getRepositories().maven(mavenArtifactRepository -> {
				mavenArtifactRepository.setName("Fabric");
				mavenArtifactRepository.setUrl("https://maven.fabricmc.net/");
			});

			/* project1.getRepositories().maven(mavenArtifactRepository -> {
				mavenArtifactRepository.setName("SpongePowered");
				mavenArtifactRepository.setUrl("http://repo.spongepowered.org/maven");
			}); */

			project1.getRepositories().maven(mavenArtifactRepository -> {
				mavenArtifactRepository.setName("Mojang");
				mavenArtifactRepository.setUrl("https://libraries.minecraft.net/");
			});

			project1.getRepositories().mavenCentral();
			project1.getRepositories().jcenter();

			LoomDependencyManager dependencyManager = new LoomDependencyManager();
			extension.setDependencyManager(dependencyManager);

			dependencyManager.addProvider(new MinecraftProvider());
			dependencyManager.addProvider(new MappingsProvider());
			dependencyManager.addProvider(new LaunchProvider());

			dependencyManager.handleDependencies(project1);

			project1.getTasks().getByName("idea").finalizedBy(project1.getTasks().getByName("genIdeaWorkspace"));
			project1.getTasks().getByName("eclipse").finalizedBy(project1.getTasks().getByName("genEclipseRuns"));

			if (extension.autoGenIDERuns && isRootProject(project1)) {
				SetupIntelijRunConfigs.setup(project1);
			}

			// Enables the default mod remapper
			if (extension.remapMod) {
				AbstractArchiveTask jarTask = (AbstractArchiveTask) project1.getTasks().getByName("jar");
				RemapJarTask remapJarTask = (RemapJarTask) project1.getTasks().findByName("remapJar");

				assert remapJarTask != null;

				if (!remapJarTask.getInput().isPresent()) {
					jarTask.setClassifier("dev");
					remapJarTask.setClassifier("");
					remapJarTask.getInput().set(jarTask.getArchivePath());
				}

				extension.addUnmappedMod(jarTask.getArchivePath().toPath());
				remapJarTask.getAddNestedDependencies().set(true);

				project1.getArtifacts().add("archives", remapJarTask);
				remapJarTask.dependsOn(jarTask);
				project1.getTasks().getByName("build").dependsOn(remapJarTask);

				Map<Project, Set<Task>> taskMap = project.getAllTasks(true);

				for (Map.Entry<Project, Set<Task>> entry : taskMap.entrySet()) {
					Set<Task> taskSet = entry.getValue();

					for (Task task : taskSet) {
						if (task instanceof RemapJarTask && ((RemapJarTask) task).getAddNestedDependencies().getOrElse(false)) {
							//Run all the sub project remap jars tasks before the root projects jar, this is to allow us to include projects
							NestedJars.getRequiredTasks(project1).forEach(task::dependsOn);
						}
					}
				}

				try {
					AbstractArchiveTask sourcesTask = (AbstractArchiveTask) project1.getTasks().getByName("sourcesJar");

					RemapSourcesJarTask remapSourcesJarTask = (RemapSourcesJarTask) project1.getTasks().findByName("remapSourcesJar");
					remapSourcesJarTask.setInput(sourcesTask.getArchivePath());
					remapSourcesJarTask.setOutput(sourcesTask.getArchivePath());
					remapSourcesJarTask.doLast(task -> project1.getArtifacts().add("archives", remapSourcesJarTask.getOutput()));
					remapSourcesJarTask.dependsOn(project1.getTasks().getByName("sourcesJar"));
					project1.getTasks().getByName("build").dependsOn(remapSourcesJarTask);
				} catch (UnknownTaskException e) {
					// pass
				}
			} else {
				AbstractArchiveTask jarTask = (AbstractArchiveTask) project1.getTasks().getByName("jar");
				extension.addUnmappedMod(jarTask.getArchivePath().toPath());
			}
		});
	}
}
