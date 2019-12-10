package net.fabricmc.loom.util;

import java.io.File;
import java.util.Objects;
import java.util.Random;

import com.google.gson.JsonObject;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.plugins.JavaPlugin;

import net.fabricmc.loom.LoomGradleExtension;

public final class CheatyFabricInstallerFinder {

    private static final String CHEATY_FABRIC_LOADER_RESOLVING_CONFIGURATION_NAME = "__cheat_fabric_loader_resolving_config";

    private CheatyFabricInstallerFinder() {
        throw new IllegalStateException("Tried to initialize: CheatyFabricInstallerFinder but this is a Utility class.");
    }

    public static void handleFabricInstaller(Project project) {
        final LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
        final Configuration tempLoaderResolvingConfig = project.getConfigurations().maybeCreate(CHEATY_FABRIC_LOADER_RESOLVING_CONFIGURATION_NAME + (new Random()).nextInt());
        Dependency fabricLoaderDependency = findFabricLoader(project);
        if (fabricLoaderDependency == null) {
            project.getLogger().lifecycle("Failed to find installer in: " + project + " no dependency found!");
            return;
        }

        tempLoaderResolvingConfig.getDependencies().add(fabricLoaderDependency);
        final ResolvedArtifact fabricLoaderArtifact = getResolvedFabricLoaderOrThrow(tempLoaderResolvingConfig);

        final File file = fabricLoaderArtifact.getFile();
        JsonObject jsonObject = InstallerUtils.readInstallerJson(file, project);

        if (jsonObject != null) {
            if (extension.getInstallerJson() != null) {
                project.getLogger().info("Found another installer JSON in, ignoring it! " + file);
                return;
            }

            project.getLogger().info("Found installer JSON in " + file);
            extension.setInstallerJson(jsonObject);
        }
    }

    private static Dependency findFabricLoader(Project project) {
        return project.getConfigurations()
                               .getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME)
                               .getDependencies()
                               .stream()
                               .filter(dep -> Objects.equals(dep.getGroup(), "net.fabricmc") && Objects.equals(dep.getName(), "fabric-loader"))
                               .findFirst()
                               .orElse(null);
    }

    private static ResolvedArtifact getResolvedFabricLoaderOrThrow(Configuration tempConfig) {
        return tempConfig.getResolvedConfiguration().getResolvedArtifacts().stream().findFirst().orElseThrow(() -> new IllegalStateException("Could not resolve Fabric-Loader"));
    }
}
