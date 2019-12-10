package net.fabricmc.loom.ide.idea.resolving;

import java.io.File;
import java.util.Collection;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.plugins.gradle.model.ExternalDependency;
import org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl;
import org.jetbrains.plugins.gradle.tooling.util.resolve.ExternalDepsResolutionResult;
import org.jetbrains.plugins.gradle.tooling.util.resolve.RuntimeDependenciesProvider;

public class IdeaLoomRuntimeDependenciesProvider {
    public static final String SCOPE = "RUNTIME";

    private final SourceSet                      mySourceSet;
    private final Project                        myProject;
    private       Configuration                  myConfiguration;
    private       Collection<ExternalDependency> myDependencies;
    private       Collection<File>               myFiles;
    private       Set<File>                      myConfigurationFiles = null;

    public IdeaLoomRuntimeDependenciesProvider(SourceSet sourceSet,
                    Project project) {
        mySourceSet = sourceSet;
        myProject = project;
    }

    public Configuration getConfiguration() {
        return myConfiguration;
    }

    public Set<File> getConfigurationFiles() {
        if (myConfigurationFiles == null) {
            myConfigurationFiles = myConfiguration.getResolvedConfiguration().getLenientConfiguration().getFiles(Specs.SATISFIES_ALL);
        }
        return myConfigurationFiles;
    }

    public Collection<ExternalDependency> getDependencies() {
        return myDependencies;
    }

    public Collection<File> getFiles() {
        return myFiles;
    }

    public IdeaLoomRuntimeDependenciesProvider resolve(IdeaLoomDependencyResolver resolver) {
        String runtimeConfigurationName = mySourceSet.getRuntimeConfigurationName();
        Configuration runtimeClasspathConfiguration = myProject.getConfigurations().findByName(runtimeConfigurationName + "Classpath");
        Configuration originRuntimeConfiguration = myProject.getConfigurations().findByName(runtimeConfigurationName);
        myConfiguration = runtimeClasspathConfiguration != null ? runtimeClasspathConfiguration : originRuntimeConfiguration;

        ExternalDepsResolutionResult externalDepsResolutionResult = resolver.resolveDependencies(myConfiguration, SCOPE);
        myDependencies = externalDepsResolutionResult.getExternalDeps();
        myFiles = externalDepsResolutionResult.getResolvedFiles();
        return this;
    }
}
