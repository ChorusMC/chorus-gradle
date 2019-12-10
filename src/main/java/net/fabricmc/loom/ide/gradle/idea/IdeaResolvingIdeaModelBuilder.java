package net.fabricmc.loom.ide.gradle.idea;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.Dependency;
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.idea.model.IdeaProject;
import org.gradle.plugins.ide.idea.model.ModuleDependency;
import org.gradle.plugins.ide.idea.model.SingleEntryModuleLibrary;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;
import org.gradle.plugins.ide.internal.tooling.GradleProjectBuilder;
import org.gradle.plugins.ide.internal.tooling.IdeaModelBuilder;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaCompilerOutput;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaContentRoot;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaDependency;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaDependencyScope;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaJavaLanguageSettings;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaLanguageLevel;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaModule;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaModuleDependency;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaProject;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaSingleEntryLibraryDependency;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaSourceDirectory;
import org.gradle.plugins.ide.internal.tooling.java.DefaultInstalledJdk;
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleModuleVersion;
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleProject;

public class IdeaResolvingIdeaModelBuilder extends IdeaModelBuilder {
    private final GradleProjectBuilder gradleProjectBuilder;

    private boolean offlineDependencyResolution;

    public IdeaResolvingIdeaModelBuilder(GradleProjectBuilder gradleProjectBuilder, ServiceRegistry services) {
        super(gradleProjectBuilder, services);
        this.gradleProjectBuilder = gradleProjectBuilder;
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.idea.IdeaProject");
    }

    @Override
    public DefaultIdeaProject buildAll(String modelName, Project project) {
        Project root = project.getRootProject();
        applyIdeaPlugin(root);
        DefaultGradleProject rootGradleProject = gradleProjectBuilder.buildAll(project);
        return build(root, rootGradleProject);
    }

    private void applyIdeaPlugin(Project root) {
        Set<Project> allProjects = root.getAllprojects();
        for (Project p : allProjects) {
            p.getPluginManager().apply(IdeaPlugin.class);
        }
        for (IncludedBuild includedBuild : root.getGradle().getIncludedBuilds()) {
            IncludedBuildState includedBuildInternal = (IncludedBuildState) includedBuild;
            applyIdeaPlugin(includedBuildInternal.getConfiguredBuild().getRootProject());
        }
    }

    private DefaultIdeaProject build(Project project, DefaultGradleProject rootGradleProject) {
        IdeaModel ideaModel = ideaPluginFor(project).getModel();
        IdeaProject projectModel = ideaModel.getProject();
        JavaVersion projectSourceLanguageLevel = convertIdeaLanguageLevelToJavaVersion(projectModel.getLanguageLevel());
        JavaVersion projectTargetBytecodeLevel = projectModel.getTargetBytecodeVersion();

        DefaultIdeaProject out = new DefaultIdeaProject()
                                                 .setName(projectModel.getName())
                                                 .setJdkName(projectModel.getJdkName())
                                                 .setLanguageLevel(new DefaultIdeaLanguageLevel(projectModel.getLanguageLevel().getLevel()))
                                                 .setJavaLanguageSettings(new DefaultIdeaJavaLanguageSettings()
                                                                                          .setSourceLanguageLevel(projectSourceLanguageLevel)
                                                                                          .setTargetBytecodeVersion(projectTargetBytecodeLevel)
                                                                                          .setJdk(DefaultInstalledJdk.current()));

        List<DefaultIdeaModule> ideaModules = Lists.newArrayList();
        for (IdeaModule module : projectModel.getModules()) {
            ideaModules.add(createModule(module, out, rootGradleProject));
        }
        out.setChildren(new LinkedList<>(ideaModules));
        return out;
    }

    private IdeaPlugin ideaPluginFor(Project project) {
        return project.getPlugins().getPlugin(IdeaPlugin.class);
    }

    private void buildDependencies(DefaultIdeaModule tapiModule, IdeaModule ideaModule) {
        ideaModule.setOffline(offlineDependencyResolution);

        //This is adapted, normally this would call ideaModule.resolveDependencies()
        Set<Dependency> resolved = this.resolveDependencies(ideaModule);
        List<DefaultIdeaDependency> dependencies = new LinkedList<DefaultIdeaDependency>();
        for (Dependency dependency : resolved) {
            if (dependency instanceof SingleEntryModuleLibrary) {
                SingleEntryModuleLibrary d = (SingleEntryModuleLibrary) dependency;
                DefaultIdeaSingleEntryLibraryDependency defaultDependency = new DefaultIdeaSingleEntryLibraryDependency()
                                                                                            .setFile(d.getLibraryFile())
                                                                                            .setSource(d.getSourceFile())
                                                                                            .setJavadoc(d.getJavadocFile())
                                                                                            .setScope(new DefaultIdeaDependencyScope(d.getScope()))
                                                                                            .setExported(d.isExported());

                if (d.getModuleVersion() != null) {
                    defaultDependency.setGradleModuleVersion(new DefaultGradleModuleVersion(d.getModuleVersion()));
                }
                dependencies.add(defaultDependency);
            } else if (dependency instanceof ModuleDependency) {
                ModuleDependency moduleDependency = (ModuleDependency) dependency;

                DefaultIdeaModuleDependency ideaModuleDependency = new DefaultIdeaModuleDependency(moduleDependency.getName())
                                                                                   .setExported(moduleDependency.isExported())
                                                                                   .setScope(new DefaultIdeaDependencyScope(moduleDependency.getScope()));

                dependencies.add(ideaModuleDependency);
            }
        }
        tapiModule.setDependencies(dependencies);
    }

    private DefaultIdeaModule createModule(IdeaModule ideaModule, DefaultIdeaProject ideaProject, DefaultGradleProject rootGradleProject) {
        DefaultIdeaContentRoot contentRoot = new DefaultIdeaContentRoot()
                                                             .setRootDirectory(ideaModule.getContentRoot())
                                                             .setSourceDirectories(srcDirs(ideaModule.getSourceDirs(), ideaModule.getGeneratedSourceDirs()))
                                                             .setTestDirectories(srcDirs(ideaModule.getTestSourceDirs(), ideaModule.getGeneratedSourceDirs()))
                                                             .setResourceDirectories(srcDirs(ideaModule.getResourceDirs(), ideaModule.getGeneratedSourceDirs()))
                                                             .setTestResourceDirectories(srcDirs(ideaModule.getTestResourceDirs(), ideaModule.getGeneratedSourceDirs()))
                                                             .setExcludeDirectories(ideaModule.getExcludeDirs());

        Project project = ideaModule.getProject();

        DefaultIdeaModule defaultIdeaModule = new DefaultIdeaModule()
                                                              .setName(ideaModule.getName())
                                                              .setParent(ideaProject)
                                                              .setGradleProject(rootGradleProject.findByPath(ideaModule.getProject().getPath()))
                                                              .setContentRoots(Collections.singletonList(contentRoot))
                                                              .setJdkName(ideaModule.getJdkName())
                                                              .setCompilerOutput(new DefaultIdeaCompilerOutput()
                                                                                                 .setInheritOutputDirs(ideaModule.getInheritOutputDirs() != null ? ideaModule.getInheritOutputDirs() : false)
                                                                                                 .setOutputDir(ideaModule.getOutputDir())
                                                                                                 .setTestOutputDir(ideaModule.getTestOutputDir()));
        JavaPluginConvention javaPluginConvention = project.getConvention().findPlugin(JavaPluginConvention.class);
        if (javaPluginConvention != null) {
            final IdeaLanguageLevel ideaModuleLanguageLevel = ideaModule.getLanguageLevel();
            JavaVersion moduleSourceLanguageLevel = convertIdeaLanguageLevelToJavaVersion(ideaModuleLanguageLevel);
            JavaVersion moduleTargetBytecodeVersion = ideaModule.getTargetBytecodeVersion();
            defaultIdeaModule.setJavaLanguageSettings(new DefaultIdeaJavaLanguageSettings()
                                                                      .setSourceLanguageLevel(moduleSourceLanguageLevel)
                                                                      .setTargetBytecodeVersion(moduleTargetBytecodeVersion));
        }
        buildDependencies(defaultIdeaModule, ideaModule);

        return defaultIdeaModule;
    }

    private Set<DefaultIdeaSourceDirectory> srcDirs(Set<File> sourceDirs, Set<File> generatedSourceDirs) {
        Set<DefaultIdeaSourceDirectory> out = new LinkedHashSet<DefaultIdeaSourceDirectory>();
        for (File s : sourceDirs) {
            DefaultIdeaSourceDirectory sourceDirectory = new DefaultIdeaSourceDirectory().setDirectory(s);
            if (generatedSourceDirs.contains(s)) {
                sourceDirectory.setGenerated(true);
            }
            out.add(sourceDirectory);
        }
        return out;
    }

    public IdeaModelBuilder setOfflineDependencyResolution(boolean offlineDependencyResolution) {
        super.setOfflineDependencyResolution(offlineDependencyResolution);
        this.offlineDependencyResolution = offlineDependencyResolution;
        return this;
    }

    private JavaVersion convertIdeaLanguageLevelToJavaVersion(IdeaLanguageLevel ideaLanguageLevel) {
        if (ideaLanguageLevel == null) {
            return null;
        }
        String languageLevel = ideaLanguageLevel.getLevel();
        return JavaVersion.valueOf(languageLevel.replaceFirst("JDK", "VERSION"));
    }

    /**
     * Identical implementation of {@link IdeaModule#resolveDependencies()} but using our custom logic to resolve artifacts and dependencies.
     *
     * @param ideaModule The module to get the dependencies for.
     * @return The dependencies.
     */
    private Set<Dependency> resolveDependencies(IdeaModule ideaModule) {
        ProjectInternal projectInternal = (ProjectInternal) ideaModule.getProject();
        IdeArtifactRegistry ideArtifactRegistry = projectInternal.getServices().get(IdeArtifactRegistry.class);
        ProjectStateRegistry projectRegistry = projectInternal.getServices().get(ProjectStateRegistry.class);

        IdeaResolvingDependenciesProvider ideaResolvingDependenciesProvider = new IdeaResolvingDependenciesProvider(projectInternal, ideArtifactRegistry, projectRegistry);
        return ideaResolvingDependenciesProvider.provide(ideaModule);
    }
}
