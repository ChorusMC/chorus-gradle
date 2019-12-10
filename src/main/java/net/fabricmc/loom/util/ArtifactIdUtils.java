/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.util;

import java.io.File;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.component.Artifact;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;

public final class ArtifactIdUtils {

    private ArtifactIdUtils() {
        throw new IllegalStateException("Tried to initialize: ArtifactIdUtils but this is a Utility class.");
    }

    /**
     * Converts a raw artifact into the identifier in maven form.
     * Needs to be an artifact from an external module.
     *
     * @param resolvedArtifact The artifact to get the identifier for.
     * @return The id.
     */
    public static String createDependencyIdentifierFromRawArtifact(final ResolvedArtifact resolvedArtifact, final Boolean useClassier)
    {
        if (!(resolvedArtifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier))
            throw new IllegalArgumentException("Can only convert artifacts to ids that reference a module");

        final ModuleComponentIdentifier moduleComponentIdentifier = (ModuleComponentIdentifier) resolvedArtifact.getId().getComponentIdentifier();

        String classifierAppendix = "";
        if (useClassier && resolvedArtifact.getClassifier() != null && !resolvedArtifact.getClassifier().isEmpty())
        {
            classifierAppendix = resolvedArtifact.getClassifier();
        }

        if (classifierAppendix.isEmpty())
            return String.format("%s:%s:%s", moduleComponentIdentifier.getGroup(), moduleComponentIdentifier.getModule(), moduleComponentIdentifier.getVersion());

        return String.format("%s:%s:%s:%s", moduleComponentIdentifier.getGroup(), moduleComponentIdentifier.getModule(), moduleComponentIdentifier.getVersion(), classifierAppendix);

    }

    /**
     * Converts a raw artifact into the appended identifier in maven form.
     * Needs to be an artifact from an external module.
     *
     * @param resolvedArtifact The artifact to get the identifier for.
     * @param appendix The appendix to append.
     * @return The id.
     */
    public static String createDependencyIdentifierFromRawArtifactWithAppendix(final ResolvedArtifact resolvedArtifact, final Boolean useClassifier , final String appendix)
    {
        if (!(resolvedArtifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier))
            throw new IllegalArgumentException("Can only convert artifacts to ids that reference a module");

        final ModuleComponentIdentifier moduleComponentIdentifier = (ModuleComponentIdentifier) resolvedArtifact.getId().getComponentIdentifier();

        String classifierAppendix = appendix;
        if (useClassifier && resolvedArtifact.getClassifier() != null && !resolvedArtifact.getClassifier().isEmpty())
        {
            classifierAppendix = resolvedArtifact.getClassifier() + "-" + classifierAppendix;
        }

        return String.format("%s:%s:%s:%s", moduleComponentIdentifier.getGroup(), moduleComponentIdentifier.getModule(), moduleComponentIdentifier.getVersion(), classifierAppendix);

    }

    /**
     * Converts an appended artifact into the identifier in maven form.
     * Needs to be an artifact from an external module.
     *
     * @param resolvedArtifact The sources artifact to get the identifier for.
     * @param appendix The appendix to remove to create the raw module identifier.
     * @return The id.
     */
    public static String createDependencyIdentifierFromAppendedArtifact(final ResolvedArtifact resolvedArtifact, final Boolean useClassifier, final String appendix)
    {
        if (!(resolvedArtifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier))
            throw new IllegalArgumentException("Can only convert artifacts to ids that reference a module");

        final ModuleComponentIdentifier moduleComponentIdentifier = (ModuleComponentIdentifier) resolvedArtifact.getId().getComponentIdentifier();

        String classifierAppendix = "";
        if (useClassifier && resolvedArtifact.getClassifier() != null && !resolvedArtifact.getClassifier().isEmpty())
        {
            classifierAppendix = resolvedArtifact.getClassifier().replace(String.format("-%s", appendix), "").replace(appendix, "");
        }

        if (classifierAppendix.isEmpty())
            return String.format("%s:%s:%s", moduleComponentIdentifier.getGroup(), moduleComponentIdentifier.getModule(), moduleComponentIdentifier.getVersion());

        return String.format("%s:%s:%s:%s", moduleComponentIdentifier.getGroup(), moduleComponentIdentifier.getModule(), moduleComponentIdentifier.getVersion(), classifierAppendix);

    }

    public static boolean doesArtifactExist(DependencyHandler dependencies, ResolvedArtifact artifact, Class<? extends Artifact> artifactClass) {
        @SuppressWarnings("unchecked") ArtifactResolutionQuery query = dependencies.createArtifactResolutionQuery()//
                                                                                       .forComponents(artifact.getId().getComponentIdentifier())
                                                                                       .withArtifacts(JvmLibrary.class, artifactClass);

        for (ComponentArtifactsResult result : query.execute().getResolvedComponents()) {
            for (ArtifactResult srcArtifact : result.getArtifacts(SourcesArtifact.class)) {
                if (srcArtifact instanceof ResolvedArtifactResult) {
                    return true;
                }
            }
        }

        return false;
    }
}
