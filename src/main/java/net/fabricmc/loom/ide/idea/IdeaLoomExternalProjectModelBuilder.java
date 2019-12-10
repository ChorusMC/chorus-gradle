package net.fabricmc.loom.ide.idea;

import static org.jetbrains.plugins.gradle.tooling.builder.ExternalProjectBuilderImpl.getFilters;
import static org.jetbrains.plugins.gradle.tooling.builder.ModelBuildersDataProviders.TASKS_PROVIDER;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.IExternalSystemSourceType;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.idea.model.IdeaProject;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.jetbrains.plugins.gradle.model.DefaultExternalFilter;
import org.jetbrains.plugins.gradle.model.DefaultExternalProject;
import org.jetbrains.plugins.gradle.model.DefaultExternalSourceDirectorySet;
import org.jetbrains.plugins.gradle.model.DefaultExternalSourceSet;
import org.jetbrains.plugins.gradle.model.DefaultExternalTask;
import org.jetbrains.plugins.gradle.model.ExternalDependency;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalProjectPreview;
import org.jetbrains.plugins.gradle.model.ExternalSourceDirectorySet;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.builder.ExternalProjectBuilderImpl;
import org.jetbrains.plugins.gradle.tooling.builder.ProjectExtensionsDataBuilderImpl;
import org.jetbrains.plugins.gradle.tooling.builder.TasksFactory;
import org.jetbrains.plugins.gradle.tooling.util.JavaPluginUtil;

import net.fabricmc.loom.ide.idea.classloading.ClassLoadingTransferer;
import net.fabricmc.loom.ide.idea.resolving.IdeaLoomDependencyResolver;
import net.fabricmc.loom.ide.idea.resolving.IdeaLoomSourceSetCachedFinder;

public class IdeaLoomExternalProjectModelBuilder implements ToolingModelBuilder {

    @Override
    public boolean canBuild(String modelName) {
        return ExternalProject.class.getName().equals(modelName) || ExternalProjectPreview.class.getName().equals(modelName);
    }

    @Override
    public Object buildAll(final String modelName, final Project project) {
        if (!project.equals(project.getRootProject())) return null;


        final ModelBuilderContext context = new IdeaLoomModelBuilderContext(project);
        final Map<Project, ExternalProject> cache = context.getData(PROJECTS_PROVIDER);
        final TasksFactory tasksFactory = context.getData(TASKS_PROVIDER);
        final IdeaLoomSourceSetCachedFinder sourceSetCachedFinder = new IdeaLoomSourceSetCachedFinder(context);
        final DefaultExternalProject externalProject = (DefaultExternalProject) doBuild(modelName, project, cache, tasksFactory, sourceSetCachedFinder);
        try {
            return ClassLoadingTransferer.transferToOtherClassLoader(externalProject);
        }
        catch (IOException | InvocationTargetException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to transfer: " + project.getName() + " to the IDEA class loader.", e);
        }
    }

    private static Object doBuild(
            final String modelName,
            final Project gradleProject,
            final Map<Project, ExternalProject> cache,
            final TasksFactory tasksFactory,
            final IdeaLoomSourceSetCachedFinder sourceSetFinder) {
        final ExternalProject externalProject = cache.get(gradleProject);
        if (externalProject != null) return externalProject;

        boolean resolveSourceSetDependencies = Boolean.parseBoolean(System.getProperties().getProperty("idea.resolveSourceSetDependencies", "true"));
        boolean isPreview = ExternalProjectPreview.class.getName().equals(modelName);
        DefaultExternalProject defaultExternalProject = new DefaultExternalProject();
        defaultExternalProject.setExternalSystemId("GRADLE");
        defaultExternalProject.setName(gradleProject.getName());
        String qName = ":".equals(gradleProject.getPath()) ? gradleProject.getName() : gradleProject.getPath();
        defaultExternalProject.setQName(qName);
        final IdeaPlugin ideaPlugin = gradleProject.getPlugins().findPlugin(IdeaPlugin.class);
        final IdeaModel model = (ideaPlugin == null ? null : ideaPlugin.getModel());
        IdeaModule ideaPluginModule = (model == null ? null : model.getModule());
        final Gradle parent = gradleProject.getGradle().getParent();
        Project parentBuildRootProject = (parent == null ? null : parent.getRootProject());
        final IdeaModel model1 = (ideaPlugin == null ? null : ideaPlugin.getModel());
        final IdeaProject ideaProject = (model1 == null ? null : model1.getProject());
        final String name = (ideaProject == null ? null : ideaProject.getName());
        String compositePrefix = parentBuildRootProject != null && !DefaultGroovyMethods.is(gradleProject.getRootProject(), parentBuildRootProject) && !":".equals(gradleProject.getPath()) ? (
                name != null
                        ? name
                        : gradleProject.getRootProject().getName()) : "";
        final String name1 = (ideaPluginModule == null ? null : ideaPluginModule.getName());
        String ideaModuleName = name1 != null ? name1 : ideaProject.getName();
        defaultExternalProject.setId(compositePrefix + (":".equals(gradleProject.getPath()) ? ideaModuleName : qName));
        defaultExternalProject.setVersion(wrap(gradleProject.getVersion()));
        defaultExternalProject.setDescription(gradleProject.getDescription());
        defaultExternalProject.setBuildDir(gradleProject.getBuildDir());
        defaultExternalProject.setBuildFile(gradleProject.getBuildFile());
        defaultExternalProject.setGroup(wrap(gradleProject.getGroup()));
        defaultExternalProject.setProjectDir(gradleProject.getProjectDir());
        defaultExternalProject.setSourceSets(getSourceSets(gradleProject, isPreview, resolveSourceSetDependencies, sourceSetFinder));
        defaultExternalProject.setTasks(getTasks(gradleProject, tasksFactory));

        addArtifactsData(gradleProject, defaultExternalProject);

        final Map<String, DefaultExternalProject> childProjects = new TreeMap<>();
        for (Map.Entry<String, Project> projectEntry : gradleProject.getChildProjects().entrySet()) {
            final Object externalProjectChild = doBuild(modelName, projectEntry.getValue(), cache, tasksFactory, sourceSetFinder);
            if (externalProjectChild instanceof DefaultExternalProject) {
                childProjects.put(projectEntry.getKey(), (DefaultExternalProject) externalProjectChild);
            }
            else if (externalProjectChild instanceof ExternalProject) {
                // convert from proxy to our model class
                childProjects.put(projectEntry.getKey(), new DefaultExternalProject((ExternalProject) externalProjectChild));
            }
        }

        defaultExternalProject.setChildProjects(childProjects);

        return defaultExternalProject;
    }

    public static void addArtifactsData(final Project gradleProject, DefaultExternalProject externalProject) {
        final List<File> artifacts = new ArrayList<>();
        for (Jar jar : gradleProject.getTasks().withType(Jar.class)) {
            try {
                // TODO use getArchiveFile method since Gradle 5.1
                artifacts.add(jar.getArchivePath());
            }
            catch (Exception e) {
                // TODO add reporting for such issues
                gradleProject.getLogger().error("warning: [task " + jar.getPath() + "] " + e.getMessage());
            }
        }

        externalProject.setArtifacts(artifacts);

        SortedMap<String, Configuration> configurationsByName = gradleProject.getConfigurations().getAsMap();
        Map<String, Set<File>> artifactsByConfiguration = new HashMap<>();
        for (Map.Entry<String, Configuration> configurationEntry : configurationsByName.entrySet()) {
            Set<File> files = configurationEntry.getValue().getArtifacts().getFiles().getFiles();
            artifactsByConfiguration.put(configurationEntry.getKey(), new LinkedHashSet<>(files));
        }

        externalProject.setArtifactsByConfiguration(artifactsByConfiguration);
    }

    public static Map<String, DefaultExternalTask> getTasks(Project gradleProject, TasksFactory tasksFactory) {
        Map<String, DefaultExternalTask> result = Maps.newHashMap();

        for (Task task : tasksFactory.getTasks(gradleProject)) {
            DefaultExternalTask externalTask = result.get(task.getName());
            if (externalTask == null) {
                externalTask = new DefaultExternalTask();
                externalTask.setName(task.getName());
                externalTask.setQName(task.getName());
                externalTask.setDescription(task.getDescription());
                externalTask.setGroup(task.getGroup() != null && !task.getGroup().isEmpty() ? task.getGroup() : "other");
                final ExtensionContainer extensions = task.getExtensions();
                ExtraPropertiesExtension ext = (extensions == null ? null : extensions.getExtraProperties());
                externalTask.setTest((task instanceof Test) || (ext.has("idea.internal.test") && Boolean.parseBoolean(String.valueOf(ext.get("idea.internal.test")))));
                externalTask.setType(ProjectExtensionsDataBuilderImpl.getType(task));
                result.put(externalTask.getName(), externalTask);
            }


            String projectTaskPath = (gradleProject.getPath().equals(":") ? ":" : gradleProject.getPath() + ":") + task.getName();
            if (projectTaskPath.equals(task.getPath())) {
                externalTask.setQName(task.getPath());
            }
        }

        return result;
    }

    private static Map<String, DefaultExternalSourceSet> getSourceSets(
            final Project gradleProject,
            final boolean isPreview,
            final boolean resolveSourceSetDependencies,
            final IdeaLoomSourceSetCachedFinder sourceSetFinder) {
        final IdeaPlugin ideaPlugin = gradleProject.getPlugins().findPlugin(IdeaPlugin.class);
        final IdeaModel model = (ideaPlugin == null ? null : ideaPlugin.getModel());
        final IdeaModule ideaPluginModule = (model == null ? null : model.getModule());
        final boolean inheritOutputDirs = (ideaPluginModule == null ? null : ideaPluginModule.getInheritOutputDirs());
        final File ideaPluginOutDir = (ideaPluginModule == null ? null : ideaPluginModule.getOutputDir());
        final File ideaPluginTestOutDir = (ideaPluginModule == null ? null : ideaPluginModule.getTestOutputDir());
        final Set<File> generatedSourceDirs = ideaPluginModule == null ? null : ideaPluginModule.getGeneratedSourceDirs();
        final Set<File> ideaSourceDirs = ideaPluginModule == null ? null : ideaPluginModule.getSourceDirs();
        final Set<File> ideaResourceDirs = ideaPluginModule == null ? null : ideaPluginModule.getResourceDirs();
        final Set<File> ideaTestSourceDirs = ideaPluginModule == null ? null : ideaPluginModule.getTestSourceDirs();
        final Set<File> ideaTestResourceDirs = ideaPluginModule == null ? null : ideaPluginModule.getTestResourceDirs();
        final boolean downloadJavadoc = ideaPluginModule != null && ideaPluginModule.isDownloadJavadoc();
        final boolean downloadSources = ideaPluginModule != null && ideaPluginModule.isDownloadSources();

        JavaPluginConvention javaPluginConvention = JavaPluginUtil.getJavaPluginConvention(gradleProject);
        final String projectSourceCompatibility = javaPluginConvention != null ? javaPluginConvention.getSourceCompatibility().toString() : "";
        final String projectTargetCompatibility = javaPluginConvention != null ? javaPluginConvention.getTargetCompatibility().toString() : "";


        final Map<String, DefaultExternalSourceSet> result = Maps.newHashMap();
        SourceSetContainer sourceSets = JavaPluginUtil.getSourceSetContainer(gradleProject);
        if (sourceSets == null) {
            return result;
        }

        // ignore inherited source sets from parent project
        SourceSetContainer parentProjectSourceSets = gradleProject.getParent() == null ? null : JavaPluginUtil.getSourceSetContainer(gradleProject.getParent());
        if (parentProjectSourceSets != null && sourceSets == parentProjectSourceSets) {
            return result;
        }

        final Iterator<List> iterator = getFilters(gradleProject, "processResources").iterator();
        List<String> resourcesIncludes = iterator.hasNext() ? iterator.next() : null;
        List<String> resourcesExcludes = iterator.hasNext() ? iterator.next() : null;
        List<DefaultExternalFilter> filterReaders = iterator.hasNext() ? iterator.next() : null;

        final Iterator<List> iterator1 = getFilters(gradleProject, "processTestResources").iterator();
        List<String> testResourcesIncludes = iterator1.hasNext() ? iterator1.next() : null;
        List<String> testResourcesExcludes = iterator1.hasNext() ? iterator1.next() : null;
        List<DefaultExternalFilter> testFilterReaders = iterator1.hasNext() ? iterator1.next() : null;

        //ORION: Commented out in original as well. Leaving here for clarity.
        //def (javaIncludes,javaExcludes) = getFilters(project,'compileJava')

        final Collection<File> additionalIdeaGenDirs = Lists.newArrayList();
        if (generatedSourceDirs != null && !generatedSourceDirs.isEmpty()) {
            additionalIdeaGenDirs.addAll(generatedSourceDirs);
        }

        sourceSets.all(sourceSet -> {
            DefaultExternalSourceSet externalSourceSet = new DefaultExternalSourceSet();
            externalSourceSet.setName(sourceSet.getName());

            Task javaCompileTask = gradleProject.getTasks().findByName(sourceSet.getCompileJavaTaskName());
            if (javaCompileTask instanceof JavaCompile) {
                final String compileTaskSourceCompatibility = ((JavaCompile) javaCompileTask).getSourceCompatibility();
                externalSourceSet.setSourceCompatibility(compileTaskSourceCompatibility != null ? compileTaskSourceCompatibility : projectSourceCompatibility);
                final String compileTaskTargetCompatibility = ((JavaCompile) javaCompileTask).getTargetCompatibility();
                externalSourceSet.setTargetCompatibility(compileTaskTargetCompatibility != null ? compileTaskTargetCompatibility : projectTargetCompatibility);
            }
            else {
                externalSourceSet.setSourceCompatibility(projectSourceCompatibility);
                externalSourceSet.setTargetCompatibility(projectTargetCompatibility);
            }


            Task jarTask = gradleProject.getTasks().findByName(sourceSet.getJarTaskName());
            if (jarTask instanceof AbstractArchiveTask) {
                externalSourceSet.setArtifacts(new ArrayList<>(Collections.singletonList(((AbstractArchiveTask) jarTask).getArchivePath())));
            }


            Map<ExternalSystemSourceType, DefaultExternalSourceDirectorySet> sources = Maps.newHashMap();
            DefaultExternalSourceDirectorySet resourcesDirectorySet = new DefaultExternalSourceDirectorySet();
            resourcesDirectorySet.setName(sourceSet.getResources().getName());
            resourcesDirectorySet.setSrcDirs(sourceSet.getResources().getSrcDirs());
            if (ideaPluginOutDir != null && SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName())) {
                resourcesDirectorySet.addGradleOutputDir(ideaPluginOutDir);
            }

            if (ideaPluginTestOutDir != null && SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName())) {
                resourcesDirectorySet.addGradleOutputDir(ideaPluginTestOutDir);
            }

            if (is4OrBetter) {
                if (sourceSet.getOutput().getResourcesDir() != null) {
                    resourcesDirectorySet.addGradleOutputDir(sourceSet.getOutput().getResourcesDir());
                }
                else {
                    for (File outDir : sourceSet.getOutput().getClassesDirs().getFiles()) {
                        resourcesDirectorySet.addGradleOutputDir(outDir);
                    }

                    if (resourcesDirectorySet.getGradleOutputDirs().isEmpty()) {
                        resourcesDirectorySet.addGradleOutputDir(gradleProject.getBuildDir());
                    }
                }
            }
            else {
                resourcesDirectorySet.addGradleOutputDir((File) ExternalProjectBuilderImpl.chooseNotNull(sourceSet.getOutput().getResourcesDir(),
                                sourceSet.getOutput().getClassesDirs(),
                                gradleProject.getBuildDir()));
            }


            File ideaOutDir = new File(gradleProject.getProjectDir(),
                            "out/" + (SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName()) || (!resolveSourceSetDependencies && !SourceSet.TEST_SOURCE_SET_NAME.equals(
                                            sourceSet.getName())) ? "production" : GUtil.toLowerCamelCase(sourceSet.getName())));
            resourcesDirectorySet.setOutputDir(new File(ideaOutDir, "resources"));
            resourcesDirectorySet.setInheritedCompilerOutput(inheritOutputDirs);

            DefaultExternalSourceDirectorySet javaDirectorySet = new DefaultExternalSourceDirectorySet();
            javaDirectorySet.setName(sourceSet.getAllJava().getName());
            javaDirectorySet.setSrcDirs(sourceSet.getAllJava().getSrcDirs());
            if (ideaPluginOutDir != null && SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName())) {
                javaDirectorySet.addGradleOutputDir(ideaPluginOutDir);
            }

            if (ideaPluginTestOutDir != null && SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName())) {
                javaDirectorySet.addGradleOutputDir(ideaPluginTestOutDir);
            }

            if (is4OrBetter) {
                for (File outDir : sourceSet.getOutput().getClassesDirs().getFiles()) {
                    javaDirectorySet.addGradleOutputDir(outDir);
                }

                if (javaDirectorySet.getGradleOutputDirs().isEmpty()) {
                    javaDirectorySet.addGradleOutputDir(gradleProject.getBuildDir());
                }
            }
            else {
                javaDirectorySet.addGradleOutputDir(
                                (File) ExternalProjectBuilderImpl.chooseNotNull(sourceSet.getOutput().getClassesDirs(), gradleProject.getBuildDir())
                );
            }


            javaDirectorySet.setOutputDir(new File(ideaOutDir, "classes"));
            javaDirectorySet.setInheritedCompilerOutput(inheritOutputDirs);
            //ORION: Commented out in the original as well. Leaving here for clarity.
//      javaDirectorySet.excludes = javaExcludes + sourceSet.java.excludes;
//      javaDirectorySet.includes = javaIncludes + sourceSet.java.includes;

            DefaultExternalSourceDirectorySet generatedDirectorySet = null;
            boolean hasExplicitlyDefinedGeneratedSources =
                            generatedSourceDirs != null && !generatedSourceDirs.isEmpty();
            if (hasExplicitlyDefinedGeneratedSources) {

                HashSet<File> files = new HashSet<>();
                for (File file : generatedSourceDirs) {
                    if (javaDirectorySet.getSrcDirs().contains(file)) {
                        files.add(file);
                    }
                }


                if (!files.isEmpty()) {
                    javaDirectorySet.getSrcDirs().removeAll(files);
                    generatedDirectorySet = new DefaultExternalSourceDirectorySet();
                    generatedDirectorySet.setName("generated " + javaDirectorySet.getName());
                    generatedDirectorySet.setSrcDirs(files);
                    for (File file : javaDirectorySet.getGradleOutputDirs()) {
                        generatedDirectorySet.addGradleOutputDir(file);
                    }

                    generatedDirectorySet.setOutputDir(javaDirectorySet.getOutputDir());
                    generatedDirectorySet.setInheritedCompilerOutput(javaDirectorySet.isCompilerOutputPathInherited());
                }

                additionalIdeaGenDirs.removeAll(files);
            }


            if (SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName())) {
                if (!inheritOutputDirs && ideaPluginTestOutDir != null) {
                    javaDirectorySet.setOutputDir( ideaPluginTestOutDir);
                    resourcesDirectorySet.setOutputDir(ideaPluginTestOutDir);
                }

                final Set<String> resourceDirectoryExcludes = Sets.newHashSet(testResourcesExcludes);
                resourceDirectoryExcludes.addAll(sourceSet.getResources().getExcludes());
                resourcesDirectorySet.setExcludes(resourceDirectoryExcludes);

                final Set<String> resourceDirectoryIncludes = Sets.newHashSet(testResourcesIncludes);
                resourceDirectoryIncludes.addAll(sourceSet.getResources().getIncludes());
                resourcesDirectorySet.setIncludes(resourceDirectoryIncludes);

                resourcesDirectorySet.setFilters(Lists.newArrayList(testFilterReaders));
                sources.put(ExternalSystemSourceType.TEST, javaDirectorySet);
                sources.put(ExternalSystemSourceType.TEST_RESOURCE, resourcesDirectorySet);
                if (generatedDirectorySet != null) {
                    sources.put(ExternalSystemSourceType.TEST_GENERATED, generatedDirectorySet);
                }
            }
            else {
                boolean isTestSourceSet = false;
                if (!inheritOutputDirs && resolveSourceSetDependencies && !SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName()) && ideaTestSourceDirs != null
                                    && ideaTestSourceDirs.containsAll(javaDirectorySet.getSrcDirs())) {
                    javaDirectorySet.setOutputDir(
                                    ideaPluginTestOutDir != null ? ideaPluginTestOutDir : new File(gradleProject.getProjectDir(), "out/test/classes"));
                    resourcesDirectorySet.setOutputDir(
                                    ideaPluginTestOutDir != null ? ideaPluginTestOutDir : new File(gradleProject.getProjectDir(), "out/test/resources"));
                    sources.put(ExternalSystemSourceType.TEST, javaDirectorySet);
                    sources.put(ExternalSystemSourceType.TEST_RESOURCE, resourcesDirectorySet);
                    isTestSourceSet = true;
                }
                else if (!inheritOutputDirs && ideaPluginOutDir != null) {
                    javaDirectorySet.setOutputDir(ideaPluginOutDir);
                    resourcesDirectorySet.setOutputDir(ideaPluginOutDir);
                }

                final Set<String> resourceDirectoryExcludes = Sets.newHashSet(resourcesExcludes);
                resourceDirectoryExcludes.addAll(sourceSet.getResources().getExcludes());
                resourcesDirectorySet.setExcludes(resourceDirectoryExcludes);

                final Set<String> resourceDirectoryIncludes = Sets.newHashSet(resourcesIncludes);
                resourceDirectoryIncludes.addAll(sourceSet.getResources().getIncludes());
                resourcesDirectorySet.setIncludes(resourceDirectoryIncludes);

                resourcesDirectorySet.setFilters(Lists.newArrayList(filterReaders));

                if (!isTestSourceSet) {
                    sources.put(ExternalSystemSourceType.SOURCE, javaDirectorySet);
                    sources.put(ExternalSystemSourceType.RESOURCE, resourcesDirectorySet);
                }


                if (!resolveSourceSetDependencies && ideaTestSourceDirs != null) {
                    Set<File> testDirs = javaDirectorySet.getSrcDirs().stream().filter(ideaTestSourceDirs::contains).collect(Collectors.toSet());
                    if (!testDirs.isEmpty()) {
                        javaDirectorySet.getSrcDirs().removeAll(ideaTestSourceDirs);
                        DefaultExternalSourceDirectorySet testDirectorySet = new DefaultExternalSourceDirectorySet();
                        testDirectorySet.setName(javaDirectorySet.getName());
                        testDirectorySet.setSrcDirs(testDirs);
                        testDirectorySet.addGradleOutputDir(javaDirectorySet.getOutputDir());
                        testDirectorySet.setOutputDir(
                                        ideaPluginTestOutDir != null ? ideaPluginTestOutDir : new File(gradleProject.getProjectDir(), "out/test/classes"));
                        testDirectorySet.setInheritedCompilerOutput(javaDirectorySet.isCompilerOutputPathInherited());
                        sources.put(ExternalSystemSourceType.TEST, testDirectorySet);
                    }


                    Set<File> testResourcesDirs = resourcesDirectorySet.getSrcDirs().stream().filter(ideaTestSourceDirs::contains).collect(Collectors.toSet());
                    if (!testResourcesDirs.isEmpty()) {
                        resourcesDirectorySet.getSrcDirs().removeAll(ideaTestSourceDirs);

                        DefaultExternalSourceDirectorySet testResourcesDirectorySet = new DefaultExternalSourceDirectorySet();
                        testResourcesDirectorySet.setName(resourcesDirectorySet.getName());
                        testResourcesDirectorySet.setSrcDirs(testResourcesDirs);
                        testResourcesDirectorySet.addGradleOutputDir(resourcesDirectorySet.getOutputDir());
                        testResourcesDirectorySet.setOutputDir(
                                        ideaPluginTestOutDir != null ? ideaPluginTestOutDir : new File(gradleProject.getProjectDir(), "out/test/resources"));
                        testResourcesDirectorySet.setInheritedCompilerOutput(resourcesDirectorySet.isCompilerOutputPathInherited());
                        sources.put(ExternalSystemSourceType.TEST_RESOURCE, testResourcesDirectorySet);
                    }
                }


                if (generatedDirectorySet != null) {
                    sources.put(ExternalSystemSourceType.SOURCE_GENERATED, generatedDirectorySet);
                    if (!resolveSourceSetDependencies && ideaTestSourceDirs != null) {
                        Set<File> testGeneratedDirs = generatedDirectorySet.getSrcDirs().stream().filter(ideaTestSourceDirs::contains).collect(Collectors.toSet());
                        if (!testGeneratedDirs.isEmpty()) {
                            generatedDirectorySet.getSrcDirs().removeAll(ideaTestSourceDirs);

                            DefaultExternalSourceDirectorySet testGeneratedDirectorySet = new DefaultExternalSourceDirectorySet();
                            testGeneratedDirectorySet.setName(generatedDirectorySet.getName());
                            testGeneratedDirectorySet.setSrcDirs(testGeneratedDirs);
                            testGeneratedDirectorySet.addGradleOutputDir(generatedDirectorySet.getOutputDir());
                            testGeneratedDirectorySet.setOutputDir(generatedDirectorySet.getOutputDir());
                            testGeneratedDirectorySet.setInheritedCompilerOutput(generatedDirectorySet.isCompilerOutputPathInherited());

                            sources.put(ExternalSystemSourceType.TEST_GENERATED, testGeneratedDirectorySet);
                        }
                    }
                }


                if (ideaPluginModule != null && !SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName()) && !SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName())) {
                    for (DefaultExternalSourceDirectorySet sourceDirectorySet : sources.values()) {
                        ideaSourceDirs.removeAll(sourceDirectorySet.getSrcDirs());
                        ideaResourceDirs.removeAll(sourceDirectorySet.getSrcDirs());
                        ideaTestSourceDirs.removeAll(sourceDirectorySet.getSrcDirs());
                        ideaTestResourceDirs.removeAll(sourceDirectorySet.getSrcDirs());
                    }
                }
            }


            if (resolveSourceSetDependencies) {
                Collection<ExternalDependency> dependencies = new IdeaLoomDependencyResolver(gradleProject,
                                isPreview,
                                downloadJavadoc,
                                downloadSources,
                                sourceSetFinder).resolveDependencies(sourceSet);
                externalSourceSet.getDependencies().addAll(dependencies);
            }


            externalSourceSet.setSources(sources);
            result.put(sourceSet.getName(), externalSourceSet);
        });

        DefaultExternalSourceSet mainSourceSet = result.get(SourceSet.MAIN_SOURCE_SET_NAME);
        if (ideaPluginModule != null && mainSourceSet != null && ideaSourceDirs != null && !ideaSourceDirs.isEmpty()) {
            SourceSet mainGradleSourceSet = sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME);
            if (mainGradleSourceSet != null) {
                ExternalSourceDirectorySet mainSourceDirectorySet = mainSourceSet.getSources().get(ExternalSystemSourceType.SOURCE);
                if (mainSourceDirectorySet!= null) {
                    mainSourceDirectorySet.getSrcDirs().addAll(ideaSourceDirs.stream().filter(file -> !mainGradleSourceSet.getResources().getSrcDirs().contains(file)).filter(file -> generatedSourceDirs == null || !generatedSourceDirs.contains(file)).collect(
                                    Collectors.toSet()));
                }

                ExternalSourceDirectorySet mainResourceDirectorySet = mainSourceSet.getSources().get(ExternalSystemSourceType.RESOURCE);
                if (mainResourceDirectorySet != null) {
                    mainResourceDirectorySet.getSrcDirs().addAll(ideaResourceDirs);
                }


                if (!additionalIdeaGenDirs.isEmpty()) {
                    Collection<File> mainAdditionalGenDirs = additionalIdeaGenDirs.stream().filter(ideaSourceDirs::contains).collect(Collectors.toSet());
                    ExternalSourceDirectorySet mainGenSourceDirectorySet = mainSourceSet.getSources().get(ExternalSystemSourceType.SOURCE_GENERATED);
                    if (mainGenSourceDirectorySet != null) {
                        mainGenSourceDirectorySet.getSrcDirs().addAll(mainAdditionalGenDirs);
                    }
                    else {
                        DefaultExternalSourceDirectorySet generatedDirectorySet = new DefaultExternalSourceDirectorySet();
                        generatedDirectorySet.setName("generated " + mainSourceSet.getName());
                        generatedDirectorySet.getSrcDirs().addAll(mainAdditionalGenDirs);
                        generatedDirectorySet.addGradleOutputDir(mainSourceDirectorySet.getOutputDir());
                        generatedDirectorySet.setOutputDir(mainSourceDirectorySet.getOutputDir());
                        generatedDirectorySet.setInheritedCompilerOutput(mainSourceDirectorySet.isCompilerOutputPathInherited());

                        ((Map) mainSourceSet.getSources()).put(ExternalSystemSourceType.SOURCE_GENERATED, generatedDirectorySet);
                    }
                }
            }
        }


        DefaultExternalSourceSet testSourceSet = result.get(SourceSet.TEST_SOURCE_SET_NAME);
        if (ideaPluginModule != null && testSourceSet != null && ideaTestSourceDirs != null && !ideaTestSourceDirs.isEmpty()) {
            SourceSet testGradleSourceSet = sourceSets.findByName(SourceSet.TEST_SOURCE_SET_NAME);
            if (testGradleSourceSet != null) {
                ExternalSourceDirectorySet testSourceDirectorySet = testSourceSet.getSources().get(ExternalSystemSourceType.TEST);
                if (testSourceDirectorySet != null) {
                    testSourceDirectorySet.getSrcDirs().addAll(ideaTestSourceDirs.stream().filter(file -> !testGradleSourceSet.getResources().getSrcDirs().contains(file)).filter(file -> generatedSourceDirs == null || !generatedSourceDirs.contains(file)).collect(
                                    Collectors.toSet()));
                }

                ExternalSourceDirectorySet testResourceDirectorySet = testSourceSet.getSources().get(ExternalSystemSourceType.TEST_RESOURCE);
                if (testResourceDirectorySet != null) {
                    testResourceDirectorySet.getSrcDirs().addAll(ideaTestResourceDirs);
                }


                if (!additionalIdeaGenDirs.isEmpty()) {
                    Set<File> testAdditionalGenDirs = additionalIdeaGenDirs.stream().filter(ideaTestSourceDirs::contains).collect(Collectors.toSet());
                    ExternalSourceDirectorySet testGenSourceDirectorySet = testSourceSet.getSources().get(ExternalSystemSourceType.TEST_GENERATED);
                    if (testGenSourceDirectorySet != null) {
                        testGenSourceDirectorySet.getSrcDirs().addAll(testAdditionalGenDirs);
                    }
                    else {
                        DefaultExternalSourceDirectorySet generatedDirectorySet = new DefaultExternalSourceDirectorySet();
                        generatedDirectorySet.setName("generated " + testSourceSet.getName());
                        generatedDirectorySet.getSrcDirs().addAll(testAdditionalGenDirs);
                        generatedDirectorySet.addGradleOutputDir(testSourceDirectorySet.getOutputDir());
                        generatedDirectorySet.setOutputDir(testSourceDirectorySet.getOutputDir());
                        generatedDirectorySet.setInheritedCompilerOutput(testSourceDirectorySet.isCompilerOutputPathInherited());


                        ((Map) testSourceSet.getSources()).put(ExternalSystemSourceType.TEST_GENERATED, generatedDirectorySet);
                    }
                }
            }
        }

        cleanupSharedSourceFolders(result);

        return result;
    }


    private static void cleanupSharedSourceFolders(Map<String, DefaultExternalSourceSet> map) {
        ExternalSourceSet mainSourceSet = map.get(SourceSet.MAIN_SOURCE_SET_NAME);
        cleanupSharedSourceFolders(map, mainSourceSet, null);
        cleanupSharedSourceFolders(map, map.get(SourceSet.TEST_SOURCE_SET_NAME), mainSourceSet);
    }

    private static void cleanupSharedSourceFolders(Map<String, DefaultExternalSourceSet> result, ExternalSourceSet sourceSet, ExternalSourceSet toIgnore) {
        if (sourceSet == null) return;

        for (Map.Entry<String, DefaultExternalSourceSet> sourceSetEntry : result.entrySet()) {
            if (!DefaultGroovyMethods.is(sourceSetEntry.getValue(), sourceSet) && !DefaultGroovyMethods.is(sourceSetEntry.getValue(), toIgnore)) {
                ExternalSourceSet customSourceSet = sourceSetEntry.getValue();
                for (ExternalSystemSourceType sourceType : ExternalSystemSourceType.values()) {
                    ExternalSourceDirectorySet customSourceDirectorySet =
                            DefaultGroovyMethods.asType(customSourceSet.getSources().get(sourceType), ExternalSourceDirectorySet.class);
                    if (customSourceDirectorySet != null) {
                        for (Map.Entry<? extends IExternalSystemSourceType, ? extends ExternalSourceDirectorySet> sourceDirEntry : sourceSet.getSources().entrySet()) {
                            customSourceDirectorySet.getSrcDirs().removeAll(sourceDirEntry.getValue().getSrcDirs());
                        }
                    }
                }
            }
        }
    }

    private static String wrap(Object o) {
        return o instanceof CharSequence ? o.toString() : "";
    }

    public static ModelBuilderContext.DataProvider<Map<Project, ExternalProject>> getPROJECTS_PROVIDER() {
        return PROJECTS_PROVIDER;
    }

    private static final boolean                                                         is4OrBetter       =
            GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("4.0")) >= 0;
    private static final ModelBuilderContext.DataProvider<Map<Project, ExternalProject>> PROJECTS_PROVIDER = new ModelBuilderContext.DataProvider<Map<Project, ExternalProject>>() {
        @Override
        public Map<Project, ExternalProject> create(Gradle gradle) {
            return new HashMap<>();
        }
    };
}
