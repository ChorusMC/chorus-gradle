package net.fabricmc.loom.ide.idea.resolving;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import groovy.lang.GString;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.composite.internal.DefaultIncludedBuild;
import org.gradle.util.GradleVersion;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.internal.ExtraModelBuilder;
import org.jetbrains.plugins.gradle.tooling.util.JavaPluginUtil;

public class IdeaLoomSourceSetCachedFinder {

    public IdeaLoomSourceSetCachedFinder(ModelBuilderContext context) {
        init(context);
    }

    private void init(ModelBuilderContext context) {
        myArtifactsMap = context.getData(ARTIFACTS_PROVIDER);
        mySourcesMap = context.getData(SOURCES_DATA_KEY);
    }

    public Set<File> findSourcesByArtifact(String path) {
        Set<File> sources = mySourcesMap.get(path);
        if (sources == null) {
            SourceSet sourceSet = myArtifactsMap.get(path);
            if (sourceSet != null) {
                sources = sourceSet.getAllJava().getSrcDirs();
                mySourcesMap.put(path, sources);
            }
        }

        return sources;
    }

    public SourceSet findByArtifact(String artifactPath) {
        return myArtifactsMap.get(artifactPath);
    }

    private static HashMap<String, SourceSet> createArtifactsMap(Gradle gradle) {
        HashMap<String, SourceSet> artifactsMap = new HashMap<String, SourceSet>();
        List<Project> projects = new ArrayList<>(gradle.getRootProject().getAllprojects());
        boolean isCompositeBuildsSupported = GradleVersion.current().compareTo(GradleVersion.version("3.1")) >= 0;
        if (isCompositeBuildsSupported) {
            projects = exposeIncludedBuilds(gradle, projects);
        }

        for (Project p : projects) {
            SourceSetContainer sourceSetContainer = JavaPluginUtil.getSourceSetContainer(p);
            if (sourceSetContainer == null || sourceSetContainer.isEmpty()) continue;

            for (SourceSet sourceSet : sourceSetContainer) {
                Task task = p.getTasks().findByName(sourceSet.getJarTaskName());
                if (task instanceof AbstractArchiveTask) {
                    AbstractArchiveTask jarTask = (AbstractArchiveTask) task;
                    File archivePath = jarTask.getArchivePath();
                    artifactsMap.put(archivePath.getPath(), sourceSet);
                }
            }
        }

        return artifactsMap;
    }

    private static List<Project> exposeIncludedBuilds(Gradle gradle, List<Project> projects) {
        for (IncludedBuild includedBuild : gradle.getIncludedBuilds()) {
            if (includedBuild instanceof DefaultIncludedBuild) {
                DefaultIncludedBuild build = DefaultGroovyMethods.asType(includedBuild, DefaultIncludedBuild.class);
                projects = DefaultGroovyMethods.plus(projects, build.getConfiguredBuild().getRootProject().getAllprojects());
            }
        }

        return projects;
    }

    private static final ModelBuilderContext.DataProvider<Map<String, SourceSet>> ARTIFACTS_PROVIDER = new ModelBuilderContext.DataProvider<Map<String, SourceSet>>() {
        @Override
        public Map<String, SourceSet> create(Gradle gradle) {
            return createArtifactsMap(gradle);
        }
    };
    private static final ModelBuilderContext.DataProvider<Map<String, Set<File>>> SOURCES_DATA_KEY   = new ModelBuilderContext.DataProvider<Map<String, Set<File>>>() {
        @Override
        public Map<String, Set<File>> create(Gradle gradle) {
            return new HashMap<String, Set<File>>();
        }
    };
    private              Map<String, SourceSet>                                   myArtifactsMap;
    private              Map<String, Set<File>>                                   mySourcesMap;
}
