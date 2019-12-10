package net.fabricmc.loom.util;

import java.util.function.Consumer;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler;

import net.fabricmc.loom.LoomGradleExtension;

public final class RepositoryUtils {

    public static final String USER_CACHE_FILES_NAME       = "UserCacheFiles";
    public static final String USER_LOCAL_CACHE_FILES_NAME   = "UserLocalCacheFiles";
    public static final String USER_LOCAL_REMAPPED_MODS_NAME = "UserLocalRemappedMods";
    public static final String MOJANG_NAME                   = "Mojang";
    public static final String MOJANG_URL = "https://libraries.minecraft.net/";
    public static final String FABRIC_NAME = "Fabric";
    public static final String FABRIC_URL = "https://maven.fabricmc.net/";

    private RepositoryUtils() {
        throw new IllegalStateException("Tried to initialize: RepositoryUtils but this is a Utility class.");
    }

    public static void ensureRepositoriesExist(final Project project)
    {
        final LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);

        addRepositoryIfNotExisting(project, USER_CACHE_FILES_NAME, (repos) -> {
            repos.flatDir(flatDirectoryArtifactRepository -> {
                flatDirectoryArtifactRepository.dir(extension.getUserCache());
                flatDirectoryArtifactRepository.setName(USER_CACHE_FILES_NAME);
            });
        });

        addRepositoryIfNotExisting(project, USER_LOCAL_CACHE_FILES_NAME, (repos) -> {
            repos.flatDir(flatDirectoryArtifactRepository -> {
                flatDirectoryArtifactRepository.dir(extension.getRootProjectBuildCache());
                flatDirectoryArtifactRepository.setName(USER_LOCAL_CACHE_FILES_NAME);
            });
        });

        addRepositoryIfNotExisting(project, USER_LOCAL_REMAPPED_MODS_NAME, (repos) -> {
            repos.flatDir(flatDirectoryArtifactRepository -> {
                flatDirectoryArtifactRepository.dir(extension.getRemappedModCache());
                flatDirectoryArtifactRepository.setName(USER_LOCAL_REMAPPED_MODS_NAME);
            });
        });

        addRepositoryIfNotExisting(project, MOJANG_NAME, (repos) -> {
            repos.maven(mavenArtifactRepository -> {
                mavenArtifactRepository.setName(MOJANG_NAME);
                mavenArtifactRepository.setUrl(MOJANG_URL);
                mavenArtifactRepository.metadataSources(metadataSources -> {
                    metadataSources.artifact();
                    metadataSources.ignoreGradleMetadataRedirection();
                    metadataSources.gradleMetadata();
                    metadataSources.mavenPom();
                });
            });
        });

        addRepositoryIfNotExisting(project, ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME, RepositoryHandler::mavenCentral);
        addRepositoryIfNotExisting(project, DefaultRepositoryHandler.DEFAULT_BINTRAY_JCENTER_REPO_NAME, RepositoryHandler::jcenter);

        addRepositoryIfNotExisting(project, FABRIC_NAME, (repos) -> {
            repos.maven(repo -> {
                repo.setName(FABRIC_NAME);
                repo.setUrl(FABRIC_URL);
                repo.metadataSources(sources -> {
                    sources.mavenPom();
                    sources.gradleMetadata();
                    sources.ignoreGradleMetadataRedirection();
                    sources.artifact();
                });
            });
        });
    }

    private static void addRepositoryIfNotExisting(final Project project, final String repoName, final Consumer<RepositoryHandler> repoAdder)
    {
        if (project.getRepositories().stream().noneMatch(repo -> repo.getName().equals(repoName)))
        {
            repoAdder.accept(project.getRepositories());
        }
    }
}
