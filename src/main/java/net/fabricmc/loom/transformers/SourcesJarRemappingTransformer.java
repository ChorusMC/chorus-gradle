package net.fabricmc.loom.transformers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.tools.ant.util.StreamUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.InputArtifactDependencies;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CompileClasspath;
import org.zeroturnaround.zip.commons.FileUtils;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftMappedProvider;
import net.fabricmc.loom.transformers.parameters.ProjectReferencingParameters;
import net.fabricmc.loom.util.FabricModUtils;
import net.fabricmc.loom.util.FileNameUtils;
import net.fabricmc.loom.util.SourceRemapper;
import net.fabricmc.loom.util.TinyRemapperMappingsHelper;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public abstract class SourcesJarRemappingTransformer implements TransformAction<ProjectReferencingParameters> {

    @CompileClasspath
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInput();

    @CompileClasspath
    @InputArtifactDependencies
    public abstract FileCollection getDependencies();

    @Override
    public void transform(final TransformOutputs outputs) {
        final FileSystemLocation resolvedInput = getInput().getOrNull();
        if (resolvedInput == null) {
            lifecycle("Can not transform a jar who has no input.");
            return;
        }

        final File inputFile = resolvedInput.getAsFile();
        if (!inputFile.exists()) {
            lifecycle("Can not transform jar who does not exists on disk.");
            return;
        }

        //Rudimentary check for sources. Which need to be remapped separately.
        //Potentially we need to check zip file contents.
        if (!inputFile.getName().contains("-sources"))
        {
            getProject().getLogger().lifecycle("[Not remapping sources, no sources jar]: " + inputFile.getName());
            final File outputFile = outputs.file("not-remapped-no-sources" + File.separator + FileNameUtils.appendToNameBeForeExtension(inputFile, "-sources_remapped"));
            try {
                FileUtils.copyFile(inputFile, outputFile);
            } catch (Exception e) {
                lifecycle("Failed to copy not remappable file to output.", e);
            }
            return;
        }

        if (!FabricModUtils.isFabricMod(
                        getProject(),
                        getProject().getLogger(),
                        inputFile,
                        inputFile.getName()
        )) {
            getProject().getLogger().lifecycle("[Not remapping sources, no mod]: " + inputFile.getName());
            final File outputFile = outputs.file("not-remapped-no-mod" + File.separator + FileNameUtils.appendToNameBeForeExtension(inputFile, "-sources_remapped"));
            try {
                FileUtils.copyFile(inputFile, outputFile);
            } catch (Exception e) {
                lifecycle("Failed to copy not remappable file to output.", e);
            }
            return;
        }

        final File outputFile = outputs.file(String.format("remapped%s%s", File.separator, FileNameUtils.appendToNameBeForeExtension(inputFile, "-sources_remapped")));
        try {
            getProject().getLogger().lifecycle("[Remapping sources]: " + inputFile.getName());
            SourceRemapper.remapSources(getProject(), inputFile, outputFile, true);
        } catch (Exception e) {
            lifecycle("Failed to remap file.", e);
        }
    }

    /**
     * Short circuit method to handle logging a lifecycle message to the projects logger.
     *
     * @param message The message to log, formatted.
     * @param objects The formatting parameters.
     */
    private void lifecycle(String message, Object... objects) {
        getProject().getLogger().lifecycle("[SourcesJarRemapping]: " + message, objects);
    }

    /**
     * Short circuit method to handle logging a lifecycle message to the projects logger.
     *
     * @param message   The message to log, formatted.
     * @param exception The exception to log.
     */
    private void lifecycle(String message, Throwable exception) {
        getProject().getLogger().lifecycle("[SourcesJarRemapping]: " + message, exception);
    }

    /**
     * Returns the project that is referenced by the parameters.
     *
     * @return The project that this transformer is operating on.
     */
    private Project getProject() {
        return TransformerProjectManager.getInstance().get(getParameters().getProjectPathParameter().get());
    }
}
