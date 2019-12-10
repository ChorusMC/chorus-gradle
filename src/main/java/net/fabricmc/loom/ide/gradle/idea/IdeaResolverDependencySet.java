package net.fabricmc.loom.ide.gradle.idea;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.component.Artifact;
import org.gradle.api.specs.Spec;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;

import net.fabricmc.loom.util.ArtifactIdUtils;

public class IdeaResolverDependencySet {
    private final DependencyHandler         dependencyHandler;
    private final Collection<Configuration> plusConfigurations;
    private final Collection<Configuration> minusConfigurations;

    public IdeaResolverDependencySet(DependencyHandler dependencyHandler, Collection<Configuration> plusConfigurations, Collection<Configuration> minusConfigurations) {
        this.dependencyHandler = dependencyHandler;
        this.plusConfigurations = plusConfigurations;
        this.minusConfigurations = minusConfigurations;
    }

    public void visit(IdeaResolvingDependenciesProvider.CustomDependencyResolvingDependenciesVisitor visitor) {
        if (plusConfigurations.isEmpty()) {
            return;
        }
        new CustomDependencyResolvingDependencyResult().visit(visitor);
    }

    private class CustomDependencyResolvingDependencyResult {
        private final Map<ComponentArtifactIdentifier, ResolvedArtifact>                                 resolvedArtifacts      = Maps.newLinkedHashMap();
        private final SetMultimap<ComponentArtifactIdentifier, Configuration>                                  configurations         = MultimapBuilder.hashKeys().linkedHashSetValues().build();
        private final Map<ComponentSelector, UnresolvedDependencyResult>                                       unresolvedDependencies = Maps.newLinkedHashMap();
        private final Table<ModuleComponentIdentifier, Class<? extends Artifact>, Set<ResolvedArtifact>> auxiliaryArtifacts     = HashBasedTable.create();

        public void visit(IdeaResolvingDependenciesProvider.CustomDependencyResolvingDependenciesVisitor visitor) {
            resolvePlusConfigurations(visitor);
            resolveMinusConfigurations(visitor);
            resolveAuxiliaryArtifacts(visitor);
            visitArtifacts(visitor);
            visitUnresolvedDependencies(visitor);
        }

        private void resolvePlusConfigurations(IdeaResolvingDependenciesProvider.CustomDependencyResolvingDependenciesVisitor visitor) {
            for (Configuration configuration : plusConfigurations) {
                Set<ResolvedDependency> artifacts = getResolvedArtifacts(configuration);
                for (ResolvedDependency resolvedArtifact : artifacts) {
                    for (final ResolvedArtifact allModuleArtifact : resolvedArtifact.getAllModuleArtifacts()) {
                        resolvedArtifacts.put(allModuleArtifact.getId(), allModuleArtifact);
                        configurations.put(allModuleArtifact.getId(), configuration);
                    }
                }
                for (UnresolvedDependencyResult unresolvedDependency : getUnresolvedDependencies(configuration, visitor)) {
                    unresolvedDependencies.put(unresolvedDependency.getAttempted(), unresolvedDependency);
                }
            }
        }

        private void resolveMinusConfigurations(IdeaResolvingDependenciesProvider.CustomDependencyResolvingDependenciesVisitor visitor) {
            for (Configuration configuration : minusConfigurations) {
                Set<ResolvedDependency> artifacts = getResolvedArtifacts(configuration);
                for (ResolvedDependency resolvedArtifact : artifacts) {
                    for (final ResolvedArtifact allModuleArtifact : resolvedArtifact.getAllModuleArtifacts()) {
                        resolvedArtifacts.put(allModuleArtifact.getId(), allModuleArtifact);
                        configurations.put(allModuleArtifact.getId(), configuration);
                    }
                }
                for (UnresolvedDependencyResult unresolvedDependency : getUnresolvedDependencies(configuration, visitor)) {
                    unresolvedDependencies.remove(unresolvedDependency.getAttempted());
                }
            }
        }

        protected Set<ResolvedDependency> getResolvedArtifacts(Configuration configuration)
        {
            return configuration.getResolvedConfiguration().getLenientConfiguration().getAllModuleDependencies();
        }

        private Iterable<UnresolvedDependencyResult> getUnresolvedDependencies(Configuration configuration, IdeaResolvingDependenciesProvider.CustomDependencyResolvingDependenciesVisitor visitor) {
            if (visitor.isOffline()) {
                return Collections.emptySet();
            }
            return Iterables.filter(configuration.getIncoming().getResolutionResult().getRoot().getDependencies(), UnresolvedDependencyResult.class);
        }

        private void resolveAuxiliaryArtifacts(IdeaResolvingDependenciesProvider.CustomDependencyResolvingDependenciesVisitor visitor) {
            if (visitor.isOffline()) {
                return;
            }

            final Map<String, ResolvedArtifact> dependencyIdsToLookup = Maps.newHashMap();
            //Global dependencies are used when a module provides multiple different artifacts but only one source jar for all.
            final Map<String, Set<ResolvedArtifact>> globalDependencyIdsToLookup = Maps.newHashMap();

            for(final Configuration configuration : plusConfigurations) {
                final Set<ResolvedDependency> dependencyToLookupSourcesFor = getResolvedArtifacts(configuration);
                for(final ResolvedDependency dependency : dependencyToLookupSourcesFor)
                {
                    for(final ResolvedArtifact artifact : dependency.getAllModuleArtifacts())
                    {
                        if (!(artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier))
                            continue;

                        final String dependencyId = ArtifactIdUtils.createDependencyIdentifierFromRawArtifact(artifact, true);
                        dependencyIdsToLookup.put(dependencyId, artifact);

                        final String globalDependencyId = ArtifactIdUtils.createDependencyIdentifierFromRawArtifact(artifact, false);
                        globalDependencyIdsToLookup.putIfAbsent(globalDependencyId, Sets.newHashSet());
                        globalDependencyIdsToLookup.get(globalDependencyId).add(artifact);
                    }
                }
            }

            for(final Configuration configuration : minusConfigurations) {
                final Set<ResolvedDependency> dependencyToLookupSourcesFor = getResolvedArtifacts(configuration);
                for(final ResolvedDependency dependency : dependencyToLookupSourcesFor)
                {
                    for(final ResolvedArtifact artifact : dependency.getAllModuleArtifacts())
                    {
                        if (!(artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier))
                            continue;

                        final String dependencyId = ArtifactIdUtils.createDependencyIdentifierFromRawArtifact(artifact, true);
                        dependencyIdsToLookup.remove(dependencyId);

                        final String globalDependencyId = ArtifactIdUtils.createDependencyIdentifierFromRawArtifact(artifact, false);
                        if (globalDependencyIdsToLookup.containsKey(globalDependencyId))
                            globalDependencyIdsToLookup.get(globalDependencyId).remove(artifact);
                    }
                }
            }

            if (visitor.downloadSources())
            {
                final Configuration sourcesToAttachConfiguration = visitor.getIdeaModule().getProject().getConfigurations().create("_______sources_idea_" + (new Random()).nextInt());
                for (final String dependencyId :
                                dependencyIdsToLookup.keySet()) {
                    if (ArtifactIdUtils.doesArtifactExist(visitor.getIdeaModule().getProject().getDependencies(), dependencyIdsToLookup.get(dependencyId), SourcesArtifact.class))
                    {
                        visitor.getIdeaModule().getProject().getDependencies().add(sourcesToAttachConfiguration.getName(), ArtifactIdUtils.createDependencyIdentifierFromRawArtifactWithAppendix(dependencyIdsToLookup.get(dependencyId), true, "sources"));
                    }
                }

                for(final String dependencyId :
                                globalDependencyIdsToLookup.keySet())
                {
                    for (final ResolvedArtifact artifact : globalDependencyIdsToLookup.get(dependencyId)) {
                        if (ArtifactIdUtils.doesArtifactExist(visitor.getIdeaModule().getProject().getDependencies(), artifact, SourcesArtifact.class))
                        {
                            visitor.getIdeaModule().getProject().getDependencies().add(sourcesToAttachConfiguration.getName(), ArtifactIdUtils.createDependencyIdentifierFromRawArtifactWithAppendix(artifact, false, "sources"));
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
                            auxiliaryArtifacts.put((ModuleComponentIdentifier) originalArtifact.getId().getComponentIdentifier(), SourcesArtifact.class, Sets.newHashSet(resolvedSourcesArtifact));
                        }
                        final String globalDependencyId = ArtifactIdUtils.createDependencyIdentifierFromAppendedArtifact(resolvedSourcesArtifact, false, "sources");
                        if (globalDependencyIdsToLookup.containsKey(globalDependencyId))
                        {
                            final Set<ResolvedArtifact> originalArtifacts = globalDependencyIdsToLookup.get(globalDependencyId);
                            for (final ResolvedArtifact originalArtifact : originalArtifacts) {
                                auxiliaryArtifacts.put((ModuleComponentIdentifier) originalArtifact.getId().getComponentIdentifier(), SourcesArtifact.class, Sets.newHashSet(resolvedSourcesArtifact));
                            }
                        }
                    }
                }
            }

            if (visitor.downloadJavaDoc())
            {
                final Configuration javaDocsToAttachConfiguration = visitor.getIdeaModule().getProject().getConfigurations().create("_______javaDocs_idea_" + (new Random()).nextInt());
                for (final String dependencyId :
                                dependencyIdsToLookup.keySet()) {
                    if (ArtifactIdUtils.doesArtifactExist(visitor.getIdeaModule().getProject().getDependencies(), dependencyIdsToLookup.get(dependencyId), JavadocArtifact.class)) {
                        visitor.getIdeaModule()
                                        .getProject()
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
                        if (ArtifactIdUtils.doesArtifactExist(visitor.getIdeaModule().getProject().getDependencies(), artifact, JavadocArtifact.class)) {
                            visitor.getIdeaModule()
                                            .getProject()
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
                            auxiliaryArtifacts.put((ModuleComponentIdentifier) originalArtifact.getId().getComponentIdentifier(), JavadocArtifact.class, Sets.newHashSet(resolvedJavaDocsArtifact));
                        }
                        final String globalDependencyId = ArtifactIdUtils.createDependencyIdentifierFromAppendedArtifact(resolvedJavaDocsArtifact, false, "javadoc");
                        if (globalDependencyIdsToLookup.containsKey(globalDependencyId))
                        {
                            final Set<ResolvedArtifact> originalArtifacts = globalDependencyIdsToLookup.get(globalDependencyId);
                            for (final ResolvedArtifact originalArtifact : originalArtifacts) {
                                auxiliaryArtifacts.put((ModuleComponentIdentifier) originalArtifact.getId().getComponentIdentifier(), JavadocArtifact.class, Sets.newHashSet(resolvedJavaDocsArtifact));
                            }
                        }
                    }
                }
            }
        }

        private void visitArtifacts(IdeaResolvingDependenciesProvider.CustomDependencyResolvingDependenciesVisitor visitor) {
            for (ResolvedArtifact artifact : resolvedArtifacts.values()) {
                ComponentIdentifier componentIdentifier = artifact.getId().getComponentIdentifier();
                ComponentArtifactIdentifier artifactIdentifier = artifact.getId();
                if (componentIdentifier instanceof ProjectComponentIdentifier) {
                    visitor.visitProjectDependency(artifact);
                } else if (componentIdentifier instanceof ModuleComponentIdentifier) {
                    Set<ResolvedArtifact> sources = auxiliaryArtifacts.get(componentIdentifier, SourcesArtifact.class);
                    sources = sources != null ? sources : ImmutableSet.of();
                    Set<ResolvedArtifact> javaDoc = auxiliaryArtifacts.get(componentIdentifier, JavadocArtifact.class);
                    javaDoc = javaDoc != null ? javaDoc : ImmutableSet.of();
                    visitor.visitModuleDependency(artifact, sources, javaDoc, isTestConfiguration(configurations.get(artifactIdentifier)));
                } else {
                    visitor.visitFileDependency(artifact, isTestConfiguration(configurations.get(artifactIdentifier)));
                }
            }
        }

        private boolean isTestConfiguration(Set<Configuration> configurations) {
            for (Configuration c : configurations) {
                if (!c.getName().toLowerCase().contains("test")) {
                    return false;
                }
            }
            return true;
        }

        private void visitUnresolvedDependencies(IdeaResolvingDependenciesProvider.CustomDependencyResolvingDependenciesVisitor visitor) {
            for (UnresolvedDependencyResult unresolvedDependency : unresolvedDependencies.values()) {
                visitor.visitUnresolvedDependency(unresolvedDependency);
            }
        }
    }

    private static final Spec<ComponentIdentifier> NOT_A_MODULE = new Spec<ComponentIdentifier>() {
        @Override
        public boolean isSatisfiedBy(ComponentIdentifier id) {
            return !(id instanceof ModuleComponentIdentifier);
        }
    };
}
