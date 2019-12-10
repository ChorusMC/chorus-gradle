package net.fabricmc.loom.ide.gradle.idea;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.internal.Factory;
import org.gradle.plugins.ide.idea.internal.IdeaModuleMetadata;
import org.gradle.plugins.ide.idea.model.Dependency;
import org.gradle.plugins.ide.idea.model.FilePath;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import org.gradle.plugins.ide.idea.model.ModuleDependency;
import org.gradle.plugins.ide.idea.model.Path;
import org.gradle.plugins.ide.idea.model.SingleEntryModuleLibrary;
import org.gradle.plugins.ide.idea.model.internal.GeneratedIdeaScope;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;
import org.gradle.plugins.ide.internal.resolver.IdeDependencyVisitor;
import org.gradle.plugins.ide.internal.resolver.UnresolvedIdeDependencyHandler;

public class IdeaResolvingDependenciesProvider {

    public static final     String                     SCOPE_PLUS = "plus";
    public static final String                     SCOPE_MINUS = "minus";
    private final CustomDependencyResolvingModuleDependencyBuilder moduleDependencyBuilder;
    private final CustomDependencyDependenciesOptimizer            optimizer;
    private final ProjectComponentIdentifier                       currentProjectId;

    public IdeaResolvingDependenciesProvider(Project project, IdeArtifactRegistry artifactRegistry, ProjectStateRegistry projectRegistry) {
        moduleDependencyBuilder = new CustomDependencyResolvingModuleDependencyBuilder(artifactRegistry);
        currentProjectId = projectRegistry.stateFor(project).getComponentIdentifier();
        optimizer = new CustomDependencyDependenciesOptimizer();
    }

    public Set<Dependency> provide(final IdeaModule ideaModule) {
        Set<Dependency> result = Sets.newLinkedHashSet();
        result.addAll(getOutputLocations(ideaModule));
        result.addAll(getDependencies(ideaModule));
        return result;
    }

    private Set<SingleEntryModuleLibrary> getOutputLocations(IdeaModule ideaModule) {
        if (ideaModule.getSingleEntryLibraries() == null) {
            return Collections.emptySet();
        }
        Set<SingleEntryModuleLibrary> outputLocations = Sets.newLinkedHashSet();
        for (Map.Entry<String, Iterable<File>> outputLocation : ideaModule.getSingleEntryLibraries().entrySet()) {
            String scope = outputLocation.getKey();
            for (File file : outputLocation.getValue()) {
                if (file != null && file.isDirectory()) {
                    outputLocations.add(new SingleEntryModuleLibrary(toPath(ideaModule, file), scope));
                }
            }
        }
        return outputLocations;
    }

    private Set<Dependency> getDependencies(IdeaModule ideaModule) {
        Set<Dependency> dependencies = Sets.newLinkedHashSet();
        Map<ComponentSelector, UnresolvedDependencyResult> unresolvedDependencies = Maps.newLinkedHashMap();
        for (GeneratedIdeaScope scope : GeneratedIdeaScope.values()) {
            CustomDependencyResolvingDependenciesVisitor visitor = visitDependencies(ideaModule, scope);
            dependencies.addAll(visitor.getDependencies());
            unresolvedDependencies.putAll(visitor.getUnresolvedDependencies());
        }
        optimizer.optimizeDeps(dependencies);
        new UnresolvedIdeDependencyHandler().log(unresolvedDependencies.values());
        return dependencies;
    }

    private CustomDependencyResolvingDependenciesVisitor visitDependencies(IdeaModule ideaModule, GeneratedIdeaScope scope) {
        ProjectInternal projectInternal = (ProjectInternal) ideaModule.getProject();
        final DependencyHandler handler = projectInternal.getDependencies();
        final Collection<Configuration> plusConfigurations = getPlusConfigurations(ideaModule, scope);
        final Collection<Configuration> minusConfigurations = getMinusConfigurations(ideaModule, scope);

        final CustomDependencyResolvingDependenciesVisitor visitor = new CustomDependencyResolvingDependenciesVisitor(ideaModule, scope.name());
        return projectInternal.getMutationState().withMutableState(new Factory<CustomDependencyResolvingDependenciesVisitor>() {
            @Nullable
            @Override
            public CustomDependencyResolvingDependenciesVisitor create() {
                //Use the custom visitor here instead of the gradle internal generic one.
                new IdeaResolverDependencySet(handler, plusConfigurations, minusConfigurations).visit(visitor);
                return visitor;
            }
        });

    }

    private Collection<Configuration> getPlusConfigurations(IdeaModule ideaModule, GeneratedIdeaScope scope) {
        return getConfigurations(ideaModule, scope, SCOPE_PLUS);
    }

    private Collection<Configuration> getMinusConfigurations(IdeaModule ideaModule, GeneratedIdeaScope scope) {
        return getConfigurations(ideaModule, scope, SCOPE_MINUS);
    }

    private Collection<Configuration> getConfigurations(IdeaModule ideaModule, GeneratedIdeaScope scope, String plusMinus) {
        Map<String, Collection<Configuration>> plusMinusConfigurations = getPlusMinusConfigurations(ideaModule, scope);
        return plusMinusConfigurations.containsKey(plusMinus) ? plusMinusConfigurations.get(plusMinus) : Collections.<Configuration>emptyList();
    }

    private Map<String, Collection<Configuration>> getPlusMinusConfigurations(IdeaModule ideaModule, GeneratedIdeaScope scope) {
        Map<String, Collection<Configuration>> plusMinusConfigurations = ideaModule.getScopes().get(scope.name());
        return plusMinusConfigurations != null ? plusMinusConfigurations : Collections.<String, Collection<Configuration>>emptyMap();
    }

    private FilePath toPath(IdeaModule ideaModule, File file) {
        return file != null ? ideaModule.getPathFactory().path(file) : null;
    }

    /**
     * Custom class does not inherit from {@link IdeDependencyVisitor} cause we resolve differently and as such have different parameters.
     * Logic is identical though.
     */
    public class CustomDependencyResolvingDependenciesVisitor {
        private final IdeaModule ideaModule;
        private final UnresolvedIdeDependencyHandler unresolvedIdeDependencyHandler = new UnresolvedIdeDependencyHandler();
        private final String scope;

        private final List<Dependency> projectDependencies = Lists.newLinkedList();
        private final List<Dependency> moduleDependencies  = Lists.newLinkedList();
        private final List<Dependency>                                   fileDependencies       = Lists.newLinkedList();
        private final Map<ComponentSelector, UnresolvedDependencyResult> unresolvedDependencies = Maps.newLinkedHashMap();

        private CustomDependencyResolvingDependenciesVisitor(IdeaModule ideaModule, String scope) {
            this.ideaModule = ideaModule;
            this.scope = scope;
        }

        public boolean isOffline() {
            return ideaModule.isOffline();
        }

        public boolean downloadSources() {
            return ideaModule.isDownloadSources();
        }

        public boolean downloadJavaDoc() {
            return ideaModule.isDownloadJavadoc();
        }

        public void visitProjectDependency(ResolvedArtifact artifact) {
            ProjectComponentIdentifier projectId = (ProjectComponentIdentifier) artifact.getId().getComponentIdentifier();
            if (!projectId.equals(currentProjectId)) {
                projectDependencies.add(moduleDependencyBuilder.create(projectId, scope));
            }
        }

        public void visitModuleDependency(ResolvedArtifact artifact, Set<ResolvedArtifact> sources, Set<ResolvedArtifact> javaDoc, boolean testDependency) {
            ModuleComponentIdentifier moduleId = (ModuleComponentIdentifier) artifact.getId().getComponentIdentifier();
            SingleEntryModuleLibrary library = new SingleEntryModuleLibrary(toPath(ideaModule, artifact.getFile()), scope);
            library.setModuleVersion(DefaultModuleVersionIdentifier.newId(moduleId.getModuleIdentifier(), moduleId.getVersion()));
            Set<Path> sourcePaths = Sets.newLinkedHashSet();
            for (ResolvedArtifact sourceArtifact : sources) {
                sourcePaths.add(toPath(ideaModule, sourceArtifact.getFile()));
            }
            library.setSources(sourcePaths);
            Set<Path> javaDocPaths = Sets.newLinkedHashSet();
            for (ResolvedArtifact javaDocArtifact : javaDoc) {
                javaDocPaths.add(toPath(ideaModule, javaDocArtifact.getFile()));
            }
            library.setJavadoc(javaDocPaths);
            moduleDependencies.add(library);
        }

        public void visitFileDependency(ResolvedArtifact artifact, boolean testDependency) {
            fileDependencies.add(new SingleEntryModuleLibrary(toPath(ideaModule, artifact.getFile()), scope));
        }
        
        public void visitUnresolvedDependency(UnresolvedDependencyResult unresolvedDependency) {
            File unresolvedFile = unresolvedIdeDependencyHandler.asFile(unresolvedDependency, ideaModule.getContentRoot());
            fileDependencies.add(new SingleEntryModuleLibrary(toPath(ideaModule, unresolvedFile), scope));
            unresolvedDependencies.put(unresolvedDependency.getAttempted(), unresolvedDependency);
        }
        
        public Collection<Dependency> getDependencies() {
            Collection<Dependency> dependencies = Sets.newLinkedHashSet();
            dependencies.addAll(projectDependencies);
            dependencies.addAll(moduleDependencies);
            dependencies.addAll(fileDependencies);
            return dependencies;
        }

        public Map<ComponentSelector, UnresolvedDependencyResult> getUnresolvedDependencies() {
            return unresolvedDependencies;
        }

        public IdeaModule getIdeaModule() {
            return ideaModule;
        }
    }

    private class CustomDependencyResolvingModuleDependencyBuilder {
        private final IdeArtifactRegistry ideArtifactRegistry;

        public CustomDependencyResolvingModuleDependencyBuilder(IdeArtifactRegistry ideArtifactRegistry) {
            this.ideArtifactRegistry = ideArtifactRegistry;
        }

        public ModuleDependency create(ProjectComponentIdentifier id, String scope) {
            return new ModuleDependency(determineProjectName(id), scope);
        }

        private String determineProjectName(ProjectComponentIdentifier id) {
            IdeaModuleMetadata moduleMetadata = ideArtifactRegistry.getIdeProject(IdeaModuleMetadata.class, id);
            return moduleMetadata == null ? id.getProjectName() : moduleMetadata.getName();
        }
    }

    private class CustomDependencyDependenciesOptimizer {
        public void optimizeDeps(Collection<Dependency> deps) {
            Multimap<Object, GeneratedIdeaScope> scopesByDependencyKey = collectScopesByDependency(deps);
            optimizeScopes(scopesByDependencyKey);
            applyScopesToDependencies(deps, scopesByDependencyKey);
        }

        private Multimap<Object, GeneratedIdeaScope> collectScopesByDependency(Collection<Dependency> deps) {
            Multimap<Object, GeneratedIdeaScope> scopesByDependencyKey = MultimapBuilder.hashKeys().enumSetValues(GeneratedIdeaScope.class).build();
            for (Dependency dep : deps) {
                scopesByDependencyKey.put(getKey(dep), GeneratedIdeaScope.nullSafeValueOf(dep.getScope()));
            }
            return scopesByDependencyKey;
        }

        private void optimizeScopes(Multimap<Object, GeneratedIdeaScope> scopesByDependencyKey) {
            for (Map.Entry<Object, Collection<GeneratedIdeaScope>> entry : scopesByDependencyKey.asMap().entrySet()) {
                optimizeScopes(entry.getValue());
            }
        }

        private void applyScopesToDependencies(Collection<Dependency> deps, Multimap<Object, GeneratedIdeaScope> scopesByDependencyKey) {
            for (Iterator<Dependency> iterator = deps.iterator(); iterator.hasNext();) {
                applyScopeToNextDependency(iterator, scopesByDependencyKey);
            }
        }

        private void applyScopeToNextDependency(Iterator<Dependency> iterator, Multimap<Object, GeneratedIdeaScope> scopesByDependencyKey) {
            Dependency dep = iterator.next();
            Object key = getKey(dep);
            Collection<GeneratedIdeaScope> ideaScopes = scopesByDependencyKey.get(key);
            if (ideaScopes.isEmpty()) {
                iterator.remove();
            } else {
                GeneratedIdeaScope scope = ideaScopes.iterator().next();
                dep.setScope(scope.name());
                scopesByDependencyKey.remove(key, scope);
            }
        }

        private Object getKey(Dependency dep) {
            if (dep instanceof ModuleDependency) {
                return ((ModuleDependency) dep).getName();
            } else if (dep instanceof SingleEntryModuleLibrary) {
                return ((SingleEntryModuleLibrary) dep).getLibraryFile();
            } else {
                throw new IllegalArgumentException("Unsupported type: " + dep.getClass().getName());
            }
        }

        private void optimizeScopes(Collection<GeneratedIdeaScope> ideaScopes) {
            boolean isRuntime = ideaScopes.contains(GeneratedIdeaScope.RUNTIME);
            boolean isProvided = ideaScopes.contains(GeneratedIdeaScope.PROVIDED);
            boolean isCompile = ideaScopes.contains(GeneratedIdeaScope.COMPILE);

            if (isProvided) {
                ideaScopes.remove(GeneratedIdeaScope.TEST);
            }

            if (isRuntime && isProvided) {
                ideaScopes.add(GeneratedIdeaScope.COMPILE);
                isCompile = true;
            }

            if (isCompile) {
                ideaScopes.remove(GeneratedIdeaScope.TEST);
                ideaScopes.remove(GeneratedIdeaScope.RUNTIME);
                ideaScopes.remove(GeneratedIdeaScope.PROVIDED);
            }
        }
    }
}
