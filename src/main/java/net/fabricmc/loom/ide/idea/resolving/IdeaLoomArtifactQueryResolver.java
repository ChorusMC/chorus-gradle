package net.fabricmc.loom.ide.idea.resolving;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.component.Artifact;
import org.gradle.api.component.Component;
import org.gradle.api.specs.Specs;
import org.gradle.internal.impldep.com.google.common.collect.ArrayListMultimap;
import org.gradle.internal.impldep.com.google.common.collect.Multimap;
import org.gradle.internal.impldep.com.google.common.collect.Sets;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;
import org.jetbrains.plugins.gradle.model.DefaultFileCollectionDependency;
import org.jetbrains.plugins.gradle.model.ExternalDependency;
import org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl;
import org.jetbrains.plugins.gradle.tooling.util.resolve.ExternalDepsResolutionResult;

import net.fabricmc.loom.util.ArtifactIdUtils;

public class IdeaLoomArtifactQueryResolver {
    private final Configuration         myConfiguration;
    private final String                myScope;
    private final Project               myProject;
    private final boolean               myDownloadSources;
    private final boolean               myDownloadJavadoc;
    private final IdeaLoomSourceSetCachedFinder mySourceSetFinder;

    IdeaLoomArtifactQueryResolver(
                    final Configuration configuration,
                    final String scope,
                    final Project project,
                    final boolean downloadJavadoc,
                    final boolean downloadSources,
                    final IdeaLoomSourceSetCachedFinder sourceSetFinder) {
        myConfiguration = configuration;
        myScope = scope;
        myProject = project;
        myDownloadJavadoc = downloadJavadoc;
        myDownloadSources = downloadSources;
        mySourceSetFinder = sourceSetFinder;
    }

    public ExternalDepsResolutionResult resolve() {
        final Collection<ExternalDependency> extDependencies = new LinkedHashSet<ExternalDependency>();

        LenientConfiguration lenientConfiguration = myConfiguration.getResolvedConfiguration().getLenientConfiguration();
        Set<UnresolvedDependency> unresolvedModuleDependencies = lenientConfiguration.getUnresolvedModuleDependencies();
        Set<ResolvedArtifact> resolvedArtifacts;
        if (unresolvedModuleDependencies.isEmpty() || !IdeaLoomDependencyResolver.is31OrBetter) {
            resolvedArtifacts = lenientConfiguration.getArtifacts(Specs.SATISFIES_ALL);
        }
        else {
            resolvedArtifacts = new LinkedHashSet<ResolvedArtifact>();
            // org.gradle.api.artifacts.LenientConfiguration.getAllModuleDependencies method was added in Gradle 3.1
            Set<ResolvedDependency> allModuleDependencies = lenientConfiguration.getAllModuleDependencies();
            for (ResolvedDependency dependency : allModuleDependencies) {
                try {
                    resolvedArtifacts.addAll(dependency.getModuleArtifacts());
                }
                catch (Exception ignore) {
                    // ignore org.gradle.internal.resolve.ArtifactResolveException
                }
            }
        }

        final Multimap<ModuleVersionIdentifier, ResolvedArtifact> artifactMap =
                        groupByModuleVersionId(resolvedArtifacts);
        final Table<ModuleComponentIdentifier, Class<? extends Artifact>, Set<ResolvedArtifact>> auxiliaryArtifactsMap =
                        buildAuxiliaryArtifactsMap(resolvedArtifacts);

        final Multimap<ModuleComponentIdentifier, ProjectDependency> configurationProjectDependencies =
                        IdeaLoomDependencyResolver.collectProjectDeps(myConfiguration);

        if (!myConfiguration.getResolvedConfiguration().hasError()) {
            extDependencies.addAll(buildFileCollectionDeps(resolvedArtifacts, configurationProjectDependencies.values()));
        }

        IdeaLoomDependencyResultsTransformer dependencyResultsTransformer =
                        new IdeaLoomDependencyResultsTransformer(myProject, mySourceSetFinder,
                                        artifactMap,
                                        auxiliaryArtifactsMap,
                                        configurationProjectDependencies,
                                        myScope);

        ResolutionResult resolutionResult = myConfiguration.getIncoming().getResolutionResult();
        extDependencies.addAll(dependencyResultsTransformer.buildExternalDependencies(resolutionResult.getRoot().getDependencies()));

        return new ExternalDepsResolutionResult(extDependencies,
                        new ArrayList<File>(dependencyResultsTransformer.getResolvedDepsFiles()));
    }

    public Multimap<ModuleVersionIdentifier, ResolvedArtifact> groupByModuleVersionId(Set<ResolvedArtifact> resolvedArtifacts) {
        Multimap<ModuleVersionIdentifier, ResolvedArtifact> artifactMap = ArrayListMultimap.create();
        for (ResolvedArtifact artifact : resolvedArtifacts) {
            final ModuleVersionIdentifier moduleVersionId = artifact.getModuleVersion().getId();
            artifactMap.put(moduleVersionId, artifact);
        }
        return artifactMap;
    }

    public Table<ModuleComponentIdentifier, Class<? extends Artifact>, Set<ResolvedArtifact>> buildAuxiliaryArtifactsMap(
                    final Set<ResolvedArtifact> resolvedArtifacts) {
        final Table<ModuleComponentIdentifier, Class<? extends Artifact>, Set<ResolvedArtifact>> auxiliaryArtifacts     = HashBasedTable.create();

        final Map<String, ResolvedArtifact> dependencyIdsToLookup = Maps.newHashMap();
        //Global dependencies are used when a module provides multiple different artifacts but only one source jar for all.
        final Map<String, Set<ResolvedArtifact>> globalDependencyIdsToLookup = Maps.newHashMap();

        for(final ResolvedArtifact artifact : resolvedArtifacts)
        {
            if (!(artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier))
                continue;

            final String dependencyId = ArtifactIdUtils.createDependencyIdentifierFromRawArtifact(artifact, true);
            dependencyIdsToLookup.put(dependencyId, artifact);

            final String globalDependencyId = ArtifactIdUtils.createDependencyIdentifierFromRawArtifact(artifact, false);
            globalDependencyIdsToLookup.putIfAbsent(globalDependencyId, com.google.common.collect.Sets.newHashSet());
            globalDependencyIdsToLookup.get(globalDependencyId).add(artifact);
        }

        if (myDownloadSources)
        {
            final Configuration sourcesToAttachConfiguration = myProject.getConfigurations().create("_______sources_idea_external_" + myConfiguration.getName() + "_" + (new Random()).nextInt());
            for (final String dependencyId :
                            dependencyIdsToLookup.keySet()) {
                if (ArtifactIdUtils.doesArtifactExist(myProject.getDependencies(), dependencyIdsToLookup.get(dependencyId), SourcesArtifact.class))
                {
                    myProject.getDependencies().add(sourcesToAttachConfiguration.getName(), ArtifactIdUtils.createDependencyIdentifierFromRawArtifactWithAppendix(dependencyIdsToLookup.get(dependencyId), true, "sources"));
                }
            }

            for(final String dependencyId :
                            globalDependencyIdsToLookup.keySet())
            {
                for (final ResolvedArtifact artifact : globalDependencyIdsToLookup.get(dependencyId)) {
                    if (ArtifactIdUtils.doesArtifactExist(myProject.getDependencies(), artifact, SourcesArtifact.class))
                    {
                        myProject.getDependencies().add(sourcesToAttachConfiguration.getName(), ArtifactIdUtils.createDependencyIdentifierFromRawArtifactWithAppendix(artifact, false, "sources"));
                    }
                }
            }

            final LenientConfiguration lenientSourcesConfiguration = sourcesToAttachConfiguration.getResolvedConfiguration().getLenientConfiguration();
            for (final ResolvedDependency resolvedSourcesDependency :
                            lenientSourcesConfiguration.getAllModuleDependencies()) {
                for (final ResolvedArtifact resolvedSourcesArtifact :
                                resolvedSourcesDependency.getAllModuleArtifacts()) {
                    if (!(resolvedSourcesArtifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier))
                        continue;

                    final String dependencyId = ArtifactIdUtils.createDependencyIdentifierFromAppendedArtifact(resolvedSourcesArtifact, true, "sources");
                    if (dependencyIdsToLookup.containsKey(dependencyId))
                    {
                        final ResolvedArtifact originalArtifact = dependencyIdsToLookup.get(dependencyId);
                        auxiliaryArtifacts.put((ModuleComponentIdentifier) originalArtifact.getId().getComponentIdentifier(), SourcesArtifact.class, com.google.common.collect.Sets
                                                                                                                                                                     .newHashSet(resolvedSourcesArtifact));
                    }
                    final String globalDependencyId = ArtifactIdUtils.createDependencyIdentifierFromAppendedArtifact(resolvedSourcesArtifact, false, "sources");
                    if (globalDependencyIdsToLookup.containsKey(globalDependencyId))
                    {
                        final Set<ResolvedArtifact> originalArtifacts = globalDependencyIdsToLookup.get(globalDependencyId);
                        for (final ResolvedArtifact originalArtifact : originalArtifacts) {
                            auxiliaryArtifacts.put((ModuleComponentIdentifier) originalArtifact.getId().getComponentIdentifier(), SourcesArtifact.class, com.google.common.collect.Sets
                                                                                                                                                                         .newHashSet(resolvedSourcesArtifact));
                        }
                    }
                }
            }
        }

        if (myDownloadJavadoc)
        {
            final Configuration javaDocsToAttachConfiguration = myProject.getConfigurations().create("_______javaDocs_idea_external_" + myConfiguration.getName() + "_" + (new Random()).nextInt());
            for (final String dependencyId :
                            dependencyIdsToLookup.keySet()) {
                if (ArtifactIdUtils.doesArtifactExist(myProject.getDependencies(), dependencyIdsToLookup.get(dependencyId), JavadocArtifact.class)) {
                    myProject
                                    .getDependencies()
                                    .add(javaDocsToAttachConfiguration.getName(),
                                                    ArtifactIdUtils.createDependencyIdentifierFromRawArtifactWithAppendix(dependencyIdsToLookup.get(dependencyId),
                                                                    true,
                                                                    "javadoc"));
                }
            }

            for(final String dependencyId :
                            globalDependencyIdsToLookup.keySet())
            {
                for (final ResolvedArtifact artifact : globalDependencyIdsToLookup.get(dependencyId)) {
                    if (ArtifactIdUtils.doesArtifactExist(myProject.getDependencies(), artifact, JavadocArtifact.class)) {
                        myProject
                                        .getDependencies()
                                        .add(javaDocsToAttachConfiguration.getName(),
                                                        ArtifactIdUtils.createDependencyIdentifierFromRawArtifactWithAppendix(artifact, false, "javadoc"));
                    }
                }
            }

            final LenientConfiguration lenientJavaDocsConfiguration = javaDocsToAttachConfiguration.getResolvedConfiguration().getLenientConfiguration();
            for (final ResolvedDependency resolvedJavaDocsDependency :
                            lenientJavaDocsConfiguration.getAllModuleDependencies()) {
                for (final ResolvedArtifact resolvedJavaDocsArtifact :
                                resolvedJavaDocsDependency.getAllModuleArtifacts()) {
                    if (!(resolvedJavaDocsArtifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier))
                        continue;

                    final String dependencyId = ArtifactIdUtils.createDependencyIdentifierFromAppendedArtifact(resolvedJavaDocsArtifact, true, "javadoc");
                    if (dependencyIdsToLookup.containsKey(dependencyId))
                    {
                        final ResolvedArtifact originalArtifact = dependencyIdsToLookup.get(dependencyId);
                        auxiliaryArtifacts.put((ModuleComponentIdentifier) originalArtifact.getId().getComponentIdentifier(), JavadocArtifact.class, com.google.common.collect.Sets
                                                                                                                                                                     .newHashSet(resolvedJavaDocsArtifact));
                    }
                    final String globalDependencyId = ArtifactIdUtils.createDependencyIdentifierFromAppendedArtifact(resolvedJavaDocsArtifact, false, "javadoc");
                    if (globalDependencyIdsToLookup.containsKey(globalDependencyId))
                    {
                        final Set<ResolvedArtifact> originalArtifacts = globalDependencyIdsToLookup.get(globalDependencyId);
                        for (final ResolvedArtifact originalArtifact : originalArtifacts) {
                            auxiliaryArtifacts.put((ModuleComponentIdentifier) originalArtifact.getId().getComponentIdentifier(), JavadocArtifact.class, com.google.common.collect.Sets
                                                                                                                                                                         .newHashSet(resolvedJavaDocsArtifact));
                        }
                    }
                }
            }
        }

        return auxiliaryArtifacts;
    }

    protected Collection<ExternalDependency> buildFileCollectionDeps(
                    Collection<ResolvedArtifact> resolvedArtifactToFilter,
                    Collection<ProjectDependency> projectDepsToFilter) {
        Collection<ExternalDependency> result = new ArrayList<ExternalDependency>();

        Set<File> fileDeps = new LinkedHashSet<File>(myConfiguration.getIncoming().getFiles().getFiles());
        for (ResolvedArtifact artifact : resolvedArtifactToFilter) {
            fileDeps.remove(artifact.getFile());
        }

        if (!fileDeps.isEmpty()) {
            for (ProjectDependency dep : projectDepsToFilter) {
                Configuration targetConfiguration = DependencyResolverImpl.getTargetConfiguration(dep);
                if (targetConfiguration == null) continue;
                Set<File> depFiles = targetConfiguration.getAllArtifacts().getFiles().getFiles();

                final Set<File> intersection = new LinkedHashSet<File>(Sets.intersection(fileDeps, depFiles));
                if (!intersection.isEmpty()) {
                    DefaultFileCollectionDependency fileCollectionDependency = new DefaultFileCollectionDependency(intersection);
                    fileCollectionDependency.setScope(myScope);
                    result.add(fileCollectionDependency);
                    fileDeps.removeAll(intersection);
                }
            }
        }

        for (File file : fileDeps) {
            DefaultFileCollectionDependency fileCollectionDependency = new DefaultFileCollectionDependency(Collections.singleton(file));
            fileCollectionDependency.setScope(myScope);
            result.add(fileCollectionDependency);
        }

        return result;
    }
}
